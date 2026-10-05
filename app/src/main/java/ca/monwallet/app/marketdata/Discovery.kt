package ca.monwallet.app.marketdata

import ca.monwallet.app.Services
import ca.monwallet.app.domain.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request

data class Filters(
    val cap: BigDecimal = BigDecimal("1000000000"),
    val volume: BigDecimal = BigDecimal("250000"),
    val price: BigDecimal = BigDecimal("5"),
    val priceMax: BigDecimal? = null,
    val peMin: BigDecimal? = null,
    val peMax: BigDecimal? = null,
    val pePositive: Boolean = false,
    val dividendMin: BigDecimal? = null,
    val dollarVolumeMin: BigDecimal? = null,
    val revenueGrowthMin: BigDecimal? = null,
    val epsGrowthMin: BigDecimal? = null,
    val fcfGrowthMin: BigDecimal? = null,
    val roeMin: BigDecimal? = null,
    val roaMin: BigDecimal? = null,
    val roicMin: BigDecimal? = null,
    val netMarginMin: BigDecimal? = null,
    val debtEquityMax: BigDecimal? = null,
    val country: String = "",
    val sector: String = "",
    val exchange: String = "NASDAQ,NYSE,TSX",
    val excludeEtf: Boolean = true,
    val maxSingleDay: Double? = 20.0,
    val maxDrawdown: Double? = null,
    val minPositiveWeeks: Double? = null,
    val minPerformance: Double? = null,
    val minPerformance6M: Double? = null,
    val minMomentum: Int? = null,
    val antiPump: Boolean = true,
    val requireHistory: Boolean = true,
    val analystMin: Int = 5,
    val upsideMin: Double = 10.0,
    val consensusMin: String = "Buy+",
    val targetAgeDays: Int? = null,
    val distanceHigh52Max: Double? = null,
    val excludeDispersion: Boolean = false,
    val columns: List<String> = emptyList(),
)

data class DiscoveryRow(
    val security: Security,
    val price: BigDecimal,
    val cap: BigDecimal?,
    val pe: BigDecimal?,
    val points: List<Point>,
    val metrics: MomentumMetrics?,
    val performances: Map<PerformancePeriod, BigDecimal?>,
    val averageVolume: BigDecimal?,
    val volume: BigDecimal?,
    val sector: String,
    val industry: String,
    val dividendYield: BigDecimal?,
    val fundamentals: Map<String, BigDecimal?>,
    val analyst: Analyst? = null,
    val opportunityScore: Int? = null,
) {
    fun performance(period: PerformancePeriod) = performances[period]
}

class Discovery(private val s: Services) {
    private val historyCache = mutableMapOf<String, Pair<Long, List<Point>>>()
    private val ratioCache = mutableMapOf<String, Pair<Long, Map<String, BigDecimal?>>>()
    private val growthCache = mutableMapOf<String, Pair<Long, Map<String, BigDecimal?>>>()
    private val resultCache = mutableMapOf<String, Pair<Long, List<DiscoveryRow>>>()
    private val now get() = System.currentTimeMillis()

    suspend fun screen(f: Filters, period: PerformancePeriod, onProgress: (String) -> Unit):
        List<DiscoveryRow> {
        val cacheKey = f.toString() + period.name
        resultCache[cacheKey]?.takeIf { now - it.first < 10 * 60_000 }?.let { return it.second }
        val key = s.secure.get("fmp_key")?.takeIf { it.isNotBlank() }
            ?: error("Clé FMP requise dans Paramètres pour charger le screener Canada/USA.")
        val params = mutableMapOf(
            "apikey" to key, "marketCapMoreThan" to f.cap.toPlainString(),
            "avgVolumeMoreThan" to f.volume.toPlainString(),
            "priceMoreThan" to f.price.toPlainString(),
            "isActivelyTrading" to "true", "limit" to "50",
        )
        if (f.excludeEtf) params["isEtf"] = "false"
        if (f.country.isNotBlank()) params["country"] = f.country
        if (f.sector.isNotBlank() && !f.sector.contains(',')) params["sector"] = f.sector
        f.priceMax?.let { params["priceLowerThan"] = it.toPlainString() }
        val exchanges = f.exchange.split(',').map(String::trim).filter { it.isNotBlank() }.distinct()
        val candidates = supervisorScope {
            exchanges.map { exchange -> async {
                Http.array(Request.Builder().url(Http.url(
                    "https://financialmodelingprep.com/stable/company-screener",
                    params + ("exchange" to exchange))).build()).objects()
            } }.awaitAll().flatten().distinctBy { it.text("symbol") }
        }
        val gate = Semaphore(3)
        val complete = java.util.concurrent.atomic.AtomicInteger()
        val failures = java.util.concurrent.atomic.AtomicInteger()
        val known = s.repo.current().securities
        val needsRatios = f.columns.any { it in setOf("pe", "forwardPe", "ps", "pb", "evEbitda",
            "roe", "roa", "roic", "netMargin", "revenueGrowth", "epsGrowth",
            "fcfGrowth", "debtEquity") } ||
            listOf(f.peMin, f.peMax, f.revenueGrowthMin, f.epsGrowthMin, f.roeMin,
                f.roaMin, f.roicMin, f.netMarginMin, f.fcfGrowthMin,
                f.debtEquityMax).any { it != null } || f.pePositive
        val rows = supervisorScope {
            candidates.map { o -> async {
                gate.withPermit {
                    try {
                        val symbol = o.text("symbol") ?: return@withPermit null
                        val exchange = o.text("exchangeShortName") ?: o.text("exchange")
                            ?: return@withPermit null
                        if (exchange !in f.exchange.split(',').map(String::trim)) return@withPermit null
                        if (o.optBoolean("isFund") || (f.excludeEtf && o.optBoolean("isEtf")) ||
                            symbol.contains(".WS") || symbol.endsWith("-W") ||
                            symbol.endsWith("-R")) return@withPermit null
                        val currency = if (exchange in listOf("TSX", "TSXV")) "CAD"
                            else if (exchange in listOf("NASDAQ", "NYSE", "AMEX")) "USD"
                            else return@withPermit null
                        val cap = o.number("marketCap") ?: return@withPermit null
                        val price = o.number("price") ?: return@withPermit null
                        if (f.sector.isNotBlank() && o.optString("sector") !in f.sector.split(','))
                            return@withPermit null
                        val avg = o.number("avgVolume") ?: o.number("averageVolume")
                            ?: return@withPermit null
                        if (cap < f.cap || price < f.price || avg < f.volume ||
                            f.priceMax != null && price > f.priceMax) return@withPermit null
                        val dollar = price * avg
                        if (f.dollarVolumeMin != null && dollar < f.dollarVolumeMin)
                            return@withPermit null
                        val div = o.number("lastAnnualDividend")?.let {
                            if (price.signum() == 0) null else it * BigDecimal(100) / price
                        }
                        if (f.dividendMin != null && (div == null || div < f.dividendMin))
                            return@withPermit null
                        val sec = known.find {
                            it.symbol == symbol && it.currency == currency
                        } ?: Security.of(symbol, o.optString("companyName", symbol),
                            exchange, currency, if (o.optBoolean("isEtf")) "ETF" else "STOCK")
                        val rawRatios = if (needsRatios) runCatching { ratios(sec.symbol, key) }
                            .getOrDefault(emptyMap()) else emptyMap()
                        val needsGrowth = listOf(f.revenueGrowthMin, f.epsGrowthMin,
                            f.fcfGrowthMin).any { it != null } ||
                            f.columns.any { it in setOf("revenueGrowth", "epsGrowth", "fcfGrowth") }
                        val growth = if (needsGrowth && (rawRatios["revenueGrowth"] == null ||
                            rawRatios["epsGrowth"] == null || f.fcfGrowthMin != null))
                            runCatching { growth(sec) }.getOrDefault(emptyMap()) else emptyMap()
                        val ratios = rawRatios + growth.filterValues { it != null }
                        val pe = ratios["pe"]
                        if (f.pePositive && (pe == null || pe <= ZERO)) return@withPermit null
                        if (f.peMin != null && (pe == null || pe < f.peMin)) return@withPermit null
                        if (f.peMax != null && (pe == null || pe > f.peMax)) return@withPermit null
                        for ((minimum, name) in listOf(
                            f.revenueGrowthMin to "revenueGrowth", f.epsGrowthMin to "epsGrowth",
                            f.fcfGrowthMin to "fcfGrowth", f.roeMin to "roe",
                            f.roaMin to "roa", f.roicMin to "roic",
                            f.netMarginMin to "netMargin")) {
                            if (minimum != null && (ratios[name] == null || ratios[name]!! < minimum))
                                return@withPermit null
                        }
                        if (f.debtEquityMax != null &&
                            (ratios["debtEquity"] == null || ratios["debtEquity"]!! > f.debtEquityMax))
                            return@withPermit null
                        val history = history(sec, if (period == PerformancePeriod.Y5 ||
                            f.columns.any { it == "5A" || it == "cagr5" }) "10y" else "2y")
                        if (f.distanceHigh52Max != null) {
                            val high = history.takeLast(252).maxOfOrNull { it.close }
                                ?: return@withPermit null
                            if (high.signum() <= 0 || (high - price).toDouble() /
                                high.toDouble() * 100 > f.distanceHigh52Max) return@withPermit null
                        }
                        val metrics = MomentumEngine.analyze(history, period, dollar.toDouble())
                        if (f.requireHistory && metrics == null) return@withPermit null
                        if (f.minPerformance6M != null &&
                            (MomentumEngine.performance(history, PerformancePeriod.M6)?.toDouble()
                                ?: Double.NEGATIVE_INFINITY) < f.minPerformance6M)
                            return@withPermit null
                        if (metrics != null) {
                            if (f.minMomentum != null && metrics.score < f.minMomentum)
                                return@withPermit null
                            if (f.maxSingleDay != null && metrics.bestDay > f.maxSingleDay)
                                return@withPermit null
                            if (f.maxDrawdown != null && -metrics.maxDrawdown > f.maxDrawdown)
                                return@withPermit null
                            if (f.minPositiveWeeks != null && metrics.totalWeeks > 0 &&
                                metrics.positiveWeeks * 100.0 / metrics.totalWeeks < f.minPositiveWeeks)
                                return@withPermit null
                            if (f.minPerformance != null && metrics.performance.toDouble() < f.minPerformance)
                                return@withPermit null
                            if (f.antiPump && metrics.gainConcentration >= 85 && metrics.bestDay >= 10)
                                return@withPermit null
                        }
                        DiscoveryRow(sec, price, cap, pe, history, metrics,
                            PerformancePeriod.entries.associateWith { MomentumEngine.performance(history, it) },
                            avg, o.number("volume"), o.optString("sector"), o.optString("industry"),
                            div, ratios)
                    } catch (_: Exception) {
                        failures.incrementAndGet()
                        null
                    } finally {
                        onProgress("Analyse " + complete.incrementAndGet() + " / " + candidates.size)
                    }
                }
            } }.awaitAll().filterNotNull()
        }.sortedWith(compareByDescending<DiscoveryRow> { it.metrics?.score ?: -1 }
            .thenByDescending { it.metrics?.performance })
        if (rows.isEmpty() && failures.get() > 0)
            error("Historique ou données du fournisseur indisponibles. Réessaie plus tard; aucun cours n'a été inventé.")
        resultCache[cacheKey] = now to rows
        return rows
    }

    suspend fun analysts(rows: List<DiscoveryRow>, f: Filters,
        onProgress: (String) -> Unit, applyFilters: Boolean = true): List<DiscoveryRow> = supervisorScope {
        val gate = Semaphore(3)
        val complete = java.util.concurrent.atomic.AtomicInteger()
        val enriched = rows.filter { it.security.currency == "USD" && it.metrics != null }
            .take(60).map { row -> async {
                gate.withPermit {
                    try {
                        val analyst = s.market.analyst(row.security) ?: return@withPermit null
                        val assessment = AnalystOpportunity.assess(analyst, row.price, row.metrics)
                            ?: return@withPermit null
                        if (applyFilters && (assessment.analystCount < f.analystMin ||
                            assessment.upside < f.upsideMin))
                            return@withPermit null
                        if (applyFilters && f.consensusMin == "Buy+" && assessment.buyRatio <= .5)
                            return@withPermit null
                        if (applyFilters && f.consensusMin == "Strong Buy" && assessment.buyRatio < .75)
                            return@withPermit null
                        val age = analyst.date?.let { runCatching {
                            java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(it), LocalDate.now())
                        }.getOrNull() }
                        if (applyFilters && f.targetAgeDays != null &&
                            (age == null || age > f.targetAgeDays))
                            return@withPermit null
                        if (applyFilters && f.excludeDispersion &&
                            (assessment.dispersion == null || assessment.dispersion > 50))
                            return@withPermit null
                        row.copy(analyst = analyst, opportunityScore = assessment.score)
                    } catch (_: Exception) {
                        null
                    } finally {
                        onProgress("Analystes " + complete.incrementAndGet() + " / " + rows.size.coerceAtMost(60))
                    }
                }
            } }.awaitAll().filterNotNull().sortedByDescending { it.opportunityScore }
        if (applyFilters) enriched else {
            val byId = enriched.associateBy { it.security.id }
            rows.map { byId[it.security.id] ?: it }
        }
    }

    private suspend fun history(sec: Security, range: String): List<Point> {
        val key = sec.id + ":" + range
        historyCache[key]?.takeIf { now - it.first < 6 * 60 * 60_000 }?.let { return it.second }
        val points = s.market.history(sec, range).sortedBy { it.date }
        historyCache[key] = now to points
        return points
    }

    private suspend fun ratios(symbol: String, apiKey: String): Map<String, BigDecimal?> {
        ratioCache[symbol]?.takeIf { now - it.first < 60 * 60_000 }?.let { return it.second }
        val o = Http.array(Request.Builder().url(Http.url(
            "https://financialmodelingprep.com/stable/ratios-ttm",
            mapOf("symbol" to symbol, "apikey" to apiKey))).build()).optJSONObject(0)
        val ratios = mapOf(
            "pe" to o?.number("priceToEarningsRatioTTM"),
            "forwardPe" to o?.number("forwardPriceToEarningsRatioTTM"),
            "ps" to o?.number("priceToSalesRatioTTM"),
            "pb" to o?.number("priceToBookRatioTTM"),
            "evEbitda" to o?.number("enterpriseValueMultipleTTM"),
            "roe" to o?.number("returnOnEquityTTM"),
            "roa" to o?.number("returnOnAssetsTTM"),
            "roic" to o?.number("returnOnInvestedCapitalTTM"),
            "netMargin" to o?.number("netProfitMarginTTM"),
            "revenueGrowth" to o?.number("revenueGrowthTTM"),
            "epsGrowth" to o?.number("epsGrowthTTM"),
            "debtEquity" to o?.number("debtEquityRatioTTM"),
        )
        ratioCache[symbol] = now to ratios
        return ratios
    }

    private suspend fun growth(security: Security): Map<String, BigDecimal?> {
        growthCache[security.id]?.takeIf { now - it.first < 6 * 60 * 60_000 }
            ?.let { return it.second }
        val data = s.market.fundamentals(security)
        fun annual(label: String): BigDecimal? {
            val series = data.annual[label]?.takeLast(2) ?: return null
            if (series.size != 2 || series.first().second.signum() <= 0) return null
            return (series.last().second - series.first().second)
                .multiply(BigDecimal(100)).divide(series.first().second, MC)
        }
        val values = mapOf("revenueGrowth" to
            (data.metrics["Croissance revenus %"]?.toBigDecimalOrNull() ?: annual("Revenus")),
            "epsGrowth" to annual("BPA"), "fcfGrowth" to annual("Free cash flow"))
        growthCache[security.id] = now to values
        return values
    }
}
