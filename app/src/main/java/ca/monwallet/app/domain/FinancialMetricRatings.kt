package ca.monwallet.app.domain

import java.math.BigDecimal

enum class FinancialMetricRating { FAVORABLE, WATCH, UNFAVORABLE, CONTEXT }

/** Broad, conservative quality bands. Absolute size and valuation need sector context. */
object FinancialMetricRatings {
    fun rate(label: String, rawValue: String, annual: List<BigDecimal> = emptyList()): FinancialMetricRating {
        val value = rawValue.trim().replace("%", "").toBigDecimalOrNull()
            ?: return FinancialMetricRating.CONTEXT
        return when (label) {
            "Marge nette %" -> when {
                value < BigDecimal("5") -> FinancialMetricRating.UNFAVORABLE
                value < BigDecimal("15") -> FinancialMetricRating.WATCH
                else -> FinancialMetricRating.FAVORABLE
            }
            "Croissance revenus %", "Croissance BPA %" -> when {
                value < BigDecimal.ZERO -> FinancialMetricRating.UNFAVORABLE
                value < BigDecimal("15") -> FinancialMetricRating.WATCH
                else -> FinancialMetricRating.FAVORABLE
            }
            "ROE %" -> when {
                value < BigDecimal("5") -> FinancialMetricRating.UNFAVORABLE
                value < BigDecimal("15") -> FinancialMetricRating.WATCH
                else -> FinancialMetricRating.FAVORABLE
            }
            "ROA %" -> when {
                value < BigDecimal("3") -> FinancialMetricRating.UNFAVORABLE
                value < BigDecimal("8") -> FinancialMetricRating.WATCH
                else -> FinancialMetricRating.FAVORABLE
            }
            "Payout %" -> when {
                value < BigDecimal.ZERO || value > BigDecimal("100") -> FinancialMetricRating.UNFAVORABLE
                value > BigDecimal("60") -> FinancialMetricRating.WATCH
                else -> FinancialMetricRating.FAVORABLE
            }
            "MER" -> when {
                value < BigDecimal.ZERO || value > BigDecimal("0.75") -> FinancialMetricRating.UNFAVORABLE
                value <= BigDecimal("0.25") -> FinancialMetricRating.FAVORABLE
                else -> FinancialMetricRating.WATCH
            }
            "Bénéfice net", "BPA", "Free cash flow", "Operating cash flow" -> when {
                value < BigDecimal.ZERO -> FinancialMetricRating.UNFAVORABLE
                value.compareTo(BigDecimal.ZERO) == 0 -> FinancialMetricRating.WATCH
                annual.size >= 2 && annual[annual.lastIndex - 1] > BigDecimal.ZERO &&
                    value < annual[annual.lastIndex - 1].multiply(BigDecimal("0.8")) ->
                    FinancialMetricRating.WATCH
                else -> FinancialMetricRating.FAVORABLE
            }
            else -> FinancialMetricRating.CONTEXT
        }
    }
}