package ca.monwallet.app.news

import ca.monwallet.app.data.Repository
import ca.monwallet.app.database.Cache
import ca.monwallet.app.domain.Security
import ca.monwallet.app.domain.Wallet
import ca.monwallet.app.marketdata.Http
import ca.monwallet.app.marketdata.Router
import com.google.gson.Gson
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request

data class NewsAnalysis(
    val summaryFr: String,
    val importance: String,
    val sentiment: String,
    val eventType: String,
    val whyItMatters: String,
    val shortTermImpact: String,
    val longTermImpact: String,
    val confidence: Double,
    val notificationWorthy: Boolean,
    val model: String,
)

data class NewsArticle(
    val id: String,
    val title: String,
    val source: String,
    val url: String,
    val publishedAt: Long,
    val tickers: List<String>,
    val market: String,
    val provider: String,
    val analysis: NewsAnalysis? = null,
)

data class NewsFeedState(
    val articles: List<NewsArticle> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val updatedAt: Long = 0,
)

/** One cache for the app. Only backend analyses may carry an IA label or trigger a news alert. */
class NewsRepository(private val repo: Repository, private val market: Router,
    private val supabaseUrl: String, private val publishableKey: String) {
    val state = MutableStateFlow(NewsFeedState())
    private val mutex = Mutex()
    private val gson = Gson()
    private val refreshInterval = 60 * 60_000L
    private var loadedOwner: String? = null
    private var loadedSymbols: Set<String> = emptySet()

    private fun cacheId() = "news:feed:${repo.owner.value}"

    suspend fun loadCache() {
        if (loadedOwner != repo.owner.value) {
            loadedOwner = repo.owner.value
            loadedSymbols = emptySet()
            state.value = NewsFeedState()
        }
        if (state.value.articles.isNotEmpty()) return
        repo.dao.cache().firstOrNull { it.id == cacheId() }?.let { row ->
            val articles = runCatching {
                gson.fromJson(row.payload, Array<NewsArticle>::class.java).toList()
            }.getOrDefault(emptyList())
            state.value = NewsFeedState(articles, updatedAt = row.fetchedAt)
        }
    }

    suspend fun refresh(wallet: Wallet, force: Boolean = false) = mutex.withLock {
        loadCache()
        val now = System.currentTimeMillis()
        val securities = (wallet.transactions.mapNotNull { it.securityId } +
            wallet.items.map { it.securityId }).distinct().mapNotNull(wallet::security).take(20)
        val symbols = securities.map { it.symbol.uppercase() }.toSet()
        if (!force && symbols == loadedSymbols && now - state.value.updatedAt < refreshInterval)
            return@withLock
        loadedSymbols = symbols
        state.value = state.value.copy(loading = true, error = null)
        val fromServer = runCatching { backend() }.getOrElse { emptyList() }
        val failures = AtomicInteger(0)
        val gate = Semaphore(3)
        val fromProvider = supervisorScope {
            securities.map { security -> async {
                gate.withPermit {
                    try { provider(security) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { failures.incrementAndGet(); emptyList() }
                }
            } }.awaitAll().flatten()
        }
        val combined = deduplicate(fromServer + fromProvider)
            .filter { it.publishedAt in (now - 30L * 24 * 3600_000)..(now + 600_000) }
            .sortedByDescending { it.publishedAt }.take(200)
        if (combined.isNotEmpty()) {
            repo.dao.cache(Cache(cacheId(), "news", gson.toJson(combined), now))
            state.value = NewsFeedState(combined, updatedAt = now,
                error = if (failures.get() > 0) "Certaines sources sont indisponibles." else null)
        } else {
            // Keep previously fetched articles on network failure; never invent an article.
            state.value = state.value.copy(loading = false, updatedAt = now,
                error = if (securities.isEmpty()) "Ajoute un titre au portefeuille ou à la Watchlist."
                    else "Aucune nouvelle disponible auprès des sources configurées.")
        }
    }

    private suspend fun provider(security: Security): List<NewsArticle> {
        val ticker = security.symbol.uppercase()
        val region = if (security.currency == "CAD") "CANADA" else "USA"
        return market.news(security).mapNotNull { n ->
            val url = n.url.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            NewsArticle("local:" + digest(canonicalUrl(url)), n.title, n.source, url,
                n.timestamp, listOf(ticker), region, "Yahoo Finance · non officiel")
        }
    }

    private suspend fun backend(): List<NewsArticle> {
        if (supabaseUrl.isBlank() || publishableKey.isBlank()) return emptyList()
        val url = Http.url("${supabaseUrl.trimEnd('/')}/rest/v1/news_articles", mapOf(
            "select" to "id,title,source,canonical_url,published_at,tickers,market,provider,news_analysis(summary_fr,importance,sentiment,event_type,why_it_matters,short_term_impact,long_term_impact,confidence,notification_worthy,model)",
            "order" to "published_at.desc", "limit" to "100"))
        val response = Http.array(Request.Builder().url(url).header("apikey", publishableKey).build())
        return (0 until response.length()).mapNotNull { index ->
            val row = response.optJSONObject(index) ?: return@mapNotNull null
            val urlValue = row.optString("canonical_url").takeIf { it.startsWith("https://") }
                ?: return@mapNotNull null
            val published = runCatching {
                java.time.Instant.parse(row.getString("published_at")).toEpochMilli()
            }.getOrNull() ?: return@mapNotNull null
            val analysis = row.optJSONObject("news_analysis")?.let { a ->
                NewsAnalysis(a.optString("summary_fr"), a.optString("importance"),
                    a.optString("sentiment"), a.optString("event_type"),
                    a.optString("why_it_matters"), a.optString("short_term_impact"),
                    a.optString("long_term_impact"), a.optDouble("confidence"),
                    a.optBoolean("notification_worthy"), a.optString("model"))
            }
            val tickers = row.optJSONArray("tickers")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
            }.orEmpty()
            NewsArticle(row.getString("id"), row.optString("title"),
                row.optString("source"), urlValue, published, tickers,
                row.optString("market"), row.optString("provider"), analysis)
        }
    }

    companion object {
        private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

        internal fun canonicalUrl(value: String): String = runCatching {
            val uri = URI(value)
            URI(uri.scheme, uri.authority, uri.path?.trimEnd('/'), null, null).toString()
        }.getOrDefault(value)

        internal fun deduplicate(rows: List<NewsArticle>): List<NewsArticle> = rows
            .groupBy { canonicalUrl(it.url).lowercase() }
            .values.map { group ->
                // Prefer the backend's sourced analysis; combine all matched symbols.
                val best = group.firstOrNull { it.analysis != null } ?: group.first()
                best.copy(tickers = group.flatMap { it.tickers }.distinct())
            }
    }
}
