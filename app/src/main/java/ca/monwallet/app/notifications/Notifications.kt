package ca.monwallet.app.notifications

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ca.monwallet.app.*
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.Finnhub
import ca.monwallet.app.news.NewsArticle
import java.net.URI
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

object Notifications {
    val categories =
        mapOf(
            "price" to "Alertes de prix",
            "earnings" to "Résultats financiers",
            "news" to "Actualités",
            "analysts" to "Analystes",
            "dividends" to "Dividendes",
            "updates" to "Mises à jour",
        )

    fun channels(c: Context) {
        val manager = c.getSystemService(NotificationManager::class.java)
        categories.forEach { (id, name) ->
            manager.createNotificationChannel(
                NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun allowed(c: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    /** A notification for a sourced article opens that exact article in a browser. */
    internal fun destination(c: Context, event: AlertEvent): Intent {
        val source = event.sourceUrl?.takeIf { url ->
            runCatching { val uri = URI(url)
                uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() &&
                    uri.userInfo == null }.getOrDefault(false)
        }
        return if (event.channel == "news" && source != null)
            Intent(Intent.ACTION_VIEW, Uri.parse(source)).addCategory(Intent.CATEGORY_BROWSABLE)
        else Intent(c, MainActivity::class.java).putExtra("security", event.securityId)
    }

    // A small, explicit rule for sourced exchange-listed Canadian disclosures when
    // paid IA analysis is unavailable. Promotional/ambiguous headlines do not alert.
    internal fun importantCanadianDisclosure(article: NewsArticle): Boolean =
        article.analysis == null && article.market == "CANADA" &&
            article.provider == "GlobeNewswire · RSS Canada" && article.tickers.isNotEmpty() &&
            Regex("\\b(financial results|quarterly results|annual results|earnings|guidance|" +
                "dividend|definitive agreement|material contract|acquisition completed|" +
                "résultats financiers|résultats trimestriels|prévisions|dividende|" +
                "contrat signé|acquisition complétée)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(article.title)

    private val evaluationMutex = kotlinx.coroutines.sync.Mutex()

    private suspend fun emit(s: Services, event: AlertEvent) {
        val w = s.repo.current()
        if (w.events.any { it.eventKey == event.eventKey }) return
        s.repo.put("event", event.id, event)
        if (!allowed(s.context) || w.settings["notify_${event.channel}"] == "false") return
        val intent =
            PendingIntent.getActivity(
                s.context,
                event.id.hashCode(),
                destination(s.context, event),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        s.context
            .getSystemService(NotificationManager::class.java)
            .notify(
                event.id.hashCode(),
                NotificationCompat.Builder(s.context, event.channel)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(event.title)
                    .setContentText(event.body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(event.body))
                    .setAutoCancel(true)
                    .setContentIntent(intent)
                    .build(),
            )
    }

    suspend fun evaluate(s: Services) {
        if (!evaluationMutex.tryLock()) return
        try { evaluateLocked(s) } finally { evaluationMutex.unlock() }
    }

    private suspend fun evaluateLocked(s: Services) {
        val w = s.repo.current()
        for (a in w.alerts.filter { it.enabled && it.triggered == null }) {
            val q = w.quotes[a.securityId] ?: continue
            if (System.currentTimeMillis() - q.timestamp > 48 * 3600_000L) continue
            val hit =
                when (a.kind) {
                    "ABOVE" -> q.price >= a.threshold
                    "BELOW" -> q.price <= a.threshold
                    "DAY_UP" -> q.percent?.let { it >= a.threshold } ?: false
                    "DAY_DOWN" -> q.percent?.let { it <= a.threshold.negate() } ?: false
                    "HIGH52" -> q.high52?.let { q.price >= it } ?: false
                    "LOW52" -> q.low52?.let { q.price <= it } ?: false
                    "VOLUME" ->
                        q.averageVolume?.let { avg ->
                            q.volume?.let { it >= avg * a.threshold } ?: false
                        } ?: false
                    else -> false
                }
            if (hit) {
                val security = w.security(a.securityId)
                emit(
                    s,
                    AlertEvent(
                        securityId = a.securityId,
                        title = "${security?.ticker} · alerte déclenchée",
                        body = "${q.price.toPlainString()} ${q.currency} · ${q.source}",
                        channel = "price",
                        eventKey = "price:${a.id}",
                    ),
                )
                s.repo.put(
                    "alert",
                    a.id,
                    a.copy(triggered = System.currentTimeMillis(), enabled = false),
                )
            }
        }
        val today = LocalDate.now(ZoneId.of("America/Toronto"))
        for (securityId in w.heldSecurityIds()) {
            val pref = w.monitoring(securityId)
            val security = w.security(securityId) ?: continue
            if (pref.earnings || (pref.earningsResults ?: pref.earnings)) {
                try {
                    for (e in s.earningsFor(security)) {
                        val date = runCatching { LocalDate.parse(e.date) }.getOrNull() ?: continue
                        val published = e.epsActual != null || e.revenueActual != null
                        if (pref.earnings && !published && date in listOf(today, today.plusDays(1)))
                            emit(s, AlertEvent(securityId = security.id,
                                title = "${security.ticker} · Résultats ${if (date == today) "aujourd’hui" else "demain"}",
                                body = "Date annoncée : $date. BPA prévu : ${e.epsEstimate ?: "indisponible"}. " +
                                    "La date peut être révisée par la société.", channel = "earnings",
                                eventKey = "earnings:before:${security.id}:$date:$today"))
                        if ((pref.earningsResults ?: pref.earnings) && published &&
                            !date.isAfter(today) && !date.isBefore(today.minusDays(2)))
                            emit(s, AlertEvent(securityId = security.id,
                                title = "${security.ticker} · Résultats publiés",
                                body = earningsSummary(e), channel = "earnings",
                                eventKey = "earnings:after:${security.id}:$date"))
                    }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            }
            if (pref.news) s.news.state.value.articles
                .filter { n -> newsMatches(n, security, System.currentTimeMillis(), pref.enabledAt) }
                .filter { n -> w.events.none { it.eventKey == newsEventKey(n, security.id) } }
                .sortedByDescending { it.publishedAt }.take(5).forEach { n ->
                    emit(s, AlertEvent(securityId = security.id,
                        title = "${security.ticker} · ${n.source}",
                        body = n.analysis?.summaryFr?.takeIf { it.isNotBlank() } ?: n.title,
                        channel = "news", eventKey = newsEventKey(n, security.id), sourceUrl = n.url))
                }
        }
    }

    internal fun newsEventKey(article: NewsArticle, securityId: String): String =
        "news:${ca.monwallet.app.news.NewsRepository.canonicalUrl(article.url)}:$securityId"

    internal fun newsMatches(n: NewsArticle, security: Security, now: Long, enabledAt: Long): Boolean =
        n.url.startsWith("https://") && n.title.isNotBlank() &&
            n.tickers.any { it.equals(security.symbol, ignoreCase = true) } &&
            now - n.publishedAt in 0L..24L * 3600_000 && n.publishedAt >= enabledAt

    internal fun earningsSummary(e: Earnings): String {
        fun comparison(actual: java.math.BigDecimal?, estimate: java.math.BigDecimal?): String {
            if (actual == null) return "non disponible"
            val text = actual.stripTrailingZeros().toPlainString()
            if (estimate == null) return "$text · consensus indisponible"
            val direction = when (actual.compareTo(estimate)) {
                1 -> "au-dessus du consensus"
                -1 -> "sous le consensus"
                else -> "conforme au consensus"
            }
            return "$text vs ${estimate.stripTrailingZeros().toPlainString()} · $direction"
        }
        return "Publication : ${e.date}\nBPA : ${comparison(e.epsActual, e.epsEstimate)}\n" +
            "Revenus : ${comparison(e.revenueActual, e.revenueEstimate)}\n" +
            "Source : ${e.source}. Résumé chiffré; les données peuvent être complétées après publication."
    }
}
