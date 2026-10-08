package ca.monwallet.app

import ca.monwallet.app.marketdata.Filters
import ca.monwallet.app.marketdata.SectorUniverse
import ca.monwallet.app.marketdata.selectionEligible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectorUniverseTest {
    @Test fun everyDiscoverSectorHasAtLeastThirtyCuratedStocks() {
        val counts = SectorUniverse.stocks.groupingBy { it.sector }.eachCount()
        assertEquals(11, SectorUniverse.labels.size)
        SectorUniverse.labels.keys.forEach { sector ->
            assertTrue("$sector should contain at least 30 candidates", (counts[sector] ?: 0) >= 30)
        }
    }

    @Test fun sectorFilterOnlyReturnsStocksFromThatSector() {
        val technology = SectorUniverse.stocks.first { it.sector == "Technology" }
        assertTrue(selectionEligible(technology, Filters(sector = "Technology")))
        assertTrue(!selectionEligible(technology, Filters(sector = "Healthcare")))
    }
}
