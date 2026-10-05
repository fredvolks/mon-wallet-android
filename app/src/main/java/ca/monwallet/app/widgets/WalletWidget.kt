package ca.monwallet.app.widgets

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.monwallet.app.MainActivity
import ca.monwallet.app.MonWallet
import ca.monwallet.app.R
import ca.monwallet.app.domain.*
import ca.monwallet.app.ui.*
import kotlinx.coroutines.*

open class WalletWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        (context.applicationContext as MonWallet).services.scope.launch {
            try {
                render(context, ids)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        options: Bundle,
    ) {
        onUpdate(context, manager, intArrayOf(id))
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val p = context.getSharedPreferences("widget_config", 0)
        ids.forEach { p.edit().remove("portfolio:$it").remove("owner:$it").apply() }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "ca.monwallet.REFRESH_WIDGET") {
            // A receiver must finish promptly; WorkManager owns network refresh work.
            androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "widget-manual-refresh",
                    androidx.work.ExistingWorkPolicy.KEEP,
                    androidx.work
                        .OneTimeWorkRequestBuilder<ca.monwallet.app.RefreshWorker>()
                        .build(),
                )
        }
    }

    companion object {
        private val providers =
            listOf(
                Widget2x2::class.java,
                Widget2x3::class.java,
                Widget4x2::class.java,
                Widget4x3::class.java,
            )

        fun updateAll(c: Context) {
            val manager = AppWidgetManager.getInstance(c)
            providers.forEach { clazz ->
                val ids = manager.getAppWidgetIds(ComponentName(c, clazz))
                if (ids.isNotEmpty())
                    c.sendBroadcast(
                        Intent(c, clazz)
                            .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    )
            }
        }

        suspend fun render(c: Context, ids: IntArray) {
            val s = (c.applicationContext as MonWallet).services
            if (!s.initialized.value) return
            val wallet = s.repo.current()
            val manager = AppWidgetManager.getInstance(c)
            val prefs = c.getSharedPreferences("widget_config", 0)
            val cache = s.repo.dao.cache()
            for (id in ids) {
                val info = manager.getAppWidgetInfo(id)
                val big = info?.provider?.className.orEmpty().endsWith("3")
                val owner = prefs.getString("owner:$id", null)
                val portfolio = prefs.getString("portfolio:$id", "all")?.takeUnless { it == "all" }
                val hidden =
                    (s.secure.get("biometric") == "true" &&
                        s.secure.get("hide_widgets") != "false") || owner != s.repo.owner.value
                val result = runCatching { wallet.result(portfolio) }.getOrNull()
                val views = RemoteViews(c.packageName, R.layout.wallet_widget)
                views.setTextViewText(
                    R.id.widget_title,
                    if (portfolio == null) "Mon portefeuille"
                    else wallet.portfolios.find { it.id == portfolio }?.name ?: "Configurer",
                )
                val tx =
                    wallet.transactions.filter { portfolio == null || it.portfolioId == portfolio }
                val held = tx.mapNotNull { it.securityId }.toSet()
                val session =
                    wallet.quotes.values
                        .filter { it.securityId in held }
                        .minOfOrNull { it.sessionDate }
                views.setTextViewText(
                    R.id.widget_day,
                    if (hidden) "Déverrouille / configure"
                    else "Jour${session?.let { " · ${date(it)}" } ?: ""}",
                )
                views.setTextViewText(R.id.widget_amount, if (hidden) "—" else signed(result?.day))
                views.setTextViewText(
                    R.id.widget_percent,
                    if (hidden) "—" else percent(result?.dayPercent),
                )
                val color =
                    if (result?.day?.signum() == -1) Color.rgb(255, 89, 110)
                    else Color.rgb(86, 248, 93)
                views.setTextColor(R.id.widget_amount, color)
                views.setTextColor(R.id.widget_percent, color)
                views.setTextViewText(
                    R.id.widget_rows,
                    if (hidden || !big) ""
                    else
                        result
                            ?.holdings
                            ?.filter { it.quantity > ZERO }
                            ?.sortedByDescending { it.value }
                            ?.take(4)
                            ?.joinToString("\n") { h ->
                                "${wallet.security(h.securityId)?.ticker}   ${signed(h.day)}  ${percent(h.dayPercent)}"
                            }
                            .orEmpty(),
                )
                views.setViewVisibility(
                    R.id.widget_rows,
                    if (big && !hidden) View.VISIBLE else View.GONE,
                )
                val points: List<Point> =
                    cache
                        .filter { it.kind == "intraday" }
                        .flatMap {
                            runCatching {
                                    s.repo.gson
                                        .fromJson(it.payload, Array<Point>::class.java)
                                        .toList()
                                }
                                .getOrDefault(emptyList())
                        }
                        .sortedBy { it.timestamp }
                val times =
                    points
                        .filter { it.securityId in held && it.date == session }
                        .map { it.timestamp }
                        .distinct()
                        .sorted()
                val values: List<Float> =
                    if (hidden || session == null) emptyList()
                    else
                        times.mapNotNull { timestamp ->
                            val quotes = wallet.quotes.toMutableMap()
                            var complete = true
                            for (security in
                                wallet.securities.filter {
                                    it.id in held || it.symbol == "CAD=X"
                                }) {
                                val original = quotes[security.id]
                                if (original == null) {
                                    if (security.id in held) complete = false
                                    continue
                                }
                                val p =
                                    points.lastOrNull {
                                        it.securityId == security.id &&
                                            it.date == session &&
                                            it.timestamp <= timestamp
                                    }
                                if (p != null)
                                    quotes[security.id] =
                                        original.copy(price = p.close, timestamp = p.timestamp)
                                else if (
                                    security.id in held ||
                                        (security.symbol == "CAD=X" &&
                                            wallet.securities.any {
                                                it.id in held && it.currency == "USD"
                                            })
                                )
                                    complete = false
                            }
                            if (complete)
                                runCatching {
                                        Engine.calculate(
                                                tx,
                                                quotes,
                                                wallet.securities
                                                    .find { it.symbol == "CAD=X" }
                                                    ?.let { quotes[it.id] },
                                            )
                                            .day
                                            ?.toFloat()
                                    }
                                    .getOrNull()
                            else null
                        }
                val bitmap = Bitmap.createBitmap(600, 170, Bitmap.Config.ARGB_8888)
                if (values.size >= 2) {
                    val canvas = Canvas(bitmap)
                    val paint =
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            this.color = color
                            style = Paint.Style.STROKE
                            strokeWidth = 4f
                        }
                    val low = values.minOrNull()!!
                    val range = (values.maxOrNull()!! - low).takeIf { it > 0 } ?: 1f
                    val path = Path()
                    values.forEachIndexed { i, v ->
                        val x = i * 596f / (values.size - 1) + 2
                        val y = 164 - (v - low) / range * 155
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    canvas.drawPath(path, paint)
                }
                views.setImageViewBitmap(R.id.widget_chart, bitmap)
                views.setTextViewText(
                    R.id.widget_footer,
                    if (hidden) "Appuie sur ⚙ pour configurer"
                    else if (values.size < 2) "Graphique intrajournalier indisponible"
                    else "P&L quotidien · cours en cache",
                )
                views.setOnClickPendingIntent(
                    R.id.widget_title,
                    PendingIntent.getActivity(
                        c,
                        id,
                        Intent(c, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                views.setOnClickPendingIntent(
                    R.id.widget_configure,
                    PendingIntent.getActivity(
                        c,
                        id + 10000,
                        Intent(c, WidgetConfiguration::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                views.setOnClickPendingIntent(
                    R.id.widget_refresh,
                    PendingIntent.getBroadcast(
                        c,
                        id,
                        Intent("ca.monwallet.REFRESH_WIDGET")
                            .setComponent(
                                info?.provider ?: ComponentName(c, Widget2x2::class.java)
                            ),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                manager.updateAppWidget(id, views)
            }
        }
    }
}

class Widget2x2 : WalletWidget()

class Widget2x3 : WalletWidget()

class Widget4x2 : WalletWidget()

class Widget4x3 : WalletWidget()

