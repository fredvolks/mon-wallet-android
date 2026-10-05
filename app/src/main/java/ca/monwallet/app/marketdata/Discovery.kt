package ca.monwallet.app.marketdata

import ca.monwallet.app.Services
import ca.monwallet.app.domain.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request

data class Filters(
    val cap: BigDecimal = BigDecimal("1000000000"),
    val volume: BigDecimal = BigDecimal("100000"),
    val price: BigDecimal = BigDecimal("5"),
    val pe: BigDecimal? = null,
    val country: String = "",
    val sector: String = "",
    val exchange: String = "NASDAQ,NYSE,TSX",
    val excludeEtf: Boolean = true,
)

data class DiscoveryRow(
    val security: Security,
    val price: BigDecimal,
    val cap: BigDecimal?,
    val pe: BigDecimal?,
    val one: BigDecimal?,
    val three: BigDecimal?,
    val six: BigDecimal?,
    val year: BigDecimal?,
    val trend: Double?,
    val points: List<Point>,
)

class Discovery(private val s: Services) {
    suspend fun screen(f: Filters, onProgress: (String) -> Unit): List<DiscoveryRow> {
        val key =
            s.secure.get("fmp_key")?.takeIf { it.isNotBlank() }
                ?: error("Données de screener indisponibles avec la source actuelle.")
        val params =
            mutableMapOf(
                "apikey" to key,
                "marketCapMoreThan" to f.cap.toPlainString(),
                "volumeMoreThan" to f.volume.toPlainString(),
                "priceMoreThan" to f.price.toPlainString(),
                "isActivelyTrading" to "true",
                "limit" to "30",
            )
        if (f.excludeEtf) params["isEtf"] = "false"
        if (f.country.isNotBlank()) params["country"] = f.country
        if (f.sector.isNotBlank()) params["sector"] = f.sector
        if (f.exchange.isNotBlank()) params["exchange"] = f.exchange
        val raw =
            Http.array(
                    Request.Builder()
                        .url(
                            Http.url(
                                "https://financialmodelingprep.com/stable/company-screener",
                                params,
                            )
                        )
                        .build()
                )
                .objects()
        val gate = Semaphore(2)
        val complete = java.util.concurrent.atomic.AtomicInteger(0)
        return supervisorScope {
            raw.map { o ->
                    async {
                        gate.withPermit {
                            try {
                                val symbol = o.text("symbol") ?: return@withPermit null
                                val exchange = o.text("exchangeShortName") ?: return@withPermit null
                                val currency =
                                    when (exchange) {
                                        "TSX",
                                        "TSXV" -> "CAD"
                                        "NASDAQ",
                                        "NYSE",
                                        "AMEX" -> "USD"
                                        else -> return@withPermit null
                                    }
                                val sec =
                                    s.repo.current().securities.find {
                                        it.symbol == symbol && it.currency == currency
                                    }
                                        ?: Security.of(
                                            symbol,
                                            o.optString("companyName", symbol),
                                            exchange,
                                            currency,
                                            if (o.optBoolean("isEtf")) "ETF" else "STOCK",
                                        )
                                val ratio =
                                    runCatching {
                                            Http.array(
                                                    Request.Builder()
                                                        .url(
                                                            Http.url(
                                                                "https://financialmodelingprep.com/stable/ratios-ttm",
                                                                mapOf(
                                                                    "symbol" to symbol,
                                                                    "apikey" to key,
                                                                ),
                                                            )
                                                        )
                                                        .build()
                                                )
                                                .optJSONObject(0)
                                        }
                                        .getOrNull()
                                val pe = ratio?.number("priceToEarningsRatioTTM")
                                if (f.pe != null && (pe == null || pe <= ZERO || pe >= f.pe))
                                    return@withPermit null
                                val points =
                                    runCatching { s.market.history(sec, "2y") }
                                        .getOrDefault(emptyList())
                                        .sortedBy { it.date }
                                fun performance(months: Long): BigDecimal? {
                                    val end = points.lastOrNull() ?: return null
                                    val start =
                                        points.lastOrNull {
                                            it.date <=
                                                LocalDate.parse(end.date)
                                                    .minusMonths(months)
                                                    .toString()
                                        } ?: return null
                                    return (end.close - start.close).pct(start.close)
                                }
                                val one = performance(1)
                                val three = performance(3)
                                val six = performance(6)
                                val daily =
                                    points
                                        .takeLast(127)
                                        .zipWithNext { a, b ->
                                            (b.close - a.close).divSafe(a.close)?.toDouble()
                                        }
                                        .filterNotNull()
                                val volatility =
                                    if (daily.isNotEmpty()) {
                                        val average = daily.average()
                                        sqrt(daily.sumOf { (it - average).pow(2) } / daily.size) *
                                            100
                                    } else null
                                var max = Double.MIN_VALUE
                                var drawdown = 0.0
                                points.takeLast(127).forEach { p ->
                                    val v = p.close.toDouble()
                                    max = max(max, v)
                                    if (max > 0) drawdown = min(drawdown, (v / max - 1) * 100)
                                }
                                val trend =
                                    if (
                                        points.size >= 126 &&
                                            one != null &&
                                            three != null &&
                                            six != null &&
                                            volatility != null
                                    )
                                        one.toDouble() * .2 +
                                            three.toDouble() * .35 +
                                            six.toDouble() * .45 - volatility * 2 +
                                            drawdown * .3 +
                                            daily.count { it > 0 }.toDouble() / daily.size * 5
                                    else null
                                DiscoveryRow(
                                    sec,
                                    o.number("price") ?: return@withPermit null,
                                    o.number("marketCap"),
                                    pe,
                                    one,
                                    three,
                                    six,
                                    performance(12),
                                    trend,
                                    points,
                                )
                            } finally {
                                onProgress("Analyse ${complete.incrementAndGet()} / ${raw.size}")
                            }
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }
}
