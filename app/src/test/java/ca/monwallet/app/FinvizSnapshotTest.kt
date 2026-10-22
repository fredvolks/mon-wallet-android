package ca.monwallet.app

import ca.monwallet.app.marketdata.FinvizSnapshotProvider
import ca.monwallet.app.domain.*
import org.junit.Assert.*
import org.junit.Test

class FinvizSnapshotTest {
    private fun page(symbol: String, pe: String, debt: String, cap: String) = """
        <html><head><title>$symbol - Company Stock</title></head><body>
        <table class="snapshot-table2"><tr><td><table><tr>
        <td class="snapshot-td2"><div>P/E</div></td><td class="snapshot-td2"><b>$pe</b></td>
        <td class="snapshot-td2">Debt/Eq</td><td class="snapshot-td2">$debt</td>
        <td class="snapshot-td2">Market Cap</td><td class="snapshot-td2">$cap</td>
        </tr></table></td></tr></table></body></html>
    """
    @Test fun nestedSnapshotSuppliesTsmMetricsAndHealth() {
        val result = FinvizSnapshotProvider().parse(page("TSM", "26.54", "0.14", "2138.65B"), "TSM")!!
        assertEquals("26.54", result.metrics["P/E"])
        assertEquals("0.14", result.metrics["Dette / capitaux propres"])
        assertEquals("2138650", result.metrics["Capitalisation (M)"])
        val health = FinancialHealthProfile.assess(Security.of("TSM", "TSMC", "NYSE", "USD"), result)
        assertEquals(FinancialMetricRating.FAVORABLE, health.rating)
        assertTrue(health.details.any { "2138.65 G USD" in it })
    }
    @Test fun spcxMissingPeRemainsUnavailableWhileDebtAndSizeLoad() {
        val result = FinvizSnapshotProvider().parse(page("SPCX", "-", "0.22", "1943.21B"), "SPCX")!!
        assertFalse("P/E" in result.metrics)
        assertTrue("P/E" in result.unavailableMetrics)
        assertEquals("0.22", result.metrics["Dette / capitaux propres"])
        assertEquals("1943210", result.metrics["Capitalisation (M)"])
        assertNull(FinvizSnapshotProvider().parse(page("TSM", "20", "1", "10B"), "SPCX"))
        assertNull(FinvizSnapshotProvider().parse("<title>Access denied</title>", "TSM"))
    }
}
