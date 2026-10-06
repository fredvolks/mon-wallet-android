package ca.monwallet.app.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import ca.monwallet.app.MonWallet
import ca.monwallet.app.R
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.MarketSession
import ca.monwallet.app.marketdata.NormalizedQuote
import ca.monwallet.app.marketdata.supportsUsExtendedHours
import ca.monwallet.app.ui.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

class WidgetConfiguration : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (id < 0) { finish(); return }
        val providerKind = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
            ?.provider?.className.orEmpty()
        val wide = providerKind.endsWith("Widget4x2")
        val widgetHeight = AppWidgetManager.getInstance(this).getAppWidgetOptions(id)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                if (providerKind.endsWith("4x3")) 240 else if (providerKind.endsWith("2x3")) 180 else 150)
        val services = (application as MonWallet).services
        setContent {
            WalletTheme {
                val wallet by services.repo.state.collectAsState()
                var config by remember(id) { mutableStateOf(WidgetSettings.load(this@WidgetConfiguration, id)) }
                val key = if (config.portfolio == "all" && wallet.portfolios.size == 1)
                    wallet.portfolios.first().id else config.portfolio
                val result = remember(wallet, key) {
                    runCatching { wallet.result(key.takeUnless { it == "all" }) }.getOrNull()
                }
                val held = result?.holdings?.filter { it.quantity > ZERO }.orEmpty()
                val current by rememberUpdatedState(config)
                var cached by remember { mutableStateOf(emptyList<Point>()) }
                LaunchedEffect(wallet.quotes, wallet.prices) {
                    cached = services.repo.dao.cache().filter { it.kind == "intraday" }.flatMap {
                        runCatching {
                            services.repo.gson.fromJson(it.payload, Array<Point>::class.java).toList()
                        }.getOrDefault(emptyList())
                    }.sortedBy { it.timestamp }
                }
                Surface {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(stringResource(R.string.widget_configure),
                            style = MaterialTheme.typography.headlineSmall)
                        if (wide) WidgetWidePreview(wallet, key, result, config, cached, widgetHeight)
                        else Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(stringResource(R.string.widget_preview), color = Muted)
                                Text(wallet.portfolios.find { it.id == key }?.name
                                    ?: stringResource(R.string.all_portfolios))
                                if (config.totalPercent) Text(
                                    stringResource(R.string.widget_total) + "  " + percent(result?.percent),
                                    color = tint(result?.pnl), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.widget_today) + "  " + listOfNotNull(
                                    signed(result?.day).takeIf { config.dayAmount && !config.hideAmounts },
                                    percent(result?.dayPercent).takeIf { config.dayPercent }
                                ).joinToString("  "), color = tint(result?.day))
                                if (config.showTitles && config.style in setOf("Mixte", "Titres"))
                                    result?.let { config.titles(it) }?.take(
                                        WidgetTitleLayout.plan(config, providerKind, widgetHeight,
                                            result?.let { config.titles(it).size } ?: 0).visible)?.forEach { h ->
                                        Text(wallet.security(h.securityId)?.ticker.orEmpty() + "  " +
                                            percent(h.dayPercent) + "  " + percent(h.percent))
                                    }
                            }
                        }
                        WidgetSection(stringResource(R.string.widget_section_portfolio))
                        if (wallet.portfolios.isEmpty()) Text(stringResource(R.string.widget_no_portfolios))
                        ((if (wallet.portfolios.size > 1)
                            listOf("all" to stringResource(R.string.all_portfolios)) else emptyList()) +
                            wallet.portfolios.map { it.id to it.name }).forEach { (id, label) ->
                            WidgetChoice(label, key == id) {
                                config = config.copy(portfolio = id, titleIds = emptyList())
                            }
                        }
                        WidgetSection(stringResource(R.string.widget_section_appearance))
                        ((if (wide) listOf("Daily + Titres" to R.string.widget_style_daily_titles)
                            else emptyList()) +
                            listOf("Résumé" to R.string.widget_style_summary, "Titres" to R.string.widget_style_titles,
                            "Graphique" to R.string.widget_style_chart, "Mixte" to R.string.widget_style_mixed,
                            "Ultra compact" to R.string.widget_style_compact)).forEach { (style, label) ->
                            WidgetChoice(stringResource(label), config.style == style) {
                                config = if (style == "Daily + Titres") config.copy(
                                    style = style, chart = true, showTitles = true, totalPercent = true,
                                    titleDayPercent = true, titleTotalPercent = false, price = true,
                                    showUpdated = true)
                                else config.copy(style = style)
                            }
                        }
                        WidgetSection("DAILY")
                        if (wide) {
                            WidgetSwitch("Afficher Aujourd’hui", config.showDailyLabel) {
                                config = config.copy(showDailyLabel = it)
                            }
                            WidgetSwitch("Mettre Daily en valeur", config.dailyHero) {
                                config = config.copy(dailyHero = it)
                            }
                        }
                        if (!wide) {
                            WidgetSwitch(stringResource(R.string.widget_total_percent), config.totalPercent) { config = config.copy(totalPercent = it) }
                        }
                        WidgetSwitch(stringResource(R.string.widget_day_percent), config.dayPercent) { config = config.copy(dayPercent = it) }
                        WidgetSwitch(stringResource(R.string.widget_day_amount), config.dayAmount) { config = config.copy(dayAmount = it) }
                        WidgetSection("TOTAL")
                        if (wide) WidgetSwitch(stringResource(R.string.widget_total_percent), config.totalPercent) {
                            config = config.copy(totalPercent = it)
                        }
                        WidgetSwitch(stringResource(R.string.widget_gain_amount), config.totalAmount) { config = config.copy(totalAmount = it) }
                        WidgetSwitch(stringResource(R.string.widget_current_value), config.value) { config = config.copy(value = it) }
                        WidgetSwitch(stringResource(R.string.widget_capital), config.invested) { config = config.copy(invested = it) }
                        WidgetSection(stringResource(R.string.widget_section_titles))
                        WidgetSwitch(stringResource(R.string.widget_show_titles), config.showTitles) { config = config.copy(showTitles = it) }
                        if (config.showTitles) {
                            Choice(stringResource(R.string.widget_title_count),
                                if (config.titleCount == 0) stringResource(R.string.widget_title_count_auto)
                                else config.titleCount.toString(),
                                listOf(stringResource(R.string.widget_title_count_auto)) + (1..6).map(Int::toString)) {
                                config = config.copy(titleCount = it)
                            }
                            WidgetChoice(stringResource(R.string.widget_automatic), !config.custom) { config = config.copy(custom = false) }
                            WidgetChoice(stringResource(R.string.widget_custom), config.custom) {
                                config = config.copy(custom = true, titleIds = config.titleIds.ifEmpty { held.map { it.securityId } })
                            }
                            if (!config.custom) listOf("Valeur" to R.string.widget_sort_value,
                                "Gain jour" to R.string.widget_sort_day_gain,
                                "Perte jour" to R.string.widget_sort_day_loss,
                                "Variation %" to R.string.widget_sort_day_percent,
                                "Rendement %" to R.string.widget_sort_total_percent,
                                "Pire rendement" to R.string.widget_sort_worst,
                                "Portefeuille" to R.string.widget_sort_portfolio).forEach { (sort, label) ->
                                WidgetChoice(stringResource(label), config.sort == sort) { config = config.copy(sort = sort) }
                            }
                            else {
                                val selectedHeld = config.titleIds.count { id -> held.any { it.securityId == id } }
                                val limit = WidgetTitleLayout.plan(config,
                                    providerKind, widgetHeight, selectedHeld).limit
                                if (selectedHeld > limit)
                                    Text(stringResource(R.string.widget_title_count_hint, limit), color = Muted,
                                        style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.widget_drag_hint), color = Muted,
                                    style = MaterialTheme.typography.bodySmall)
                                Row {
                                    TextButton(onClick = { config = config.copy(titleIds = held.map { it.securityId }) }) {
                                        Text(stringResource(R.string.widget_select_all))
                                    }
                                    TextButton(onClick = { config = config.copy(titleIds = emptyList()) }) {
                                        Text(stringResource(R.string.widget_select_none))
                                    }
                                }
                                val chosen = config.titleIds.mapNotNull { id ->
                                    held.firstOrNull { it.securityId == id }
                                }
                                (chosen + held.filter { h -> h.securityId !in config.titleIds }).forEach { h ->
                                    val security = wallet.security(h.securityId) ?: return@forEach
                                    key(security.id) {
                                        var distance by remember { mutableFloatStateOf(0f) }
                                        Row(Modifier.fillMaxWidth().heightIn(min = 42.dp)
                                            .pointerInput(security.id, held.map { it.securityId }) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragEnd = { distance = 0f },
                                                    onDragCancel = { distance = 0f }) { change, drag ->
                                                    change.consume()
                                                    distance += drag.y
                                                    val order = current.titleIds
                                                    val activeIds = held.map { it.securityId }.toSet()
                                                    val active = order.filter { it in activeIds }
                                                    val index = active.indexOf(security.id)
                                                    val target = if (distance > 26.dp.toPx()) index + 1
                                                        else if (distance < -26.dp.toPx()) index - 1 else index
                                                    if (index >= 0 && target in active.indices && target != index) {
                                                        val moved = active.toMutableList().apply {
                                                            add(target, removeAt(index))
                                                        }.iterator()
                                                        config = current.copy(titleIds = order.map {
                                                            if (it in activeIds) moved.next() else it
                                                        })
                                                        distance = 0f
                                                    }
                                                }
                                            }, verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(security.id in config.titleIds, onCheckedChange = { yes ->
                                                config = config.copy(titleIds = if (yes) config.titleIds + security.id
                                                    else config.titleIds - security.id)
                                            })
                                            Text((if (security.id in config.titleIds) "☰  " else "") + security.ticker)
                                        }
                                    }
                                }
                            }
                            WidgetSwitch(stringResource(R.string.widget_title_ticker), config.ticker) { config = config.copy(ticker = it) }
                            WidgetSwitch(stringResource(R.string.widget_title_logo), config.logo) { config = config.copy(logo = it) }
                            WidgetSwitch(stringResource(R.string.widget_title_day_percent), config.titleDayPercent) { config = config.copy(titleDayPercent = it) }
                            WidgetSwitch(stringResource(R.string.widget_title_day_amount), config.titleDayAmount) { config = config.copy(titleDayAmount = it) }
                            WidgetSwitch(stringResource(R.string.widget_title_total_percent), config.titleTotalPercent) { config = config.copy(titleTotalPercent = it) }
                            WidgetSwitch(stringResource(R.string.widget_title_price), config.price) { config = config.copy(price = it) }
                            WidgetSwitch(stringResource(R.string.widget_title_value), config.titleValue) { config = config.copy(titleValue = it) }
                            WidgetSwitch(stringResource(R.string.widget_title_weight), config.weight) { config = config.copy(weight = it) }
                            if (wide) WidgetSwitch("Pre-market / After-hours", config.showExtended) {
                                config = config.copy(showExtended = it)
                            }
                        }
                        WidgetSection(stringResource(R.string.widget_section_chart))
                        WidgetSwitch(stringResource(R.string.widget_show_chart), config.chart) { config = config.copy(chart = it) }
                        if (config.chart) listOf("Jour", "1S", "1M", "3M", "6M", "1A").forEach { period ->
                            WidgetChoice(period, config.period == period) { config = config.copy(period = period) }
                        }
                        if (wide) WidgetSwitch(stringResource(R.string.widget_show_updated), config.showUpdated) {
                            config = config.copy(showUpdated = it)
                        }
                        WidgetSection(stringResource(R.string.widget_section_privacy))
                        WidgetSwitch(stringResource(R.string.widget_hide_amounts), config.hideAmounts) { config = config.copy(hideAmounts = it) }
                        WidgetSection(stringResource(R.string.widget_section_refresh))
                        listOf(0 to R.string.widget_automatic, 15 to R.string.widget_refresh_15,
                            30 to R.string.widget_refresh_30, 60 to R.string.widget_refresh_60).forEach { (minutes, label) ->
                            WidgetChoice(stringResource(label), config.refresh == minutes) { config = config.copy(refresh = minutes) }
                        }
                        Text(stringResource(R.string.widget_refresh_note), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            WidgetSettings.save(this@WidgetConfiguration, id, config.copy(portfolio = key),
                                services.repo.owner.value)
                            val work = WorkManager.getInstance(this@WidgetConfiguration)
                            work.cancelUniqueWork("widget-refresh:$id")
                            if (config.refresh in listOf(15, 30, 60))
                                work.enqueueUniquePeriodicWork("widget-refresh:$id", ExistingPeriodicWorkPolicy.UPDATE,
                                    PeriodicWorkRequestBuilder<ca.monwallet.app.RefreshWorker>(
                                        config.refresh.toLong(), TimeUnit.MINUTES)
                                        .setInputData(workDataOf("widgetId" to id)).build())
                            services.scope.launch {
                                WalletWidget.render(applicationContext, intArrayOf(id))
                            }
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                            finish()
                        }, enabled = wallet.portfolios.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.widget_save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetWidePreview(wallet: Wallet, key: String, result: Result?,
    config: WidgetSettings, cached: List<Point>, heightDp: Int) {
    val portfolio = key.takeUnless { it == "all" }
    val title = wallet.portfolios.find { it.id == key }?.name
        ?: if (portfolio == null) stringResource(R.string.all_portfolios) else "Portefeuille supprimé"
    val ordered = result?.let { config.titles(it) }.orEmpty()
    val plan = WidgetTitleLayout.plan(config, "4x2", heightDp, ordered.size)
    val holdings = ordered.take(plan.visible)
    val series = remember(wallet, key, plan.chart, config.period, cached) {
        if (plan.chart) WidgetChartData.values(wallet, portfolio, config.period, cached)
        else emptyList()
    }
    val totalTint = when (result?.percent?.signum()) {
        -1 -> Red
        null, 0 -> Muted
        else -> Green
    }
    val dayTint = when (result?.day?.signum()) {
        -1 -> Red
        null, 0 -> Muted
        else -> Green
    }
    Column(Modifier.fillMaxWidth().height(heightDp.dp)
        .background(Color(0xFF061722), RoundedCornerShape(22.dp))
        .border(1.dp, Color(0xFF19747B), RoundedCornerShape(22.dp))
        .padding(horizontal = 9.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().height(23.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = Color.White, fontSize = 16.sp)
            Text("↻   ⚙", color = Color.White, fontSize = 17.sp)
        }
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(0.45f).fillMaxHeight()) {
                Column(Modifier.fillMaxWidth().weight(1f)
                    .background(if (result?.day?.signum() == -1) Color(0xFF38202B)
                        else Color(0xFF123927), RoundedCornerShape(15.dp))
                    .padding(start = 7.dp, end = 4.dp),
                    verticalArrangement = Arrangement.Center) {
                    val today = result?.holdings?.filter { it.quantity > ZERO }
                        ?.all { wallet.quotes[it.securityId]?.sessionDate == java.time.LocalDate.now().toString() } == true
                    if (config.showDailyLabel) Text(if (today || result?.day == null) stringResource(R.string.widget_today)
                        else "Dernière séance", color = Muted, fontSize = 9.sp, maxLines = 1)
                    if (config.dayAmount && !config.hideAmounts)
                        Text(if (result?.day == null) "Marché fermé" else signed(result.day),
                            color = dayTint, fontSize = if (result?.day == null) 15.sp
                                else if (config.dailyHero) 26.sp else 21.sp,
                            lineHeight = if (config.dailyHero) 28.sp else 23.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (config.dayPercent && result?.dayPercent != null)
                        Text(percent(result.dayPercent), color = dayTint,
                            fontSize = if (config.dailyHero) 22.sp else 17.sp,
                            maxLines = 1)
                    if (series.size >= 2 && plan.chart) {
                        val lineColor = if (series.last() < series.first()) Red else Green
                        Canvas(Modifier.fillMaxWidth().height(11.dp)) {
                            val low = series.minOrNull() ?: 0f
                            val span = ((series.maxOrNull() ?: low) - low).takeIf { it > 0 } ?: 1f
                            series.zipWithNext().forEachIndexed { i, pair ->
                                fun point(index: Int, value: Float) =
                                    Offset(index * size.width / (series.size - 1),
                                        size.height - 2f - (value - low) / span * (size.height - 4f))
                                drawLine(lineColor, point(i, pair.first), point(i + 1, pair.second),
                                    strokeWidth = 2.dp.toPx())
                            }
                        }
                    }
                }
                if (config.totalPercent) Row(Modifier.fillMaxWidth().height(27.dp).padding(top = 3.dp)
                    .background(Color(0xFF102530), RoundedCornerShape(13.dp))
                    .padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.widget_return_total), Modifier.weight(1f),
                        color = Muted, fontSize = 8.sp, maxLines = 1)
                    Text(percent(result?.percent), color = totalTint, fontSize = 16.sp,
                        maxLines = 1)
                }
            }
            Spacer(Modifier.width(5.dp))
            Spacer(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF25424E)))
            Column(Modifier.weight(0.55f).fillMaxHeight().padding(start = 6.dp),
                verticalArrangement = Arrangement.Center) {
                holdings.forEach { h ->
                    val security = wallet.security(h.securityId) ?: return@forEach
                    val font = if (plan.density == WidgetTitleLayout.Density.ULTRA) 9.sp
                        else if (plan.density == WidgetTitleLayout.Density.COMPACT) 10.sp else 11.sp
                    val logo = if (plan.density == WidgetTitleLayout.Density.ULTRA) 16.dp
                        else if (plan.density == WidgetTitleLayout.Density.COMPACT) 18.dp else 22.dp
                    val q = wallet.quotes[security.id]
                    val normalized = q?.takeIf { config.showExtended && supportsUsExtendedHours(security) }
                        ?.let { NormalizedQuote.from(it) }
                    val extraPrice = when (normalized?.marketSession) {
                        MarketSession.PRE_MARKET -> normalized.preMarketPrice
                        MarketSession.AFTER_HOURS -> normalized.afterHoursPrice
                        else -> null
                    }
                    val change = when (normalized?.marketSession) {
                        MarketSession.PRE_MARKET -> normalized.preMarketChangePercent
                        MarketSession.AFTER_HOURS -> normalized.afterHoursChangePercent
                        else -> null
                    }
                    val shownPercent = if (extraPrice == null) h.dayPercent else change
                    Row(Modifier.fillMaxWidth().height(plan.density.rowHeight.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (config.logo) Logo(security, logo)
                        Text(if (config.ticker) security.ticker else security.name,
                            Modifier.weight(1f).padding(start = 3.dp),
                            fontSize = font, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (extraPrice != null) Text(if (normalized?.marketSession == MarketSession.PRE_MARKET)
                            "☀" else "☾", color = Blue, fontSize = 9.sp)
                        if (config.price && !config.hideAmounts)
                            Text(money(extraPrice ?: q?.price, security.currency),
                                Modifier.weight(1.9f), fontSize = font, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        if (config.titleDayPercent)
                            Text(percent(shownPercent), Modifier.weight(1.35f).padding(start = 2.dp),
                                color = if (shownPercent?.signum() == -1) Red else Green,
                                fontSize = font, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (holdings.isEmpty() && config.showTitles)
                    Text(stringResource(R.string.widget_no_holdings), color = Muted, fontSize = 9.sp)
            }
        }
        if (plan.footer) {
            val heldIds = result?.holdings?.filter { it.quantity > ZERO }
                ?.map { it.securityId }?.toSet().orEmpty()
            val fetched = wallet.quotes.values.filter { it.securityId in heldIds }
                .minOfOrNull { it.fetchedAt }
            val minutes = fetched?.let {
                TimeUnit.MILLISECONDS.toMinutes((System.currentTimeMillis() - it).coerceAtLeast(0L))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(minutes?.let { stringResource(R.string.widget_updated_short, "${it} min") }
                    ?: stringResource(R.string.widget_no_quote), color = Muted, fontSize = 8.sp)
            }
        }
    }
}

@Composable private fun WidgetSection(label: String) {
    HorizontalDivider()
    Text(label, color = Green, style = MaterialTheme.typography.titleSmall)
}
@Composable private fun WidgetChoice(label: String, selected: Boolean, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = click)
        TextButton(onClick = click) { Text(label) }
    }
}
@Composable private fun WidgetSwitch(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onCheckedChange = change)
    }
}
