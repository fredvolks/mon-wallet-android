package ca.monwallet.app

import ca.monwallet.app.domain.Quote
import ca.monwallet.app.domain.Security
import ca.monwallet.app.marketdata.*
import com.google.gson.Gson
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class YahooExtendedHoursTest {
    private val ny = ZoneId.of("America/New_York")
    private val date = LocalDate.of(2026, 10, 6)
    private fun at(hour: Int, minute: Int = 0) =
        date.atTime(LocalTime.of(hour, minute)).atZone(ny).toEpochSecond()

    private fun chart(pre: Boolean, print: Boolean = true, metaPrint: Boolean = false): JSONObject {
        val start = if (pre) at(4) else at(16)
        val end = if (pre) at(9, 30) else at(20)
        val key = if (pre) "pre" else "post"
        val meta = JSONObject()
            .put("currency", "USD")
            .put("exchangeTimezoneName", "America/New_York")
            .put("regularMarketTime", at(16) - if (pre) 86_400 else 0)
            .put("regularMarketPrice", 100)
            .put("previousClose", 98)
            .put("exchangeDataDelayedBy", 15)
            .put("currentTradingPeriod", JSONObject()
                .put("pre", JSONObject().put("start", at(4)).put("end", at(9, 30)))
                .put("regular", JSONObject().put("start", at(9, 30)).put("end", at(16)))
                .put("post", JSONObject().put("start", at(16)).put("end", at(20))))
        if (metaPrint) {
            meta.put(key + "MarketTime", start + 900)
                .put(key + "MarketPrice", 100.25)
                .put(key + "MarketChange", 0.25)
                .put(key + "MarketChangePercent", 0.25)
        }
        // A prior-session bar can be returned at the start of today's pre-market.
        val times = JSONArray().put(start - 86_400)
        val closes = JSONArray().put(150)
        if (print) {
            times.put(start + 300)
            closes.put(100.5)
        }
        return JSONObject().put("meta", meta).put("timestamp", times)
            .put("indicators", JSONObject().put("quote", JSONArray().put(
                JSONObject().put("close", closes))))
    }

    @Test fun currentPreMarketBarFlowsThroughCacheAndNormalizedQuote() {
        for ((symbol, exchange) in listOf("AAPL" to "NASDAQ", "NVDA" to "NASDAQ",
            "TSM" to "NYSE", "META" to "NASDAQ", "MSFT" to "NASDAQ", "TSLA" to "NASDAQ")) {
            val s = Security.of(symbol, symbol, exchange, "USD")
            assertTrue(supportsUsExtendedHours(s))
            val mapped = YahooQuoteMapper.map(s, chart(pre = true), at(4, 20), "Yahoo test")
            val cached = Gson().fromJson(Gson().toJson(mapped), Quote::class.java)
            val normalized = NormalizedQuote.from(cached, at(4, 20) * 1000)
            assertEquals(MarketSession.PRE_MARKET, normalized.marketSession)
            assertEquals(0, normalized.regularPrice.compareTo(BigDecimal("100")))
            assertTrue(normalized.regularChangePercent!! > BigDecimal("2"))
            assertTrue(normalized.regularChangePercent!! < BigDecimal("2.1"))
            assertEquals(0, normalized.preMarketPrice!!.compareTo(BigDecimal("100.5")))
            assertEquals(0, normalized.preMarketChange!!.compareTo(BigDecimal("0.5")))
            assertEquals(0, normalized.preMarketChangePercent!!.compareTo(BigDecimal("0.5")))
            assertEquals(QuoteFreshness.DELAYED, normalized.freshness)
            assertNull(normalized.afterHoursPrice)
        }
    }

    @Test fun afterHoursKeepsProviderChangeAndDoesNotUseRegularDayChange() {
        val s = Security.of("TSM", "TSM", "NYSE", "USD")
        val mapped = YahooQuoteMapper.map(s, chart(pre = false, print = false,
            metaPrint = true), at(16, 20), "Yahoo test")
        val normalized = NormalizedQuote.from(mapped, at(16, 20) * 1000)
        assertEquals(MarketSession.AFTER_HOURS, normalized.marketSession)
        assertEquals(0, normalized.afterHoursPrice!!.compareTo(BigDecimal("100.25")))
        assertEquals(0, normalized.afterHoursChange!!.compareTo(BigDecimal("0.25")))
        assertEquals(0, normalized.afterHoursChangePercent!!.compareTo(BigDecimal("0.25")))
        assertEquals(0, normalized.regularChange!!.compareTo(BigDecimal("2")))
        assertNull(normalized.preMarketPrice)
        assertNull(NormalizedQuote.from(mapped, at(20) * 1000).afterHoursPrice)
    }

    @Test fun noCurrentPrintAndCanadianListingShowNoExtendedPrice() {
        val aapl = Security.of("AAPL", "Apple", "NASDAQ", "USD")
        val noPrint = YahooQuoteMapper.map(aapl, chart(pre = true, print = false),
            at(4, 20), "Yahoo test")
        assertNull(noPrint.preMarketPrice)
        assertNull(NormalizedQuote.from(noPrint, at(4, 20) * 1000).preMarketPrice)
        val dol = Security.of("DOL.TO", "Dollarama", "TSX", "CAD")
        assertFalse(supportsUsExtendedHours(dol))
        val canadianChart = chart(pre = true).apply { getJSONObject("meta").put("currency", "CAD") }
        val canadian = YahooQuoteMapper.map(dol, canadianChart, at(4, 20), "Yahoo test")
        assertNull(canadian.preMarketPrice)
        assertNull(canadian.afterHoursPrice)
    }
}
