package ca.monwallet.app.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.work.*
import ca.monwallet.app.MonWallet
import ca.monwallet.app.R
import ca.monwallet.app.domain.*
import ca.monwallet.app.ui.*
import java.util.concurrent.TimeUnit

class WidgetConfiguration : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (id < 0) { finish(); return }
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
                Surface {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(stringResource(R.string.widget_configure),
                            style = MaterialTheme.typography.headlineSmall)
                        Card(Modifier.fillMaxWidth()) {
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
                                    result?.let { config.titles(it) }?.take(3)?.forEach { h ->
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
                        listOf("Résumé" to R.string.widget_style_summary, "Titres" to R.string.widget_style_titles,
                            "Graphique" to R.string.widget_style_chart, "Mixte" to R.string.widget_style_mixed,
                            "Ultra compact" to R.string.widget_style_compact).forEach { (style, label) ->
                            WidgetChoice(stringResource(label), config.style == style) {
                                config = config.copy(style = style)
                            }
                        }
                        WidgetSection(stringResource(R.string.widget_section_returns))
                        WidgetSwitch(stringResource(R.string.widget_total_percent), config.totalPercent) { config = config.copy(totalPercent = it) }
                        WidgetSwitch(stringResource(R.string.widget_day_percent), config.dayPercent) { config = config.copy(dayPercent = it) }
                        WidgetSwitch(stringResource(R.string.widget_day_amount), config.dayAmount) { config = config.copy(dayAmount = it) }
                        WidgetSwitch(stringResource(R.string.widget_gain_amount), config.totalAmount) { config = config.copy(totalAmount = it) }
                        WidgetSwitch(stringResource(R.string.widget_current_value), config.value) { config = config.copy(value = it) }
                        WidgetSwitch(stringResource(R.string.widget_capital), config.invested) { config = config.copy(invested = it) }
                        WidgetSection(stringResource(R.string.widget_section_titles))
                        WidgetSwitch(stringResource(R.string.widget_show_titles), config.showTitles) { config = config.copy(showTitles = it) }
                        if (config.showTitles) {
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
                                Row {
                                    TextButton(onClick = { config = config.copy(titleIds = held.map { it.securityId }) }) {
                                        Text(stringResource(R.string.widget_select_all))
                                    }
                                    TextButton(onClick = { config = config.copy(titleIds = emptyList()) }) {
                                        Text(stringResource(R.string.widget_select_none))
                                    }
                                }
                                held.forEach { h ->
                                    val security = wallet.security(h.securityId) ?: return@forEach
                                    var distance by remember(security.id) { mutableFloatStateOf(0f) }
                                    Row(Modifier.fillMaxWidth().heightIn(min = 42.dp)
                                        .pointerInput(security.id) {
                                            detectDragGesturesAfterLongPress(onDragEnd = { distance = 0f }) { change, drag ->
                                                change.consume()
                                                distance += drag.y
                                                val order = current.titleIds
                                                val index = order.indexOf(security.id)
                                                val target = if (distance > 26.dp.toPx()) index + 1
                                                    else if (distance < -26.dp.toPx()) index - 1 else index
                                                if (index >= 0 && target in order.indices && target != index) {
                                                    config = current.copy(titleIds = order.toMutableList().apply {
                                                        add(target, removeAt(index))
                                                    })
                                                    distance = 0f
                                                }
                                            }
                                        }, verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(security.id in config.titleIds, onCheckedChange = { yes ->
                                            config = config.copy(titleIds = if (yes) config.titleIds + security.id
                                                else config.titleIds - security.id)
                                        })
                                        Text(security.ticker)
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
                        }
                        WidgetSection(stringResource(R.string.widget_section_chart))
                        WidgetSwitch(stringResource(R.string.widget_show_chart), config.chart) { config = config.copy(chart = it) }
                        if (config.chart) listOf("Jour", "1S", "1M", "3M", "6M", "1A").forEach { period ->
                            WidgetChoice(period, config.period == period) { config = config.copy(period = period) }
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
                                        config.refresh.toLong(), TimeUnit.MINUTES).build())
                            WalletWidget.updateAll(this@WidgetConfiguration)
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
