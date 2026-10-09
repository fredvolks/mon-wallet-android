package ca.monwallet.app.ui

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartPeriodReturnTest {
    @Test fun returnUsesSelectedChartStartAndLatestQuote() {
        val result = chartPeriodReturn(BigDecimal("110"), BigDecimal("100"))

        assertEquals(BigDecimal("10"), result?.amount)
        assertEquals(BigDecimal("10.0"), result?.percent)
    }

    @Test fun returnsAreNegativeWhenQuoteIsBelowSelectedPeriodStart() {
        val result = chartPeriodReturn(BigDecimal("90"), BigDecimal("100"))

        assertEquals(BigDecimal("-10"), result?.amount)
        assertEquals(BigDecimal("-10.0"), result?.percent)
    }

    @Test fun returnIsUnavailableWithoutValidStartPrice() {
        assertNull(chartPeriodReturn(BigDecimal("100"), BigDecimal.ZERO))
        assertNull(chartPeriodReturn(null, BigDecimal("100")))
    }
}
