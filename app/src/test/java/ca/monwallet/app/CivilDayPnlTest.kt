package ca.monwallet.app

import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId

class CivilDayPnlTest {
    @After fun restoreClock() { MarketClock.mode = "AUTO" }
    private fun at(time: String) = LocalDateTime.parse(time).atZone(ZoneId.of("America/New_York"))
        .toInstant().toEpochMilli()
    private val security = Security.of("TSM", "TSM", "NYSE", "USD")
    private val fx = Security.of("CAD=X", "USD/CAD", "FX", "CAD")
    private fun quote() = Quote(security.id, "100".dec(), "98".dec(), "USD",
        at("2026-10-22T16:00:00"), "2026-10-22", "fixture",
        afterHoursPrice = "103".dec(), afterHoursTimestamp = at("2026-10-22T19:59:00"),
        marketSession = "CLOSED")
    private fun wallet(q: Quote, sale: Boolean = false): Wallet {
        val usd = Quote(fx.id, ONE, ONE, "CAD", at("2026-10-22T23:59:00"),
            "2026-10-22", "fixture")
        val tx = mutableListOf(Transaction(portfolioId = "p", securityId = security.id,
            quantity = "10".dec(), price = "90".dec(), currency = "USD",
            date = "2026-10-20"))
        if (sale) tx += Transaction(portfolioId = "p", securityId = security.id,
            quantity = "10".dec(), price = "104".dec(), currency = "USD",
            type = TxType.SELL, date = "2026-10-23")
        return Wallet(portfolios = listOf(Portfolio(id = "p", name = "Test")),
            securities = listOf(security, fx), transactions = tx,
            quotes = mapOf(security.id to q, fx.id to usd))
    }
    @Test fun midnightKeeps16hCloseAndDiscardsPersistedMidnightReference() {
        val before = at("2026-10-22T23:59:59")
        val midnight = at("2026-10-23T00:00:00")
        val q = quote().copy(dayReferenceDate = "2026-10-23", dayReferencePrice = "103".dec())
        assertEquals(0, CivilDayPnl.capture(q, q, midnight).dayReferencePrice!!.compareTo("100".dec()))
        assertEquals(0, wallet(q).result(now = before).day!!.compareTo("30".dec()))
        assertEquals(0, wallet(q).result(now = midnight).day!!.compareTo("30".dec()))
        assertEquals(MarketClock.chartStart(before), MarketClock.chartStart(midnight))
    }
    @Test fun preMarketUsesCloseNotMidnightOrPriorAfterPrice() {
        val now = at("2026-10-23T07:26:00")
        val q = quote().copy(price = "412.35".dec(), previous = "431.89".dec(),
            preMarketPrice = "414.50".dec(), preMarketTimestamp = now, marketSession = "PRE_MARKET",
            dayReferencePrice = "410.03".dec(), dayReferenceDate = "2026-10-23")
        val daily = CivilDayPnl.dailyQuote(q, security, now)
        assertEquals("412.35".dec(), daily.previous)
        assertEquals(0, daily.percent!!.setScale(2, java.math.RoundingMode.HALF_UP).compareTo("0.52".dec()))
        assertEquals(0, wallet(q).result(now = now).day!!.compareTo("21.50".dec()))
    }
    @Test fun openingEndsExtendedSessionAndNeverReplaysYesterdaysRegularMove() {
        val opening = at("2026-10-23T09:30:00")
        assertFalse(MarketClock.offSession(opening))
        assertEquals(MarketSession.CLOSED, NormalizedQuote.from(quote(), opening).marketSession)
        assertEquals(0, wallet(quote()).result(now = opening).day!!.compareTo(ZERO))
        val regular = quote().copy(price = "101".dec(), previous = "100".dec(),
            timestamp = opening, sessionDate = "2026-10-23", marketSession = "REGULAR")
        assertEquals(0, wallet(regular).result(now = opening).day!!.compareTo("10".dec()))
        assertEquals(opening, MarketClock.chartStart(opening))
    }
    @Test fun closeStartsNewOffSessionAndDoesNotCarryRegularGain() {
        val close = at("2026-10-23T16:00:00")
        val q = quote().copy(price = "104".dec(), previous = "100".dec(),
            timestamp = close, sessionDate = "2026-10-23", afterHoursPrice = null,
            afterHoursTimestamp = null, marketSession = "CLOSED")
        assertTrue(MarketClock.offSession(close))
        assertEquals(0, wallet(q).result(now = close).day!!.compareTo(ZERO))
        val later = q.copy(afterHoursPrice = "105".dec(), afterHoursTimestamp = close + 60_000)
        assertEquals(0, wallet(later).result(now = close + 60_000).day!!.compareTo("10".dec()))
    }
    @Test fun regularSaleGainStaysInTotalButNotNextOffSession() {
        val now = at("2026-10-23T10:00:00")
        val q = quote().copy(price = "104".dec(), previous = "100".dec(), timestamp = now,
            sessionDate = "2026-10-23", marketSession = "REGULAR")
        val sold = wallet(q, sale = true)
        assertEquals(0, sold.result(now = now).day!!.compareTo("40".dec()))
        val overnight = sold.result(now = at("2026-10-24T00:00:00"))
        assertEquals(0, overnight.day!!.compareTo(ZERO))
        assertEquals(sold.result(now = now).realized, overnight.realized)
        assertEquals(sold.result(now = now).pnl, overnight.pnl)
    }
    @Test fun actualOvernightPrintUses16hClose() {
        val now = at("2026-10-23T02:00:00")
        val q = quote().copy(afterHoursPrice = "105".dec(), afterHoursTimestamp = now)
        assertEquals(MarketSession.AFTER_HOURS, NormalizedQuote.from(q, now).marketSession)
        assertEquals(0, wallet(q).result(now = now).day!!.compareTo("50".dec()))
    }
    @Test fun canadianListingWithoutExtendedTradesStaysAtZeroOffSession() {
        val cad = Security.of("XEQT.TO", "XEQT", "TSX", "CAD", "ETF")
        val q = quote().copy(securityId = cad.id, currency = "CAD")
        val daily = CivilDayPnl.dailyQuote(q, cad, at("2026-10-23T06:14:00"))
        assertEquals(q.price, daily.price)
        assertEquals(0, daily.percent!!.compareTo(ZERO))
    }
    @Test fun clockAutomaticallyFollowsSummerAndWinterAndAcceptsManualOverride() {
        val summer = Instant.parse("2026-07-10T13:30:00Z").toEpochMilli()
        val winter = Instant.parse("2026-12-10T14:30:00Z").toEpochMilli()
        assertFalse(MarketClock.offSession(summer))
        assertFalse(MarketClock.offSession(winter))
        MarketClock.mode = "WINTER"
        assertTrue(MarketClock.offSession(summer))
        MarketClock.mode = "SUMMER"
        assertFalse(MarketClock.offSession(Instant.parse("2026-12-10T13:30:00Z").toEpochMilli()))
        MarketClock.mode = "AUTO"
        val afterChange = Instant.parse("2026-11-02T14:29:59Z").toEpochMilli()
        assertTrue(MarketClock.offSession(afterChange))
        assertFalse(MarketClock.offSession(afterChange + 1000))
    }
    @Test fun weekendAndHolidayKeepLastTradingCloseUntilNextOpening() {
        val friday = at("2026-10-23T16:00:00")
        assertEquals(friday, MarketClock.chartStart(at("2026-10-26T09:29:59")))
        assertEquals(at("2026-11-25T16:00:00"),
            MarketClock.chartStart(at("2026-11-27T09:29:59")))
    }
}
