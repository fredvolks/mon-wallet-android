package ca.monwallet.app

import ca.monwallet.app.domain.Security
import ca.monwallet.app.marketdata.GlobalXFunds
import org.junit.Assert.*
import org.junit.Test

class GlobalXFundsTest {
    @Test fun officialTopHoldingsAndExposuresKeepTheirOwnDates() {
        val s = Security.of("HXS.TO", "Global X S&P 500", "TSX", "CAD", "ETF")
        val html = """<title>HXS - Global X fund</title><h1>Global X fund</h1><div id="holdings">
        <section><h2>Top Sector Allocations</h2><p>As at September 30, 2026</p>
          <div><div><div id="sector-allocation-chart" data-chart='{"Technology":"39.71"}'></div></div></div></section>
        <section><div><h2>Top Holdings</h2><p>As at October 20, 2026</p></div>
          <div><p>NVIDIA CORP</p><p>8.38%</p></div></section></div>"""
        val fund = GlobalXFunds.parse(s, html, "https://www.globalx.ca/product/hxs")
        assertEquals("2026-10-20", fund.direct.asOf)
        assertEquals("8.38", fund.direct.positions.single().weight.toPlainString())
        assertEquals("2026-09-30", fund.sectorExposure!!.asOf)
        assertEquals("39.71", fund.sectorExposure.weights.getValue("Technology").toPlainString())
        assertTrue(fund.compositionNote!!.contains("partielle"))
        assertNull(fund.underlying)
        assertTrue(runCatching { GlobalXFunds.parse(s, html.replace("HXS -", "ABC -"), "") }.isFailure)
    }
}
