package ca.monwallet.app.marketdata

import org.junit.Assert.assertEquals
import org.junit.Test

class YahooDataSymbolTest {
    @Test fun mapsCanadianMcDonaldsCdrToYahooListing() {
        assertEquals("ZMCD.NE", yahooDataSymbol("MCD.NE"))
        assertEquals("ZMCD.NE", yahooDataSymbol("mcd.ne"))
    }

    @Test fun leavesOtherYahooSymbolsUnchanged() {
        assertEquals("AAPL", yahooDataSymbol("AAPL"))
        assertEquals("DOL.TO", yahooDataSymbol("DOL.TO"))
    }
}
