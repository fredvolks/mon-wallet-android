package ca.monwallet.app.marketdata

import ca.monwallet.app.domain.*
import kotlinx.coroutines.*
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Public market evidence only. No wallet identity or transactions in this snapshot. */
data class ScreenCandidate(
    val listing: String, val industry: String, val sector: String,
    val values: Map<String, Double>, val observedAt: Long,
    val score: Double? = null, val coverage: Int = 0, val flags: List<String> = emptyList(),
) {
    val sourceUrl get() = "https://www.tradingview.com/symbols/" + listing.replace(':', '-') + "/"
}
data class ScreenSummary(val received: Int, val eligible: Int, val excludedBiotech: Int,
    val incomplete: Int, val createdAt: Long, val financialFilters: Boolean, val version: Int = 1)
data class ScreenResult(val stocks: List<ResearchStock>, val summary: ScreenSummary)
data class ScreenOpinion(val listing: String, val priority: Int?, val verdict: String,
    val reason: String, val risk: String, val nextCheck: String,
    val model: String = "", val createdAt: Long = 0)

object StockScreener {
    const val VERSION = 1
    val exchanges = setOf("NASDAQ", "NYSE", "AMEX", "TSX", "TSXV", "CSE", "NEO")
    val biotech = setOf("Biotechnology", "Pharmaceuticals: Major")
    val columns = listOf("name", "description", "exchange", "country", "sector", "industry", "close", "currency",
        "price_earnings_ttm", "return_on_equity", "debt_to_equity", "total_revenue_yoy_growth_ttm",
        "earnings_per_share_diluted_yoy_growth_ttm", "net_margin", "Perf.6M", "average_volume_30d_calc",
        "free_cash_flow_ttm", "cash_f_operating_activities_ttm", "net_income_ttm", "price_book_fq", "return_on_assets")
    val weights = linkedMapOf("price_earnings_ttm" to 20.0, "return_on_equity" to 15.0,
        "debt_to_equity" to 20.0, "total_revenue_yoy_growth_ttm" to 12.5,
        "earnings_per_share_diluted_yoy_growth_ttm" to 12.5, "net_margin" to 5.0, "Perf.6M" to 15.0)

    suspend fun scan(country: String, financialFilters: Boolean = true, status: (String) -> Unit): ScreenResult {
        status("Scan des bourses US et canadiennes…")
        val filters = JSONArray().put(JSONObject().put("left", "is_primary").put("operation", "equal").put("right", true))
            .put(JSONObject().put("left", "type").put("operation", "equal").put("right", "stock"))
            .put(JSONObject().put("left", "exchange").put("operation", "in_range").put("right", JSONArray(exchanges.toList())))
            .put(JSONObject().put("left", "close").put("operation", "egreater").put("right", 1))
        val body = JSONObject().put("filter", filters).put("columns", JSONArray(columns)).put("range", JSONArray(listOf(0, 20000)))
        val raw = Http.raw(Request.Builder().url("https://scanner.tradingview.com/global/scan")
            .header("User-Agent", "MonWallet/StockResearch").post(body.toString().toRequestBody("application/json".toMediaType())).build())
        currentCoroutineContext().ensureActive()
        status("Classement sectoriel et sélection des 200 candidats…")
        return withContext(Dispatchers.Default) { parse(JSONObject(raw), country, financialFilters, System.currentTimeMillis()) }
    }

    fun parse(json: JSONObject, country: String, financialFilters: Boolean, now: Long): ScreenResult {
        val data = json.optJSONArray("data") ?: error("Le screener n’a fourni aucun univers. Aucun ancien catalogue n’est utilisé à sa place.")
        val total = json.optInt("totalCount", -1)
        require(total >= 0 && total == data.length()) { "Réponse partielle du screener : ${data.length()} / $total. Relance le scan." }
        require(total > 0) { "Aucun titre reçu du fournisseur." }
        var excludedBio = 0; var incomplete = 0
        val rows = (0 until data.length()).mapNotNull { index ->
            val entry = data.optJSONObject(index) ?: return@mapNotNull null
            val d = entry.optJSONArray("d") ?: return@mapNotNull null
            val listing = entry.optString("s"); val exchange = d.optString(2); val ticker = d.optString(0)
            if (exchange !in exchanges || listing != "$exchange:$ticker" || !ticker.matches(Regex("[A-Za-z0-9.\\-]{1,25}"))) return@mapNotNull null
            val currency = d.optString(7); val price = d.optDouble(6, Double.NaN)
            if (currency !in setOf("CAD", "USD") || !price.isFinite() || price < 1.0) return@mapNotNull null
            if (country == "Canada" && exchange !in setOf("TSX", "TSXV", "CSE", "NEO")) return@mapNotNull null
            if (country == "États-Unis" && exchange !in setOf("NASDAQ", "NYSE", "AMEX")) return@mapNotNull null
            val industry = d.optString(5)
            if (industry in biotech) { excludedBio++; return@mapNotNull null }
            // Source 'stock' includes split funds and investment trusts. Do not rank those as operating companies.
            if (industry == "Investment Trusts/Mutual Funds") return@mapNotNull null
            val values = columns.mapIndexedNotNull { i, key -> d.optDouble(i, Double.NaN).takeIf { it.isFinite() }?.let { key to it } }.toMap()
            val complete = weights.keys.all { values[it] != null }
            if (!complete) incomplete++
            if (financialFilters && (!complete || values.getValue("price_earnings_ttm") <= 0 ||
                    values.getValue("return_on_equity") <= 0 || values.getValue("net_margin") <= 0 || values.getValue("debt_to_equity") < 0)) return@mapNotNull null
            val symbol = ticker.replace('.', '-').let { normalized -> when (exchange) {
                "TSX" -> "$normalized.TO"; "TSXV" -> "$normalized.V"; "CSE" -> "$normalized.CN"; "NEO" -> "$normalized.NE"; else -> normalized } }
            val s = Security.of(symbol, d.optString(1).ifBlank { ticker }, exchange, currency).copy(sector = sector(d.optString(4)), country = d.optString(3))
            val screen = ScreenCandidate(listing, industry, d.optString(4), values, now,
                coverage = weights.filterKeys { values[it] != null }.values.sum().toInt(), flags = flags(values, d.optString(4)))
            ResearchStock(s, Quote(s.id, price.toBigDecimal(), null, currency, now,
                Instant.ofEpochMilli(now).atZone(java.time.ZoneId.of("America/Toronto")).toLocalDate().toString(),
                "TradingView · relevé possiblement différé", averageVolume = values["average_volume_30d_calc"]?.toBigDecimal()),
                Fundamentals(mapOfNotNull("P/E" to values["price_earnings_ttm"], "Dette / capitaux propres" to values["debt_to_equity"],
                    "Rendement des capitaux propres" to values["return_on_equity"]).mapValues { it.value.toString() }, source = "TradingView · ratios agrégés, date des états non confirmée"),
                emptyList(), emptyList(), emptyList(), listOf("Heure exacte de cotation non fournie : la date affichée est celle du relevé.",
                    "Nouvelles, bénéfices normalisés et états détaillés non vérifiés par ce préfiltre."), screen)
        }.distinctBy { it.screen!!.listing }
        val ranked = rank(rows).take(200)
        return ScreenResult(ranked, ScreenSummary(total, rows.size, excludedBio, incomplete, now, financialFilters))
    }
    private fun mapOfNotNull(vararg pairs: Pair<String, Double?>) = pairs.mapNotNull { (k,v) -> v?.let { k to it } }.toMap()
    fun rank(rows: List<ResearchStock>): List<ResearchStock> {
        val groups = rows.groupBy { it.screen!!.sector }
        val ranked = rows.map { row ->
            val evidence = row.screen!!
            val peers = groups.getValue(evidence.sector).takeIf { it.size >= 5 } ?: rows
            val score = if (weights.keys.any { evidence.values[it] == null }) null else weights.entries.sumOf { (key, weight) ->
                val v = evidence.values.getValue(key)
                val values = peers.mapNotNull { it.screen!!.values[key] }
                val less = values.count { it < v }; val equal = values.count { it == v }
                val percentile = if (values.size <= 1) .5 else (less + (equal - 1) / 2.0) / (values.size - 1)
                if ((key == "price_earnings_ttm" && v <= 0) || (key == "debt_to_equity" && v < 0)) 0.0
                else weight * if (key in setOf("price_earnings_ttm", "debt_to_equity")) 1 - percentile else percentile
            }
            row.copy(screen = evidence.copy(score = score))
        }
        return ranked.sortedWith(compareByDescending<ResearchStock> { it.screen!!.score ?: -1.0 }.thenBy { it.screen!!.listing })
    }
    private fun flags(v: Map<String, Double>, sector: String) = buildList {
        if (sector == "Finance") add("Modèle financier : capital, crédit, réserves ou actif net à analyser séparément.")
        if (v["average_volume_30d_calc"] == null) add("Volume moyen indisponible.")
        else if (v.getValue("average_volume_30d_calc") < 50000) add("Moins de 50 000 actions/jour : liquidité et spread à vérifier.")
        if (v["free_cash_flow_ttm"] == null) add("Flux disponible non couvert.")
        else if (v.getValue("free_cash_flow_ttm") < 0) add("Flux disponible négatif.")
        if ((v["earnings_per_share_diluted_yoy_growth_ttm"] ?: 0.0) > 300) add("Croissance du BPA extrême : effet de base ou profit ponctuel possible.")
        if ((v["net_margin"] ?: 0.0) > 80 || (v["return_on_equity"] ?: 0.0) > 100) add("Rentabilité atypique : vérifier éléments exceptionnels et capitaux propres.")
        if ((v["debt_to_equity"] ?: 0.0) > 2) add("Levier élevé ou capitaux propres faibles.")
    }
    private fun sector(value: String): String? = when (value) {
        "Electronic Technology", "Technology Services" -> "Technology"
        "Finance" -> "Financials"; "Health Technology", "Health Services" -> "Healthcare"
        "Non-Energy Minerals", "Process Industries" -> "Materials"; "Energy Minerals" -> "Energy"
        "Producer Manufacturing", "Industrial Services", "Transportation", "Commercial Services", "Distribution Services" -> "Industrials"
        "Consumer Durables", "Consumer Services", "Retail Trade" -> "Consumer Cyclical"
        "Consumer Non-Durables" -> "Consumer Defensive"; "Communications" -> "Communication Services"; "Utilities" -> "Utilities"
        else -> null
    }
}
