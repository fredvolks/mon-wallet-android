package ca.monwallet.app

import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.ResearchStock
import ca.monwallet.app.ui.analystPurchase
import org.junit.Assert.*
import org.junit.Test

class AnalystPurchaseTest {
    private fun row(currency: String, price: String): ResearchStock {
        val s = Security.of("DEMO", "Test", if (currency == "CAD") "TSX" else "NYSE", currency)
        return ResearchStock(s, Quote(s.id, price.dec(), price.dec(), currency, 1L, "2026-10-23", "fixture"),
            null, emptyList(), emptyList(), emptyList(), emptyList())
    }
    @Test fun cardsShowWholeSharesAndTheirTotalCadCost() {
        val cad = analystPurchase(row("CAD", "44.84"), "500".dec(), null)!!
        assertEquals(0, cad.first.compareTo("11".dec()))
        assertEquals(0, cad.second.compareTo("493.24".dec()))
        val usd = analystPurchase(row("USD", "321.21"), "500".dec(), "1.4".dec())!!
        assertEquals(0, usd.first.compareTo(ONE))
        assertEquals(0, usd.second.compareTo("449.694".dec()))
    }
    @Test fun missingFxOrUnaffordableSharesNeverProduceAnInventedQuantity() {
        assertNull(analystPurchase(row("USD", "100"), "500".dec(), null))
        assertNull(analystPurchase(row("CAD", "501"), "500".dec(), null))
        assertNull(analystPurchase(row("CAD", "0"), "500".dec(), null))
        val mismatch = row("CAD", "100").let { it.copy(quote = it.quote!!.copy(currency = "USD")) }
        assertNull(analystPurchase(mismatch, "500".dec(), ONE))
    }
}
