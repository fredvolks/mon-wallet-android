package ca.monwallet.app

import ca.monwallet.app.domain.Security
import ca.monwallet.app.marketdata.Filters
import ca.monwallet.app.marketdata.NasdaqSummaryProvider
import ca.monwallet.app.marketdata.selectionEligible
import ca.monwallet.app.ui.compactQuantity
import java.math.BigDecimal
import java.util.Locale
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TestMonWallet::class)
class CompactAndSelectionTest {
    @Test fun compactQuantityOmitsCurrencyAndUsesCorrectUnits() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.CANADA_FRENCH)
            val stock = Security.of("DOL.TO", "Dollarama", "TSX", "CAD")
            assertEquals("1 part", compactQuantity(stock, BigDecimal.ONE))
            assertEquals("2 parts", compactQuantity(stock, BigDecimal(2)))
            assertEquals("0,25 part", compactQuantity(stock, BigDecimal("0.25")))
            val btc = Security.of("BTC-USD", "Bitcoin", "Crypto", "USD", "CRYPTO")
            assertEquals("0,024 BTC", compactQuantity(btc, BigDecimal("0.024")))
        } finally { Locale.setDefault(previous) }
    }

    @Test fun limitedSelectionRespectsMarketsAndAssetType() {
        val tsm = Security.of("TSM", "Taiwan Semiconductor", "NYSE", "USD")
        val dol = Security.of("DOL.TO", "Dollarama", "TSX", "CAD")
        assertTrue(selectionEligible(tsm, Filters()))
        assertTrue(selectionEligible(dol, Filters()))
        assertFalse(selectionEligible(dol, Filters(exchange = "NASDAQ,NYSE")))
        assertFalse(selectionEligible(tsm, Filters(country = "CA")))
        assertFalse(selectionEligible(Security.of("XEQT.TO", "XEQT", "TSX", "CAD", "ETF"), Filters()))
    }

    @Test fun nasdaqDisplayMagnitudesAreNotDropped() {
        val raw = JSONObject("""{"data":{"summaryData":{"MarketCap":{"value":"2.52T"},
            "AverageVolume":{"value":"11.5M"},"Yield":{"value":"0.80%"}}}}""")
        val metrics = NasdaqSummaryProvider().parse(raw)!!.metrics
        assertEquals("2520000", metrics["Capitalisation (M)"])
        assertEquals("11500000", metrics["Volume moyen"])
        assertEquals("0.80", metrics["Rendement dividende %"])
    }
}
