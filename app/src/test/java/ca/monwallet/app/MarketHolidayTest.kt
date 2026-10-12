package ca.monwallet.app

import ca.monwallet.app.domain.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MarketHolidayTest {
    private fun d(value: String) = BigDecimal(value)
    private val mondayThanksgiving = LocalDate.parse("2026-10-12")
    private val now = Instant.parse("2026-10-12T18:58:00Z").toEpochMilli()

    @Test fun canadianThanksgivingClosesTorontoButNotNewYork() {
        val canadian = Security.of("XEQT.TO", "XEQT", "TSX", "CAD", "ETF")
        val american = Security.of("TSM", "TSMC", "NYSE", "USD")
        assertTrue(MarketHoliday.isHoliday(canadian, mondayThanksgiving))
        assertFalse(MarketHoliday.isHoliday(american, mondayThanksgiving))
        assertTrue(MarketHoliday.isHolidayToday(canadian, now))
        assertFalse(MarketHoliday.isHolidayToday(american, now))
    }

    @Test fun excludesOnlyStaleMarketHolidayQuoteFromDailyPortfolioPnl() {
        val canadian = Security.of("XEQT.TO", "XEQT", "TSX", "CAD", "ETF")
        val american = Security.of("TSM", "TSMC", "NYSE", "USD")
        val fx = Security.of("CAD=X", "USD/CAD", "FX", "CAD", "FX")
        val tx = listOf(
            Transaction(portfolioId = "p", securityId = canadian.id, quantity = d("10"),
                price = d("100"), date = "2026-10-01"),
            Transaction(portfolioId = "p", securityId = american.id, quantity = d("10"),
                price = d("40"), currency = "USD", fxRate = d("1"), date = "2026-10-01"),
        )
        val quotes = mapOf(
            canadian.id to Quote(canadian.id, d("110"), d("100"), "CAD", 0,
                "2026-10-09", "test"),
            american.id to Quote(american.id, d("45"), d("40"), "USD", 0,
                "2026-10-12", "test"),
            fx.id to Quote(fx.id, d("1"), d("1"), "CAD", 0,
                "2026-10-12", "test"),
        )
        val wallet = Wallet(securities = listOf(canadian, american, fx), transactions = tx,
            quotes = quotes)
        val result = wallet.result(now = now)
        assertEquals(0, d("50").compareTo(result.day!!))
        assertEquals(0, d("400").compareTo(result.dayBase!!))
        assertNull(result.holdings.single { it.securityId == canadian.id }.day)
        assertEquals(0, d("50").compareTo(
            result.holdings.single { it.securityId == american.id }.day))
    }

    @Test fun currentCanadianQuoteStillContributesOnExchangeHolidayDate() {
        val canadian = Security.of("XEQT.TO", "XEQT", "TSX", "CAD", "ETF")
        val tx = listOf(Transaction(portfolioId = "p", securityId = canadian.id,
            quantity = d("10"), price = d("100"), date = "2026-10-01"))
        val quote = Quote(canadian.id, d("110"), d("100"), "CAD", 0,
            "2026-10-12", "test")
        val wallet = Wallet(securities = listOf(canadian), transactions = tx,
            quotes = mapOf(canadian.id to quote))
        assertEquals(0, d("100").compareTo(wallet.result(now = now).day!!))
    }
}
