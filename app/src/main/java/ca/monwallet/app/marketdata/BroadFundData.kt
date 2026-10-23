package ca.monwallet.app.marketdata

import ca.monwallet.app.domain.Security
import okhttp3.Request
import org.jsoup.Jsoup
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Dated public top holdings fallback. Never presented as a complete issuer portfolio. */
object BroadFundData {
    internal fun url(s: Security): String {
        require(s.type.uppercase() == "ETF")
        val symbol = s.symbol.uppercase().removeSuffix(".TO")
        require(symbol.matches(Regex("[A-Z0-9][A-Z0-9.-]{0,14}")))
        return when {
            s.exchange.uppercase() in setOf("TSX", "TOR", "TORONTO") && s.currency in setOf("CAD", "USD") ->
                "https://stockanalysis.com/quote/tsx/$symbol/holdings/"
            s.exchange.uppercase() in setOf("NASDAQ", "NASDAQGS", "NMS", "NYSE", "NYSEARCA", "NYSE ARCA", "AMEX", "PCX", "BATS") && s.currency == "USD" ->
                "https://stockanalysis.com/etf/${symbol.lowercase()}/holdings/"
            else -> error("Cette place boursière n’est pas couverte.")
        }
    }
    suspend fun fetch(s: Security): EtfDetails {
        val url = url(s)
        try { return parse(s, Http.raw(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 MonWallet").build()), url) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) {
            val overview = url.removeSuffix("holdings/")
            return parse(s, Http.raw(Request.Builder().url(overview).header("User-Agent", "Mozilla/5.0 MonWallet").build()), overview)
        }
    }
    internal fun parse(s: Security, html: String, url: String): EtfDetails {
        val doc = Jsoup.parse(html)
        val expected = if (url.contains("/quote/tsx/")) "TSX:${s.symbol.uppercase().removeSuffix(".TO")}" else s.symbol.uppercase()
        val title = doc.select("h1").text()
        val holdingsPage = url.endsWith("/holdings/")
        check(title.startsWith("$expected Holdings", ignoreCase = true) || (!holdingsPage && title.contains("($expected)", ignoreCase = true))) { "Identité du fonds non confirmée." }
        val body = doc.text()
        val rawDate = if (holdingsPage) Regex("lastUpdated:\"([A-Z][a-z]{2} \\d{1,2}, \\d{4})\"").find(html)?.groupValues?.get(1)
            ?: Regex("[A-Z][a-z]{2} \\d{1,2}, \\d{4}").find(body.substringBefore("Filter results").substringAfterLast("Holdings"))?.value
            ?: error("Date de composition indisponible.") else null
        val day = rawDate?.let { LocalDate.parse(it, DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US)).toString() }.orEmpty()
        // Locate columns by their headers, rather than assuming the order of prices/weights.
        val table = doc.select("table").firstOrNull { it.select("th").any { th -> th.text().contains("Weight") } }
            ?: error("Table de pondérations indisponible.")
        val headers = table.select("thead th").map { it.text() }
        val weightIndex = headers.indexOfFirst { it.contains("Weight") }
        val symbolIndex = headers.indexOfFirst { it.contains("Symbol") }
        val nameIndex = headers.indexOfFirst { it == "Name" }
        check(weightIndex >= 0 && symbolIndex >= 0 && nameIndex >= 0)
        val positions = table.select("tbody tr").mapNotNull { row ->
            val cells = row.select("td")
            val weight = cells.getOrNull(weightIndex)?.text()?.removeSuffix("%")?.replace(",", "")?.toBigDecimalOrNull()
                ?: return@mapNotNull null
            val name = cells.getOrNull(nameIndex)?.text() ?: return@mapNotNull null
            val symbol = cells.getOrNull(symbolIndex)?.text()?.takeUnless { it in setOf("n/a", "—", "-") } ?: name
            FundPosition(symbol, name, weight, null, null, null)
        }
        check(positions.isNotEmpty()) { "Pondérations indisponibles." }
        return EtfDetails(s.name, "Stock Analysis · source secondaire", url,
            FundComposition(day, positions), null, null, null, null, null,
            compositionNote = (if (day.isBlank()) "Date de composition non fournie par cette source : données indicatives, à vérifier auprès de l’émetteur. " else "") + "Principales positions publiées par Stock Analysis, couverture partielle (${positions.size} lignes). La source ne garantit pas la distinction positions directes / sous-jacentes. Les poids ne sont pas renormalisés; les secteurs et pays ne sont pas extrapolés.")
    }
}
