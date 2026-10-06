package ca.monwallet.app.marketdata

import ca.monwallet.app.domain.Quote
import ca.monwallet.app.domain.Security
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

enum class QuoteFreshness { REALTIME, DELAYED, CACHED, STALE }
enum class MarketSession { PRE_MARKET, REGULAR, AFTER_HOURS, CLOSED }

data class NormalizedQuote(
    val securityId: String,
    val price: BigDecimal,
    val change: BigDecimal?,
    val changePercent: BigDecimal?,
    val timestamp: Long,
    val source: String,
    val freshness: QuoteFreshness,
    val marketSession: MarketSession,
    val regularPrice: BigDecimal,
    val regularChange: BigDecimal?,
    val regularChangePercent: BigDecimal?,
    val preMarketPrice: BigDecimal?,
    val preMarketChange: BigDecimal?,
    val preMarketChangePercent: BigDecimal?,
    val preMarketTimestamp: Long?,
    val afterHoursPrice: BigDecimal?,
    val afterHoursChange: BigDecimal?,
    val afterHoursChangePercent: BigDecimal?,
    val afterHoursTimestamp: Long?,
    val preMarketFreshness: QuoteFreshness?,
    val afterHoursFreshness: QuoteFreshness?,
) {
    companion object {
        private val newYork = ZoneId.of("America/New_York")

        private fun currentExtendedSession(now: Long): MarketSession? {
            val time = Instant.ofEpochMilli(now).atZone(newYork)
            if (time.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) return null
            return when {
                time.toLocalTime() >= LocalTime.of(4, 0) &&
                    time.toLocalTime() < LocalTime.of(9, 30) -> MarketSession.PRE_MARKET
                time.toLocalTime() >= LocalTime.of(16, 0) &&
                    time.toLocalTime() < LocalTime.of(20, 0) -> MarketSession.AFTER_HOURS
                else -> null
            }
        }

        fun from(q: Quote, now: Long = System.currentTimeMillis()): NormalizedQuote {
            val providerSession = runCatching { MarketSession.valueOf(q.marketSession) }
                .getOrDefault(MarketSession.CLOSED)
            val active = currentExtendedSession(now)
            val extendedTime = when (providerSession) {
                MarketSession.PRE_MARKET -> q.preMarketTimestamp
                MarketSession.AFTER_HOURS -> q.afterHoursTimestamp
                else -> null
            }
            // A cached quote must not carry yesterday's PRE/AFTER label into a new session.
            val sameDay = extendedTime?.let {
                Instant.ofEpochMilli(it).atZone(newYork).toLocalDate() ==
                    Instant.ofEpochMilli(now).atZone(newYork).toLocalDate()
            } == true
            val session = if (providerSession in setOf(MarketSession.PRE_MARKET,
                    MarketSession.AFTER_HOURS) && (active != providerSession || !sameDay))
                MarketSession.CLOSED else providerSession
            val age = (now - q.fetchedAt).coerceAtLeast(0)
            val freshness = when {
                age > 30 * 60_000 -> QuoteFreshness.STALE
                q.delay != null && q.delay > 0 -> QuoteFreshness.DELAYED
                // Neither the unofficial Yahoo endpoint nor a generic Twelve quote proves
                // a real-time exchange entitlement. Do not label either as REALTIME.
                else -> QuoteFreshness.CACHED
            }
            val pre = q.preMarketPrice?.takeIf { session == MarketSession.PRE_MARKET }
            val post = q.afterHoursPrice?.takeIf { session == MarketSession.AFTER_HOURS }
            val extra = pre ?: post
            val delta = extra?.minus(q.price)
            val extraPercent = delta?.takeIf { q.price.signum() != 0 }
                ?.multiply(BigDecimal(100))?.divide(q.price, java.math.MathContext.DECIMAL128)
            fun extendedFreshness(time: Long?): QuoteFreshness? = when {
                time == null -> null
                now - time > 30 * 60_000 -> QuoteFreshness.STALE
                (q.delay ?: 0) > 0 -> QuoteFreshness.DELAYED
                else -> QuoteFreshness.CACHED
            }
            return NormalizedQuote(q.securityId, q.price, q.change, q.percent, q.timestamp,
                q.source, freshness, session, q.price, q.change, q.percent,
                pre, if (pre != null) q.preMarketChange ?: delta else null,
                if (pre != null) q.preMarketChangePercent ?: extraPercent else null,
                q.preMarketTimestamp?.takeIf { pre != null },
                post, if (post != null) q.afterHoursChange ?: delta else null,
                if (post != null) q.afterHoursChangePercent ?: extraPercent else null,
                q.afterHoursTimestamp?.takeIf { post != null },
                extendedFreshness(q.preMarketTimestamp?.takeIf { pre != null }),
                extendedFreshness(q.afterHoursTimestamp?.takeIf { post != null }))
        }
    }
}

interface LiveQuoteProvider {
    /** Returns provider data only; the caller owns foreground lifecycle and polling cadence. */
    suspend fun quote(security: Security): LiveQuoteUpdate
}

data class LiveQuoteUpdate(val cached: Quote, val normalized: NormalizedQuote)

class ForegroundQuoteProvider(private val market: MarketDataProvider) : LiveQuoteProvider {
    override suspend fun quote(security: Security): LiveQuoteUpdate {
        val raw = market.quote(security)
        return LiveQuoteUpdate(raw, NormalizedQuote.from(raw))
    }
}

fun quoteFreshnessLabel(q: Quote, now: Long = System.currentTimeMillis()): String {
    val normalized = NormalizedQuote.from(q, now)
    val minutes = ((now - q.fetchedAt).coerceAtLeast(0) / 60_000).toInt()
    return when (normalized.freshness) {
        QuoteFreshness.REALTIME -> "Temps réel"
        QuoteFreshness.DELAYED -> "Différé " + q.delay + " min"
        QuoteFreshness.CACHED -> "Cache · mis à jour il y a " + minutes + " min"
        QuoteFreshness.STALE -> "Cache · " + minutes + " min"
    }
}
