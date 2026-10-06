package ca.monwallet.app.marketdata

import ca.monwallet.app.BuildConfig
import android.util.Log
import ca.monwallet.app.data.SecureSettings
import ca.monwallet.app.domain.*
import java.math.BigDecimal
import java.time.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.*

fun JSONObject.number(k: String): BigDecimal? =
    if (isNull(k)) null else opt(k)?.toString()?.toBigDecimalOrNull()

fun JSONObject.text(k: String) = optString(k).takeIf { it.isNotBlank() && it != "null" }

fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }

object Http {
    val client =
        OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(8, TimeUnit.SECONDS)
            .followSslRedirects(false)
            .build()

    fun url(base: String, p: Map<String, String> = emptyMap()) =
        base
            .toHttpUrl()
            .newBuilder()
            .apply { p.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()

    suspend fun raw(r: Request): String =
        withContext(Dispatchers.IO) {
            require(r.url.isHttps) { "HTTPS obligatoire." }
            client.newCall(r).execute().use { response ->
                if (BuildConfig.DEBUG && r.url.host in setOf("data.sec.gov", "www.sec.gov", "api.nasdaq.com"))
                    Log.d("MonWalletMarket", "${r.url.host}${r.url.encodedPath} HTTP ${response.code}")
                check(response.isSuccessful) { "Service indisponible (HTTP ${response.code})." }
                response.body?.string() ?: error("Réponse vide.")
            }
        }

    suspend fun json(r: Request) = JSONObject(raw(r))

    suspend fun array(r: Request) = JSONArray(raw(r))
}

interface MarketDataProvider {
    val name: String

    suspend fun quote(s: Security): Quote

    suspend fun history(s: Security, range: String = "5y", interval: String = "1d"): List<Point>

    suspend fun search(q: String): List<Security>

    suspend fun news(s: Security): List<News>
}

class Yahoo : MarketDataProvider {
    override val name = "Yahoo Finance · non officiel"

    private suspend fun get(path: String, p: Map<String, String> = emptyMap()) =
        Http.json(
            Request.Builder()
                .url(Http.url("https://query1.finance.yahoo.com$path", p))
                .header("User-Agent", "Mozilla/5.0 MonWallet/0.1")
                .build()
        )

    private suspend fun chart(s: Security, range: String, interval: String,
        includePrePost: Boolean = false) =
        get(
                "/v8/finance/chart/" + java.net.URLEncoder.encode(s.symbol, "UTF-8"),
                mapOf("range" to range, "interval" to interval,
                    "includePrePost" to includePrePost.toString()),
            )
            .getJSONObject("chart")
            .optJSONArray("result")
            ?.optJSONObject(0) ?: error("Cours indisponible pour ${s.symbol}.")

    override suspend fun quote(s: Security): Quote {
        val us = supportsUsExtendedHours(s)
        // A 5-minute chart includes actual extended-session prints. Daily bars do not.
        val c = if (us) chart(s, "1d", "5m", includePrePost = true)
            else chart(s, "5d", "1d")
        return YahooQuoteMapper.map(s, c, Instant.now().epochSecond, name, us)
            .also { q ->
                if (BuildConfig.DEBUG && s.symbol == "AAPL")
                    Log.d("MonWalletQuote", "AAPL regular=${q.price} pre=${q.preMarketPrice} " +
                        "after=${q.afterHoursPrice} session=${q.marketSession} " +
                        "timestamp=${q.preMarketTimestamp ?: q.afterHoursTimestamp ?: q.timestamp} " +
                        "freshness=${NormalizedQuote.from(q).freshness} provider=${q.source}")
            }
    }

    private fun zone(m: JSONObject) =
        runCatching { ZoneId.of(m.optString("exchangeTimezoneName", "America/Toronto")) }
            .getOrDefault(ZoneId.of("America/Toronto"))

    override suspend fun history(s: Security, range: String, interval: String): List<Point> {
        val c = chart(s, range, interval)
        val times = c.optJSONArray("timestamp") ?: return emptyList()
        val quote = c.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
        val closes = quote.getJSONArray("close")
        fun at(field: String, index: Int): BigDecimal? = quote.optJSONArray(field)?.let { array ->
            if (index >= array.length() || array.isNull(index)) null else array.opt(index)?.toString()?.toBigDecimalOrNull()
        }
        val z = zone(c.getJSONObject("meta"))
        return (0 until times.length()).mapNotNull { i ->
            if (closes.isNull(i)) null
            else
                closes.get(i).toString().toBigDecimalOrNull()?.let { v ->
                    val time = times.getLong(i) * 1000
                    Point(
                        s.id,
                        Instant.ofEpochMilli(time).atZone(z).toLocalDate().toString(),
                        v,
                        time,
                        at("open", i), at("high", i), at("low", i), at("volume", i),
                    )
                }
        }
    }

    override suspend fun search(q: String): List<Security> {
        return get("/v1/finance/search", mapOf("q" to q, "quotesCount" to "20", "newsCount" to "0"))
            .optJSONArray("quotes")
            ?.objects()
            ?.mapNotNull { o ->
                val symbol = o.text("symbol") ?: return@mapNotNull null
                val exchange = o.text("exchDisp") ?: o.text("exchange") ?: return@mapNotNull null
                val currency =
                    o.text("currency")
                        ?: when {
                            listOf(".TO", ".NE", ".CN", ".V").any { symbol.endsWith(it) } -> "CAD"
                            exchange in
                                listOf(
                                    "NASDAQ",
                                    "NYSE",
                                    "NYSEArca",
                                    "NasdaqGS",
                                    "NasdaqCM",
                                    "NasdaqGM",
                                    "NMS",
                                    "NYQ",
                                    "NCM",
                                    "NGM",
                                ) -> "USD"
                            else -> return@mapNotNull null
                        }
                val type = o.optString("quoteType")
                if (type !in listOf("EQUITY", "ETF", "CRYPTOCURRENCY")) return@mapNotNull null
                Security.of(
                    symbol,
                    o.text("longname") ?: o.text("shortname") ?: symbol,
                    exchange,
                    currency,
                    if (type == "ETF") "ETF" else "STOCK",
                )
            } ?: emptyList()
    }

    override suspend fun news(s: Security) =
        get("/v1/finance/search", mapOf("q" to s.symbol, "quotesCount" to "0", "newsCount" to "10"))
            .optJSONArray("news")
            ?.objects()
            ?.mapNotNull { o ->
                val link =
                    o.text("link")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                News(
                    o.optString("title"),
                    o.optString("publisher"),
                    link,
                    o.optLong("providerPublishTime") * 1000,
                )
            } ?: emptyList()
}

/** Only exchange-identified US shares can have a Yahoo extended-session line. */
fun supportsUsExtendedHours(s: Security): Boolean =
    s.currency == "USD" && s.type in setOf("STOCK", "ETF") &&
        s.exchange.uppercase() in setOf("NASDAQ", "NYSE", "AMEX", "NYSEARCA", "NMS", "NYQ",
            "ASE", "NCM", "NGM", "NASDAQGS", "NASDAQGM", "NASDAQCM") &&
        listOf(".TO", ".V", ".NE", ".CN").none { s.symbol.uppercase().endsWith(it) }

internal object YahooQuoteMapper {
    private fun zone(m: JSONObject) =
        runCatching { ZoneId.of(m.optString("exchangeTimezoneName", "America/New_York")) }
            .getOrDefault(ZoneId.of("America/New_York"))

    private fun within(time: Long, window: JSONObject?): Boolean {
        if (window == null || !window.has("start") || !window.has("end")) return false
        return time >= window.optLong("start") && time < window.optLong("end")
    }

    private data class Print(val price: BigDecimal, val timestamp: Long)

    private fun latestPrint(c: JSONObject, window: JSONObject?): Print? {
        val times = c.optJSONArray("timestamp") ?: return null
        val closes = c.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)
            ?.optJSONArray("close") ?: return null
        for (i in minOf(times.length(), closes.length()) - 1 downTo 0) {
            val time = times.optLong(i)
            val price = closes.opt(i)?.toString()?.toBigDecimalOrNull()
            if (within(time, window) && price != null && price.signum() > 0)
                return Print(price, time)
        }
        return null
    }

    fun map(s: Security, c: JSONObject, now: Long, source: String,
        extendedEligible: Boolean = supportsUsExtendedHours(s)): Quote {
        val m = c.getJSONObject("meta")
        val currency = m.text("currency") ?: error("Devise absente.")
        require(currency == s.currency) {
            "Le fournisseur retourne $currency. Vérifie le marché sélectionné."
        }
        val time =
            m.number("regularMarketTime")?.toLong()?.times(1000) ?: error("Date du cours absente.")
        val date = Instant.ofEpochMilli(time).atZone(zone(m)).toLocalDate().toString()
        val times = c.optJSONArray("timestamp")
        val closes =
            c.optJSONObject("indicators")
                ?.optJSONArray("quote")
                ?.optJSONObject(0)
                ?.optJSONArray("close")
        val previous =
            if (!extendedEligible && times != null && closes != null)
                (0 until times.length())
                    .lastOrNull {
                        Instant.ofEpochSecond(times.getLong(it))
                            .atZone(zone(m))
                            .toLocalDate()
                            .toString() < date
                    }
                    ?.let {
                        if (closes.isNull(it)) null
                        else closes.get(it).toString().toBigDecimalOrNull()
                    }
            else null
        val periods = m.optJSONObject("currentTradingPeriod")
        val period = periods?.optJSONObject("regular")
        val session = when {
            within(now, periods?.optJSONObject("pre")) -> "PRE_MARKET"
            within(now, period) -> "REGULAR"
            within(now, periods?.optJSONObject("post")) -> "AFTER_HOURS"
            else -> "CLOSED"
        }
        val preWindow = periods?.optJSONObject("pre")
        val postWindow = periods?.optJSONObject("post")
        fun marketPrint(kind: String, window: JSONObject?): Print? {
            if (!extendedEligible || session != kind) return null
            val prefix = if (kind == "PRE_MARKET") "pre" else "post"
            val metaTime = m.number(prefix + "MarketTime")?.toLong()
            val metaPrice = m.number(prefix + "MarketPrice")
            val metaPrint = if (metaTime != null && within(metaTime, window) &&
                metaPrice != null && metaPrice.signum() > 0) Print(metaPrice, metaTime) else null
            val barPrint = latestPrint(c, window)
            return listOfNotNull(metaPrint, barPrint).maxByOrNull { it.timestamp }
        }
        val pre = marketPrint("PRE_MARKET", preWindow)
        val post = marketPrint("AFTER_HOURS", postWindow)
        val regular = m.number("regularMarketPrice") ?: error("Prix absent.")
        // A provider's change fields refer to its own price. Never attach them to a
        // different, newer chart candle.
        fun providerChange(print: Print?, prefix: String) =
            print?.takeIf { it.timestamp == m.number(prefix + "MarketTime")?.toLong() &&
                m.number(prefix + "MarketPrice")?.let { price -> it.price.compareTo(price) == 0 } == true }
                ?.let { m.number(prefix + "MarketChange") }
        fun providerPercent(print: Print?, prefix: String) =
            print?.takeIf { it.timestamp == m.number(prefix + "MarketTime")?.toLong() &&
                m.number(prefix + "MarketPrice")?.let { price -> it.price.compareTo(price) == 0 } == true }
                ?.let { m.number(prefix + "MarketChangePercent") }
        return Quote(
            s.id,
            regular,
            previous ?: m.number("previousClose") ?: m.number("chartPreviousClose"),
            currency,
            time,
            date,
            name,
            marketOpen = period?.let { within(now, it) },
            volume = m.number("regularMarketVolume"),
            averageVolume = m.number("averageDailyVolume3Month")
                ?: m.number("averageDailyVolume10Day"),
            high52 = m.number("fiftyTwoWeekHigh"),
            low52 = m.number("fiftyTwoWeekLow"),
            delay = m.number("exchangeDataDelayedBy")?.toInt()?.takeIf { it > 0 },
            preMarketPrice = pre?.price,
            preMarketTimestamp = pre?.timestamp?.times(1000),
            preMarketChange = providerChange(pre, "pre"),
            preMarketChangePercent = providerPercent(pre, "pre"),
            afterHoursPrice = post?.price,
            afterHoursTimestamp = post?.timestamp?.times(1000),
            afterHoursChange = providerChange(post, "post"),
            afterHoursChangePercent = providerPercent(post, "post"),
            marketSession = session,
        )
    }
}

class Twelve(private val key: String) : MarketDataProvider {
    override val name = "Twelve Data"

    private suspend fun get(path: String, p: Map<String, String>) =
        Http.json(
                Request.Builder()
                    .url(Http.url("https://api.twelvedata.com/$path", p + mapOf("apikey" to key)))
                    .build()
            )
            .also { check(it.optString("status") != "error") { it.optString("message").take(180) } }

    private fun symbol(s: Security) =
        when (s.symbol) {
            "CAD=X" -> "USD/CAD"
            "CADUSD=X" -> "CAD/USD"
            else -> s.symbol.removeSuffix(".TO").removeSuffix(".NE").removeSuffix(".CN")
        }

    override suspend fun quote(s: Security): Quote {
        val j = get("quote", mapOf("symbol" to symbol(s), "exchange" to s.exchange))
        val currency = j.text("currency") ?: error("Devise absente.")
        require(currency == s.currency)
        val time = j.number("timestamp")?.toLong()?.times(1000) ?: error("Date absente.")
        return Quote(
            s.id,
            j.number("close") ?: error("Prix absent."),
            j.number("previous_close"),
            currency,
            time,
            j.optString("datetime").take(10),
            name,
            marketOpen = j.optBoolean("is_market_open"),
            volume = j.number("volume"),
            high52 = j.optJSONObject("fifty_two_week")?.number("high"),
            low52 = j.optJSONObject("fifty_two_week")?.number("low"),
            averageVolume = j.number("average_volume"),
        )
    }

    override suspend fun history(s: Security, range: String, interval: String): List<Point> {
        val size =
            when (range) {
                "1d" -> 100
                "5d" -> 200
                "1mo" -> 30
                "3mo" -> 100
                "6mo" -> 200
                "1y" -> 300
                else -> 5000
            }
        val i =
            when (interval) {
                "5m" -> "5min"
                "30m" -> "30min"
                "1wk" -> "1week"
                "1mo" -> "1month"
                else -> "1day"
            }
        return get(
                "time_series",
                mapOf(
                    "symbol" to symbol(s),
                    "exchange" to s.exchange,
                    "interval" to i,
                    "outputsize" to size.toString(),
                    "timezone" to "UTC",
                ),
            )
            .optJSONArray("values")
            ?.objects()
            ?.mapNotNull { o ->
                val dt = o.text("datetime") ?: return@mapNotNull null
                val close = o.number("close") ?: return@mapNotNull null
                val epoch =
                    if (dt.length == 10)
                        LocalDate.parse(dt).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    else
                        LocalDateTime.parse(dt.replace(' ', 'T'))
                            .toInstant(ZoneOffset.UTC)
                            .toEpochMilli()
                Point(s.id, dt.take(10), close, epoch,
                    o.number("open"), o.number("high"), o.number("low"), o.number("volume"))
            }
            ?.reversed() ?: emptyList()
    }

    override suspend fun search(q: String) =
        get("symbol_search", mapOf("symbol" to q)).optJSONArray("data")?.objects()?.mapNotNull { o
            ->
            val symbol = o.text("symbol") ?: return@mapNotNull null
            val currency = o.text("currency") ?: return@mapNotNull null
            Security.of(
                symbol,
                o.optString("instrument_name", symbol),
                o.optString("exchange"),
                currency,
                if (o.optString("instrument_type").contains("ETF")) "ETF" else "STOCK",
            )
        } ?: emptyList()

    override suspend fun news(s: Security) = Yahoo().news(s)
}

class Router(private val settings: SecureSettings) : MarketDataProvider {
    private val yahoo = Yahoo()
    private val sec = SecFilingsProvider()
    private val nasdaqSummary = NasdaqSummaryProvider()
    private val nasdaqAnalyst = NasdaqAnalystProvider()
    private val financialRouter = FinancialSourceRouter(
        canada = BuildConfig.CANADA_FINANCIALS_URL.takeIf { it.isNotBlank() }
            ?.let { LicensedFundamentalsProvider(it, "Canada") },
        finviz = BuildConfig.FINVIZ_FINANCIALS_URL.takeIf { it.isNotBlank() }
            ?.let { LicensedFundamentalsProvider(it, "Finviz Elite") },
        sec = sec, nasdaq = nasdaqSummary,
    )

    private fun provider(): MarketDataProvider =
        settings.get("twelve_key")?.takeIf { it.isNotBlank() && settings.get("provider") == "twelve" }
            ?.let(::Twelve) ?: yahoo

    override val name
        get() = provider().name

    override suspend fun quote(s: Security) = provider().quote(s)

    override suspend fun history(s: Security, range: String, interval: String) =
        provider().history(s, range, interval)

    override suspend fun search(q: String) = provider().search(q)

    override suspend fun news(s: Security) = provider().news(s)

    suspend fun fundamentals(s: Security): Fundamentals = financialRouter.load(s)

    suspend fun analyst(s: Security): Analyst? {
        if (ProviderSymbolResolver.usEquity(s) == null) return null
        val primary = runCatching { nasdaqAnalyst.load(s) }
        primary.getOrNull()?.let { return it }
        settings.get("finnhub_key")?.takeIf { it.isNotBlank() }?.let { key ->
            runCatching { Finnhub(key).fundamentals(s).analyst }.getOrNull()?.let { return it }
        }
        primary.exceptionOrNull()?.let { throw it }
        return null
    }
}

class Finnhub(private val key: String) {
    private fun request(path: String, p: Map<String, String>) =
        Request.Builder()
            .url(Http.url("https://finnhub.io/api/v1/$path", p + mapOf("token" to key)))
            .build()

    suspend fun fundamentals(s: Security): Fundamentals {
        require(s.type == "STOCK") { "Données ETF détaillées non disponibles avec ce fournisseur." }
        val raw = Http.json(request("stock/metric", mapOf("symbol" to s.symbol, "metric" to "all")))
        val m = raw.optJSONObject("metric") ?: JSONObject()
        val names =
            mapOf(
                "peTTM" to "P/E",
                "psTTM" to "P/S",
                "pbQuarterly" to "P/B",
                "marketCapitalization" to "Capitalisation (M)",
                "epsTTM" to "BPA",
                "netProfitMarginTTM" to "Marge nette %",
                "grossMarginTTM" to "Marge brute %",
                "roeTTM" to "ROE %",
                "roaTTM" to "ROA %",
                "revenueGrowthTTMYoy" to "Croissance revenus %",
                "epsGrowthTTMYoy" to "Croissance BPA %",
                "dividendYieldIndicatedAnnual" to "Rendement dividende %",
                "payoutRatioTTM" to "Payout %",
                "52WeekHigh" to "Sommet 52 semaines",
                "52WeekLow" to "Creux 52 semaines",
            )
        val metrics =
            names
                .mapNotNull { (key, label) -> m.number(key)?.let { label to it.toPlainString() } }
                .toMap()
        val annual = raw.optJSONObject("series")?.optJSONObject("annual")
        val series =
            mapOf(
                    "salesPerShare" to "Revenus par action",
                    "eps" to "BPA",
                    "netMargin" to "Marge nette",
                    "cashFlowPerShare" to "Cash flow par action",
                )
                .mapNotNull { (key, label) ->
                    annual
                        ?.optJSONArray(key)
                        ?.objects()
                        ?.mapNotNull { p ->
                            val value = p.number("v") ?: return@mapNotNull null
                            (p.text("period") ?: return@mapNotNull null) to value
                        }
                        ?.let { label to it }
                }
                .toMap()
        val recommendation =
            runCatching {
                    Http.array(request("stock/recommendation", mapOf("symbol" to s.symbol)))
                        .optJSONObject(0)
                }
                .getOrNull()
        val target =
            runCatching { Http.json(request("stock/price-target", mapOf("symbol" to s.symbol))) }
                .getOrNull()
        val analyst =
            if (recommendation != null || target != null)
                Analyst(
                    recommendation?.let { it.optInt("buy") + it.optInt("strongBuy") },
                    recommendation?.optInt("hold"),
                    recommendation?.let { it.optInt("sell") + it.optInt("strongSell") },
                    target?.number("targetMean"),
                    target?.number("targetHigh"),
                    target?.number("targetLow"),
                    recommendation?.text("period") ?: target?.text("lastUpdated"),
                    "Finnhub",
                )
            else null
        return Fundamentals(metrics, series, analyst, "Finnhub")
    }

    suspend fun earnings(s: Security): List<Earnings> {
        val now = LocalDate.now()
        return Http.json(
                request(
                    "calendar/earnings",
                    mapOf(
                        "symbol" to s.symbol,
                        "from" to now.minusDays(7).toString(),
                        "to" to now.plusDays(30).toString(),
                    ),
                )
            )
            .optJSONArray("earningsCalendar")
            ?.objects()
            ?.map {
                Earnings(
                    it.text("date"),
                    it.number("epsEstimate"),
                    it.number("epsActual"),
                    it.number("revenueEstimate"),
                    it.number("revenueActual"),
                )
            } ?: emptyList()
    }
}
