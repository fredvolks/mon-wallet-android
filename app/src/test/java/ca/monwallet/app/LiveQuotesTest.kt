package ca.monwallet.app

import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.*
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class LiveQuotesTest {
    @Test fun foregroundPollingIsAdaptiveAndDoesNotHammerClosedMarkets() {
        assertEquals(5_000L, foregroundQuoteRefreshDelayMillis(true, 8, false))
        assertEquals(5_000L, foregroundQuoteRefreshDelayMillis(true, 9, false))
        assertEquals(5_000L, foregroundQuoteRefreshDelayMillis(true, 15, false))
        assertEquals(5_000L, foregroundQuoteRefreshDelayMillis(true, 16, false))
        assertEquals(5_000L, foregroundQuoteRefreshDelayMillis(false, 8, false))
        assertEquals(60_000L, foregroundQuoteRefreshDelayMillis(true, 8, true))
        assertEquals(60_000L, foregroundQuoteRefreshDelayMillis(false, 8, true))
    }

    private val now = 1_700_000_000_000L
    private fun quote(session: String, delay: Int? = null, fetched: Long = now) = Quote(
        securityId = "US", price = BigDecimal("100"), previous = BigDecimal("98"),
        currency = "USD", timestamp = now, sessionDate = LocalDate.now().toString(),
        source = "test", delay = delay, fetchedAt = fetched,
        preMarketPrice = BigDecimal("101"), preMarketTimestamp = now,
        afterHoursPrice = BigDecimal("103"), afterHoursTimestamp = now,
        marketSession = session,
    )

    @Test fun preAndAfterNeverReplaceRegularChange() {
        val preNow = now - 10 * 60 * 60_000L
        val pre = NormalizedQuote.from(quote("PRE_MARKET", fetched = preNow).copy(
            preMarketTimestamp = preNow), preNow)
        assertEquals(MarketSession.PRE_MARKET, pre.marketSession)
        assertEquals(0, pre.regularChange!!.compareTo(BigDecimal("2")))
        assertEquals(0, pre.preMarketChange!!.compareTo(BigDecimal("1")))
        assertNull(pre.afterHoursPrice)
        val after = NormalizedQuote.from(quote("AFTER_HOURS"), now)
        assertEquals(0, after.afterHoursChange!!.compareTo(BigDecimal("3")))
        assertNull(after.preMarketPrice)
        assertNotNull(NormalizedQuote.from(quote("CLOSED"), now).afterHoursPrice)
        val direct = NormalizedQuote.from(quote("AFTER_HOURS").copy(
            afterHoursChange = BigDecimal("2.5"),
            afterHoursChangePercent = BigDecimal("2.08")), now)
        assertEquals(0, direct.afterHoursChange!!.compareTo(BigDecimal("2.5")))
        assertEquals(0, direct.afterHoursChangePercent!!.compareTo(BigDecimal("2.08")))
        assertNull(NormalizedQuote.from(quote("AFTER_HOURS"), now + 24 * 60 * 60_000L)
            .afterHoursPrice)
    }

    private fun eastern(time: String) = java.time.LocalDateTime.parse(time)
        .atZone(java.time.ZoneId.of("America/New_York")).toInstant().toEpochMilli()

    @Test fun afterSurvivesClosedNightAndMorningUntil0930() {
        val print = eastern("2026-10-22T19:59:00")
        val q = quote("CLOSED").copy(afterHoursTimestamp = print, fetchedAt = print)
        for (time in listOf("2026-10-22T20:00:00", "2026-10-22T23:59:00",
            "2026-10-23T00:01:00", "2026-10-23T09:29:59")) {
            val normalized = NormalizedQuote.from(q, eastern(time))
            assertEquals(time, MarketSession.AFTER_HOURS, normalized.marketSession)
            assertNotNull(time, normalized.afterHoursPrice)
            assertEquals(q.afterHoursTimestamp, normalized.afterHoursTimestamp)
        }
        assertNull(NormalizedQuote.from(q, eastern("2026-10-23T09:30:00")).afterHoursPrice)
        assertFalse(NormalizedQuote.retainsAfterHours(print, print - 1))
    }

    @Test fun afterSurvivesWeekendHolidayAndDstUntilNextOpening() {
        val friday = eastern("2026-10-30T19:59:00")
        assertTrue(NormalizedQuote.retainsAfterHours(friday, eastern("2026-11-02T09:29:59")))
        assertFalse(NormalizedQuote.retainsAfterHours(friday, eastern("2026-11-02T09:30:00")))
        val beforeHoliday = eastern("2026-11-25T19:59:00")
        assertTrue(NormalizedQuote.retainsAfterHours(beforeHoliday, eastern("2026-11-27T09:29:59")))
        assertFalse(NormalizedQuote.retainsAfterHours(beforeHoliday, eastern("2026-11-27T09:30:00")))
    }

    @Test fun refreshWithoutAfterRetainsActualPrintAndTimestampButExpiresAtOpen() {
        val print = eastern("2026-10-22T19:59:00")
        val previous = quote("AFTER_HOURS").copy(afterHoursTimestamp = print)
        val refresh = previous.copy(marketSession = "CLOSED", afterHoursPrice = null,
            afterHoursTimestamp = null, afterHoursChange = null, afterHoursChangePercent = null)
        val now = eastern("2026-10-23T09:29:59")
        val retained = NormalizedQuote.retainPreviousAfterHours(refresh, previous, now)
        assertEquals(previous.afterHoursPrice, retained.afterHoursPrice)
        assertEquals(print, retained.afterHoursTimestamp)
        assertEquals(MarketSession.AFTER_HOURS, NormalizedQuote.from(retained, now).marketSession)
        assertNull(NormalizedQuote.retainPreviousAfterHours(refresh, previous,
            eastern("2026-10-23T09:30:00")).afterHoursPrice)
        assertNull(NormalizedQuote.retainPreviousAfterHours(refresh.copy(price = BigDecimal("110")),
            previous, now).afterHoursPrice)
    }

    @Test fun uncertainProviderIsNeverCalledRealtime() {
        assertEquals(QuoteFreshness.CACHED, NormalizedQuote.from(quote("REGULAR"), now).freshness)
        assertEquals(QuoteFreshness.DELAYED,
            NormalizedQuote.from(quote("REGULAR", 15), now).freshness)
        assertEquals(QuoteFreshness.STALE,
            NormalizedQuote.from(quote("REGULAR", 15, now - 31 * 60_000), now).freshness)
    }

    @Test fun estimatedValueKeepsRegularDayChange() {
        val security = Security.of("ABC", "Example", "NYSE", "USD")
        val usd = Security.of("CAD=X", "USD/CAD", "FX", "CAD")
        val portfolio = Portfolio(name = "Test")
        val today = LocalDate.now().toString()
        val holding = Transaction(portfolioId = portfolio.id, securityId = security.id,
            quantity = BigDecimal("100"), price = BigDecimal("10"),
            currency = "USD", fxRate = BigDecimal("1.3"), date = LocalDate.now().minusDays(10).toString())
        val usQuote = quote("AFTER_HOURS").copy(securityId = security.id,
            price = BigDecimal("12"), previous = BigDecimal("11"),
            afterHoursPrice = BigDecimal("12.5"), sessionDate = today)
        val fx = Quote(usd.id, BigDecimal("1.3"), BigDecimal("1.3"), "CAD", now, today, "test")
        val base = Wallet(portfolios = listOf(portfolio), securities = listOf(security, usd),
            transactions = listOf(holding), quotes = mapOf(security.id to usQuote, usd.id to fx))
        val regular = base.copy(settings = mapOf("portfolio_extended" to "REGULAR"))
            .result(now = now)
        val estimated = base.copy(settings = mapOf("portfolio_extended" to "LAST"))
            .result(now = now)
        assertEquals(0, regular.value!!.compareTo(BigDecimal("1560")))
        assertEquals(0, estimated.value!!.compareTo(BigDecimal("1625")))
        assertEquals(regular.day, estimated.day)
        assertEquals(regular.value, base.copy(settings = mapOf("portfolio_extended" to "LAST"))
            .result(now = now + 24 * 60 * 60_000L).value)
    }
}
