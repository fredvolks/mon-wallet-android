package ca.monwallet.app

import ca.monwallet.app.domain.FinancialMetricRating
import ca.monwallet.app.domain.FinancialMetricRatings
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialMetricRatingsTest {
    @Test fun profitabilityGrowthAndContextUseConservativeBands() {
        assertEquals(FinancialMetricRating.FAVORABLE,
            FinancialMetricRatings.rate("Marge nette %", "39.99"))
        assertEquals(FinancialMetricRating.WATCH,
            FinancialMetricRatings.rate("Croissance revenus %", "8"))
        assertEquals(FinancialMetricRating.UNFAVORABLE,
            FinancialMetricRatings.rate("Bénéfice net", "-1"))
        assertEquals(FinancialMetricRating.CONTEXT,
            FinancialMetricRatings.rate("Cash", "64890000000"))
    }

    @Test fun strongMetricCanBeFlaggedWhenItsAnnualValueFallsSharply() {
        assertEquals(FinancialMetricRating.WATCH,
            FinancialMetricRatings.rate("BPA", "8", listOf(BigDecimal("11"), BigDecimal("8"))))
    }
}