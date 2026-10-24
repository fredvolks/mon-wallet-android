package ca.monwallet.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.monwallet.app.marketdata.StockScreener
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StockScreenerNetworkTest {
    @Test fun actualMarketScanReturns200EligibleStocksInsteadOfACuratedSample() = runBlocking {
        val result = withTimeout(60000) { StockScreener.scan("Tous", true, {}) }
        assertTrue("Thousands of exchange listings must actually be received",result.summary.received > 1000)
        assertEquals(200,result.stocks.size)
        assertEquals(200,result.stocks.map { it.screen!!.listing }.toSet().size)
        assertTrue(result.stocks.all { it.quote!!.price.toDouble() >= 1 && it.security.exchange in StockScreener.exchanges && it.screen!!.industry !in StockScreener.biotech })
        assertTrue(result.stocks.any { it.security.currency == "CAD" })
        assertTrue(result.stocks.any { it.security.currency == "USD" })
        android.util.Log.i("StockScreenerTest", "Received=${result.summary.received}; eligible=${result.summary.eligible}; retained=${result.stocks.size}; no biotech/OTC/sub-dollar")
    }
}
