package ca.monwallet.app

import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.EtfData
import org.junit.Assert.*
import org.junit.Test

class EtfCompositionTest {
    @Test fun directAndUnderlyingTablesKeepSeparateDatesAndPublishedWeights() {
        val csv = """Fund Holdings as of,"Oct 22, 2026"
Ticker,Name,Sector,Asset Class,Weight (%),Location
XTOT,"iShares US, total",Other,Equity,45.69,Canada
XIC,iShares Canada,Financials,Equity,25.72,Canada
CAD,Cash,Cash,Cash,0.09,Canada
Fund Holdings as of,"Oct 21, 2026"
Ticker,Name,Sector,Asset Class,Weight (%),Location
NVDA,NVIDIA,Information Technology,Equity,3.17,United States
AAPL,APPLE,Information Technology,Equity,3.13,United States
RY,Royal Bank,Financials,Equity,1.97,Canada
"""
        val tables = EtfData.parseHoldingsCsv(csv)
        assertEquals(2, tables.size)
        assertEquals("2026-10-22", tables[0].asOf)
        assertEquals("2026-10-21", tables[1].asOf)
        assertEquals("iShares US, total", tables[0].positions[0].name)
        assertEquals("3.17".dec(), tables[1].positions[0].weight)
        assertEquals("6.30".dec(), tables[1].weights(true).first().second)
        assertEquals("7.27".dec(), tables[1].positions.fold(ZERO) { a, h -> a + h.weight })
    }
    @Test fun incompleteOrUndatedIssuerResponsesAreRejected() {
        listOf("<html>Access denied</html>", "Ticker,Name,Weight (%)\nX,Foo,20",
            "Fund Holdings as of,garbage\nTicker,Name,Weight (%)\nX,Foo,20").forEach { csv ->
            assertTrue(runCatching { EtfData.parseHoldingsCsv(csv) }.isFailure)
        }
    }
    @Test fun fundAndDirectSharesUseMarketValueWithoutDoubleCounting() {
        val p = Portfolio(id = "p", name = "Test")
        val etf = Security.of("XEQT.TO", "XEQT", "TSX", "CAD", "ETF")
        val stock = Security.of("GURU.TO", "GURU Energy", "TSX", "CAD", "STOCK")
        val wallet = Wallet(portfolios = listOf(p), securities = listOf(etf, stock),
            transactions = listOf(Transaction(portfolioId = p.id, securityId = etf.id, quantity = "10".dec(), price = "50".dec()),
                Transaction(portfolioId = p.id, securityId = stock.id, quantity = "10".dec(), price = "30".dec())),
            quotes = mapOf(etf.id to Quote(etf.id, "50".dec(), null, "CAD", 0, "2026-10-22", "test"),
                stock.id to Quote(stock.id, "30".dec(), null, "CAD", 0, "2026-10-22", "test")))
        val result = wallet.result()
        val a = Allocation.byAssetType(result, wallet.securities.associateBy { it.id }, false)
        assertEquals(2, a.slices.size)
        assertEquals(0, "62.5".dec().compareTo(a.slices.first { it.label == "FNB / ETF" }.weight))
        assertEquals(0, "800".dec().compareTo(a.total!!))
        val missing = result.copy(holdings = result.holdings.map { if (it.securityId == stock.id) it.copy(value = null) else it })
        assertFalse(Allocation.byAssetType(missing, wallet.securities.associateBy { it.id }, false).complete)
        assertNull(Allocation.byAssetType(missing, wallet.securities.associateBy { it.id }, false).total)
        val unknown = Allocation.byAssetType(result, mapOf(etf.id to etf), false)
        assertEquals(0, "300".dec().compareTo(unknown.slices.first { it.label == "Autres / non classés" }.value))
        val cash = Allocation.byAssetType(result.copy(cash = "200".dec()), wallet.securities.associateBy { it.id }, true)
        assertEquals(0, "50".dec().compareTo(cash.slices.first { it.label == "FNB / ETF" }.weight))
        assertNull(EtfData.officialPage(etf.copy(currency = "USD", exchange = "NASDAQ")))
        assertNull(EtfData.officialPage(stock))
    }
}
