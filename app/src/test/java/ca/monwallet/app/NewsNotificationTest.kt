package ca.monwallet.app

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import ca.monwallet.app.domain.AlertEvent
import ca.monwallet.app.news.NewsArticle
import ca.monwallet.app.notifications.Notifications
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TestMonWallet::class)
class NewsNotificationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun event(channel: String, url: String?) = AlertEvent(
        securityId = "aapl", title = "Nouvelle", body = "Article", channel = channel,
        sourceUrl = url)

    @Test fun articleNotificationOpensExactHttpsSourceWhileOtherAlertsOpenApp() {
        val url = "https://example.com/market/aapl-results?id=42"
        val article = Notifications.destination(context, event("news", url))
        assertEquals(Intent.ACTION_VIEW, article.action)
        assertEquals(url, article.data.toString())
        assertTrue(article.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertNull(article.component)

        for ((channel, source) in listOf("price" to url, "news" to "javascript:alert(1)",
            "news" to "https://user@example.com/article", "news" to null)) {
            val fallback = Notifications.destination(context, event(channel, source))
            assertEquals(MainActivity::class.java.name, fallback.component?.className)
            assertEquals("aapl", fallback.getStringExtra("security"))
        }
    }

    @Test fun freeCanadianAlertsRequireListedIssuerAndConcreteDisclosure() {
        val release = NewsArticle("id", "Example reports quarterly results", "GlobeNewswire",
            "https://example.com/release", System.currentTimeMillis(), listOf("EX.V"),
            "CANADA", "GlobeNewswire · RSS Canada")
        assertTrue(Notifications.importantCanadianDisclosure(release))
        assertFalse(Notifications.importantCanadianDisclosure(release.copy(tickers = emptyList())))
        assertFalse(Notifications.importantCanadianDisclosure(release.copy(
            title = "Example to present at investor conference")))
        assertFalse(Notifications.importantCanadianDisclosure(release.copy(provider = "Yahoo")))
    }
}
