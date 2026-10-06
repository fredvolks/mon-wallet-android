package ca.monwallet.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import ca.monwallet.app.MainActivity
import ca.monwallet.app.R
import ca.monwallet.app.domain.Point
import ca.monwallet.app.domain.Result
import ca.monwallet.app.domain.Wallet
import ca.monwallet.app.domain.ZERO
import ca.monwallet.app.domain.pct
import ca.monwallet.app.marketdata.MarketSession
import ca.monwallet.app.marketdata.NormalizedQuote
import ca.monwallet.app.marketdata.OfficialLogoProvider
import ca.monwallet.app.marketdata.supportsUsExtendedHours
import ca.monwallet.app.ui.money
import ca.monwallet.app.ui.number
import ca.monwallet.app.ui.percent
import ca.monwallet.app.ui.signed
import java.util.concurrent.TimeUnit
import java.time.LocalDate

/** RemoteViews restricted to the 4x2 provider. No placeholder prices or sample holdings. */
internal object WidgetWideRenderer {
    suspend fun render(
        c: Context, id: Int, info: AppWidgetProviderInfo?,
        config: WidgetSettings, wallet: Wallet, portfolio: String?,
        result: Result?, hidden: Boolean, cached: List<Point>,
    ): RemoteViews {
        val views = RemoteViews(c.packageName, R.layout.wallet_widget_wide)
        val title = if (portfolio == null) c.getString(R.string.all_portfolios)
            else wallet.portfolios.find { it.id == portfolio }?.name ?: "Portefeuille supprimé"
        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_total_label, c.getString(R.string.widget_return_total))
        views.setTextViewText(R.id.widget_total, if (hidden) "—" else percent(result?.percent))
        views.setTextColor(R.id.widget_total, tone(result?.percent?.signum()))
        views.setViewVisibility(R.id.widget_total_group,
            if (config.totalPercent && !hidden) View.VISIBLE else View.GONE)

        val held = result?.holdings?.filter { it.quantity > ZERO }
            ?.map { it.securityId }?.toSet().orEmpty()
        val currentDay = held.isNotEmpty() && held.all { key ->
            wallet.quotes[key]?.sessionDate == LocalDate.now().toString()
        }
        views.setTextViewText(R.id.widget_day,
            if (hidden) c.getString(R.string.widget_locked) else if (currentDay || result?.day == null)
                c.getString(R.string.widget_today) else "Dernière séance")
        views.setViewVisibility(R.id.widget_day,
            if (config.showDailyLabel) View.VISIBLE else View.GONE)
        val unavailable = result?.day == null && result?.dayPercent == null && !hidden
        val showAmount = !hidden && !config.hideAmounts && config.dayAmount && result?.day != null
        val showPercent = !hidden && config.dayPercent && result?.dayPercent != null
        views.setTextViewText(R.id.widget_amount, when {
            showAmount -> signed(result?.day)
            unavailable -> "Marché fermé"
            else -> ""
        })
        views.setTextViewText(R.id.widget_percent, if (showPercent) percent(result?.dayPercent) else "")
        views.setTextViewTextSize(R.id.widget_amount, android.util.TypedValue.COMPLEX_UNIT_SP,
            if (unavailable) 15f else if (config.dailyHero) 35f else 22f)
        views.setTextViewTextSize(R.id.widget_percent, android.util.TypedValue.COMPLEX_UNIT_SP,
            if (config.dailyHero) 24f else 17f)
        views.setViewVisibility(R.id.widget_amount, if (showAmount) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_percent, if (showPercent) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_day_pill,
            if (!hidden) View.VISIBLE else View.GONE)
        if (unavailable) views.setViewVisibility(R.id.widget_amount, View.VISIBLE)
        val daySign = result?.day?.signum()
        val dayColor = tone(daySign)
        views.setTextColor(R.id.widget_amount, dayColor)
        views.setTextColor(R.id.widget_percent, dayColor)
        views.setInt(R.id.widget_day_pill, "setBackgroundResource",
            when {
                !config.dailyHero -> R.drawable.widget_pill_neutral
                daySign == null || daySign == 0 -> R.drawable.widget_pill_neutral
                daySign < 0 -> R.drawable.widget_pill_negative
                else -> R.drawable.widget_pill_positive
            })
        val extras = if (hidden || config.hideAmounts) emptyList() else buildList {
            if (config.totalAmount) add(c.getString(R.string.widget_gain) + " " + signed(result?.pnl))
            if (config.value) add(c.getString(R.string.widget_value) + " " + money(result?.value))
            if (config.invested) add(c.getString(R.string.widget_invested) + " " + money(result?.invested))
        }
        views.setTextViewText(R.id.widget_extras, extras.joinToString(" · "))
        views.setViewVisibility(R.id.widget_extras, if (extras.isEmpty()) View.GONE else View.VISIBLE)

        val options = AppWidgetManager.getInstance(c).getAppWidgetOptions(id)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val ordered = if (hidden || result == null) emptyList() else config.titles(result)
        val plan = WidgetTitleLayout.plan(config, "4x2", minHeight, ordered.size)
        val chartSeries = if (hidden || !plan.chart || result == null) emptyList()
            else WidgetChartData.values(wallet, portfolio, config.period, cached)
        val chartVisible = chartSeries.size >= 2 &&
            config.style in setOf("Mixte", "Mixte premium", "Daily + Titres", "Graphique")
        views.setViewVisibility(R.id.widget_chart, if (chartVisible) View.VISIBLE else View.GONE)
        if (chartVisible) {
            val up = chartSeries.last() >= chartSeries.first()
            views.setImageViewBitmap(R.id.widget_chart, WidgetChartData.bitmap(chartSeries, up))
        }

        views.removeAllViews(R.id.widget_rows)
        val showTitles = !hidden && config.showTitles &&
            config.style !in setOf("Résumé", "Ultra compact", "Graphique")
        val titles = if (showTitles) ordered.take(plan.limit) else emptyList()
        val rowLayout = when (plan.density) {
            WidgetTitleLayout.Density.SPACIOUS -> R.layout.wallet_widget_wide_row_spacious
            WidgetTitleLayout.Density.COMPACT -> R.layout.wallet_widget_wide_row
            WidgetTitleLayout.Density.ULTRA -> R.layout.wallet_widget_wide_row_ultra
        }
        val extendedSessions = mutableSetOf<MarketSession>()
        for (holding in titles) {
            val security = wallet.security(holding.securityId) ?: continue
            val row = RemoteViews(c.packageName, rowLayout)
            row.setTextViewText(R.id.widget_row_ticker,
                if (config.ticker) security.ticker else security.name)
            val logo = if (config.logo) OfficialLogoProvider.load(c, security) else null
            row.setViewVisibility(R.id.widget_row_logo, if (logo == null) View.GONE else View.VISIBLE)
            row.setViewVisibility(R.id.widget_row_fallback,
                if (logo == null) View.VISIBLE else View.GONE)
            row.setTextViewText(R.id.widget_row_fallback, security.ticker.take(2))
            if (logo != null) row.setImageViewBitmap(R.id.widget_row_logo, logo)
            val q = wallet.quotes[security.id]
            val extended = q?.takeIf { config.showExtended && supportsUsExtendedHours(security) }
                ?.let { NormalizedQuote.from(it) }
            val session = extended?.marketSession
            val extraPrice = when (session) {
                MarketSession.PRE_MARKET -> extended.preMarketPrice
                MarketSession.AFTER_HOURS -> extended.afterHoursPrice
                else -> null
            }
            val extraPercent = when (session) {
                MarketSession.PRE_MARKET -> extended.preMarketChangePercent
                MarketSession.AFTER_HOURS -> extended.afterHoursChangePercent
                else -> null
            }
            if (extraPrice != null && session != null) extendedSessions.add(session)
            row.setViewVisibility(R.id.widget_row_session,
                if (extraPrice != null) View.VISIBLE else View.GONE)
            row.setTextViewText(R.id.widget_row_session,
                if (session == MarketSession.PRE_MARKET) "☀" else "☾")
            row.setTextColor(R.id.widget_row_session,
                if (session == MarketSession.PRE_MARKET) Color.rgb(90, 183, 255)
                else Color.rgb(181, 150, 241))
            val showPrice = config.price && !config.hideAmounts
            val quotePrice = extraPrice ?: q?.price
            row.setTextViewText(R.id.widget_row_price,
                if (!showPrice) "" else if (minWidth < 290 && quotePrice != null)
                    number(quotePrice) + if (security.currency == "USD") " U$" else " $"
                else money(quotePrice, security.currency))
            row.setViewVisibility(R.id.widget_row_price,
                if (showPrice) View.VISIBLE else View.GONE)
            val dayPercent = if (extraPrice != null) extraPercent else holding.dayPercent
            val details = buildList {
                if (config.titleDayPercent) add(percent(dayPercent))
                if (config.titleTotalPercent)
                    add(c.getString(R.string.widget_total_short) + " " + percent(holding.percent))
                if (config.titleDayAmount && !config.hideAmounts)
                    add(if (extraPrice == null) signed(holding.day) else "—")
                if (config.titleValue && !config.hideAmounts) add(money(holding.value))
                if (config.weight) add(percent(holding.value?.let { value ->
                    result?.value?.takeIf { it.signum() != 0 }?.let { value.pct(it) }
                }))
            }
            row.setTextViewText(R.id.widget_row_values, details.joinToString(" · "))
            row.setTextColor(R.id.widget_row_values,
                tone((if (config.titleDayPercent) dayPercent else holding.percent)?.signum()))
            row.setOnClickPendingIntent(R.id.widget_row,
                PendingIntent.getActivity(c, id xor security.id.hashCode(),
                    Intent(c, MainActivity::class.java).putExtra("security", security.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.addView(R.id.widget_rows, row)
        }
        if (titles.isEmpty() && showTitles) {
            val row = RemoteViews(c.packageName, rowLayout)
            row.setViewVisibility(R.id.widget_row_logo, View.GONE)
            row.setViewVisibility(R.id.widget_row_fallback, View.GONE)
            row.setViewVisibility(R.id.widget_row_price, View.GONE)
            row.setViewVisibility(R.id.widget_row_values, View.GONE)
            row.setTextViewText(R.id.widget_row_ticker,
                if (hidden) c.getString(R.string.widget_locked)
                else c.getString(R.string.widget_no_holdings))
            views.addView(R.id.widget_rows, row)
        }
        views.setViewVisibility(R.id.widget_rows,
            if (showTitles) View.VISIBLE else View.GONE)
        val fetchedAt = wallet.quotes.values.filter { it.securityId in held }
            .minOfOrNull { it.fetchedAt }
        val elapsed = fetchedAt?.let {
            TimeUnit.MILLISECONDS.toMinutes((System.currentTimeMillis() - it).coerceAtLeast(0L))
        }
        val age = elapsed?.let { when {
            it < 60 -> "$it min"
            it < 1440 -> "${it / 60} h"
            else -> "${it / 1440} j"
        } }
        views.setTextViewText(R.id.widget_footer,
            if (hidden) "" else (age?.let { c.getString(R.string.widget_updated_short, it) }
                ?: c.getString(R.string.widget_no_quote)) + when {
                extendedSessions == setOf(MarketSession.PRE_MARKET) -> " · PRE"
                extendedSessions == setOf(MarketSession.AFTER_HOURS) -> " · AFTER"
                else -> ""
            })
        views.setViewVisibility(R.id.widget_footer,
            if (plan.footer && !hidden) View.VISIBLE else View.GONE)
        views.setOnClickPendingIntent(R.id.widget_root,
            PendingIntent.getActivity(c, id + 20000,
                Intent(c, MainActivity::class.java).putExtra("portfolio", portfolio),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        views.setOnClickPendingIntent(R.id.widget_configure,
            PendingIntent.getActivity(c, id + 10000,
                Intent(c, WidgetConfiguration::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        views.setOnClickPendingIntent(R.id.widget_refresh,
            PendingIntent.getBroadcast(c, id,
                Intent("ca.monwallet.REFRESH_WIDGET")
                    .setComponent(info?.provider ?: ComponentName(c, Widget4x2::class.java))
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return views
    }

    private fun tone(sign: Int?): Int = when {
        sign == null || sign == 0 -> Color.rgb(204, 221, 227)
        sign < 0 -> Color.rgb(255, 92, 113)
        else -> Color.rgb(83, 244, 141)
    }
}
