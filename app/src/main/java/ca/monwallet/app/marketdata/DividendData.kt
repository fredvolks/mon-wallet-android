package ca.monwallet.app.marketdata

import ca.monwallet.app.domain.*
import okhttp3.Request
import org.json.JSONObject
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException

data class DividendPayment(val date: String, val amount: BigDecimal)
data class DividendSnapshot(val annualAmount: BigDecimal, val yield: BigDecimal?,
    val frequency: String, val growth: BigDecimal?, val lastExDate: String?,
    val asOf: String, val currency: String, val source: String = "Yahoo Finance · distributions historiques sur 12 mois")

object DividendData {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, DividendSnapshot>>()
    suspend fun fetch(s: Security): DividendSnapshot {
        cache[s.id]?.takeIf { System.currentTimeMillis() - it.first < 6 * 3600_000L }?.let { return it.second }
        val json = Http.json(Request.Builder().url(Http.url(
            "https://query1.finance.yahoo.com/v8/finance/chart/${java.net.URLEncoder.encode(yahooDataSymbol(s.symbol), "UTF-8")}",
            mapOf("range" to "5y", "interval" to "1mo", "events" to "div")))
            .header("User-Agent", "Mozilla/5.0 MonWallet/0.1").build())
        val result = json.getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val meta = result.getJSONObject("meta")
        check(meta.text("currency") == s.currency) { "Devise des dividendes non confirmée." }
        check(meta.text("symbol")?.equals(yahooDataSymbol(s.symbol), true) == true) { "Identité des dividendes non confirmée." }
        val dividends = result.optJSONObject("events")?.optJSONObject("dividends")
        val payments = dividends?.keys()?.asSequence()?.mapNotNull { id ->
            val row = dividends.optJSONObject(id) ?: return@mapNotNull null
            val timestamp = row.optLong("date", 0L)
            val amount = row.number("amount")?.takeIf { it >= ZERO } ?: return@mapNotNull null
            if (timestamp <= 0) null else DividendPayment(Instant.ofEpochSecond(timestamp)
                .atZone(ZoneOffset.UTC).toLocalDate().toString(), amount)
        }?.toList().orEmpty()
        val now = MarketClock.date(System.currentTimeMillis())
        val first = meta.optLong("firstTradeDate", 0).takeIf { it != 0L }?.let {
            Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).toLocalDate() }
        val snapshot = calculate(payments, meta.number("regularMarketPrice"), s.currency, now, first)
        cache[s.id] = System.currentTimeMillis() to snapshot
        return snapshot
    }
    fun calculate(payments: List<DividendPayment>, price: BigDecimal?, currency: String,
        asOf: LocalDate, listedSince: LocalDate?): DividendSnapshot {
        val rows = payments.filter { LocalDate.parse(it.date) <= asOf }.distinctBy { it.date to it.amount }.sortedBy { it.date }
        val start = asOf.minusYears(1)
        val current = rows.filter { LocalDate.parse(it.date) > start }
        val previous = rows.filter { LocalDate.parse(it.date) > start.minusYears(1) && LocalDate.parse(it.date) <= start }
        val amount = current.fold(ZERO) { a, p -> a + p.amount }
        val prior = previous.fold(ZERO) { a, p -> a + p.amount }
        val complete = listedSince != null && listedSince <= start
        val growth = if (listedSince != null && listedSince <= start.minusYears(1) && prior > ZERO)
            (amount - prior).pct(prior) else null
        val gaps = current.zipWithNext().map { (a,b) -> java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(a.date), LocalDate.parse(b.date)) }
        val frequency = when {
            !complete || current.isEmpty() -> "UNKNOWN"
            gaps.size >= 8 && gaps.all { it in 20..42 } -> "MONTHLY"
            gaps.size >= 2 && gaps.all { it in 60..120 } -> "QUARTERLY"
            gaps.size == 1 && gaps.single() in 150..215 -> "SEMI_ANNUAL"
            current.size == 1 && previous.isNotEmpty() -> "ANNUAL"
            else -> "IRREGULAR"
        }
        return DividendSnapshot(amount, if (complete && price != null && price > ZERO) amount.pct(price) else null,
            frequency, growth, current.lastOrNull()?.date, asOf.toString(), currency)
    }
}

internal fun dividendEligible(filters: Filters, snapshot: DividendSnapshot?, fallbackYield: BigDecimal?, payout: BigDecimal?): Boolean {
    val yield = snapshot?.yield ?: fallbackYield
    if (filters.dividendOnly && (yield == null || yield <= ZERO)) return false
    if (filters.dividendMin != null && (yield == null || yield < filters.dividendMin)) return false
    if (filters.dividendMax != null && (yield == null || yield > filters.dividendMax)) return false
    if (filters.dividendFrequency.isNotBlank() && snapshot?.frequency != filters.dividendFrequency) return false
    if (filters.dividendGrowthMin != null && (snapshot?.growth == null || snapshot.growth < filters.dividendGrowthMin)) return false
    if (filters.payoutMax != null && (payout == null || payout < ZERO || payout > filters.payoutMax)) return false
    return true
}
