package ca.monwallet.app.marketdata

import ca.monwallet.app.domain.*
import java.math.BigDecimal
import java.time.LocalDate
import okhttp3.Request
import org.jsoup.Jsoup

/** Public US snapshot. Missing dashes remain missing, never zero. */
class FinvizSnapshotProvider : FundamentalsProvider {
    override val name = "Finviz · ratios publics"
    override suspend fun load(security: Security): Fundamentals? {
        val symbol = ProviderSymbolResolver.usEquity(security) ?: return null
        val html = Http.raw(Request.Builder().url(Http.url("https://finviz.com/quote.ashx", mapOf("t" to symbol)))
            .header("User-Agent", "Mozilla/5.0 MonWallet").build())
        return parse(html, symbol)
    }

    internal fun parse(html: String, symbol: String): Fundamentals? {
        val doc = Jsoup.parse(html)
        // An error page or a different instrument must never populate this title.
        val title = doc.title()
        if (!title.startsWith("$symbol -", ignoreCase = true)) return null
        val cells = doc.select("table.snapshot-table2 td.snapshot-td2").map { it.text().trim() }
        val values = cells.chunked(2).filter { it.size == 2 }.associate { it[0] to it[1] }
        fun number(raw: String?): BigDecimal? = raw?.replace(",", "")?.removeSuffix("%")?.toBigDecimalOrNull()
        fun scaled(raw: String?): BigDecimal? {
            if (raw.isNullOrBlank()) return null
            val multiple = when (raw.last().uppercaseChar()) {
                'T' -> BigDecimal("1000000000000")
                'B' -> BigDecimal("1000000000")
                'M' -> BigDecimal("1000000")
                'K' -> BigDecimal("1000")
                else -> BigDecimal.ONE
            }
            val amount = if (multiple != BigDecimal.ONE) raw.dropLast(1) else raw
            return number(amount)?.multiply(multiple)
        }
        val metrics = linkedMapOf<String, String>()
        mapOf("P/E" to "P/E", "Forward P/E" to "P/E prévisionnel",
            "Debt/Eq" to "Dette / capitaux propres", "LT Debt/Eq" to "Dette long terme / capitaux propres",
            "EPS (ttm)" to "BPA TTM (cotation)", "Profit Margin" to "Marge nette %",
            "ROE" to "ROE %", "ROA" to "ROA %", "Payout" to "Payout %").forEach { (key, label) ->
            number(values[key])?.let { metrics[label] = it.stripTrailingZeros().toPlainString() }
        }
        scaled(values["Market Cap"])?.divide(BigDecimal("1000000"))?.let {
            metrics["Capitalisation (M)"] = it.stripTrailingZeros().toPlainString()
        }
        if (metrics.isEmpty()) return null
        return Fundamentals(metrics = metrics, source = name, ratiosAsOf = LocalDate.now().toString(),
            unavailableMetrics = if (values["P/E"] == "-") listOf("P/E") else emptyList())
    }
}
