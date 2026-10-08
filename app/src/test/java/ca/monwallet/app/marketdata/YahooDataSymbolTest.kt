package ca.monwallet.app.marketdata

import org.junit.Assert.assertEquals
import org.junit.Test

class YahooDataSymbolTest {
    @Test fun mapsCanadianMcDonaldsCdrToYahooListing() {
        assertEquals("ZMCD.NE", yahooDataSymbol("MCD.NE"))
        assertEquals("ZMCD.NE", yahooDataSymbol("MCD.TO"))
        assertEquals("ZMCD.NE", yahooDataSymbol("mcd.ne"))
        assertEquals("ZMCD.NE", yahooDataSymbol("mcd.to"))
    }

    @Test fun leavesOtherYahooSymbolsUnchanged() {
        assertEquals("AAPL", yahooDataSymbol("AAPL"))
        assertEquals("DOL.TO", yahooDataSymbol("DOL.TO"))
    }
}
