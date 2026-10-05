package ca.monwallet.app.marketdata

import ca.monwallet.app.domain.Point
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.WeekFields
import kotlin.math.*

enum class PerformancePeriod(val label: String, val minimumSessions: Int) {
    W1("1S", 4), M1("1M", 15), M3("3M", 50), M6("6M", 100), Y1("1A", 200), Y5("5A", 1000);

    fun start(end: LocalDate): LocalDate = when (this) {
        W1 -> end.minusWeeks(1)
        M1 -> end.minusMonths(1)
        M3 -> end.minusMonths(3)
        M6 -> end.minusMonths(6)
        Y1 -> end.minusYears(1)
        Y5 -> end.minusYears(5)
    }
}

data class MomentumMetrics(
    val performance: BigDecimal,
    val cagr5y: BigDecimal?,
    val bestDay: Double,
    val worstDay: Double,
    val extremeDays: Int,
    val topThreeDays: Double,
    val gainConcentration: Double,
    val maxDrawdown: Double,
    val dailyVolatility: Double,
    val positiveWeeks: Int,
    val totalWeeks: Int,
    val regularity: Int,
    val score: Int,
)

/** Scores historical closing prices only. Quotes from pre/after-hours never enter this ranking. */
object MomentumEngine {
    fun performance(points: List<Point>, period: PerformancePeriod): BigDecimal? =
        window(points, period)?.let { (start, end) ->
            if (start.close.signum() <= 0) null
            else end.close.subtract(start.close).multiply(BigDecimal(100)).divide(start.close,
                java.math.MathContext.DECIMAL128)
        }

    fun analyze(points: List<Point>, period: PerformancePeriod, averageDollarVolume: Double?): MomentumMetrics? {
        val slice = windowSlice(points, period) ?: return null
        val values = slice.map { it.close.toDouble() }
        if (values.any { !it.isFinite() || it <= 0 }) return null
        val daily = values.zipWithNext { a, b -> (b / a - 1.0) * 100.0 }
        val total = (values.last() / values.first() - 1.0) * 100.0
        val positive = daily.filter { it > 0 }.sortedDescending()
        val top3 = positive.take(3).sum()
        val concentration = if (total > 0.1) (top3 / total * 100).coerceAtLeast(0.0) else 100.0
        var peak = values.first()
        var drawdown = 0.0
        values.forEach { v -> peak = max(peak, v); drawdown = min(drawdown, (v / peak - 1) * 100) }
        val average = daily.average()
        val volatility = sqrt(daily.sumOf { (it - average).pow(2) } / daily.size)
        val weeks = slice.groupBy {
            val date = LocalDate.parse(it.date)
            date.get(WeekFields.ISO.weekBasedYear()) to date.get(WeekFields.ISO.weekOfWeekBasedYear())
        }.values.map { it.last().close.toDouble() }
        val positiveWeeks = weeks.zipWithNext().count { (a, b) -> b > a }
        val totalWeeks = (weeks.size - 1).coerceAtLeast(0)
        val positiveRatio = if (totalWeeks > 0) positiveWeeks.toDouble() / totalWeeks
            else daily.count { it > 0 }.toDouble() / daily.size
        val logPrices = values.map(::ln)
        val n = logPrices.size.toDouble()
        val meanX = (n - 1) / 2
        val meanY = logPrices.average()
        val cov = logPrices.indices.sumOf { (it - meanX) * (logPrices[it] - meanY) }
        val varX = logPrices.indices.sumOf { (it - meanX).pow(2) }
        val varY = logPrices.sumOf { (it - meanY).pow(2) }
        val r2 = if (varX > 0 && varY > 0) (cov * cov / (varX * varY)).coerceIn(0.0, 1.0) else 0.0
        val slopeQuality = if (cov > 0) r2 else 0.0
        val extreme = daily.count { abs(it) >= 10 }
        val regularity = ((positiveRatio * 45 + slopeQuality * 35 +
            (1 - volatility / 12).coerceIn(0.0, 1.0) * 20) -
            (concentration - 65).coerceAtLeast(0.0) * 0.25).roundToInt().coerceIn(0, 100)
        val targetReturn = when (period) {
            PerformancePeriod.W1 -> 5.0
            PerformancePeriod.M1 -> 12.0
            PerformancePeriod.M3 -> 25.0
            PerformancePeriod.M6 -> 40.0
            PerformancePeriod.Y1 -> 65.0
            PerformancePeriod.Y5 -> 180.0
        }
        val returnPart = (total / targetReturn).coerceIn(0.0, 1.0) * 30
        val drawdownPart = (1 - abs(drawdown) / 35).coerceIn(0.0, 1.0) * 15
        val concentrationPart = (1 - (concentration - 30).coerceAtLeast(0.0) / 75).coerceIn(0.0, 1.0) * 15
        val liquidityPart = ((averageDollarVolume ?: 0.0) / 25_000_000.0).coerceIn(0.0, 1.0) * 10
        val confirmation = if (period == PerformancePeriod.W1) positiveRatio * 10 else {
            val confirm = when (period) {
                PerformancePeriod.M3 -> listOf(PerformancePeriod.M1, PerformancePeriod.M6)
                PerformancePeriod.Y5 -> listOf(PerformancePeriod.Y1)
                else -> listOf(PerformancePeriod.M1)
            }
            confirm.mapNotNull { performance(points, it)?.toDouble() }
                .takeIf { it.isNotEmpty() }?.count { it > 0 }?.toDouble()?.div(confirm.size)?.times(10) ?: 0.0
        }
        val spikePenalty = (daily.maxOrNull() ?: 0.0).let { (it - 10).coerceAtLeast(0.0) * 0.55 }
        val extremePenalty = (extreme - 1).coerceAtLeast(0) * 1.5
        val score = (returnPart + regularity * .20 + drawdownPart + concentrationPart +
            liquidityPart + confirmation - spikePenalty - extremePenalty)
            .roundToInt().coerceIn(0, 100)
        val cagr = if (period == PerformancePeriod.Y5)
            BigDecimal.valueOf(((values.last() / values.first()).pow(1.0 / 5) - 1) * 100)
        else null
        return MomentumMetrics(BigDecimal.valueOf(total), cagr, daily.maxOrNull() ?: 0.0,
            daily.minOrNull() ?: 0.0, extreme, top3, concentration, drawdown,
            volatility, positiveWeeks, totalWeeks, regularity, score)
    }

    private fun window(points: List<Point>, period: PerformancePeriod): Pair<Point, Point>? =
        windowSlice(points, period)?.let { it.first() to it.last() }

    private fun windowSlice(points: List<Point>, period: PerformancePeriod): List<Point>? {
        val sorted = points.filter { it.close.signum() > 0 }.distinctBy { it.date }.sortedBy { it.date }
        val end = sorted.lastOrNull() ?: return null
        val target = period.start(LocalDate.parse(end.date)).toString()
        val startIndex = sorted.indexOfLast { it.date <= target }
        if (startIndex < 0) return null
        val slice = sorted.drop(startIndex)
        return slice.takeIf { it.size - 1 >= period.minimumSessions }
    }
}
