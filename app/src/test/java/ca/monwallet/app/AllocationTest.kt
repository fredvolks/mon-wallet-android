package ca.monwallet.app

import ca.monwallet.app.domain.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class AllocationTest {
    private fun d(value: String) = BigDecimal(value)
    private val etf = Security("etf", "XEQT.TO", "All Equity ETF", "TSX", "CAD", "ETF")
    private val stock = Security("stock", "TSM", "TSMC", "NYSE", "USD", "STOCK", sector = "Technologie")
    private fun position(id: String, value: String) = Holding(id, d("10"), d("10"), d("10"),
        ZERO, ZERO, d(value), ZERO, ZERO, ZERO, ZERO)
    private fun result() = Result(listOf(position("etf", "500"), position("stock", "300")),
        d("600"), d("200"), d("1000"), ZERO, ZERO, null, null)

    @Test fun titleWeightsFollowValueAndCashSetting() {
        val map = listOf(etf, stock).associateBy { it.id }
        val without = Allocation.byTitle(result(), map, false)
        assertTrue(without.complete)
        assertEquals(0, d("62.5").compareTo(without.slices.first().weight))
        val withCash = Allocation.byTitle(result(), map, true)
        assertEquals(0, d("50").compareTo(withCash.slices.first().weight))
        assertEquals(3, withCash.slices.size)
    }

    @Test fun etfSectorNeedsVerifiedComposition() {
        val map = listOf(etf, stock).associateBy { it.id }
        assertFalse(Allocation.bySector(result(), map).complete)
        assertNull(Allocation.bySector(result(), map).total)
        val actual = Allocation.bySector(result(), map,
            mapOf("etf" to mapOf("Technologie" to d("0.2"), "Finance" to d("0.8"))))
        assertEquals(0, d("400").compareTo(actual.slices.first { it.label == "Technologie" }.value))
    }

    @Test fun missingQuotesNeverBecomeZeroAllocations() {
        val unknown = result().copy(holdings = listOf(position("etf", "500"), position("stock", "300").copy(value = null)))
        val allocation = Allocation.byTitle(unknown, listOf(etf, stock).associateBy { it.id }, false)
        assertFalse(allocation.complete)
        assertNull(allocation.total)
    }
}
