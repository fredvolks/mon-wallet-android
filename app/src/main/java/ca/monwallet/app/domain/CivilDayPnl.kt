package ca.monwallet.app.domain

import ca.monwallet.app.marketdata.MarketSession
import ca.monwallet.app.marketdata.NormalizedQuote
import ca.monwallet.app.marketdata.supportsUsExtendedHours
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

/** Civil-day P&L uses actual recorded prices, never yesterday's full-session change. */
object CivilDayPnl {
    val zone: ZoneId = ZoneId.of("America/Toronto")
    fun date(now: Long): String = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()

    private fun prints(q: Quote): List<Pair<Long, BigDecimal>> = listOfNotNull(
        q.timestamp.takeIf { it > 0 }?.let { it to q.price },
        q.preMarketTimestamp?.takeIf { q.currency == "USD" }?.let { time -> q.preMarketPrice?.let { time to it } },
        q.afterHoursTimestamp?.takeIf { q.currency == "USD" }?.let { time -> q.afterHoursPrice?.let { time to it } })

    fun capture(q: Quote, previous: Quote?, now: Long): Quote {
        val date = date(now)
        if (previous?.dayReferenceDate == date && previous.dayReferencePrice != null)
            return q.copy(dayReferenceDate = date, dayReferencePrice = previous.dayReferencePrice,
                dayReferenceTimestamp = previous.dayReferenceTimestamp)
        if (q.dayReferenceDate == date && q.dayReferencePrice != null) return q
        val midnight = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val lastBeforeMidnight = (prints(q) + previous?.let { prints(it) }.orEmpty())
            .filter { it.first <= midnight && it.second.signum() > 0 }
            .maxByOrNull { it.first }
        // A first intraday install may only have the provider's preceding close.
        // Keep the reference timestamp absent in that case; do not invent a midnight trade.
        val reference = lastBeforeMidnight?.second ?: q.previous ?: q.price
        return q.copy(dayReferenceDate = date, dayReferencePrice = reference,
            dayReferenceTimestamp = lastBeforeMidnight?.first)
    }

    fun dailyQuote(q: Quote, security: Security?, now: Long): Quote {
        val referenced = capture(q, null, now)
        val normalized = security?.takeIf { supportsUsExtendedHours(it) }
            ?.let { NormalizedQuote.from(q, now) }
        val current = when (normalized?.marketSession) {
            MarketSession.PRE_MARKET -> normalized.preMarketPrice
            MarketSession.AFTER_HOURS -> normalized.afterHoursPrice
            else -> null
        } ?: q.price
        return referenced.copy(price = current, previous = referenced.dayReferencePrice,
            sessionDate = date(now))
    }
}
