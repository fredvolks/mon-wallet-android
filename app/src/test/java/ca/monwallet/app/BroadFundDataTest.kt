package ca.monwallet.app

import ca.monwallet.app.domain.Security
import ca.monwallet.app.marketdata.BroadFundData
import org.junit.Assert.*
import org.junit.Test

class BroadFundDataTest {
    @Test fun overviewDoesNotMisusePriceDatesAsCompositionDates() {
        val s = Security.of("HXS.TO", "Global X", "TSX", "CAD", "ETF")
        val html = """<h1>Global X fund (TSX:HXS)</h1><p>Oct 22, 2026</p>
            <table><thead><tr><th>Name</th><th>Symbol</th><th>Weight</th></tr></thead><tbody>
            <tr><td>TRS S&P 500</td><td>n/a</td><td>100.00%</td></tr></tbody></table>"""
        val fund = BroadFundData.parse(s, html, "https://stockanalysis.com/quote/tsx/HXS/")
        assertTrue(fund.direct.asOf.isBlank())
        assertTrue(fund.compositionNote!!.contains("Date de composition non fournie"))
        assertEquals("TRS S&P 500", fund.direct.positions.single().symbol)
    }
    @Test fun fallbackIsDatedPartialAndDoesNotPretendToKnowUnderlyingCountries() {
        val s = Security.of("ZSP.TO", "BMO S&P 500 Index ETF", "TSX", "CAD", "ETF")
        val html = """<h1>TSX:ZSP Holdings Information</h1><h2>Top 25 Holdings</h2>Oct 20, 2026 Filter results
          <table><thead><tr><th>No.</th><th>Symbol</th><th>Name</th><th>Weight</th></tr></thead><tbody>
          <tr><td>1</td><td>NVDA</td><td>NVIDIA</td><td>7.89%</td></tr></tbody></table>"""
        val data = BroadFundData.parse(s, html, BroadFundData.url(s))
        assertEquals("2026-10-20", data.direct.asOf)
        assertEquals("7.89", data.direct.positions.single().weight.toPlainString())
        assertNull(data.underlying)
        assertTrue(data.compositionNote!!.contains("partielle"))
        assertNull(data.direct.positions.single().country)
        assertTrue(runCatching { BroadFundData.parse(s, html.replace("ZSP", "ABC"), BroadFundData.url(s)) }.isFailure)
    }
}
