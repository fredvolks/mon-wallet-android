package ca.monwallet.app.marketdata

import android.util.Log
import ca.monwallet.app.BuildConfig
import ca.monwallet.app.domain.*
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

data class FinancialListing(val market: String, val symbol: String,
    val exchange: String, val currency: String, val type: String)

/** The listed instrument determines the source, not the issuer's country or ticker alone. */
object FinancialSymbolResolver {
    private val canada = mapOf("TSX" to ".TO", "TOR" to ".TO", "TORONTO" to ".TO",
        "TSXV" to ".V", "CSE" to ".CN", "CNQ" to ".CN", "NEO" to ".NE")
    private val us = setOf("NASDAQ", "NASDAQGS", "NASDAQGM", "NASDAQCM",
        "NMS", "NCM", "NGM", "NYSE", "NYQ", "AMEX", "NYSEAMERICAN")
    private val suffixes = setOf(".TO", ".V", ".CN", ".NE")
    fun resolve(s: Security): FinancialListing? {
        val exchange = s.exchange.uppercase().replace(" ", "")
        val symbol = s.symbol.uppercase().trim()
        val type = s.type.uppercase()
        if (type !in setOf("STOCK", "ETF")) return null
        canada[exchange]?.let { suffix ->
            if (s.currency.uppercase() != "CAD" ||
                suffixes.any { symbol.endsWith(it) && it != suffix }) return null
            val base = symbol.removeSuffix(suffix)
            if (!base.matches(Regex("[A-Z0-9]{1,8}(?:[.-][A-Z0-9]{1,3})?"))) return null
            return FinancialListing("CANADA", base + suffix, exchange, "CAD", type)
        }
        if (exchange in us && s.currency.uppercase() == "USD" &&
            suffixes.none { symbol.endsWith(it) } &&
            symbol.matches(Regex("[A-Z]{1,6}(?:[.-][A-Z]{1,2})?")))
            return FinancialListing("US", symbol, exchange, "USD", type)
        return null
    }
    fun external(s: Security): Pair<String, String>? = resolve(s)?.let {
        if (it.market == "CANADA")
            "Yahoo Finance" to "https://finance.yahoo.com/quote/${it.symbol}/"
        else "Finviz" to "https://finviz.com/quote.ashx?t=${it.symbol}"
    }
}

class NoFinancialCoverage(message: String) : Exception(message)

/** A licensed Mon Wallet backend owns provider secrets and redistribution rights. */
class LicensedFundamentalsProvider(private val endpoint: String, override val name: String) :
    FundamentalsProvider {
    override suspend fun load(security: Security): Fundamentals? {
        val listing = FinancialSymbolResolver.resolve(security) ?: return null
        val url = Http.url(endpoint, mapOf("market" to listing.market,
            "symbol" to listing.symbol, "exchange" to listing.exchange,
            "currency" to listing.currency, "assetType" to listing.type))
        val builder = Request.Builder().url(url)
        if (url.host == BuildConfig.SUPABASE_URL.toHttpUrl().host)
            builder.header("apikey", BuildConfig.SUPABASE_ANON_KEY)
        val json = Http.json(builder.build())
        require(json.optString("symbol").uppercase() == listing.symbol &&
            json.optString("exchange").uppercase().replace(" ", "") == listing.exchange &&
            json.optString("currency").uppercase() == listing.currency &&
            json.optString("assetType").uppercase() == listing.type) {
            "Réponse financière pour un autre instrument."
        }
        val source = json.text("source") ?: return null
        val raw = json.optJSONObject("metrics") ?: return null
        val metrics = raw.keys().asSequence().mapNotNull { key ->
            raw.opt(key)?.toString()?.toBigDecimalOrNull()?.let { key to it.toPlainString() }
        }.toMap()
        return metrics.takeIf { it.isNotEmpty() }?.let {
            Fundamentals(metrics = it, source = source, asOf = json.text("asOf"))
        }
    }
}

class FinancialSourceRouter(
    private val canada: FundamentalsProvider?,
    private val finviz: FundamentalsProvider?,
    private val sec: FundamentalsProvider,
    private val nasdaq: FundamentalsProvider,
) {
    suspend fun load(s: Security): Fundamentals {
        val listing = FinancialSymbolResolver.resolve(s)
            ?: throw NoFinancialCoverage("Instrument non reconnu.")
        if (BuildConfig.DEBUG) Log.d("MonWalletMarket",
            "Financial ${listing.market} ${listing.symbol} ${listing.exchange}")
        val sources = if (listing.market == "CANADA") listOfNotNull(canada)
            else if (listing.type == "ETF") listOfNotNull(finviz)
            else listOfNotNull(finviz, sec, nasdaq)
        if (sources.isEmpty()) throw NoFinancialCoverage(
            "Aucune source autorisée pour ${listing.symbol}.")
        val results = mutableListOf<Fundamentals>()
        var failure: Throwable? = null
        sources.forEach { provider ->
            runCatching { provider.load(s) }
                .onSuccess { if (it != null && it.metrics.isNotEmpty()) results.add(it) }
                .onFailure { if (failure == null) failure = it }
        }
        if (results.isEmpty()) {
            if (failure != null) throw failure!!
            throw NoFinancialCoverage("Aucune donnée pour ${listing.symbol}.")
        }
        return Fundamentals(
            metrics = results.asReversed().fold(emptyMap()) { acc, next -> acc + next.metrics },
            annual = results.firstOrNull { it.annual.isNotEmpty() }?.annual ?: emptyMap(),
            quarterly = results.firstOrNull { it.quarterly.isNotEmpty() }?.quarterly ?: emptyMap(),
            source = results.joinToString(" · ") { it.source },
            asOf = results.mapNotNull { it.asOf }.maxOrNull(),
        )
    }
}
