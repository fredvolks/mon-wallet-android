package ca.monwallet.app

import ca.monwallet.app.domain.*
import ca.monwallet.app.news.NewsArticle
import ca.monwallet.app.notifications.Notifications
import ca.monwallet.app.marketdata.NasdaqEarnings
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class StockMonitoringTest {
    private val stock = Security.of("AAPL", "Apple", "NASDAQ", "USD")
    @Test fun soldTitlesAreNotMonitoredAndSavedSwitchesWin() {
        val buy = Transaction(portfolioId = "p", securityId = stock.id,
            quantity = "3".dec(), price = "10".dec())
        val w = Wallet(transactions = listOf(buy))
        assertEquals(setOf(stock.id), w.heldSecurityIds())
        assertTrue(w.monitoring(stock.id).news)
        assertFalse(w.copy(transactions = listOf(buy, buy.copy(id = "sale", type = TxType.SELL)))
            .monitoring(stock.id).news)
        assertFalse(w.copy(notifications = listOf(NotificationPreference("n", stock.id,
            earnings = false, news = false, earningsResults = false))).monitoring(stock.id).news)
    }
    @Test fun newsWithoutAiAlertsOnlyMatchingFreshArticlesAfterSubscription() {
        val n = NewsArticle("n", "Apple announces quarterly results", "Issuer",
            "https://example.com/results", 100_000, listOf("AAPL"), "USA", "Yahoo")
        assertTrue(Notifications.newsMatches(n, stock, 101_000, 90_000))
        assertFalse(Notifications.newsMatches(n, stock, 101_000, 100_001))
        assertFalse(Notifications.newsMatches(n.copy(tickers = listOf("AAPL.TO")), stock, 101_000, 0))
        assertFalse(Notifications.newsMatches(n, stock, 100_000 + 25 * 3600_000L, 0))
    }
    @Test fun nasdaqDatesAndActualResultsKeepTheirDistinctMeaning() {
        val date = NasdaqEarnings.parseUpcoming(JSONObject().put("reportText",
            "Expected to report on 11/02/2026. The consensus EPS forecast for the quarter is $2."))!!
        assertEquals("2026-11-02", date.date)
        assertEquals("2".dec(), date.epsEstimate)
        assertNull(date.epsActual)
        val rows = NasdaqEarnings.parseResults(JSONObject("""{"earningsSurpriseTable":{"rows":[
            {"dateReported":"7/30/2026","eps":1.91,"consensusForecast":"1.88"},
            {"dateReported":"invalid","eps":2}]}}"""))
        assertEquals(1, rows.size)
        assertTrue(Notifications.earningsSummary(rows.single()).contains("au-dessus du consensus"))
        assertTrue(Notifications.earningsSummary(rows.single()).contains("Revenus : non disponible"))
    }
    @Test fun healthNeverCallsMissingDataGreenAndSeparatesYellowFromRed() {
        fun assess(m: Map<String, String>) = FinancialHealthProfile.assess(stock, Fundamentals(metrics = m)).rating
        val good = mapOf("P/E" to "20", "Dette / capitaux propres" to "1", "Capitalisation (M)" to "2000")
        assertEquals(FinancialMetricRating.FAVORABLE, assess(good))
        assertEquals(FinancialMetricRating.WATCH, assess(good + ("P/E" to "30")))
        assertEquals(FinancialMetricRating.UNFAVORABLE, assess(good + ("Dette / capitaux propres" to "3")))
        assertEquals(FinancialMetricRating.CONTEXT, assess(good - "P/E"))
    }
}
