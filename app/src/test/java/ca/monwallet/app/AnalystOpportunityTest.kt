package ca.monwallet.app

import ca.monwallet.app.domain.Analyst
import ca.monwallet.app.marketdata.AnalystOpportunity
import ca.monwallet.app.marketdata.MomentumMetrics
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class AnalystOpportunityTest {
    private fun momentum(score: Int) = MomentumMetrics(BigDecimal("20"), null,
        4.0, -3.0, 0, 8.0, 40.0, -8.0, 2.0, 10, 13, 80, score)

    @Test fun broadCoverageAndHealthyTrendCanBeatHugeSparseTarget() {
        val speculative = Analyst(2, 0, 0, BigDecimal("180"), BigDecimal("260"),
            BigDecimal("100"), null, "test")
        val covered = Analyst(26, 9, 0, BigDecimal("118"), BigDecimal("120"),
            BigDecimal("116"), null, "test")
        val a = AnalystOpportunity.assess(speculative, BigDecimal("100"), momentum(30))!!
        val b = AnalystOpportunity.assess(covered, BigDecimal("100"), momentum(90))!!
        assertTrue(a.upside > b.upside)
        assertTrue(b.analystCount > a.analystCount)
        assertTrue(b.score > a.score)
    }

    @Test fun missingCoverageHasNoOpportunityScore() {
        val unknown = Analyst(null, null, null, BigDecimal("180"), null, null, null, "test")
        assertNull(AnalystOpportunity.assess(unknown, BigDecimal("100"), momentum(50)))
    }
}
