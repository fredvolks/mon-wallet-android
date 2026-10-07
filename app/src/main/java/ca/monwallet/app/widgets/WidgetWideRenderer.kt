package ca.monwallet.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import ca.monwallet.app.MainActivity
import ca.monwallet.app.R
import ca.monwallet.app.data.Catalog
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
    internal fun titleSummary(label: String, priceText: String, dayPercent: java.math.BigDecimal?,
        showPrice: Boolean, showPercent: Boolean, session: MarketSession?): SpannableStringBuilder {
        val summary = SpannableStringBuilder()
        // Keep every value at the same monospace character offset, even with PRE/AH.
        summary.append(label.take(5).padEnd(5)).append(" ")
        if (showPrice) {
            val start = summary.length
            summary.append(priceText.take(10).padEnd(10))
            summary.setSpan(ForegroundColorSpan(Color.rgb(218, 230, 235)),
                start, summary.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (showPercent) {
            val start = summary.length
            summary.append(percent(dayPercent))
            summary.setSpan(ForegroundColorSpan(tone(dayPercent?.signum())),
                start, summary.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (session in setOf(MarketSession.PRE_MARKET, MarketSession.AFTER_HOURS)) {
            val start = summary.length
            summary.append(if (session == MarketSession.PRE_MARKET) " ☀" else " ☾")
            summary.setSpan(ForegroundColorSpan(if (session == MarketSession.PRE_MARKET)
                Color.rgb(90, 183, 255) else Color.rgb(181, 150, 241)),
                start, summary.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return summary
    }

    suspend fun render(
        c: Context, id: Int, info: AppWidgetProviderInfo?,
        config: WidgetSettings, wallet: Wallet, portfolio: String?,
        result: Result?, hidden: Boolean, cached: List<Point>,
        sizeOverrideDp: Pair<Int, Int>? = null,
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
            unavailable -> "Jour indisponible"
            else -> ""
        })
        views.setTextViewText(R.id.widget_percent, if (showPercent) percent(result?.dayPercent) else "")
        views.setTextViewTextSize(R.id.widget_amount, android.util.TypedValue.COMPLEX_UNIT_SP,
            if (unavailable) 12f else if (config.dailyHero) 26f else 22f)
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
        // The 4x2 never reveals wallet value or capital, including legacy configs.
        views.setTextViewText(R.id.widget_extras, "")
        views.setViewVisibility(R.id.widget_extras, View.GONE)

        // These are cached provider quotes, including the completed session after close.
        // Never infer 0% when a feed has not supplied an index quote.
        listOf("^GSPC" to R.id.widget_sp500, "^IXIC" to R.id.widget_nasdaq,
            "^DJI" to R.id.widget_dow).forEach { (symbol, viewId) ->
            val security = Catalog.markets.firstOrNull { it.symbol == symbol }
            val change = security?.let { wallet.quotes[it.id]?.percent }
            views.setTextViewText(viewId, if (hidden) "" else percent(change))
            views.setTextColor(viewId, tone(change?.signum()))
        }
        views.setViewVisibility(R.id.widget_indices, if (hidden) View.GONE else View.VISIBLE)

        val options = if (sizeOverrideDp == null) AppWidgetManager.getInstance(c)
            .getAppWidgetOptions(id) else null
        val minHeight = sizeOverrideDp?.second
            ?: options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150) ?: 150
        val minWidth = sizeOverrideDp?.first
            ?: options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) ?: 250
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
        val summaries = titles.mapNotNull { holding ->
            val security = wallet.security(holding.securityId) ?: return@mapNotNull null
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
            val showPrice = config.price && !config.hideAmounts
            security.id to titleSummary(if (config.ticker) security.ticker else security.name,
                if (showPrice) number(extraPrice ?: q?.price) else "",
                if (extraPrice != null) extraPercent else q?.percent,
                showPrice, config.titleDayPercent, session.takeIf { extraPrice != null })
        }.toMap()
        // One text size for every title keeps the monospace price/% columns aligned.
        // Account for the actual 4x2 width, the divider, and the larger logos.
        val logoWidth = if (!config.logo) 0f else when (plan.density) {
            WidgetTitleLayout.Density.SPACIOUS -> 31f
            WidgetTitleLayout.Density.COMPACT -> 25f
            WidgetTitleLayout.Density.ULTRA -> 22f
        }
        val maxSp = when (plan.density) {
            WidgetTitleLayout.Density.SPACIOUS -> 13f
            WidgetTitleLayout.Density.COMPACT -> 12f
            WidgetTitleLayout.Density.ULTRA -> 11f
        }
        val metrics = c.resources.displayMetrics
        val roomPx = (((minWidth - 30f) * .55f - logoWidth - 3f).coerceAtLeast(60f)) *
            metrics.density
        val measure = Paint().apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = maxSp * metrics.scaledDensity
        }
        val widestPx = summaries.values.maxOfOrNull { measure.measureText(it.toString()) } ?: 0f
        val rowTextSp = if (widestPx > 0f) (maxSp * roomPx / widestPx)
            .coerceIn(8f, maxSp) else maxSp
        val extendedSessions = mutableSetOf<MarketSession>()
        for (holding in titles) {
            val security = wallet.security(holding.securityId) ?: continue
            val row = RemoteViews(c.packageName, rowLayout)
            val label = if (config.ticker) security.ticker else security.name
            val logo = if (config.logo) OfficialLogoProvider.load(c, security)
                ?: fallbackLogo(c, security.ticker, plan.density) else null
            row.setViewVisibility(R.id.widget_row_logo,
                if (logo == null) View.GONE else View.VISIBLE)
            row.setViewVisibility(R.id.widget_row_fallback, View.GONE)
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
            if (extraPrice != null && session != null) extendedSessions.add(session)
            row.setViewVisibility(R.id.widget_row_session, View.GONE)
            row.setTextViewText(R.id.widget_row_session,
                if (session == MarketSession.PRE_MARKET) "☀" else "☾")
            row.setTextColor(R.id.widget_row_session,
                if (session == MarketSession.PRE_MARKET) Color.rgb(90, 183, 255)
                else Color.rgb(181, 150, 241))
            row.setViewVisibility(R.id.widget_row_price, View.GONE)
            row.setViewVisibility(R.id.widget_row_values, View.GONE)
            // A single text cell keeps all quote figures in the actual RemoteViews
            // draw pass. Separate nested numeric TextViews can measure normally yet
            // paint nothing on some Android widget hosts.
            row.setTextViewTextSize(R.id.widget_row_ticker,
                android.util.TypedValue.COMPLEX_UNIT_SP, rowTextSp)
            row.setTextViewText(R.id.widget_row_ticker, summaries[security.id] ?: label)
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
        val marketsIntent = PendingIntent.getActivity(c, id + 30000,
            Intent(c, MainActivity::class.java).putExtra("section", "markets"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        listOf(R.id.widget_sp500_group, R.id.widget_nasdaq_group,
            R.id.widget_dow_group).forEach { views.setOnClickPendingIntent(it, marketsIntent) }
        views.setOnClickPendingIntent(R.id.widget_total_group,
            PendingIntent.getActivity(c, id + 40000,
                Intent(c, MainActivity::class.java).putExtra("section", "reports"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        views.setOnClickPendingIntent(R.id.widget_day_pill,
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

    private fun fallbackLogo(c: Context, ticker: String,
        density: WidgetTitleLayout.Density): Bitmap {
        val dp = when (density) {
            WidgetTitleLayout.Density.SPACIOUS -> 22
            WidgetTitleLayout.Density.COMPACT -> 18
            WidgetTitleLayout.Density.ULTRA -> 16
        }
        val size = (dp * c.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(28, 64, 77) }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = Color.rgb(189, 230, 239)
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size * 0.38f
        val baseline = size / 2f - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(ticker.take(2), size / 2f, baseline, paint)
        return bitmap
    }
}
