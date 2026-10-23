package ca.monwallet.app

import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DividendScreenerTest {
    private val today = LocalDate.parse("2026-10-23")
    private fun payment(day: String, amount: String) = DividendPayment(day, amount.dec())
    @Test fun trailingYearIncludesOnlyPastEventsAndPreservesAnnualGrowth() {
        val payments = listOf(payment("2025-01-15", "1"), payment("2025-04-15", "1"),
            payment("2025-07-15", "1"), payment("2025-10-15", "1"),
            payment("2026-01-15", "1.10"), payment("2026-04-15", "1.10"),
            payment("2026-07-15", "1.10"), payment("2026-10-15", "1.10"), payment("2026-11-15", "99"))
        val d = DividendData.calculate(payments, "100".dec(), "CAD", today, LocalDate.parse("2000-01-01"))
        assertEquals("4.40".dec(), d.annualAmount)
        assertEquals(0, "4.4".dec().compareTo(d.yield!!))
        assertEquals(0, "10".dec().compareTo(d.growth!!))
        assertEquals("QUARTERLY", d.frequency)
        assertEquals("2026-10-15", d.lastExDate)
    }
    @Test fun monthlyFiltersUnknownCoverageAndBoundaryValues() {
        val payments = (0..11).map { payment(today.minusMonths(it.toLong()).withDayOfMonth(15).toString(), "0.50") }
        val d = DividendData.calculate(payments, "100".dec(), "USD", today, LocalDate.parse("2000-01-01"))
        assertEquals("MONTHLY", d.frequency)
        assertTrue(dividendEligible(dividendPreset("6", true).copy(dividendMax = "6".dec()), d, null, null))
        assertFalse(dividendEligible(dividendPreset("7"), d, null, null))
        assertFalse(dividendEligible(dividendPreset("0"), null, null, null))
        assertFalse(dividendEligible(dividendPreset().copy(payoutMax = "50".dec()), d, null, null))
        assertTrue(dividendEligible(dividendPreset().copy(payoutMax = "50".dec()), d, null, "40".dec()))
        val recent = DividendData.calculate(payments, "100".dec(), "USD", today, today.minusMonths(3))
        assertNull(recent.yield)
        assertEquals("UNKNOWN", recent.frequency)
    }
    @Test fun incomePresetsDoNotSilentlyRequirePositiveMomentum() {
        val f = dividendPreset()
        assertFalse(f.requireHistory)
        assertFalse(f.antiPump)
        assertNull(f.minPerformance)
        assertTrue(f.dividendOnly)
    }
}
