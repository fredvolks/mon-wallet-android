package ca.monwallet.app.notifications

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ca.monwallet.app.*
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.Finnhub
import java.time.LocalDate

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

    private suspend fun emit(s: Services, event: AlertEvent) {
        val w = s.repo.current()
        if (w.events.any { it.eventKey == event.eventKey }) return
        s.repo.put("event", event.id, event)
        if (!allowed(s.context) || w.settings["notify_${event.channel}"] == "false") return
        val intent =
            PendingIntent.getActivity(
                s.context,
                event.id.hashCode(),
                Intent(s.context, MainActivity::class.java).putExtra("security", event.securityId),
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
        val last = s.secure.get("events_checked")?.toLongOrNull() ?: 0
        if (System.currentTimeMillis() - last < 3600_000) return
        s.secure.put("events_checked", System.currentTimeMillis().toString())
        for (pref in w.notifications) {
            val security = w.security(pref.securityId) ?: continue
            if (pref.earnings) {
                val key = s.secure.get("finnhub_key")
                if (!key.isNullOrBlank())
                    runCatching {
                        for (e in Finnhub(key).earnings(security)) {
                            val date = e.date ?: continue
                            if (date == LocalDate.now().plusDays(1).toString())
                                emit(
                                    s,
                                    AlertEvent(
                                        securityId = security.id,
                                        title = "${security.ticker} · Earnings demain",
                                        body =
                                            "BPA attendu : ${e.epsEstimate?:"—"}\nRevenus attendus : ${e.revenueEstimate?:"—"}",
                                        channel = "earnings",
                                        eventKey = "earnings:before:${security.id}:$date",
                                    ),
                                )
                            if (e.epsActual != null || e.revenueActual != null)
                                emit(
                                    s,
                                    AlertEvent(
                                        securityId = security.id,
                                        title = "${security.ticker} · Résultats publiés",
                                        body =
                                            "BPA : ${e.epsActual?:"—"} vs ${e.epsEstimate?:"—"}\nRevenus : ${e.revenueActual?:"—"} vs ${e.revenueEstimate?:"—"}",
                                        channel = "earnings",
                                        eventKey = "earnings:after:${security.id}:$date",
                                    ),
                                )
                        }
                    }
            }
            // Only a sourced backend analysis may authorize an IA notification.
            if (pref.news)
                s.news.state.value.articles
                    .filter { n ->
                        val analysis = n.analysis
                        analysis != null && analysis.notificationWorthy &&
                            analysis.importance in setOf("HIGH", "CRITICAL") &&
                            n.tickers.any { it.equals(security.symbol, ignoreCase = true) } &&
                            System.currentTimeMillis() - n.publishedAt in 0..(48L * 3600_000)
                    }
                    .forEach { n ->
                        emit(
                            s,
                            AlertEvent(
                                securityId = security.id,
                                title = "${security.ticker} · ${n.analysis?.importance} · ${n.source}",
                                body = n.analysis?.summaryFr?.takeIf { it.isNotBlank() } ?: n.title,
                                channel = "news",
                                eventKey = "news:ia:${n.id}:${security.id}",
                            ),
                        )
                    }
        }
    }
}

