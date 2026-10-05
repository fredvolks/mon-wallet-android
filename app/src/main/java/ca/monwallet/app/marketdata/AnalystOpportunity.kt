package ca.monwallet.app.marketdata

import ca.monwallet.app.domain.Analyst
import java.math.BigDecimal
import kotlin.math.roundToInt

data class AnalystAssessment(val upside: Double, val analystCount: Int,
    val buyRatio: Double, val dispersion: Double?, val score: Int)

/** A ranking aid, never a buy recommendation. Missing coverage is not fabricated. */
object AnalystOpportunity {
    fun assess(analyst: Analyst, price: BigDecimal, momentum: MomentumMetrics?): AnalystAssessment? {
        val count = listOfNotNull(analyst.buy, analyst.hold, analyst.sell).sum()
        val target = analyst.target ?: return null
        if (price.signum() <= 0 || target.signum() <= 0 || count <= 0) return null
        val upside = (target - price).toDouble() / price.toDouble() * 100
        val buyRatio = (analyst.buy ?: 0).toDouble() / count
        val dispersion = if (analyst.high != null && analyst.low != null)
            (analyst.high - analyst.low).toDouble() / target.toDouble() * 100 else null
        val score = (upside.coerceIn(0.0, 60.0) / 60 * 30 +
            buyRatio * 20 + (count.toDouble() / 30).coerceIn(0.0, 1.0) * 15 +
            (momentum?.score ?: 0) * .25 +
            (1 - (dispersion ?: 50.0) / 100).coerceIn(0.0, 1.0) * 10)
            .roundToInt().coerceIn(0, 100)
        return AnalystAssessment(upside, count, buyRatio, dispersion, score)
    }
}
