package ca.monwallet.app.domain

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Reports use dated closes only. A live quote never rewrites an older day's value. */
data class PortfolioDailySnapshot(
    val date: LocalDate,
    val openingValue: BigDecimal?,
    val closingValue: BigDecimal?,
    val netExternalFlow: BigDecimal,
    val dailyPnl: BigDecimal?,
    val dailyReturn: BigDecimal?, // percent, external cash flows at the day's opening
    val capitalInvested: BigDecimal,
    val cash: BigDecimal,
    val realizedPnl: BigDecimal,
    val unrealizedPnl: BigDecimal?,
    val dividends: BigDecimal,
    val positions: Map<String, BigDecimal>,
    val hasMarketClose: Boolean,
)

enum class ReportRange(val label: String) {
    WEEK("1S"), MONTH("1M"), THREE_MONTHS("3M"), SIX_MONTHS("6M"),
    YTD("YTD"), YEAR("1A"), FIVE_YEARS("5A"), TEN_YEARS("10A"), TOTAL("Total");

    fun start(today: LocalDate): LocalDate? = when (this) {
        WEEK -> today.minusWeeks(1)
        MONTH -> today.minusMonths(1)
        THREE_MONTHS -> today.minusMonths(3)
        SIX_MONTHS -> today.minusMonths(6)
        YTD -> today.withDayOfYear(1)
        YEAR -> today.minusYears(1)
        FIVE_YEARS -> today.minusYears(5)
        TEN_YEARS -> today.minusYears(10)
        TOTAL -> null
    }
}

data class ReportContribution(val securityId: String, val pnl: BigDecimal)
data class ReportSummary(
    val range: ReportRange,
    val base: PortfolioDailySnapshot?,
    val days: List<PortfolioDailySnapshot>,
    val complete: Boolean,
    val reason: String?,
    val performance: BigDecimal?,
    val gain: BigDecimal?,
    val value: BigDecimal?,
    val capital: BigDecimal?,
    val deposits: BigDecimal,
    val withdrawals: BigDecimal,
    val implicitFunding: BigDecimal,
    val dividends: BigDecimal,
    val fees: BigDecimal,
    val maxDrawdown: BigDecimal?,
    val currentDrawdown: BigDecimal?,
    val bestDay: PortfolioDailySnapshot?,
    val worstDay: PortfolioDailySnapshot?,
    val contributions: List<ReportContribution>,
    val personalReturn: BigDecimal?,
    val cumulative: List<Pair<LocalDate, BigDecimal>>,
)

object ReportEngine {
    private val oneHundred = BigDecimal(100)
    private val maxCloseAge = 7L

    private fun pointAt(points: List<Point>, date: LocalDate): Point? =
        points.lastOrNull { it.date <= date.toString() }
            ?.takeIf { ChronoUnit.DAYS.between(LocalDate.parse(it.date), date) <= maxCloseAge }

    /** A missing position close or historical USD/CAD rate invalidates that day's valuation. */
    fun history(wallet: Wallet, portfolioId: String? = null): List<PortfolioDailySnapshot> {
        val tx = wallet.transactions.filter { portfolioId == null || it.portfolioId == portfolioId }
            .sortedWith(compareBy<Transaction> { it.date }.thenBy { it.createdAt }.thenBy { it.id })
        if (tx.isEmpty()) return emptyList()
        val first = LocalDate.parse(tx.first().date)
        val today = LocalDate.now()
        if (first > today) return emptyList()
        val ids = tx.mapNotNull { it.securityId }.toSet()
        val fxId = wallet.securities.firstOrNull { it.symbol == "CAD=X" }?.id
        val grouped = wallet.prices.filter { it.securityId in ids || it.securityId == fxId }
            .groupBy { it.securityId }
            .mapValues { (_, list) -> list.distinctBy { it.date }.sortedBy { it.date } }
        val marketDates = grouped.values.flatMap { it.map { p -> LocalDate.parse(p.date) } }
        val observedDates = marketDates.toSet()
        val dates = (marketDates + tx.map { LocalDate.parse(it.date) })
            .filter { it >= first && it <= today }.toSortedSet()
        // First funding is the 0-value boundary for the lifetime return.
        val result = mutableListOf(PortfolioDailySnapshot(first.minusDays(1), ZERO, ZERO, ZERO,
            null, null, ZERO, ZERO, ZERO, ZERO, ZERO, emptyMap(), false))
        var previous = result.first()
        for (date in dates) {
            val datedTx = tx.filter { it.date <= date.toString() }
            val quoteMap = ids.mapNotNull { id ->
                val security = wallet.security(id) ?: return@mapNotNull null
                pointAt(grouped[id].orEmpty(), date)?.let { point ->
                    id to Quote(id, point.close, null, security.currency, point.timestamp,
                        point.date, "Clôture historique")
                }
            }.toMap()
            val fx = fxId?.let { id -> pointAt(grouped[id].orEmpty(), date)?.let { point ->
                Quote(id, point.close, null, "CAD", point.timestamp, point.date, "FX historique")
            } }
            val calculated = Engine.calculate(datedTx, quoteMap, fx)
            val weekday = date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            val observed = date in observedDates || (weekday &&
                calculated.holdings.none { it.quantity > ZERO })
            val closes = calculated.value?.takeIf { value -> value >= ZERO }
            val flow = calculated.invested - previous.capitalInvested
            val opening = previous.closingValue
            val pnl = if (observed && weekday && opening != null && closes != null &&
                ChronoUnit.DAYS.between(previous.date, date) <= maxCloseAge)
                closes - opening - flow else null
            // Transaction dates have no execution time. Assume external flows precede the close;
            // this is a daily flow-adjusted estimate, not an exact intraday TWR at each flow.
            val base = opening?.plus(flow)
            val dailyReturn = if (pnl != null && base != null && base > ZERO)
                pnl.pct(base) else null
            val positions = calculated.holdings.mapNotNull { holding ->
                holding.value?.let { holding.securityId to it }
            }.toMap()
            val unrealized = if (closes != null) calculated.holdings.fold(ZERO) { sum, holding ->
                sum + ((holding.value ?: ZERO) - holding.costCad)
            } else null
            val snap = PortfolioDailySnapshot(date, opening, closes, flow, pnl, dailyReturn,
                calculated.invested, calculated.cash, calculated.realized, unrealized,
                calculated.dividends, positions, observed && weekday)
            result += snap
            previous = snap
        }
        return result
    }

    fun summary(wallet: Wallet, history: List<PortfolioDailySnapshot>, range: ReportRange,
        today: LocalDate = LocalDate.now(), portfolioId: String? = null): ReportSummary {
        val cutoff = range.start(today)
        val base = if (cutoff == null) history.firstOrNull()
            else history.lastOrNull { it.date <= cutoff }
        val days = if (base == null) emptyList() else history.filter {
            it.date > base.date && it.date <= today
        }
        val latest = days.lastOrNull() ?: base
        val tooYoung = cutoff != null && (history.getOrNull(1)?.date ?: LocalDate.MAX) > cutoff
        val missing = days.any { it.closingValue == null ||
            (it.hasMarketClose && it.dailyReturn == null) }
        val complete = base != null && !tooYoung && !missing && days.isNotEmpty() &&
            base.closingValue != null && latest?.closingValue != null &&
            days.any { it.dailyReturn != null }
        val reason = when {
            base == null || tooYoung || days.isEmpty() -> "Historique insuffisant"
            missing || !complete -> "Données historiques partielles (cours ou FX manquant)"
            else -> null
        }
        var factor = ONE
        val cumulative = mutableListOf<Pair<LocalDate, BigDecimal>>()
        if (complete) {
            cumulative += base!!.date to ZERO
            for (day in days) {
                day.dailyReturn?.let { factor *= ONE + it.divide(oneHundred, MC) }
                cumulative += day.date to (factor - ONE) * oneHundred
            }
        }
        val gain = if (complete) latest!!.closingValue!! - base!!.closingValue!! -
            (latest.capitalInvested - base.capitalInvested) else null
        val perf = if (complete) (factor - ONE) * oneHundred else null
        val confirmed = days.filter { it.dailyReturn != null }
        var peak = ONE
        var worst = ZERO
        var current = ZERO
        if (complete) for ((_, pct) in cumulative) {
            val level = ONE + pct.divide(oneHundred, MC)
            if (level > peak) peak = level
            current = (level.divide(peak, MC) - ONE) * oneHundred
            if (current < worst) worst = current
        }
        val transactions = wallet.transactions.filter { t ->
            (portfolioId == null || t.portfolioId == portfolioId) &&
                base != null && t.date > base.date.toString() && t.date <= today.toString()
        }
        fun sum(type: TxType) = transactions.filter { it.type == type }
            .fold(ZERO) { value, tx -> value + tx.cad }
        val deposits = sum(TxType.DEPOSIT)
        val withdrawals = transactions.filter { it.type == TxType.WITHDRAWAL }
            .fold(ZERO) { value, tx -> value + tx.price * tx.fxRate }
        val dividends = sum(TxType.DIVIDEND)
        val fees = transactions.fold(ZERO) { value, tx -> value + tx.fees * tx.fxRate +
            (if (tx.type == TxType.FEE) tx.price * tx.fxRate else ZERO) }
        val implicit = if (complete) latest!!.capitalInvested - base!!.capitalInvested -
            deposits + withdrawals else ZERO
        val contributions = if (complete) {
            (base!!.positions.keys + latest!!.positions.keys + transactions.mapNotNull { it.securityId })
                .distinct().map { id ->
                    val related = transactions.filter { it.securityId == id }
                    val purchases = related.filter { it.type == TxType.BUY }.fold(ZERO) { a, t -> a + t.cad }
                    val sales = related.filter { it.type == TxType.SELL }.fold(ZERO) { a, t -> a + t.cad }
                    val income = related.filter { it.type == TxType.DIVIDEND }.fold(ZERO) { a, t -> a + t.cad }
                    ReportContribution(id, (latest.positions[id] ?: ZERO) -
                        (base.positions[id] ?: ZERO) - purchases + sales + income)
                }.sortedByDescending { it.pnl }
        } else emptyList()
        val cashFlows = if (complete) days.filter { it.netExternalFlow != ZERO }
            .map { it.date to it.netExternalFlow } else emptyList()
        return ReportSummary(range, base, days, complete, reason, perf, gain,
            if (complete) latest!!.closingValue else null,
            if (complete) latest!!.capitalInvested else null, deposits, withdrawals,
            implicit, dividends, fees, if (complete) worst else null,
            if (complete) current else null, confirmed.maxByOrNull { it.dailyReturn!! },
            confirmed.minByOrNull { it.dailyReturn!! }, contributions,
            if (complete) xirr(cashFlows, latest!!.date, latest.closingValue!!) else null,
            cumulative)
    }

    /** Annualized money weighted return. External cash flows are investor outlays. */
    fun xirr(flows: List<Pair<LocalDate, BigDecimal>>, end: LocalDate,
        finalValue: BigDecimal): BigDecimal? {
        if (flows.isEmpty() || finalValue <= ZERO) return null
        val origin = flows.minOf { it.first }
        if (ChronoUnit.DAYS.between(origin, end) < 7) return null
        val amounts = flows.map { it.first to -it.second } + (end to finalValue)
        fun npv(rate: Double): Double = amounts.sumOf { (day, amount) ->
            val years = ChronoUnit.DAYS.between(origin, day) / 365.25
            amount.toDouble() / Math.pow(1.0 + rate, years)
        }
        var low = -0.9999
        var high = 1000.0
        val left = npv(low)
        val right = npv(high)
        if (!left.isFinite() || !right.isFinite() || left * right >= 0) return null
        repeat(160) {
            val middle = (low + high) / 2.0
            if (npv(middle) * left > 0) low = middle else high = middle
        }
        return BigDecimal.valueOf((low + high) * 50.0)
    }

    /** Price return, converted to CAD at each endpoint for USD indices; dividends excluded. */
    fun benchmark(wallet: Wallet, symbol: String, start: LocalDate, end: LocalDate): BigDecimal? {
        val security = wallet.securities.firstOrNull { it.symbol == symbol } ?: return null
        val series = wallet.prices.filter { it.securityId == security.id }.sortedBy { it.date }
        val begin = pointAt(series, start) ?: return null
        val finish = pointAt(series, end) ?: return null
        val fx = wallet.securities.firstOrNull { it.symbol == "CAD=X" }
        val rates = wallet.prices.filter { it.securityId == fx?.id }.sortedBy { it.date }
        val firstFx = if (security.currency == "USD") pointAt(rates, LocalDate.parse(begin.date))?.close
            ?: return null else ONE
        val lastFx = if (security.currency == "USD") pointAt(rates, LocalDate.parse(finish.date))?.close
            ?: return null else ONE
        val initial = begin.close * firstFx
        return if (initial > ZERO) ((finish.close * lastFx) - initial).pct(initial) else null
    }
}
