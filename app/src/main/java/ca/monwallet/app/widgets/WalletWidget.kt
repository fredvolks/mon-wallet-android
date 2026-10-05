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
import ca.monwallet.app.marketdata.OfficialLogoProvider
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
        ids.forEach {
            WidgetSettings.delete(context, it)
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("widget-refresh:$it")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "ca.monwallet.REFRESH_WIDGET") {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            if (id < 0) return
            // A receiver must finish promptly; WorkManager owns network refresh work.
            androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "widget-manual-refresh:$id",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    androidx.work
                        .OneTimeWorkRequestBuilder<ca.monwallet.app.RefreshWorker>()
                        .setInputData(androidx.work.workDataOf("widgetId" to id))
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

        fun ids(c: Context): List<Int> {
            val manager = AppWidgetManager.getInstance(c)
            return providers.flatMap { manager.getAppWidgetIds(ComponentName(c, it)).toList() }
        }

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
                val kind = info?.provider?.className.orEmpty()
                val big = kind.endsWith("3") || kind.endsWith("4x2")
                val config = WidgetSettings.load(c, id)
                val owner = prefs.getString("owner:$id", null)
                val configured = if (config.portfolio == "all" && wallet.portfolios.size == 1)
                    wallet.portfolios.first().id else config.portfolio
                val portfolio = configured.takeUnless { it == "all" }
                val valid = portfolio == null || wallet.portfolios.any { it.id == portfolio }
                val hidden =
                    (s.secure.get("biometric") == "true" &&
                        s.secure.get("hide_widgets") != "false") || owner != s.repo.owner.value || !valid
                val result = if (hidden) null else runCatching { wallet.result(portfolio) }.getOrNull()
                if (kind.endsWith("4x2")) {
                    val points = cache.filter { it.kind == "intraday" }.flatMap {
                        runCatching {
                            s.repo.gson.fromJson(it.payload, Array<Point>::class.java).toList()
                        }.getOrDefault(emptyList())
                    }.sortedBy { it.timestamp }
                    manager.updateAppWidget(id, WidgetWideRenderer.render(c, id, info, config,
                        wallet, portfolio, result, hidden, points))
                    continue
                }
                val views = RemoteViews(c.packageName, R.layout.wallet_widget)
                views.setTextViewText(
                    R.id.widget_title,
                    if (portfolio == null) c.getString(R.string.all_portfolios)
                    else wallet.portfolios.find { it.id == portfolio }?.name ?: "Configurer",
                )
                views.setTextViewText(R.id.widget_total_label, c.getString(R.string.widget_total))
                views.setTextViewText(R.id.widget_total, if (hidden) "—" else percent(result?.percent))
                views.setTextColor(R.id.widget_total, if (result?.pnl?.signum() == -1)
                    Color.rgb(255,89,110) else Color.rgb(86,248,93))
                views.setViewVisibility(R.id.widget_total_group,
                    if (config.totalPercent) View.VISIBLE else View.GONE)
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
                    else c.getString(R.string.widget_today) + (session?.let { " · ${date(it)}" } ?: ""),
                )
                views.setTextViewText(R.id.widget_amount,
                    if (hidden || config.hideAmounts || !config.dayAmount || kind.endsWith("2x2")) ""
                    else signed(result?.day))
                views.setTextViewText(
                    R.id.widget_percent,
                    if (hidden || !config.dayPercent) "" else percent(result?.dayPercent),
                )
                views.setViewVisibility(R.id.widget_amount,
                    if (config.hideAmounts || !config.dayAmount || kind.endsWith("2x2"))
                        View.GONE else View.VISIBLE)
                views.setViewVisibility(R.id.widget_percent,
                    if (config.dayPercent) View.VISIBLE else View.GONE)
                views.setTextViewText(R.id.widget_extras,
                    if (hidden || config.hideAmounts) "" else buildList {
                        if (config.totalAmount) add(c.getString(R.string.widget_gain) + " " + signed(result?.pnl))
                        if (config.value) add(c.getString(R.string.widget_value) + " " + money(result?.value))
                        if (config.invested) add(c.getString(R.string.widget_invested) + " " + money(result?.invested))
                    }.joinToString(" · "))
                val color =
                    if (result?.day?.signum() == -1) Color.rgb(255, 89, 110)
                    else Color.rgb(86, 248, 93)
                views.setTextColor(R.id.widget_amount, color)
                views.setTextColor(R.id.widget_percent, color)
                views.removeAllViews(R.id.widget_rows)
                if (!hidden && big && config.showTitles && config.style !in setOf("Résumé", "Ultra compact")) {
                    val limit = when {
                        kind.endsWith("4x3") -> if (manager.getAppWidgetOptions(id)
                            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180) >= 230) 6 else 3
                        kind.endsWith("2x3") -> 2
                        else -> 3
                    }
                    result?.let { config.titles(it) }?.take(limit)?.forEach { h ->
                        val security = wallet.security(h.securityId) ?: return@forEach
                        val row = RemoteViews(c.packageName, R.layout.wallet_widget_row)
                        row.setTextViewText(R.id.widget_row_ticker,
                            if (config.ticker) security.ticker else security.name)
                        val parts = buildList {
                            if (config.titleDayPercent) add(percent(h.dayPercent))
                            if (config.titleDayAmount && !config.hideAmounts) add(signed(h.day))
                            if (config.titleTotalPercent)
                                add(c.getString(R.string.widget_total_short) + " " + percent(h.percent))
                            if (config.price && !config.hideAmounts)
                                add(money(wallet.quotes[security.id]?.price, security.currency))
                            if (config.titleValue && !config.hideAmounts) add(money(h.value))
                            if (config.weight) add(percent(h.value?.pct(result.value ?: ZERO)))
                        }
                        row.setTextViewText(R.id.widget_row_values, parts.joinToString(" · "))
                        val logo = if (config.logo) OfficialLogoProvider.load(c, security) else null
                        row.setViewVisibility(R.id.widget_row_logo,
                            if (logo == null) View.GONE else View.VISIBLE)
                        if (logo != null) row.setImageViewBitmap(R.id.widget_row_logo, logo)
                        row.setOnClickPendingIntent(R.id.widget_row,
                            PendingIntent.getActivity(c, id xor security.id.hashCode(),
                                Intent(c, MainActivity::class.java).putExtra("security", security.id),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                        views.addView(R.id.widget_rows, row)
                    }
                }
                views.setViewVisibility(
                    R.id.widget_rows,
                    if (big && !hidden && config.showTitles &&
                        config.style !in setOf("Résumé", "Ultra compact")) View.VISIBLE else View.GONE,
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
                    if (hidden || session == null || !config.chart || !big) emptyList()
                    else if (config.period != "Jour") {
                        val days = mapOf("1S" to 7L, "1M" to 31L, "3M" to 93L,
                            "6M" to 186L, "1A" to 366L)[config.period] ?: 7L
                        val start = java.time.LocalDate.now().minusDays(days).toString()
                        if (wallet.prices.isEmpty()) emptyList() else runCatching {
                            Engine.history(wallet.transactions, wallet.prices, wallet.securities, portfolio)
                                .filter { it.date >= start }.mapNotNull { it.value?.toFloat() }
                        }.getOrDefault(emptyList())
                    }
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
                val bitmap = Bitmap.createBitmap(600, 90, Bitmap.Config.ARGB_8888)
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
                        val y = 86 - (v - low) / range * 80
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    canvas.drawPath(path, paint)
                }
                val chartVisible = !hidden && config.chart && big && values.size >= 2 &&
                    config.style in setOf("Graphique", "Mixte")
                views.setViewVisibility(R.id.widget_chart,
                    if (chartVisible) View.VISIBLE else View.GONE)
                if (chartVisible) views.setImageViewBitmap(R.id.widget_chart, bitmap)
                if (chartVisible && (config.style == "Graphique" || !kind.endsWith("4x3")))
                    views.setViewVisibility(R.id.widget_rows, View.GONE)
                views.setTextViewText(
                    R.id.widget_footer,
                    if (hidden) "Appuie sur ⚙ pour configurer"
                    else c.getString(R.string.widget_updated,
                        wallet.quotes.values.filter { it.securityId in held }.minOfOrNull { it.fetchedAt }
                            ?.let { android.text.format.DateUtils.getRelativeTimeSpanString(it).toString() }
                            ?: c.getString(R.string.widget_no_quote)),
                )
                if (kind.endsWith("2x2")) views.setViewVisibility(R.id.widget_footer, View.GONE)
                views.setOnClickPendingIntent(R.id.widget_root,
                    PendingIntent.getActivity(c, id + 20000,
                        Intent(c, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
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
                            ).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
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
