package ca.monwallet.app.domain

import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.util.UUID

val ZERO = BigDecimal.ZERO
val ONE = BigDecimal.ONE
val MC = MathContext.DECIMAL128

fun String.dec() = replace(" ", "").replace("\u00a0", "").replace(',', '.').toBigDecimal()

fun BigDecimal.divSafe(b: BigDecimal): BigDecimal? = if (b.signum() == 0) null else divide(b, MC)

fun BigDecimal.pct(b: BigDecimal) = divSafe(b)?.multiply(BigDecimal(100))

fun uuid() = UUID.randomUUID().toString()

enum class TxType {
    BUY,
    SELL,
    DIVIDEND,
    FEE,
    DEPOSIT,
    WITHDRAWAL,
}

data class Portfolio(
    val id: String = uuid(),
    val name: String,
    val type: String = "CELI",
    val currency: String = "CAD",
)

data class Security(
    val id: String,
    val symbol: String,
    val name: String,
    val exchange: String,
    val currency: String,
    val type: String = "STOCK",
    val sector: String? = null,
    val country: String? = null,
    val isin: String? = null,
    val officialDomain: String? = null,
) {
    val ticker
        get() = symbol.substringBefore('.')

    companion object {
        fun of(
            symbol: String,
            name: String,
            exchange: String,
            currency: String,
            type: String = "STOCK",
        ) =
            Security(
                UUID.nameUUIDFromBytes("$symbol|$exchange|$currency".toByteArray()).toString(),
                symbol,
                name,
                exchange,
                currency,
                type,
                officialDomain = OfficialDomains.forIdentity(symbol, exchange, currency, type),
            )
    }
}

/** Verified issuer identity, never a ticker-only match (GURU and PHOS are ambiguous). */
object OfficialDomains {
    private val domains = mapOf(
        "AAPL|NASDAQ|USD|STOCK" to "apple.com",
        "MSFT|NASDAQ|USD|STOCK" to "microsoft.com",
        "NVDA|NASDAQ|USD|STOCK" to "nvidia.com",
        "TSM|NYSE|USD|STOCK" to "tsmc.com",
        "MCD|NYSE|USD|STOCK" to "mcdonalds.com",
        "DOL.TO|TSX|CAD|STOCK" to "dollarama.com",
        "GURU.TO|TSX|CAD|STOCK" to "guruenergy.com",
        "BLDP.TO|TSX|CAD|STOCK" to "ballard.com",
        "BLDP|NASDAQ|USD|STOCK" to "ballard.com",
        "PHOS.CN|CSE|CAD|STOCK" to "firstphosphate.com",
        "XEQT.TO|TSX|CAD|ETF" to "ishares.com",
    )
    fun forIdentity(symbol: String, exchange: String, currency: String, type: String): String? {
        val market = when (exchange.uppercase().replace(" ", "")) {
            "TORONTO", "TOR", "TSX" -> "TSX"
            "CNQ", "CSE" -> "CSE"
            "NASDAQGS", "NASDAQGM", "NASDAQCM", "NMS", "NCM", "NGM", "NASDAQ" -> "NASDAQ"
            "NYQ", "NYSE" -> "NYSE"
            else -> exchange.uppercase()
        }
        return domains["${symbol.uppercase()}|$market|${currency.uppercase()}|${type.uppercase()}"]
    }
}

data class Transaction(
    val id: String = uuid(),
    val portfolioId: String,
    val securityId: String? = null,
    val type: TxType = TxType.BUY,
    val quantity: BigDecimal = ZERO,
    val price: BigDecimal,
    val currency: String = "CAD",
    val fxRate: BigDecimal = ONE,
    val fees: BigDecimal = ZERO,
    val date: String = LocalDate.now().toString(),
    val fxDate: String = date,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String = "",
) {
    val gross
        get() = if (type in listOf(TxType.BUY, TxType.SELL)) quantity * price else price

    val total
        get() = if (type in listOf(TxType.SELL, TxType.DIVIDEND)) gross - fees else gross + fees

    val cad
        get() = total * fxRate
}

data class Watchlist(val id: String = uuid(), val name: String, val order: Int = 0)

data class WatchItem(
    val id: String = uuid(),
    val watchlistId: String,
    val securityId: String,
    val order: Int = 0,
)

data class PriceAlert(
    val id: String = uuid(),
    val securityId: String,
    val kind: String = "ABOVE",
    val threshold: BigDecimal,
    val enabled: Boolean = true,
    val triggered: Long? = null,
)

data class NotificationPreference(
    val id: String,
    val securityId: String,
    val earnings: Boolean = true,
    val news: Boolean = false,
)

data class AlertEvent(
    val id: String = uuid(),
    val securityId: String,
    val title: String,
    val body: String,
    val channel: String,
    val timestamp: Long = System.currentTimeMillis(),
    val eventKey: String = id,
)

data class Setting(val id: String, val value: String)

data class Quote(
    val securityId: String,
    val price: BigDecimal,
    val previous: BigDecimal?,
    val currency: String,
    val timestamp: Long,
    val sessionDate: String,
    val source: String,
    val delay: Int? = null,
    val marketOpen: Boolean? = null,
    val volume: BigDecimal? = null,
    val high52: BigDecimal? = null,
    val low52: BigDecimal? = null,
    val averageVolume: BigDecimal? = null,
    val fetchedAt: Long = System.currentTimeMillis(),
) {
    val change
        get() = previous?.let { price - it }

    val percent
        get() = change?.pct(previous!!)
}

data class Point(
    val securityId: String,
    val date: String,
    val close: BigDecimal,
    val timestamp: Long,
    val open: BigDecimal? = null,
    val high: BigDecimal? = null,
    val low: BigDecimal? = null,
    val volume: BigDecimal? = null,
)

data class News(val title: String, val source: String, val url: String, val timestamp: Long)

data class Earnings(
    val date: String?,
    val epsEstimate: BigDecimal?,
    val epsActual: BigDecimal?,
    val revenueEstimate: BigDecimal?,
    val revenueActual: BigDecimal?,
)

data class Analyst(
    val buy: Int?,
    val hold: Int?,
    val sell: Int?,
    val target: BigDecimal?,
    val high: BigDecimal?,
    val low: BigDecimal?,
    val date: String?,
    val source: String,
    val median: BigDecimal? = null,
    val strongBuy: Int? = null,
    val strongSell: Int? = null,
)

data class Fundamentals(
    val metrics: Map<String, String> = emptyMap(),
    val annual: Map<String, List<Pair<String, BigDecimal>>> = emptyMap(),
    val analyst: Analyst? = null,
    val source: String = "",
    val quarterly: Map<String, List<Pair<String, BigDecimal>>> = emptyMap(),
    val asOf: String? = null,
)

data class Holding(
    val securityId: String,
    val quantity: BigDecimal,
    val costOriginal: BigDecimal,
    val costCad: BigDecimal,
    val realized: BigDecimal,
    val dividends: BigDecimal,
    val value: BigDecimal?,
    val priceGain: BigDecimal?,
    val fxGain: BigDecimal?,
    val day: BigDecimal?,
    val dayBase: BigDecimal?,
) {
    val average
        get() = costOriginal.divSafe(quantity)

    val pnl
        get() = value?.minus(costCad)

    val percent
        get() = pnl?.pct(costCad)

    val dayPercent
        get() = day?.let { p -> dayBase?.let { p.pct(it) } }
}

data class Result(
    val holdings: List<Holding>,
    val invested: BigDecimal,
    val cash: BigDecimal,
    val value: BigDecimal?,
    val realized: BigDecimal,
    val dividends: BigDecimal,
    val day: BigDecimal?,
    val dayBase: BigDecimal?,
) {
    val pnl
        get() = value?.minus(invested)

    val percent
        get() = if (invested > ZERO) pnl?.pct(invested) else null

    val dayPercent
        get() = day?.let { p -> dayBase?.let { p.pct(it) } }
}

data class Snapshot(
    val date: String,
    val portfolioId: String,
    val value: BigDecimal?,
    val invested: BigDecimal,
    val cash: BigDecimal,
)

data class Wallet(
    val portfolios: List<Portfolio> = emptyList(),
    val securities: List<Security> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val watchlists: List<Watchlist> = emptyList(),
    val items: List<WatchItem> = emptyList(),
    val alerts: List<PriceAlert> = emptyList(),
    val notifications: List<NotificationPreference> = emptyList(),
    val events: List<AlertEvent> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
    val quotes: Map<String, Quote> = emptyMap(),
    val prices: List<Point> = emptyList(),
    val pending: Int = 0,
) {
    fun security(id: String?) = securities.find { it.id == id }

    fun result(portfolio: String? = null) =
        Engine.calculate(
            transactions.filter { portfolio == null || it.portfolioId == portfolio },
            quotes,
            securities.find { it.symbol == "CAD=X" }?.let { quotes[it.id] },
        )
}
