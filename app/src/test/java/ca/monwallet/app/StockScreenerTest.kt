package ca.monwallet.app

import ca.monwallet.app.marketdata.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StockScreenerTest {
    private fun row(ticker: String, exchange: String = "NYSE", price: Double = 10.0,
        industry: String = "Industrial Machinery", pe: Double? = 10.0, growth: Double = 10.0): JSONObject {
        val v = mapOf<String, Any?>("name" to ticker, "description" to "Company $ticker", "exchange" to exchange,
            "country" to "United States", "sector" to "Producer Manufacturing", "industry" to industry,
            "close" to price, "currency" to if (exchange == "TSX") "CAD" else "USD", "price_earnings_ttm" to pe,
            "return_on_equity" to 15.0, "debt_to_equity" to .1, "total_revenue_yoy_growth_ttm" to growth,
            "earnings_per_share_diluted_yoy_growth_ttm" to 10.0, "net_margin" to 5.0, "Perf.6M" to 10.0)
        return JSONObject().put("s", "$exchange:$ticker").put("d", JSONArray(StockScreener.columns.map { v[it] ?: JSONObject.NULL }))
    }
    private fun response(rows: List<JSONObject>) = JSONObject().put("totalCount", rows.size).put("data", JSONArray(rows))
    @Test fun hardRulesExcludeOtcSubDollarBiotechAndDrugDevelopersBeforeTop200() {
        val r = StockScreener.parse(response(listOf(row("GOOD"),row("LOW",price=.99),row("OTC",exchange="OTC"),
            row("BIO",industry="Biotechnology"),row("DRUG",industry="Pharmaceuticals: Major"),
            row("SPLIT",industry="Investment Trusts/Mutual Funds"),row("CAD",exchange="TSX"))), "Tous", true, 1000)
        assertEquals(setOf("NYSE:GOOD","TSX:CAD"), r.stocks.map { it.screen!!.listing }.toSet())
        assertEquals(2,r.summary.excludedBiotech)
        assertEquals("CAD.TO", r.stocks.first { it.security.currency == "CAD" }.security.symbol)
    }
    @Test fun retains200AndRefillsAfterExclusionsWithoutBudgetOrCapBias() {
        val rows = (0..219).map { row("X$it",price=if (it==0) 10000.0 else 1.0,growth=it.toDouble()) } + row("BIO",industry="Biotechnology")
        val r = StockScreener.parse(response(rows), "Tous", true, 1000)
        assertEquals(200,r.stocks.size);assertEquals(220,r.summary.eligible)
        assertEquals("NYSE:X219",r.stocks.first().screen!!.listing)
        assertTrue(r.stocks.none { it.screen!!.listing == "NYSE:BIO" })
    }
    @Test fun noFinancialFiltersKeepMissingAndLossMakingEvidenceWithoutInventedScores() {
        val data=response(listOf(row("MISSING",pe=null),row("LOSS",pe=-3.0),row("NORMAL")))
        assertEquals(1,StockScreener.parse(data,"Tous",true,1000).stocks.size)
        val r=StockScreener.parse(data,"Tous",false,1000)
        assertEquals(3,r.stocks.size);assertNull(r.stocks.first { it.security.ticker=="MISSING" }.screen!!.score)
    }
    @Test fun partialUniverseIsAnErrorInsteadOfSilentFallbackToACatalog() {
        assertThrows(IllegalArgumentException::class.java) { StockScreener.parse(response(listOf(row("A"))).put("totalCount",3000),"Tous",true,1000) }
    }
    @Test fun regionalFilterUsesListingExchangeAndNotIssuerDomicile() {
        val r=StockScreener.parse(response(listOf(row("CA",exchange="TSX"),row("US"))),"Canada",true,1000)
        assertEquals(listOf("TSX:CA"),r.stocks.map { it.screen!!.listing })
    }
}
