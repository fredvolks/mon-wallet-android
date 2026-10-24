package ca.monwallet.app.marketdata

import ca.monwallet.app.Services
import ca.monwallet.app.database.Cache
import ca.monwallet.app.domain.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate

data class ResearchStock(val security: Security, val quote: Quote?, val fundamentals: Fundamentals?,
    val points: List<Point>, val news: List<News>, val earnings: List<Earnings>, val gaps: List<String>, val screen: ScreenCandidate? = null) {
    val monthReturn: BigDecimal? get() = points.takeIf { it.size >= 22 }?.let {
        (it.last().close - it[it.size - 22].close).pct(it[it.size - 22].close) }
    val average50: BigDecimal? get() = points.takeLast(50).takeIf { it.size == 50 }?.fold(ZERO) { n, p -> n + p.close }?.divide(BigDecimal("50"), MC)
}
data class ResearchPosition(val symbol: String, val name: String, val quantity: BigDecimal, val valueCad: BigDecimal?, val currency: String, val sector: String?, val quoteTime: Long?)
data class ResearchSource(val id: String, val label: String, val url: String, val date: String)
data class ResearchPack(val createdAt: Long, val mode: String, val exposure: PortfolioExposure,
    val funds: Map<String, EtfDetails>, val stocks: List<ResearchStock>, val benchmarks: List<ResearchStock>, val sources: List<ResearchSource>,
    val budget: BigDecimal, val country: String, val horizon: String, val risk: String,
    val threshold: Int, val notes: List<String>, val fxRate: BigDecimal? = null, val fromCash: Boolean = false, val ledgerKey: String = "", val positions: List<ResearchPosition> = emptyList(),
    val screening: ScreenSummary? = null, val opinions: Map<String, ScreenOpinion> = emptyMap(), val financialFilters: Boolean = true)
data class InvestmentAnswer(val verdict: String, val summary: String, val reasons: List<String>,
    val risks: List<String>, val nextSteps: List<String>, val sourceIds: List<String>,
    val model: String, val createdAt: Long)

class InvestmentResearch(private val services: Services) {
    private val gson = Gson()
    fun ledgerKey(wallet: Wallet, memberId: String?, all: Boolean, portfolioId: String?): String {
        val tx = wallet.transactions.filter { (all || it.memberId == memberId) && (portfolioId == null || it.portfolioId == portfolioId) }
        return java.security.MessageDigest.getInstance("SHA-256").digest(gson.toJson(FamilyLedger.ordered(tx)).toByteArray()).joinToString("") { "%02x".format(it) }
    }
    private suspend fun <T> optional(block: suspend () -> T): T? = try { withTimeout(25_000) { block() } }
        catch (cancelled: CancellationException) { if (cancelled is TimeoutCancellationException) null else throw cancelled }
        catch (_: Exception) { null }

    private suspend fun stock(s: Security, deep: Boolean): ResearchStock = supervisorScope {
        val q = async { optional { services.market.quote(s) } }
        val p = async { optional { services.market.history(s, "6mo", "1d") } }
        val f = async { if (deep && s.type.uppercase() == "STOCK") optional { services.market.fundamentals(s) } else null }
        val n = async { if (deep) optional { services.market.news(s) } else null }
        val e = async { if (deep) optional { services.earningsFor(s) } else null }
        val quote = q.await()?.let { raw ->
            val current = CivilDayPnl.dailyQuote(raw, s, System.currentTimeMillis())
            val stamp = if (current.price != raw.price) (if (raw.marketSession.startsWith("PRE")) raw.preMarketTimestamp else raw.afterHoursTimestamp) ?: raw.timestamp else raw.timestamp
            current.copy(timestamp = stamp)
        }; val finance = f.await()
        val points = p.await().orEmpty().sortedBy { it.date }
        ResearchStock(PortfolioResearch.classified(s), quote, finance, points, n.await().orEmpty().take(6),
            e.await().orEmpty().take(4), listOfNotNull(if (quote == null) "Cours indisponible" else null,
                if (points.size < 50) "Historique de tendance incomplet" else null,
                if (finance == null && s.type.uppercase() == "STOCK") (if (deep) "États financiers ou ratios non couverts" else "Ratios non collectés pour cette idée préliminaire") else null))
    }
    suspend fun collect(wallet: Wallet, mode: String, memberId: String?, all: Boolean, portfolioId: String?,
        selected: Security?, budget: BigDecimal, country: String, horizon: String, risk: String,
        threshold: Int, status: (String) -> Unit, financialFilters: Boolean = true): ResearchPack = supervisorScope {
        val owned = (if (all) wallet else wallet.forMember(memberId)).let { w ->
            w.copy(transactions = w.transactions.filter { portfolioId == null || it.portfolioId == portfolioId }) }
        status("Actualisation du portefeuille…")
        val gate = Semaphore(3)
        val held = runCatching { owned.result().holdings.filter { it.quantity > ZERO }.mapNotNull { owned.security(it.securityId) } }.getOrDefault(emptyList())
        val quotes = wallet.quotes.toMutableMap()
        val fxSecurity = wallet.securities.find { it.symbol == "CAD=X" } ?: Security.of("CAD=X", "USD / CAD", "FOREX", "CAD", "FX")
        val quoteIds = (held.take(30) + fxSecurity).distinctBy { it.id }
        quoteIds.map { s -> async { gate.withPermit { optional { services.market.quote(s) } } } }.awaitAll().filterNotNull().forEach { quotes[it.securityId] = it }
        val funds = (held + listOfNotNull(selected)).filter { it.type.uppercase() == "ETF" }.distinctBy { it.id }.take(12).map { s -> async {
            gate.withPermit { optional { services.etfDetails(s).details }?.let { s.id to it } }
        } }.awaitAll().filterNotNull().toMap()
        val valuation = owned.copy(quotes = quotes).result()
        val exposure = PortfolioResearch.build(valuation, wallet.securities, funds)
        val positions = valuation.holdings.filter { it.quantity > ZERO }.mapNotNull { h -> owned.security(h.securityId)?.let { security ->
            ResearchPosition(security.symbol, security.name, h.quantity, h.value, security.currency,
                PortfolioResearch.classified(security).sector, quotes[security.id]?.timestamp) } }
        val screenResult = if (mode == "IDEAS") StockScreener.scan(country, financialFilters, status) else null
        if (mode != "IDEAS") status("Collecte des données du titre et du marché…")
        val stocks = screenResult?.stocks ?: listOfNotNull(selected?.takeIf { mode == "TITLE" }).map { stock(it, true) }
        val benchmarks = listOf(Security.of("^GSPTSE", "S&P/TSX", "INDEX", "CAD", "INDEX"),
            Security.of("^GSPC", "S&P 500", "INDEX", "USD", "INDEX")).map { s -> async { gate.withPermit { stock(s, false) } } }.awaitAll()
        val sources = mutableListOf<ResearchSource>()
        positions.forEachIndexed { i, p -> sources += ResearchSource("holding$i", "Cours du portefeuille · " + p.symbol,
            "https://finance.yahoo.com/quote/" + p.symbol + "/", p.quoteTime?.let { java.time.Instant.ofEpochMilli(it).toString() } ?: "date non fournie") }
        (stocks + benchmarks).forEachIndexed { index, row ->
            row.quote?.let { sources += ResearchSource("q$index", row.security.symbol + " · " + it.source,
                row.screen?.sourceUrl ?: ("https://finance.yahoo.com/quote/" + row.security.symbol + "/"), java.time.Instant.ofEpochMilli(it.timestamp).toString()) }
            row.fundamentals?.let { f -> sources += ResearchSource("f$index", f.source + " · états financiers " + row.security.symbol,
                row.screen?.sourceUrl ?: FinancialSymbolResolver.external(row.security)?.second ?: "https://www.sec.gov/edgar/search/",
                f.asOf ?: "date non fournie") }
            row.news.forEachIndexed { n, article -> if (article.url.startsWith("https://")) sources += ResearchSource(
                "n${index}_$n", article.source + " · " + article.title, article.url,
                java.time.Instant.ofEpochMilli(article.timestamp).toString()) }
        }
        funds.forEach { (id, fund) -> sources += ResearchSource("etf$id", fund.issuer + " · " + (wallet.security(id)?.symbol ?: id),
            fund.sourceUrl, fund.underlying?.asOf ?: fund.direct.asOf) }
        ResearchPack(System.currentTimeMillis(), mode, exposure, funds, stocks, benchmarks, sources, budget,
            country, horizon, risk, threshold, listOfNotNull(
                if (mode == "IDEAS" && stocks.isEmpty()) "Aucun candidat évaluable selon les règles et les données reçues." else null,
                if (mode == "IDEAS") "Scan des bourses US/Canada couvertes par TradingView; au plus 200 candidats. Aucun OTC, cours ≥ 1 dans sa devise, biotech et développeurs de médicaments exclus. Aucune limite de capitalisation. Le budget ne change pas le classement." else null,
                if (held.size > 30) "Cours actualisés pour les 30 premiers titres; les autres restent en cache." else null,
                if (held.count { it.type.uppercase() == "ETF" } > 12) "Composition chargée pour les 12 premiers FNB." else null,
                "Pays = domicile de l’émetteur, pas répartition de ses revenus. Devises = devises de cotation, pas risque de change sous-jacent.",
                if (mode == "IDEAS") "Score sectoriel : croissance 25 points, rentabilité 20, dette 20, P/E 20, tendance six mois 15. Les secteurs de moins de cinq candidats sont comparés à l’univers admissible. Données incomplètes non notées; aucune limite de capitalisation ni de prix maximum."
                else "Les seuils P/E ≤ 27, dette/capitaux propres ≤ 2 et capitalisation ≥ 1 G sont des repères, pas des signaux d’achat.",
                if (mode == "IDEAS") "Le score ne vérifie pas les profits exceptionnels, les dépôts ou les nouvelles. Les véhicules de placement identifiés sont exclus; holdings et sociétés financières demandent un modèle distinct. La classification fournisseur et les cours peuvent être incomplets ou différés." else null),
            quotes[fxSecurity.id]?.price, ledgerKey = ledgerKey(wallet, memberId, all, portfolioId), positions = positions, screening = screenResult?.summary, financialFilters = financialFilters)
    }
    fun evidence(pack: ResearchPack, question: String): String {
        // No member names, email, account identifiers or individual transactions leave the device.
        val data = mapOf("question" to question.take(500), "generatedAt" to pack.createdAt, "mode" to pack.mode,
            "portfolio" to pack.exposure, "positions" to pack.positions, "budgetCad" to pack.budget, "budgetFunding" to if (pack.fromCash) "Existing cash" else "New contribution", "country" to pack.country,
            "horizon" to pack.horizon, "risk" to pack.risk, "sectorAlertPercent" to pack.threshold,
            "stocks" to pack.stocks.map { r -> mapOf("symbol" to r.security.symbol, "name" to r.security.name,
                "screening" to r.screen, "sector" to r.security.sector, "currency" to r.security.currency, "price" to r.quote?.price,
                "quoteTime" to r.quote?.timestamp, "financials" to r.fundamentals?.let { f -> mapOf("metrics" to f.metrics, "source" to f.source, "asOf" to f.asOf, "ratiosAsOf" to f.ratiosAsOf, "quarterly" to f.quarterly.mapValues { it.value.takeLast(4) }, "missing" to f.unavailableMetrics) },
                "fundSectorPct" to PortfolioResearch.fundWeights(pack.funds[r.security.id], true), "monthReturnPct" to r.monthReturn,
                "movingAverage50" to r.average50, "news" to r.news, "earnings" to r.earnings, "missing" to r.gaps) },
            "benchmarks" to pack.benchmarks.map { r -> mapOf("symbol" to r.security.symbol,
                "monthReturnPct" to r.monthReturn, "movingAverage50" to r.average50, "price" to r.quote?.price,
                "quoteTime" to r.quote?.timestamp) }, "sources" to pack.sources, "limitations" to pack.notes)
        return gson.toJson(data)
    }
    suspend fun ask(pack: ResearchPack, question: String): InvestmentAnswer {
        val token = services.auth.token()
        val body = evidence(pack, question)
        require(body.length <= 90_000) { "Dossier trop volumineux. Réduis le nombre de titres." }
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(services.secure.url + "/functions/v1/analyze-investments")
                .header("apikey", services.secure.apiKey).header("Authorization", "Bearer " + token)
                .post(body.toRequestBody("application/json".toMediaType())).build()
            Http.client.newBuilder().callTimeout(65, java.util.concurrent.TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string() ?: "{}")
                check(response.isSuccessful) { json.optString("error", "IA indisponible (HTTP ${response.code}).") }
                val a = json.getJSONObject("analysis")
                fun list(key: String) = a.getJSONArray(key).let { values -> (0 until values.length()).map { values.getString(it) } }
                InvestmentAnswer(a.getString("verdict"), a.getString("summary"), list("reasons"), list("risks"),
                    list("nextSteps"), list("sourceIds").filter { id -> pack.sources.any { it.id == id } },
                    json.getString("model"), System.currentTimeMillis())
            }
        }
    }
    /** Resume deterministic groups; a completed server group is idempotent even after a lost response. */
    suspend fun analyzeScreen(pack: ResearchPack, status: (String) -> Unit,
        checkpoint: suspend (ResearchPack) -> Unit): ResearchPack {
        require(pack.screening != null && pack.stocks.isNotEmpty()) { "Lance d’abord le préfiltre." }
        require(System.currentTimeMillis() - pack.createdAt <= 31L * 24 * 3600_000) { "Ce scan date de plus d’un mois. Actualise-le." }
        var current = pack
        for (group in pack.stocks.chunked(20)) {
            currentCoroutineContext().ensureActive()
            if (group.all { current.opinions.orEmpty().containsKey(it.screen?.listing) }) continue
            status("Analyse IA : ${current.opinions.orEmpty().size}/${pack.stocks.size} titres · groupe de ${group.size}")
            val body = gson.toJson(mapOf("version" to StockScreener.VERSION, "generatedAt" to pack.createdAt,
                "budgetCad" to pack.budget, "horizon" to pack.horizon, "risk" to pack.risk,
                "stocks" to group.map { row -> val e = requireNotNull(row.screen)
                    mapOf("listing" to e.listing, "name" to row.security.name.take(200), "industry" to e.industry,
                        "sector" to e.sector, "price" to e.values["close"], "currency" to row.security.currency,
                        "values" to e.values, "flags" to e.flags, "sourceUrl" to e.sourceUrl) }))
            val token = services.auth.token()
            val opinions = withContext(Dispatchers.IO) {
                val request = Request.Builder().url(services.secure.url + "/functions/v1/analyze-stock-screen")
                    .header("apikey", services.secure.apiKey).header("Authorization", "Bearer " + token)
                    .post(body.toRequestBody("application/json".toMediaType())).build()
                Http.client.newBuilder().callTimeout(115, java.util.concurrent.TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
                    val json = JSONObject(response.body?.string() ?: "{}")
                    check(response.isSuccessful) { json.optString("error", "IA indisponible (HTTP ${response.code}).") }
                    val received = json.getJSONArray("opinions").let { a -> (0 until a.length()).map { i -> gson.fromJson(a.getJSONObject(i).toString(), ScreenOpinion::class.java) } }
                    require(received.map { it.listing }.toSet() == group.map { it.screen!!.listing }.toSet() && received.size == group.size) { "Réponse IA partielle ou titres inattendus. Aucun groupe incomplet enregistré." }
                    received.associateBy { it.listing }
                }
            }
            currentCoroutineContext().ensureActive()
            current = current.copy(opinions = current.opinions.orEmpty() + opinions)
            checkpoint(current)
        }
        status("Analyse IA terminée : ${current.opinions.orEmpty().size}/${pack.stocks.size}")
        return current
    }
    suspend fun save(key: String, pack: ResearchPack, answer: InvestmentAnswer?) {
        services.repo.dao.cache(Cache("investment:${services.repo.owner.value}:$key", "investment_research",
            gson.toJson(SavedResearch(pack, answer)), System.currentTimeMillis()))
    }
    suspend fun cached(key: String): SavedResearch? = withContext(Dispatchers.IO) {
        services.repo.dao.cache().find { it.id == "investment:${services.repo.owner.value}:$key" }?.let {
            runCatching { gson.fromJson(it.payload, SavedResearch::class.java) }.getOrNull() }
    }
}
data class SavedResearch(val pack: ResearchPack, val answer: InvestmentAnswer?)
