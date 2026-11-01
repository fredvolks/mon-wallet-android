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

    @Test fun nonEquityUsdInstrumentsDoNotInheritStockExchangeHolidays() {
        val thanksgiving = LocalDate.parse("2026-11-26")
        val thanksgivingTime = Instant.parse("2026-11-26T17:00:00Z").toEpochMilli()
        val crypto = Security.of("BTC-USD", "Bitcoin", "Crypto", "USD", "CRYPTO")
        val ether = Security.of("ETH-USD", "Ethereum", "Crypto", "USD", "CRYPTO")
        val fx = Security.of("CADUSD=X", "CAD/USD", "FX", "USD", "FX")
        val commodity = Security.of("GC=F", "Gold", "COMEX", "USD", "COMMODITY")
        val future = Security.of("ES=F", "S&P 500 Future", "CME", "USD", "FUTURE")
        val usStock = Security.of("TSM", "TSMC", "NYSE", "USD", "STOCK")

        // Thanksgiving closes US equities but is not a blanket closure of USD assets.
        assertTrue(MarketHoliday.isHoliday(usStock, thanksgiving))
        listOf(crypto, ether, fx, commodity, future).forEach { security ->
            assertEquals(ExchangeCalendar.NONE, MarketHoliday.calendar(security))
            assertFalse(MarketHoliday.isHoliday(security, thanksgiving))
            assertFalse(MarketHoliday.excludesDailyPnl(security, thanksgivingTime))
        }
        val quote = Quote(crypto.id, d("110"), d("100"), "USD",
            thanksgivingTime, thanksgiving.toString(), "test")
        assertEquals(0, d("10").compareTo(MarketHoliday.dailyChange(crypto, quote, thanksgivingTime)))
        assertEquals(0, d("10").compareTo(MarketHoliday.dailyPercent(crypto, quote, thanksgivingTime)))
    }

    @Test fun wholeWalletPercentCountsClosedUsStocksWhileCryptoKeepsItsDailyMove() {
        val holidayNow = Instant.parse("2026-11-26T17:00:00Z").toEpochMilli()
        val crypto = Security.of("BTC-USD", "Bitcoin", "Crypto", "USD", "CRYPTO")
        val stock = Security.of("AAPL", "Apple", "NYSE", "USD", "STOCK")
        val fx = Security.of("CAD=X", "USD / CAD", "FX", "CAD", "FX")
        val transactions = listOf(crypto, stock).map { security ->
            Transaction(portfolioId = "p", securityId = security.id,
                quantity = d("10"), price = d("100"), currency = "USD",
                fxRate = d("1"), date = "2026-11-01")
        }
        val wallet = Wallet(securities = listOf(crypto, stock, fx),
            transactions = transactions,
            quotes = mapOf(
                crypto.id to Quote(crypto.id, d("110"), d("100"),
                    "USD", holidayNow, "2026-11-26", "test"),
                stock.id to Quote(stock.id, d("120"), d("100"),
                    "USD", holidayNow, "2026-11-25", "test"),
                fx.id to Quote(fx.id, d("1"), d("1"),
                    "CAD", holidayNow, "2026-11-26", "test")
            ))
        val result = wallet.result(now = holidayNow)
        assertEquals(0, d("100").compareTo(result.day))
        // The holiday stock moves by $0 today but its full $1200 value stays
        // in the wallet-wide percent denominator.
        assertEquals(0, d("2200").compareTo(result.dayBase))
        assertEquals(4.54545, result.dayPercent!!.toDouble(), 0.001)
        assertEquals(0, d("2300").compareTo(result.value))
    }

    @Test fun excludesCanadianHolidayFromDailyPortfolioPnl() {
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
        assertEquals(0, d("1500").compareTo(result.dayBase!!))
        assertEquals(3.3333, result.dayPercent!!.toDouble(), 0.01)
        val canadianHolding = result.holdings.single { it.securityId == canadian.id }
        assertEquals(0, BigDecimal.ZERO.compareTo(canadianHolding.day!!))
        assertEquals(0.0, canadianHolding.dayPercent!!.toDouble(), 0.001)
        assertEquals(0, d("50").compareTo(
            result.holdings.single { it.securityId == american.id }.day))
    }

    @Test fun canadianHolidayExcludesTitleEvenIfProviderHasSameDayQuote() {
        val canadian = Security.of("XEQT.TO", "XEQT", "TSX", "CAD", "ETF")
        val tx = listOf(Transaction(portfolioId = "p", securityId = canadian.id,
            quantity = d("10"), price = d("100"), date = "2026-10-01"))
        val quote = Quote(canadian.id, d("110"), d("100"), "CAD", 0,
            "2026-10-12", "test")
        val wallet = Wallet(securities = listOf(canadian), transactions = tx,
            quotes = mapOf(canadian.id to quote))
        val result = wallet.result(now = now)
        assertEquals(0, BigDecimal.ZERO.compareTo(result.day))
        assertEquals(0, d("1100").compareTo(result.dayBase!!))
        assertEquals(0.0, result.dayPercent!!.toDouble(), 0.001)
        val holding = result.holdings.single()
        assertEquals(0, BigDecimal.ZERO.compareTo(holding.day!!))
        assertEquals(0.0, holding.dayPercent!!.toDouble(), 0.001)
        assertEquals(0, BigDecimal.ZERO.compareTo(
            MarketHoliday.dailyChange(canadian, quote, now)))
        assertEquals(0, BigDecimal.ZERO.compareTo(
            MarketHoliday.dailyPercent(canadian, quote, now)))
    }
}
