package ca.monwallet.app.marketdata

import org.junit.Assert.assertEquals
import org.junit.Test

class YahooDataSymbolTest {
    @Test fun mapsCanadianMcDonaldsCdrAliasesToCurrentYahooListing() {
        assertEquals("MCD.TO", yahooDataSymbol("MCD.NE"))
        assertEquals("MCD.TO", yahooDataSymbol("MCD.TO"))
        assertEquals("MCD.TO", yahooDataSymbol("MCDS.NE"))
        assertEquals("MCD.TO", yahooDataSymbol("MCDS.TO"))
        assertEquals("MCD.TO", yahooDataSymbol("mcd.ne"))
    }

    @Test fun leavesOtherYahooSymbolsUnchanged() {
        assertEquals("AAPL", yahooDataSymbol("AAPL"))
        assertEquals("DOL.TO", yahooDataSymbol("DOL.TO"))
    }
}
