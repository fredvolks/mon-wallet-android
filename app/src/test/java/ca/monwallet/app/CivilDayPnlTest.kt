package ca.monwallet.app

import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class CivilDayPnlTest {
    private fun at(time: String) = LocalDateTime.parse(time).atZone(CivilDayPnl.zone)
        .toInstant().toEpochMilli()
    private val security = Security.of("TSM", "TSM", "NYSE", "USD")
    private val fx = Security.of("CAD=X", "USD/CAD", "FX", "CAD")
    private val portfolio = Portfolio(id = "p", name = "Test")
    private fun quote() = Quote(security.id, "100".dec(), "98".dec(), "USD",
        at("2026-10-22T16:00:00"), "2026-10-22", "fixture",
        afterHoursPrice = "103".dec(), afterHoursTimestamp = at("2026-10-22T19:59:00"),
        marketSession = "CLOSED")
    private fun wallet(q: Quote, sale: Boolean = false): Wallet {
        val usd = Quote(fx.id, ONE, ONE, "CAD", at("2026-10-22T23:59:00"),
            "2026-10-22", "fixture", dayReferenceDate = "2026-10-23", dayReferencePrice = ONE)
        val tx = mutableListOf(Transaction(portfolioId = "p", securityId = security.id,
            quantity = "10".dec(), price = "90".dec(), currency = "USD",
            date = "2026-10-20"))
        if (sale) tx += Transaction(portfolioId = "p", securityId = security.id,
            quantity = "10".dec(), price = "104".dec(), currency = "USD",
            type = TxType.SELL, date = "2026-10-23")
        return Wallet(portfolios = listOf(portfolio), securities = listOf(security, fx),
            transactions = tx, quotes = mapOf(security.id to q, fx.id to usd))
    }
    @Test fun midnightResetsYesterdayIncludingItsAfterMove() {
        val midnight = at("2026-10-23T00:00:00")
        val q = CivilDayPnl.capture(quote(), null, midnight)
        assertEquals(0, q.dayReferencePrice!!.compareTo("103".dec()))
        val r = wallet(q).result(now = midnight)
        assertEquals(0, r.day!!.compareTo(ZERO))
        assertEquals(0, r.dayPercent!!.compareTo(ZERO))
        assertEquals(0, r.realized.compareTo(ZERO))
    }
    @Test fun freshPreMarketWinsOverRetainedAfterAndContributesToToday() {
        val midnight = at("2026-10-23T00:00:00")
        val reference = CivilDayPnl.capture(quote(), null, midnight)
        val now = at("2026-10-23T06:14:00")
        val fresh = CivilDayPnl.capture(quote().copy(preMarketPrice = "104".dec(),
            preMarketTimestamp = now, marketSession = "PRE_MARKET"), reference, now)
        assertEquals(MarketSession.PRE_MARKET, NormalizedQuote.from(fresh, now).marketSession)
        assertEquals(0, wallet(fresh).result(now = now).day!!.compareTo("10".dec()))
        assertEquals(reference.dayReferencePrice, fresh.dayReferencePrice)
    }
    @Test fun saleTodayRetainsRealizedDailyGainAndSaleYesterdayDoesNotLeak() {
        val now = at("2026-10-23T10:00:00")
        val q = quote().copy(price = "104".dec(), timestamp = now,
            sessionDate = "2026-10-23", marketSession = "REGULAR",
            dayReferenceDate = "2026-10-23", dayReferencePrice = "103".dec())
        val sold = wallet(q, sale = true)
        assertEquals(0, sold.result(now = now).day!!.compareTo("10".dec()))
        val tomorrow = at("2026-10-24T00:00:00")
        assertEquals(0, sold.result(now = tomorrow).day!!.compareTo(ZERO))
        assertEquals(sold.result(now = now).realized, sold.result(now = tomorrow).realized)
    }
    @Test fun realOvernightPrintIsAcceptedAndNotCutOffAt20() {
        val now = at("2026-10-23T02:00:00")
        val q = quote().copy(afterHoursPrice = "105".dec(), afterHoursTimestamp = now)
        assertEquals(MarketSession.AFTER_HOURS, NormalizedQuote.from(q, now).marketSession)
        val referenced = CivilDayPnl.capture(q, quote(), now)
        assertEquals(0, referenced.dayReferencePrice!!.compareTo("103".dec()))
        assertEquals(0, wallet(referenced).result(now = now).day!!.compareTo("20".dec()))
    }
    @Test fun canadianOldCloseIsZeroOnNewDayWithoutFabricatingAPrice() {
        val cad = Security.of("XEQT.TO", "XEQT", "TSX", "CAD", "ETF")
        val q = quote().copy(securityId = cad.id, currency = "CAD")
        val day = CivilDayPnl.dailyQuote(q, cad, at("2026-10-23T06:14:00"))
        // Canadian listings cannot use a US extended print.
        assertEquals(q.price, day.price)
    }
}
