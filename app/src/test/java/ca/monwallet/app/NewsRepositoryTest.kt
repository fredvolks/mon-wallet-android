package ca.monwallet.app

import ca.monwallet.app.news.NewsAnalysis
import ca.monwallet.app.news.NewsArticle
import ca.monwallet.app.news.NewsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewsRepositoryTest {
    private fun article(id: String, url: String, tickers: List<String>,
        analysis: NewsAnalysis? = null) = NewsArticle(id, "Résultats publiés", "Source",
        url, 1_700_000_000_000, tickers, "USA", "Fournisseur", analysis)

    @Test fun duplicateProviderAndBackendNewsKeepsOnlyRealAnalysis() {
        val raw = article("local:1", "https://example.com/results?utm_source=feed",
            listOf("AAPL"))
        val sourced = article("server:1", "https://example.com/results", listOf("MSFT"),
            NewsAnalysis("Résultats confirmés.", "HIGH", "POSITIVE", "EARNINGS",
                "Revenus publiés.", "Non déterminé.", "Non déterminé.", .8, true, "gpt-4o-mini"))
        val result = NewsRepository.deduplicate(listOf(raw, sourced))
        assertEquals(1, result.size)
        assertEquals("server:1", result.single().id)
        assertEquals(setOf("AAPL", "MSFT"), result.single().tickers.toSet())
        assertEquals("Résultats confirmés.", result.single().analysis?.summaryFr)
    }

    @Test fun rawProviderNewsNeverAcquiresAnInventedAnalysis() {
        val result = NewsRepository.deduplicate(listOf(article("local:2",
            "https://example.com/report?ref=one", listOf("DOL.TO"))))
        assertNull(result.single().analysis)
    }
}
