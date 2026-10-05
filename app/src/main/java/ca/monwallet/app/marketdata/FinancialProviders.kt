package ca.monwallet.app.marketdata

import android.util.Log
import ca.monwallet.app.BuildConfig
import ca.monwallet.app.domain.Analyst
import ca.monwallet.app.domain.Fundamentals
import ca.monwallet.app.domain.Security
import ca.monwallet.app.domain.divSafe
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

interface FundamentalsProvider {
    val name: String
    suspend fun load(security: Security): Fundamentals?
}

interface AnalystProvider {
    val name: String
    suspend fun load(security: Security): Analyst?
}

/** The exchange, currency and asset type must agree before any provider symbol is used. */
object ProviderSymbolResolver {
    fun usEquity(s: Security): String? {
        if (s.type.uppercase() != "STOCK" || s.currency.uppercase() != "USD") return null
        val exchange = s.exchange.uppercase().replace(" ", "")
        if (exchange !in setOf("NASDAQ", "NASDAQGS", "NASDAQGM", "NASDAQCM", "NMS", "NCM", "NGM", "NYQ", "NYSE")) return null
        val symbol = s.symbol.uppercase()
        return symbol.takeIf { it.matches(Regex("[A-Z]{1,6}(?:[.-][A-Z]{1,2})?")) }
    }

    fun secExchange(exchange: String): String? = when (exchange.uppercase().replace(" ", "")) {
        "NASDAQ", "NASDAQGS", "NASDAQGM", "NASDAQCM", "NMS", "NCM", "NGM" -> "Nasdaq"
        "NYSE", "NYQ" -> "NYSE"
        else -> null
    }
}

private fun diagnostics(message: String) { if (BuildConfig.DEBUG) Log.d("MonWalletMarket", message) }

/** SEC XBRL facts are issuer filings. Only exact exchange/ticker matches resolve to a CIK. */
class SecFilingsProvider : FundamentalsProvider {
    override val name = "SEC EDGAR"
    private val mutex = Mutex()
    private var index: Map<Pair<String, String>, Int>? = null
    private var indexAt = 0L
    private val userAgent = "MonWalletAndroid/0.2.1 (personal portfolio tracker; contact via application owner)"

    private suspend fun cik(symbol: String, exchange: String): Int? = mutex.withLock {
        if (index == null || System.currentTimeMillis() - indexAt > 86_400_000) {
            val request = Request.Builder().url("https://www.sec.gov/files/company_tickers_exchange.json")
                .header("User-Agent", userAgent).build()
            val root = Http.json(request)
            val fields = root.getJSONArray("fields")
            val tickerIndex = (0 until fields.length()).first { fields.getString(it) == "ticker" }
            val exchangeIndex = (0 until fields.length()).first { fields.getString(it) == "exchange" }
            val cikIndex = (0 until fields.length()).first { fields.getString(it) == "cik" }
            index = root.getJSONArray("data").let { rows ->
                (0 until rows.length()).mapNotNull { n ->
                    val row = rows.optJSONArray(n) ?: return@mapNotNull null
                    (row.getString(tickerIndex).uppercase() to row.getString(exchangeIndex)) to row.getInt(cikIndex)
                }.toMap()
            }
            indexAt = System.currentTimeMillis()
            diagnostics("SEC exchange index loaded: ${index?.size} issuers")
        }
        index?.get(symbol to exchange)
    }

    override suspend fun load(security: Security): Fundamentals? {
        val symbol = ProviderSymbolResolver.usEquity(security) ?: return null
        val exchange = ProviderSymbolResolver.secExchange(security.exchange) ?: return null
        val cik = cik(symbol, exchange) ?: return null
        val url = "https://data.sec.gov/api/xbrl/companyfacts/CIK${cik.toString().padStart(10, '0')}.json"
        diagnostics("SEC ${security.symbol} ${security.exchange}: CIK $cik")
        val root = Http.json(Request.Builder().url(url).header("User-Agent", userAgent).build())
        require(root.getInt("cik") == cik) { "L'identité du dépôt SEC ne correspond pas." }
        return SecFacts.parse(root)
    }
}

/** Filters comparative/duplicate facts and never turns a missing fact into zero. */
object SecFacts {
    private data class Fact(val start: LocalDate?, val end: LocalDate, val filed: LocalDate, val value: BigDecimal, val form: String)

    private fun periods(facts: JSONObject, concepts: List<String>, unit: String): List<Fact> {
        val today = LocalDate.now()
        val entries = concepts.firstNotNullOfOrNull { name ->
            facts.optJSONObject(name)?.optJSONObject("units")?.optJSONArray(unit)
                ?.takeIf { it.length() > 0 }
        } ?: return emptyList()
        return (0 until entries.length()).mapNotNull { i ->
            val entry = entries.optJSONObject(i) ?: return@mapNotNull null
            val value = entry.number("val") ?: return@mapNotNull null
            val end = runCatching { LocalDate.parse(entry.getString("end")) }.getOrNull() ?: return@mapNotNull null
            val filed = runCatching { LocalDate.parse(entry.getString("filed")) }.getOrNull() ?: return@mapNotNull null
            val start = entry.text("start")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val form = entry.optString("form")
            if (end > today || filed > today || form !in setOf("10-K", "10-Q", "20-F", "40-F")) null
            else Fact(start, end, filed, value, form)
        }
    }

    private fun series(facts: JSONObject, names: List<String>, unit: String, kind: String): List<Pair<String, BigDecimal>> {
        val candidates = periods(facts, names, unit).filter { fact ->
            val duration = fact.start?.let { ChronoUnit.DAYS.between(it, fact.end) } ?: -1L
            when (kind) {
                "annual" -> duration in 330..390 && fact.form in setOf("10-K", "20-F", "40-F")
                "quarterly" -> duration in 65..110 && fact.form in setOf("10-Q", "10-K")
                else -> fact.start == null
            }
        }
        return candidates.groupBy { it.end }.mapValues { (_, v) -> v.maxBy { it.filed } }
            .toSortedMap().map { (end, fact) -> end.toString() to fact.value }
    }

    fun parse(root: JSONObject): Fundamentals? {
        val namespaces = root.optJSONObject("facts") ?: return null
        val facts = namespaces.optJSONObject("us-gaap") ?: namespaces.optJSONObject("ifrs-full") ?: return null
        val ifrs = namespaces.optJSONObject("us-gaap") == null
        fun annual(vararg names: String, unit: String = "USD") = series(facts, names.toList(), unit, "annual")
        fun quarterly(vararg names: String, unit: String = "USD") = series(facts, names.toList(), unit, "quarterly")
        fun balance(vararg names: String) = series(facts, names.toList(), "USD", "balance")
        val revenue = if (ifrs) annual("Revenue", "RevenueFromContractsWithCustomers") else
            annual("RevenueFromContractWithCustomerExcludingAssessedTax", "Revenues", "SalesRevenueNet", "RevenueFromContractWithCustomerIncludingAssessedTax")
        val income = annual("NetIncomeLoss", "ProfitLoss")
        val eps = if (ifrs) annual("DilutedEarningsLossPerShare", unit = "USD/shares") else
            annual("EarningsPerShareDiluted", unit = "USD/shares")
        val operating = if (ifrs) annual("CashFlowsFromUsedInOperatingActivities") else
            annual("NetCashProvidedByUsedInOperatingActivities")
        val capex = annual("PaymentsToAcquirePropertyPlantAndEquipment")
        val cash = balance("CashAndCashEquivalentsAtCarryingValue", "CashAndCashEquivalents")
        val longDebt = balance("LongTermDebt", "LongTermDebtNoncurrent", "LongTermDebtAndFinanceLeaseObligations")
        val currentDebt = balance("LongTermDebtCurrent", "LongTermDebtAndFinanceLeaseObligationsCurrent")
        val freeCash = operating.mapNotNull { (date, value) -> capex.lastOrNull { it.first == date }?.let { date to value - it.second } }
        val metrics = linkedMapOf<String, String>()
        fun put(label: String, p: Pair<String, BigDecimal>?) { if (p != null) metrics[label] = p.second.stripTrailingZeros().toPlainString() }
        put("Revenus", revenue.lastOrNull())
        put("Bénéfice net", income.lastOrNull())
        put("BPA", eps.lastOrNull())
        put("Cash", cash.lastOrNull())
        put("Dette long terme", longDebt.lastOrNull())
        put("Dette court terme", currentDebt.lastOrNull())
        val debtEnd = longDebt.lastOrNull()?.first
        if (debtEnd != null && debtEnd == currentDebt.lastOrNull()?.first) {
            val debt = longDebt.last().second + currentDebt.last().second
            metrics["Dette totale"] = debt.toPlainString()
            if (cash.lastOrNull()?.first == debtEnd)
                metrics["Dette nette"] = (debt - cash.last().second).toPlainString()
        }
        put("Operating cash flow", operating.lastOrNull())
        put("Free cash flow", freeCash.lastOrNull())
        val incomeEnd = income.lastOrNull()?.first
        if (incomeEnd != null && incomeEnd == revenue.lastOrNull()?.first) {
            val margin = income.last().second.divSafe(revenue.last().second)?.multiply(BigDecimal(100))
            if (margin != null) metrics["Marge nette %"] = margin.setScale(2, RoundingMode.HALF_UP).toPlainString()
        }
        if (revenue.size >= 2) {
            val growth = (revenue.last().second - revenue[revenue.lastIndex - 1].second)
                .divSafe(revenue[revenue.lastIndex - 1].second)?.multiply(BigDecimal(100))
            if (growth != null) metrics["Croissance revenus %"] = growth.setScale(2, RoundingMode.HALF_UP).toPlainString()
        }
        val histories = linkedMapOf("Revenus" to revenue, "Bénéfice net" to income,
            "BPA" to eps, "Free cash flow" to freeCash)
        val quarters = linkedMapOf(
            "Revenus" to quarterly("RevenueFromContractWithCustomerExcludingAssessedTax", "Revenues", "SalesRevenueNet", "Revenue"),
            "Bénéfice net" to quarterly("NetIncomeLoss", "ProfitLoss"),
            "BPA" to quarterly("EarningsPerShareDiluted", unit = "USD/shares"),
        )
        if (metrics.isEmpty()) return null
        diagnostics("SEC mapped ${metrics.size} metrics, ${histories.count { it.value.isNotEmpty() }} annual series")
        val asOf = listOfNotNull(revenue.lastOrNull()?.first, cash.lastOrNull()?.first).maxOrNull()
        return Fundamentals(metrics, histories.filterValues { it.isNotEmpty() }, source = "SEC EDGAR · ${if (ifrs) "IFRS en USD" else "US GAAP en USD"}",
            quarterly = quarters.filterValues { it.isNotEmpty() }, asOf = asOf)
    }
}

/** Nasdaq consensus reflects only the available counts; it does not guess strong buy/sell. */
class NasdaqAnalystProvider : AnalystProvider {
    override val name = "Nasdaq"

    override suspend fun load(security: Security): Analyst? {
        val symbol = ProviderSymbolResolver.usEquity(security) ?: return null
        diagnostics("Nasdaq analyst ${security.symbol} ${security.exchange}")
        val request = Request.Builder().url("https://api.nasdaq.com/api/analyst/$symbol/targetprice")
            .header("User-Agent", "Mozilla/5.0 MonWallet/0.2.1")
            .header("Accept", "application/json")
            .header("Origin", "https://www.nasdaq.com").build()
        return parse(Http.json(request))
    }

    fun parse(root: JSONObject): Analyst? {
        val d = root.optJSONObject("data") ?: return null
        val overview = d.optJSONObject("consensusOverview") ?: return null
        val buy = overview.number("buy")?.toInt()
        val hold = overview.number("hold")?.toInt()
        val sell = overview.number("sell")?.toInt()
        val target = overview.number("priceTarget")
        if (buy == null && hold == null && sell == null && target == null) return null
        val history = d.optJSONArray("historicalConsensus")
        val latest = history?.optJSONObject(history.length() - 1)?.optJSONObject("z")
        val date = latest?.text("date")?.let {
            runCatching { LocalDate.parse(it, DateTimeFormatter.ofPattern("MM/dd/yyyy")).toString() }.getOrNull()
        }
        return Analyst(buy, hold, sell, target, overview.number("highPriceTarget"),
            overview.number("lowPriceTarget"), date, name)
    }
}

class NasdaqSummaryProvider : FundamentalsProvider {
    override val name = "Nasdaq"

    override suspend fun load(security: Security): Fundamentals? {
        val symbol = ProviderSymbolResolver.usEquity(security) ?: return null
        val request = Request.Builder()
            .url("https://api.nasdaq.com/api/quote/$symbol/summary?assetclass=stocks")
            .header("User-Agent", "Mozilla/5.0 MonWallet/0.2.1")
            .header("Accept", "application/json")
            .header("Origin", "https://www.nasdaq.com").build()
        diagnostics("Nasdaq summary ${security.symbol} ${security.exchange}")
        return parse(Http.json(request))
    }

    fun parse(root: JSONObject): Fundamentals? {
        val data = root.optJSONObject("data") ?: return null
        val entries = data.optJSONObject("summaryData") ?: return null
        fun value(name: String): String? = entries.optJSONObject(name)?.text("value")?.takeIf { it != "N/A" }
        fun number(name: String): BigDecimal? = value(name)?.replace(Regex("[,$% ]"), "")?.toBigDecimalOrNull()
        val metrics = linkedMapOf<String, String>()
        number("MarketCap")?.let { metrics["Capitalisation (M)"] = it.divide(BigDecimal(1_000_000)).stripTrailingZeros().toPlainString() }
        number("ShareVolume")?.let { metrics["Volume"] = it.toPlainString() }
        number("AverageVolume")?.let { metrics["Volume moyen"] = it.toPlainString() }
        number("AnnualizedDividend")?.let { metrics["Dividende annuel"] = it.toPlainString() }
        number("Yield")?.let { metrics["Rendement dividende %"] = it.toPlainString() }
        value("ExDividendDate")?.let { metrics["Ex-dividend date"] = it }
        value("DividendPaymentDate")?.let { metrics["Payment date"] = it }
        value("FiftTwoWeekHighLow")?.split('/')?.takeIf { it.size == 2 }?.let { parts ->
            parts[0].trim().removePrefix("$").toBigDecimalOrNull()?.let { metrics["Sommet 52 semaines"] = it.toPlainString() }
            parts[1].trim().removePrefix("$").toBigDecimalOrNull()?.let { metrics["Creux 52 semaines"] = it.toPlainString() }
        }
        return metrics.takeIf { it.isNotEmpty() }?.let { Fundamentals(metrics = it, source = name) }
    }
}
