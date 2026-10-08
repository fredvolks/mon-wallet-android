package ca.monwallet.app.ui

import ca.monwallet.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.MarketSession
import ca.monwallet.app.marketdata.NormalizedQuote
import ca.monwallet.app.marketdata.supportsUsExtendedHours
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@Composable
fun PortfolioScreen(
    w: Wallet,
    vm: WalletViewModel,
    selected: String?,
    onSelected: (String?) -> Unit,
    onDetail: (Security) -> Unit,
    onBuy: () -> Unit,
    onHistory: () -> Unit,
    onReports: () -> Unit,
    onCreatePortfolio: () -> Unit,
) {
    if (w.portfolios.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.app_name), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.no_portfolios), fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Caption(stringResource(R.string.create_first_portfolio))
            Spacer(Modifier.height(24.dp))
            Button(onClick = onCreatePortfolio, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.first_portfolio_button))
            }
            Spacer(Modifier.height(8.dp))
            Caption(stringResource(R.string.portfolio_type_examples))
        }
        return
    }
    var period by remember { mutableIntStateOf(6) }
    var compact by
        remember(w.settings["compact"]) { mutableStateOf(w.settings["compact"] == "true") }
    var compactSort by remember(w.settings["compact_sort"]) {
        mutableStateOf(w.settings["compact_sort"] ?: "Valeur")
    }
    var showWeight by remember(w.settings["compact_show_weight"]) {
        mutableStateOf(w.settings["compact_show_weight"] == "true")
    }
    var sparkPeriod by remember(w.settings["compact_sparkline_period"]) {
        mutableStateOf(w.settings["compact_sparkline_period"] ?: "Jour")
    }
    var sortMenu by remember { mutableStateOf(false) }
    var showColumns by remember { mutableStateOf(false) }
    var compactColumns by remember(w.settings["portfolio_columns"]) {
        mutableStateOf(PortfolioTableColumns.restore(w.settings["portfolio_columns"]))
    }
    val tableScroll = rememberScrollState()
    val result = remember(w, selected) { runCatching { w.result(selected) }.getOrNull() }
    val snapshots by
        produceState(emptyList<Snapshot>(), w.transactions, w.prices, selected) {
            value =
                withContext(Dispatchers.Default) {
                    runCatching { Engine.history(w.transactions, w.prices, w.securities, selected) }
                        .getOrDefault(emptyList())
                }
        }
    val periods = listOf(
        stringResource(R.string.period_week), "1M", "3M", "6M",
        stringResource(R.string.period_year), stringResource(R.string.period_five_years),
        stringResource(R.string.period_all),
    )
    val cutoff =
        when (period) {
            0 -> LocalDate.now().minusWeeks(1)
            1 -> LocalDate.now().minusMonths(1)
            2 -> LocalDate.now().minusMonths(3)
            3 -> LocalDate.now().minusMonths(6)
            4 -> LocalDate.now().minusYears(1)
            5 -> LocalDate.now().minusYears(5)
            else -> LocalDate.MIN
        }.toString()
    val visible = snapshots.filter { it.date >= cutoff }
    val owned = result?.holdings?.filter { it.quantity > ZERO }.orEmpty()
    val extendedSession = if (w.settings["portfolio_extended"] != "REGULAR")
        owned.mapNotNull { h -> w.security(h.securityId)?.let { activeExtended(w, it)?.session } }
            .distinct().singleOrNull() else null
    val valuationLabel = when (extendedSession) {
        MarketSession.PRE_MARKET -> "Valeur estimée · Pre-market"
        MarketSession.AFTER_HOURS -> "Valeur estimée · After-hours"
        else -> stringResource(R.string.ui_valeur_actuelle_e0c0f)
    }
    val manualKey = "compact_order:${selected ?: "all"}"
    val manualOrder = w.settings[manualKey].orEmpty().split('|').filter { it.isNotBlank() }
    val holdings = remember(owned, compactSort, manualOrder) {
        when (compactSort) {
            "Ticker" -> owned.sortedBy { w.security(it.securityId)?.ticker }
            "Gain jour" -> owned.sortedByDescending { it.day ?: ZERO }
            "Perte jour" -> owned.sortedBy { it.day ?: ZERO }
            "Gain total" -> owned.sortedByDescending { it.pnl ?: ZERO }
            "Poids portefeuille" -> owned.sortedByDescending { it.value ?: ZERO }
            "Ordre manuel" -> owned.sortedWith(compareBy<Holding> {
                manualOrder.indexOf(it.securityId).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
            }.thenBy { owned.indexOf(it) })
            else -> owned.sortedByDescending { it.value ?: ZERO }
        }
    }
    val sparkIds = owned.joinToString("|") { it.securityId }
    val sparklines by produceState<Map<String, List<Point>>>(emptyMap(), compact, sparkIds,
        sparkPeriod, compactColumns) {
        value = emptyMap()
        if (!compact || "sparkline" !in compactColumns) return@produceState
        val (range, interval) = when (sparkPeriod) {
            "1S" -> "5d" to "1h"
            "1M" -> "1mo" to "1d"
            "3M" -> "3mo" to "1d"
            else -> "1d" to "5m"
        }
        val gate = Semaphore(2)
        supervisorScope {
            owned.take(20).mapNotNull { h -> w.security(h.securityId) }.map { security -> async {
                gate.withPermit {
                    try {
                        val points = vm.services.market.history(security, range, interval)
                        value = value + (security.id to points)
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { value = value + (security.id to emptyList()) }
                }
            } }.awaitAll()
        }
    }
    fun moveHolding(id: String, offset: Int) {
        val ids = holdings.map { it.securityId }.toMutableList()
        val from = ids.indexOf(id)
        val to = from + offset
        if (from < 0 || to !in ids.indices) return
        java.util.Collections.swap(ids, from, to)
        vm.run { vm.services.repo.setting(manualKey, ids.joinToString("|")) }
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            if (w.portfolios.size == 1) {
                Chips(listOf(w.portfolios.single().name), 0) { onSelected(w.portfolios.single().id) }
            } else Chips(
                listOf(stringResource(R.string.all_portfolios)) + w.portfolios.map { it.name },
                if (selected == null) 0 else w.portfolios.indexOfFirst { it.id == selected } + 1,
            ) { onSelected(if (it == 0) null else w.portfolios[it - 1].id) }
        }
        item {
            if (compact) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Caption(if (selected == null) stringResource(R.string.all_portfolios_heading)
                            else w.portfolios.find { it.id == selected }?.name.orEmpty())
                        if (extendedSession != null) Caption(valuationLabel)
                        Text(money(result?.value), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Caption(stringResource(R.string.ui_p_l_du_jour_e22e7))
                        Text("${signed(result?.day)}  ${percent(result?.dayPercent)}",
                            fontSize = 12.sp, color = tint(result?.day),
                            fontWeight = FontWeight.SemiBold)
                        Text("Profit portefeuille  ${percent(result?.percent)}",
                            fontSize = 11.sp, color = tint(result?.pnl))
                    }
                }
            } else CardBlock {
                Caption(
                    if (selected == null) stringResource(R.string.all_portfolios_heading)
                    else w.portfolios.find { it.id == selected }?.name.orEmpty()
                )
                Spacer(Modifier.height(7.dp))
                Caption(valuationLabel)
                Text(money(result?.value), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    Metric(
                        stringResource(R.string.ui_capital_net_investi_59ad4),
                        money(result?.invested),
                        modifier = Modifier.weight(1f),
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Caption("Profit portefeuille")
                        Text(percent(result?.percent), color = tint(result?.pnl),
                            fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }
                Chart(
                    visible.map { it.value },
                    visible.map { it.invested },
                    Modifier.height(155.dp),
                )
                Chips(periods, period) { period = it }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(stringResource(R.string.ui_valeur_3f7f3), color = Green, fontSize = 11.sp)
                    Caption(stringResource(R.string.ui_capital_investi_da16a))
                }
                if (visible.any { it.value == null })
                    Caption(stringResource(R.string.ui_les_lacunes_correspondent_a_des_cours_histori_cb9dc))
                HorizontalDivider(Modifier.padding(vertical = 13.dp), color = Border)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric(stringResource(R.string.ui_p_l_du_jour_e22e7), signed(result?.day), tint(result?.day))
                    Metric(stringResource(R.string.ui_jour_a2cf1), percent(result?.dayPercent), tint(result?.day))
                    Metric(stringResource(R.string.ui_encaisse_d0263), money(result?.cash))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onHistory) {
                    Icon(Icons.Outlined.History, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_historique_34f3a))
                }
                TextButton(onClick = onReports) {
                    Icon(Icons.Outlined.BarChart, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_rapports_db7d9))
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.ui_mes_titres_055ac),
                    Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                )
                Chips(listOf(stringResource(R.string.view_cards), stringResource(R.string.view_compact)), if (compact) 1 else 0) {
                    compact = it == 1
                    vm.run { vm.services.repo.setting("compact", compact.toString()) }
                }
                if (compact) Box {
                    IconButton(onClick = { sortMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Sort, "Tri et options", Modifier.size(19.dp))
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        DropdownMenuItem(text = { Text("⚙ Colonnes") }, onClick = {
                            sortMenu = false; showColumns = true
                        })
                        listOf("Valeur", "Ticker", "Gain jour", "Perte jour", "Gain total",
                            "Poids portefeuille", "Ordre manuel").forEach { option ->
                            DropdownMenuItem(text = { Text((if (compactSort == option) "✓ " else "") + option) },
                                onClick = {
                                    compactSort = option; sortMenu = false
                                    vm.run { vm.services.repo.setting("compact_sort", option) }
                                })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Afficher poids % " +
                            if (showWeight) "✓" else "") }, onClick = {
                            showWeight = !showWeight; sortMenu = false
                            vm.run { vm.services.repo.setting("compact_show_weight", showWeight.toString()) }
                        })
                        listOf("Jour", "1S", "1M", "3M").forEach { option ->
                            DropdownMenuItem(text = { Text("Courbe $option " +
                                if (sparkPeriod == option) "✓" else "") }, onClick = {
                                sparkPeriod = option; sortMenu = false
                                vm.run { vm.services.repo.setting("compact_sparkline_period", option) }
                            })
                        }
                    }
                }
            }
        }
        if (w.portfolios.isEmpty())
            item {
                Empty(
                    stringResource(R.string.no_portfolios),
                    stringResource(R.string.create_first_portfolio),
                    stringResource(R.string.go_to_portfolios),
                    onCreatePortfolio,
                )
            }
        else if (result == null)
            item {
                Empty(
                    stringResource(R.string.ui_calcul_a_verifier_639af),
                    stringResource(R.string.portfolio_inconsistent),
                )
            }
        else if (owned.isEmpty())
            item {
                Empty(
                    stringResource(R.string.ui_ton_portefeuille_commence_ici_a7ba7),
                    stringResource(R.string.portfolio_empty),
                    stringResource(R.string.add_purchase),
                    onBuy,
                )
            }
        else
            if (compact) {
                item { PortfolioTableHeader(compactColumns, tableScroll) }
                items(holdings, key = { it.securityId }) { h ->
                    w.security(h.securityId)?.let { security ->
                        PortfolioTableRow(w, security, h, result, compactColumns, tableScroll,
                            sparklines[security.id].orEmpty(),
                            compactSort == "Ordre manuel", { moveHolding(security.id, it) }) {
                            onDetail(security)
                        }
                    }
                }
            } else
            items(holdings, key = { it.securityId }) { h ->
                w.security(h.securityId)?.let { s ->
                    HoldingRow(w, s, h, compact, result.value,
                        sparklines[s.id].orEmpty(), sparkPeriod, showWeight,
                        compactSort == "Ordre manuel", { moveHolding(s.id, it) }) { onDetail(s) }
                }
            }
        item {
            Caption(
                stringResource(R.string.ui_rendement_simple_en_cad_incluant_lencaisse_le_0b7d1)
            )
        }
    }
    if (showColumns) PortfolioColumnPicker(compactColumns, { updated ->
        compactColumns = updated
        vm.run { vm.services.repo.setting("portfolio_columns", updated.joinToString("|")) }
    }, { showColumns = false })
}

private object PortfolioTableColumns {
    val labels = linkedMapOf(
        "ticker" to "Ticker", "value" to "Valeur", "dayAmount" to "Jour $",
        "dayPercent" to "Jour %", "totalPercent" to "Total %", "totalAmount" to "Total $",
        "price" to "Prix", "average" to "Prix moyen", "quantity" to "Parts",
        "weight" to "Poids %", "invested" to "Capital investi",
        "realized" to "Gain réalisé", "unrealized" to "Gain non réalisé",
        "dividend" to "Dividendes", "fx" to "Effet FX", "sparkline" to "Sparkline",
    )
    val defaults = listOf("ticker", "value", "dayAmount", "dayPercent", "totalPercent")
    val presets = linkedMapOf("Minimal" to listOf("ticker", "value", "dayPercent", "totalPercent"),
        "Journalier" to listOf("ticker", "value", "dayAmount", "dayPercent"),
        "Performance" to listOf("ticker", "value", "totalAmount", "totalPercent"),
        "Complet" to listOf("ticker", "quantity", "price", "average", "value",
            "dayAmount", "dayPercent", "totalAmount", "totalPercent", "weight"))
    fun restore(raw: String?): List<String> = raw?.split('|')
        ?.filter { it in labels }.orEmpty().distinct().takeIf { it.isNotEmpty() }
        ?.let { listOf("ticker") + it.filter { key -> key != "ticker" } } ?: defaults
    fun width(key: String): Dp = when (key) {
        "ticker" -> 78.dp
        "sparkline" -> 62.dp
        "quantity", "weight", "dayPercent", "totalPercent" -> 60.dp
        else -> 76.dp
    }
}

@Composable
private fun PortfolioTableGrid(columns: List<String>, scroll: ScrollState,
    modifier: Modifier = Modifier, ticker: @Composable () -> Unit,
    value: @Composable (String) -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(PortfolioTableColumns.width("ticker")),
            contentAlignment = Alignment.CenterStart) { ticker() }
        Row(Modifier.weight(1f).horizontalScroll(scroll),
            verticalAlignment = Alignment.CenterVertically) {
            columns.filter { it != "ticker" }.forEach { key ->
                Box(Modifier.width(PortfolioTableColumns.width(key)),
                    contentAlignment = Alignment.CenterEnd) { value(key) }
            }
        }
    }
}

@Composable
private fun PortfolioTableHeader(columns: List<String>, scroll: ScrollState) {
    PortfolioTableGrid(columns, scroll, Modifier.height(23.dp),
        ticker = { Text("TICKER", color = Muted, fontSize = 9.sp) },
        value = { key -> Text(PortfolioTableColumns.labels.getValue(key).uppercase(),
            Modifier.fillMaxWidth(), textAlign = TextAlign.End, color = Muted, fontSize = 9.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis) })
    HorizontalDivider(color = Border)
}

@Composable
private fun PortfolioTableRow(w: Wallet, s: Security, h: Holding, result: Result,
    columns: List<String>, scroll: ScrollState, spark: List<Point>, manual: Boolean,
    move: (Int) -> Unit, onClick: () -> Unit) {
    var drag by remember(s.id) { mutableFloatStateOf(0f) }
    PortfolioTableGrid(columns, scroll, Modifier.height(34.dp).clickable(onClick = onClick),
        ticker = {
            Row(Modifier.pointerInput(s.id, manual) {
                if (manual) detectDragGesturesAfterLongPress(onDragEnd = { drag = 0f },
                    onDragCancel = { drag = 0f }) { change, distance ->
                    change.consume()
                    drag += distance.y
                    if (drag > 24.dp.toPx()) { move(1); drag = 0f }
                    if (drag < -24.dp.toPx()) { move(-1); drag = 0f }
                }
            }, verticalAlignment = Alignment.CenterVertically) {
                Logo(s, 20.dp)
                Spacer(Modifier.width(4.dp))
                Text(s.ticker, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }, value = { key ->
            if (key == "sparkline") {
                if (spark.size > 1) Chart(spark.map { it.close },
                    modifier = Modifier.fillMaxWidth().height(20.dp), color = tint(h.day))
            } else {
                val shown = when (key) {
                    "value" -> money(h.value)
                    "dayAmount" -> signed(h.day)
                    "dayPercent" -> percent(h.dayPercent)
                    "totalAmount", "unrealized" -> signed(h.pnl)
                    "totalPercent" -> percent(h.percent)
                    "price" -> number(w.quotes[s.id]?.price)
                    "average" -> number(h.average)
                    "quantity" -> number(h.quantity)
                    "weight" -> percent(h.value?.let { v ->
                        result.value?.takeIf { it.signum() > 0 }?.let { v.pct(it) }
                    })
                    "invested" -> money(h.costCad)
                    "realized" -> signed(h.realized)
                    "dividend" -> signed(h.dividends)
                    "fx" -> signed(h.fxGain)
                    else -> "—"
                }
                val amount = when (key) {
                    "dayAmount", "dayPercent" -> h.day
                    "totalAmount", "totalPercent", "unrealized" -> h.pnl
                    "realized" -> h.realized
                    "fx" -> h.fxGain
                    else -> null
                }
                Text(shown, Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    fontSize = 10.sp, fontWeight = if (key == "value") FontWeight.SemiBold
                        else FontWeight.Normal,
                    color = if (amount == null) MaterialTheme.colorScheme.onSurface else tint(amount),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        })
    HorizontalDivider(color = Border.copy(alpha = .45f))
}

@Composable
private fun PortfolioColumnPicker(columns: List<String>, update: (List<String>) -> Unit,
    close: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text("Colonnes · Mes titres") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                PortfolioTableColumns.presets.forEach { (name, preset) ->
                    TextButton(onClick = { update(preset) }) { Text("Preset : $name") }
                }
                HorizontalDivider()
                (columns + PortfolioTableColumns.labels.keys.filter { it !in columns }).forEach { key ->
                    var offset by remember(key) { mutableFloatStateOf(0f) }
                    Row(Modifier.fillMaxWidth().height(40.dp).pointerInput(key, columns) {
                        detectDragGesturesAfterLongPress(onDragEnd = { offset = 0f },
                            onDragCancel = { offset = 0f }) { change, drag ->
                            change.consume()
                            offset += drag.y
                            val index = columns.indexOf(key)
                            val target = if (offset > 24.dp.toPx()) index + 1
                                else if (offset < -24.dp.toPx()) index - 1 else index
                            if (index > 0 && target in 1 until columns.size && target != index) {
                                update(columns.toMutableList().apply { add(target, removeAt(index)) })
                                offset = 0f
                            }
                        }
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(key in columns, enabled = key != "ticker", onCheckedChange = { yes ->
                            update(if (yes) columns + key else columns - key)
                        })
                        Text((if (key in columns && key != "ticker") "☰  " else "") +
                            PortfolioTableColumns.labels.getValue(key), fontSize = 13.sp)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = close) { Text("Terminé") } })
}

@Composable
fun HoldingRow(w: Wallet, s: Security, h: Holding, compact: Boolean,
    portfolioValue: java.math.BigDecimal?, sparkPoints: List<Point> = emptyList(),
    sparkPeriod: String = "Jour", showWeight: Boolean = false,
    manual: Boolean = false, onMove: (Int) -> Unit = {}, onClick: () -> Unit) {
    if (compact) {
        CompactHoldingRow(w, s, h, portfolioValue, sparkPoints, sparkPeriod,
            showWeight, manual, onMove, onClick)
        return
    }
    Column(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(s)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.ticker, fontWeight = FontWeight.Bold)
                Caption(stringResource(R.string.shares_currency, number(h.quantity, 6), s.currency))
            }
            Chart(
                w.prices.filter { it.securityId == s.id }.takeLast(25).map { it.close },
                modifier = Modifier.width(54.dp).height(28.dp),
                color = tint(h.pnl),
            )
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 10.dp),
            ) {
                Text(
                    money(h.value),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(signed(h.pnl), fontSize = 13.sp, color = tint(h.pnl))
                Text(percent(h.percent), fontSize = 11.sp, color = tint(h.pnl))
            }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = Muted)
        }
            Row(
                Modifier.fillMaxWidth().padding(start = 44.dp, top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Caption(s.name)
                Text(stringResource(R.string.day_value, signed(h.day)), fontSize = 11.sp, color = tint(h.day))
            }
            activeExtended(w, s)?.let { extra ->
                Text(extra.label + "  " + money(extra.price, s.currency) +
                    "  " + percent(extra.changePercent),
                    Modifier.padding(start = 44.dp, top = 2.dp),
                    color = Muted, fontSize = 11.sp)
            }
    }
    HorizontalDivider(color = Border)
}

fun compactQuantity(s: Security, quantity: java.math.BigDecimal): String =
    if (s.type.uppercase() == "CRYPTO")
        "${number(quantity, 8)} ${s.symbol.substringBefore('-')}"
    else "${number(quantity, 6)} ${if (quantity <= ONE) "part" else "parts"}"

private data class ExtendedHoldingQuote(
    val session: MarketSession, val price: BigDecimal, val changePercent: BigDecimal?,
) {
    val label: String get() = if (session == MarketSession.PRE_MARKET) "☀ PRE" else "☾ AFTER"
}

private fun activeExtended(w: Wallet, s: Security): ExtendedHoldingQuote? {
    if (!supportsUsExtendedHours(s)) return null
    val quote = w.quotes[s.id]?.let { NormalizedQuote.from(it) } ?: return null
    return when (quote.marketSession) {
        MarketSession.PRE_MARKET -> quote.preMarketPrice?.let {
            ExtendedHoldingQuote(MarketSession.PRE_MARKET, it, quote.preMarketChangePercent)
        }
        MarketSession.AFTER_HOURS -> quote.afterHoursPrice?.let {
            ExtendedHoldingQuote(MarketSession.AFTER_HOURS, it, quote.afterHoursChangePercent)
        }
        else -> null
    }
}

@Composable
private fun CompactHoldingRow(w: Wallet, s: Security, h: Holding,
    portfolioValue: java.math.BigDecimal?, sparkPoints: List<Point>, sparkPeriod: String,
    showWeight: Boolean, manual: Boolean, onMove: (Int) -> Unit, onClick: () -> Unit) {
    val q = w.quotes[s.id]
    val extended = activeExtended(w, s)
    val points = sparkPoints.map { it.close }.toMutableList()
    if (sparkPeriod == "Jour" && q != null && sparkPoints.lastOrNull()?.date == q.sessionDate &&
        q.price != points.lastOrNull()) points.add(q.price)
    var drag by remember(s.id) { mutableFloatStateOf(0f) }
    val currentMove by rememberUpdatedState(onMove)
    Row(Modifier.fillMaxWidth().height(if (extended == null) 64.dp else 72.dp)
        .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).then(if (manual) Modifier.pointerInput(s.id) {
            detectDragGesturesAfterLongPress(onDragEnd = { drag = 0f },
                onDragCancel = { drag = 0f }) { change, amount ->
                change.consume(); drag += amount.y
                if (drag > 25.dp.toPx()) { currentMove(1); drag = 0f }
                if (drag < -25.dp.toPx()) { currentMove(-1); drag = 0f }
            }
        } else Modifier), verticalAlignment = Alignment.CenterVertically) {
            Logo(s, 42.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(s.ticker, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(compactQuantity(s, h.quantity) + if (showWeight) " · " +
                    percent(h.value?.let { v -> portfolioValue?.let { v.pct(it) } }) else "",
                    color = Muted, fontSize = 10.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.width(50.dp).height(22.dp), contentAlignment = Alignment.Center) {
            if (points.size > 1) SmallSparkline(points,
                if (points.last() > points.first()) Green
                else if (points.last() < points.first()) Red else Muted)
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.width(116.dp), horizontalAlignment = Alignment.End) {
            Text(money(h.value), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1)
            Text("${signed(h.day)}  ${percent(h.dayPercent)}", fontSize = 10.sp,
                color = tint(h.day), maxLines = 1)
            if (extended != null)
                Text("${extended.label}  ${number(extended.price)}  ${percent(extended.changePercent)}",
                    color = Muted, fontSize = 9.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(15.dp), tint = Muted)
    }
    HorizontalDivider(color = Border.copy(alpha = .45f))
}

@Composable
private fun SmallSparkline(values: List<java.math.BigDecimal>, color: androidx.compose.ui.graphics.Color) {
    val low = values.minOf { it.toDouble() }
    val span = (values.maxOf { it.toDouble() } - low).takeIf { it > 0 } ?: 1.0
    Canvas(Modifier.fillMaxSize()) {
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index.toFloat() / (values.size - 1) * size.width
            val y = size.height * (.85f - .7f * ((value.toDouble() - low) / span).toFloat())
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(1.6.dp.toPx()))
    }
}

@Composable
fun HistoryScreen(w: Wallet, onEdit: (Transaction) -> Unit, onDelete: (String) -> Unit) {
    var filter by remember { mutableIntStateOf(0) }
    var portfolio by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(0) }
    var from by remember { mutableStateOf("") }
    var until by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val filtered =
        w.transactions.filter { t ->
            (portfolio == null || t.portfolioId == portfolio) &&
                (filter == 0 ||
                    t.type ==
                        listOf(
                            TxType.BUY,
                            TxType.SELL,
                            TxType.DIVIDEND,
                            TxType.DEPOSIT,
                            TxType.WITHDRAWAL,
                            TxType.FEE,
                        )[filter - 1]) &&
                "${w.security(t.securityId)?.symbol} ${w.security(t.securityId)?.name} ${t.note}"
                    .contains(query, true) &&
                (from.isBlank() || t.date >= from) &&
                (until.isBlank() || t.date <= until)
        }
    val tx =
        when (sort) {
            1 -> filtered.sortedBy { it.date }
            2 -> filtered.sortedBy { w.security(it.securityId)?.symbol }
            3 -> filtered.sortedByDescending { it.cad }
            4 -> filtered.sortedByDescending { it.price }
            else ->
                filtered.sortedWith(
                    compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt }
                )
        }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Chips(
                listOf(
                    R.string.all_transactions, R.string.tx_buys, R.string.tx_sells,
                    R.string.tx_dividends, R.string.tx_deposits, R.string.tx_withdrawals,
                    R.string.tx_fees,
                ).map { stringResource(it) },
                filter,
            ) {
                filter = it
            }
            TextEntry(stringResource(R.string.ui_rechercher_un_titre_ou_une_note_9d774), query, { query = it })
            Row {
                TextButton(onClick = { advanced = !advanced }) { Text(stringResource(R.string.ui_filtres_et_tri_603f2)) }
                Text(
                    stringResource(R.string.transaction_count, tx.size),
                    Modifier.padding(12.dp),
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
            if (advanced) {
                Choice(
                    stringResource(R.string.ui_portefeuille_c2303),
                    w.portfolios.find { it.id == portfolio }?.name ?: stringResource(R.string.all_portfolios),
                    listOf(stringResource(R.string.all_portfolios)) + w.portfolios.map { it.name },
                ) {
                    portfolio = if (it == 0) null else w.portfolios[it - 1].id
                }
                val sorts = listOf(
                    R.string.sort_recent, R.string.sort_old, R.string.sort_ticker,
                    R.string.sort_amount, R.string.sort_price,
                ).map { stringResource(it) }
                Choice(stringResource(R.string.ui_trier_a7e4c), sorts[sort], sorts) { sort = it }
                TextEntry(stringResource(R.string.ui_du_aaaa_mm_jj_d7352), from, { from = it })
                TextEntry(stringResource(R.string.ui_au_aaaa_mm_jj_dac26), until, { until = it })
            }
        }
        if (tx.isEmpty())
            item {
                Empty(
                    stringResource(R.string.ui_aucune_transaction_02bb0),
                    stringResource(R.string.history_empty),
                )
            }
        items(tx, key = { it.id }) { t ->
            var menu by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().clickable { onEdit(t) }.padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val sec = w.security(t.securityId)
                    if (sec != null) Logo(sec) else Icon(Icons.Outlined.Payments, null, tint = Blue)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sec?.ticker ?: transactionLabel(t.type), fontWeight = FontWeight.Bold)
                        Caption(
                            "${date(t.date)} · ${w.portfolios.find{it.id==t.portfolioId}?.name}"
                        )
                        Caption(
                            "${transactionLabel(t.type)} · ${number(t.quantity,6)} × ${money(t.price,t.currency)}"
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(t.total, t.currency), fontSize = 14.sp)
                        Caption(money(t.cad))
                    }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.nav_options))
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_modifier_f260e)) },
                                onClick = {
                                    menu = false
                                    onEdit(t)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_supprimer_1acfc), color = Red) },
                                onClick = {
                                    menu = false
                                    deleting = t.id
                                },
                            )
                        }
                    }
                }
                if (t.note.isNotBlank()) Caption(t.note, Modifier.padding(top = 5.dp))
            }
            HorizontalDivider(color = Border)
        }
    }
    deleting?.let { id ->
        Confirm(
            stringResource(R.string.ui_supprimer_la_transaction_3f93c),
            stringResource(R.string.delete_transaction_caption),
            { deleting = null },
        ) {
            onDelete(id)
        }
    }
}

