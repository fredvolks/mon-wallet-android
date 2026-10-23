package ca.monwallet.app.domain

import ca.monwallet.app.marketdata.MarketSession
import ca.monwallet.app.marketdata.NormalizedQuote
import ca.monwallet.app.marketdata.supportsUsExtendedHours
import java.time.ZoneId

/** Session reference: last regular close outside 09:30–16:00; never a midnight price.
 * The legacy object/JSON field names remain readable for existing installations.
 */
object CivilDayPnl {
    val zone: ZoneId get() = MarketClock.zone
    fun date(now: Long): String = MarketClock.date(now).toString()

    fun capture(q: Quote, previous: Quote?, now: Long): Quote {
        // Deliberately discard v58/v59 midnight references, including already persisted ones.
        val outside = MarketClock.offSession(now)
        return q.copy(dayReferenceDate = date(now),
            dayReferencePrice = if (outside || q.sessionDate != date(now)) q.price else q.previous,
            dayReferenceTimestamp = if (outside) q.timestamp else null)
    }

    fun dailyQuote(q: Quote, security: Security?, now: Long): Quote {
        val outside = MarketClock.offSession(now)
        val normalized = security?.takeIf { supportsUsExtendedHours(it) }
            ?.let { NormalizedQuote.from(q, now) }
        val current = when (normalized?.marketSession) {
            MarketSession.PRE_MARKET -> normalized.preMarketPrice
            MarketSession.AFTER_HOURS -> normalized.afterHoursPrice
            else -> null
        } ?: q.price
        // At 09:30 yesterday's cached regular move must not reappear while awaiting a new print.
        val reference = if (outside || q.sessionDate != date(now)) q.price else q.previous
        return q.copy(price = if (outside) current else q.price, previous = reference,
            sessionDate = date(now), dayReferenceDate = date(now),
            dayReferencePrice = reference, dayReferenceTimestamp = null)
    }
}
