package ca.monwallet.app.marketdata

import ca.monwallet.app.domain.*
import okhttp3.Request
import org.jsoup.Jsoup
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Percentages are published weights (0..100), never rescaled to make a partial list total 100. */
data class FundPosition(val symbol: String, val name: String, val weight: BigDecimal,
    val sector: String?, val country: String?, val assetClass: String?)
data class FundComposition(val asOf: String, val positions: List<FundPosition>) {
    fun weights(sector: Boolean): List<Pair<String, BigDecimal>> = positions
        .groupBy { (if (sector) it.sector else it.country)?.takeUnless { s -> s == "-" || s.isBlank() } ?: "Non déterminé" }
        .map { (name, rows) -> name to rows.fold(ZERO) { total, row -> total + row.weight } }
        .sortedByDescending { it.second }
}
data class FundExposure(val asOf: String, val weights: Map<String, BigDecimal>)

data class EtfDetails(val name: String, val issuer: String, val sourceUrl: String,
    val direct: FundComposition, val underlying: FundComposition?,
    val managementFee: String?, val mer: String?, val distributionFrequency: String?,
    val netAssets: String?, val fetchedAt: Long = System.currentTimeMillis(), val compositionNote: String? = null,
    val sectorExposure: FundExposure? = null, val countryExposure: FundExposure? = null)

data class EtfLoad(val details: EtfDetails?, val cached: Boolean = false, val error: String? = null)

object EtfData {
    private const val root = "https://www.blackrock.com"
    // Full identities avoid collisions such as Canadian GURU shares and the US GURU fund.
    fun officialPage(s: Security): String? = when {
        s.type.uppercase() != "ETF" -> null
        s.currency == "CAD" && s.symbol.uppercase() in setOf("XEQT", "XEQT.TO") &&
            s.exchange.uppercase() in setOf("TSX", "TOR", "TORONTO") ->
            "$root/ca/investors/en/products/309480/ishares-core-equity-etf-portfolio"
        s.currency == "CAD" && s.symbol.uppercase() in setOf("VFV", "VFV.TO") &&
            s.exchange.uppercase() in setOf("TSX", "TOR", "TORONTO") ->
            "https://www.vanguard.ca/en/product/etf/equity/9563/vanguard-sp-500-index-etf"
        else -> null
    }
    private suspend fun get(url: String) = Http.raw(Request.Builder().url(url)
        .header("User-Agent", "Mozilla/5.0 MonWallet ETF holdings").build())

    @Volatile private var catalog: Pair<Long, Map<String, String>>? = null

    internal fun parseCatalog(html: String): Map<String, String> = Jsoup.parse(html, root)
        .select("table a[href]").mapNotNull { a ->
            val ticker = a.text().trim()
            val url = a.absUrl("href").substringBefore('?').substringBefore('#')
            if (ticker.matches(Regex("[A-Z0-9][A-Z0-9.\\-]{0,14}")) &&
                url.startsWith("$root/ca/investors/en/products/") &&
                url.substringAfter("/products/").substringBefore('/').toLongOrNull() != null)
                ticker to url else null
        }.toMap()

    private suspend fun resolvePage(s: Security): String? {
        officialPage(s)?.let { return it }
        if (s.type.uppercase() != "ETF" || s.currency !in setOf("CAD", "USD") ||
            s.exchange.uppercase() !in setOf("TSX", "TOR", "TORONTO")) return null
        var current = catalog
        if (current == null || System.currentTimeMillis() - current.first > 24 * 3600_000L) {
            val fresh = parseCatalog(get("$root/ca/investors/en/products/product-list"))
            check(fresh.isNotEmpty()) { "Catalogue de l’émetteur indisponible." }
            current = System.currentTimeMillis() to fresh
            catalog = current
        }
        return current.second[s.symbol.uppercase().removeSuffix(".TO")]
    }

    suspend fun fetch(s: Security): EtfDetails {
        if (s.type.uppercase() != "ETF") error("Ce titre n’est pas un FNB.")
        if (s.currency == "CAD" && (s.symbol.uppercase().removeSuffix(".TO").startsWith("V"))) {
            try { return VanguardFunds.fetch(s) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
        }
        if (s.currency == "CAD" && (s.symbol.startsWith("H", true) || s.symbol.uppercase() in setOf("CASH.TO", "CBIL.TO") || s.name.contains("Global X", true))) {
            try { return GlobalXFunds.fetch(s) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { if (ca.monwallet.app.BuildConfig.DEBUG) android.util.Log.d("FundCoverage", "Global X ${s.symbol}: ${e.message}") }
        }
        try { return fetchIshares(s) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { return BroadFundData.fetch(s) }
    }

    private suspend fun fetchIshares(s: Security): EtfDetails {
        val page = resolvePage(s) ?: error("Composition non couverte pour ce FNB pour le moment.")
        check(page.startsWith(root)) { "Composition automatique non couverte pour ce fournisseur pour le moment. La fiche officielle reste accessible." }
        val html = get(page)
        val doc = Jsoup.parse(html, page)
        // Reject consent/error pages and fund identity changes instead of caching unrelated data.
        check(doc.title().substringAfterLast('|').trim().equals(s.symbol.removeSuffix(".TO"), ignoreCase = true)) { "Identité du fonds non confirmée." }
        val link = doc.select("#holdings a[href]").firstOrNull {
            it.attr("href").contains("fileType=csv") }?.absUrl("href")
            ?: error("Fichier de composition indisponible chez l’émetteur.")
        check(link.startsWith("$root/ca/investors/en/products/")) { "Source de composition non reconnue." }
        val compositions = parseHoldingsCsv(get(link))
        fun field(selector: String) = doc.selectFirst(selector)?.text()?.takeIf { it.isNotBlank() && it != "-" }
        return EtfDetails(doc.select("h1").firstOrNull { it.text().isNotBlank() }?.text() ?: s.name,
            "BlackRock / iShares", page, compositions.first(), compositions.getOrNull(1) ?: compositions.first().takeIf {
                !doc.select("#holdingsTabs").text().contains("Aggregate Underlying Holdings") },
            field(".fee-code-mgt .data"), field(".fee-code-mer .data"),
            field(".col-distributionFrequency .data"), field(".col-totalNetAssets .data"))
    }

    /** iShares CSV contains separate dated tables: direct positions, then look-through holdings. */
    fun parseHoldingsCsv(text: String): List<FundComposition> {
        val rows = csvRows(text.removePrefix("\uFEFF"))
        val out = mutableListOf<FundComposition>()
        var date: String? = null
        var header: List<String>? = null
        var positions = mutableListOf<FundPosition>()
        fun finish() {
            if (positions.isNotEmpty()) {
                val day = date ?: error("Date de composition absente.")
                out += FundComposition(day, positions.toList())
            }
            positions = mutableListOf()
        }
        for (row in rows) {
            if (row.firstOrNull() == "Fund Holdings as of") {
                finish(); header = null
                date = row.getOrNull(1)?.trim()?.let {
                    runCatching { LocalDate.parse(it, DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US)).toString() }.getOrNull()
                }
            } else if (row.firstOrNull() == "Ticker" && "Weight (%)" in row) {
                header = row
            } else {
                val columns = header ?: continue
                fun value(key: String) = row.getOrNull(columns.indexOf(key))?.trim()
                val weight = value("Weight (%)")?.replace(",", "")?.toBigDecimalOrNull() ?: continue
                val symbol = value("Ticker")?.takeIf { it.isNotBlank() } ?: continue
                val name = value("Name")?.takeIf { it.isNotBlank() } ?: continue
                // Cash/derivative rows, including signed weights, remain part of published coverage.
                positions += FundPosition(symbol, name, weight, value("Sector"), value("Location"), value("Asset Class"))
            }
        }
        finish()
        check(out.isNotEmpty()) { "Composition de l’émetteur vide ou format non reconnu." }
        return out
    }

    internal fun csvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val fields = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        fun field() { fields += cell.toString(); cell.setLength(0) }
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> field()
                (c == '\n' || c == '\r') && !quoted -> {
                    field(); rows += fields.toList(); fields.clear()
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> cell.append(c)
            }
            i++
        }
        check(!quoted) { "Fichier de composition incomplet." }
        if (cell.isNotEmpty() || fields.isNotEmpty()) { field(); rows += fields.toList() }
        return rows
    }
}
