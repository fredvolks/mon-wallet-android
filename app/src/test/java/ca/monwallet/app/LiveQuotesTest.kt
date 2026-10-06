package ca.monwallet.app

import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.*
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class LiveQuotesTest {
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
        assertNull(NormalizedQuote.from(quote("CLOSED"), now).afterHoursPrice)
        val direct = NormalizedQuote.from(quote("AFTER_HOURS").copy(
            afterHoursChange = BigDecimal("2.5"),
            afterHoursChangePercent = BigDecimal("2.08")), now)
        assertEquals(0, direct.afterHoursChange!!.compareTo(BigDecimal("2.5")))
        assertEquals(0, direct.afterHoursChangePercent!!.compareTo(BigDecimal("2.08")))
        assertNull(NormalizedQuote.from(quote("AFTER_HOURS"), now + 24 * 60 * 60_000L)
            .afterHoursPrice)
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
