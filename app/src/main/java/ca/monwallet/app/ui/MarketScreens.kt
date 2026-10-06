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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import ca.monwallet.app.data.Catalog
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.*
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private data class WatchFacts(val fundamentals: Fundamentals? = null, val analyst: Analyst? = null) {
    fun metric(label: String) = fundamentals?.metrics?.get(label)?.toBigDecimalOrNull()
    fun cap() = metric("Capitalisation (M)")?.multiply(BigDecimal("1000000"))
    fun pe(price: BigDecimal?) = metric("BPA")?.takeIf { it.signum() > 0 && price != null }
        ?.let { price!!.divide(it, MC) }
    fun dividend() = metric("Rendement dividende %")
}

@Composable
fun QuoteRow(w: Wallet, s: Security, onClick: () -> Unit, menu: (@Composable () -> Unit)? = null) {
    val q = w.quotes[s.id]
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo(s)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.ticker, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (w.alerts.any { it.securityId == s.id && it.enabled })
                    Icon(
                        Icons.Outlined.Notifications,
                        stringResource(R.string.watchlist_alert_active),
                        Modifier.padding(start = 4.dp).size(12.dp),
                        tint = Blue,
                    )
                if (w.transactions.any { it.securityId == s.id })
                    Icon(
                        Icons.Outlined.AccountBalanceWallet,
                        stringResource(R.string.watchlist_holding_history),
                        Modifier.padding(start = 4.dp).size(12.dp),
                        tint = Muted,
                    )
            }
            Text(
                s.name,
                color = Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Caption("${s.exchange} · ${s.currency}")
        }
        val history = w.prices.filter { it.securityId == s.id }.takeLast(24)
        if (history.size > 1)
            Chart(
                history.map { it.close },
                modifier = Modifier.width(42.dp).height(23.dp),
                color = tint(q?.change),
            )
        Column(
            Modifier.widthIn(min = 90.dp).padding(start = 6.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(number(q?.price), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(signed(q?.change, s.currency), color = tint(q?.change), fontSize = 11.sp)
            Text(percent(q?.percent), color = tint(q?.change), fontSize = 11.sp)
        }
        menu?.invoke()
    }
    HorizontalDivider(color = Border)
}

@Composable
fun WatchlistScreen(
    w: Wallet,
    vm: WalletViewModel,
    onDetail: (Security) -> Unit,
    onBuy: (Security) -> Unit,
    onAlert: (Security) -> Unit,
    onAdd: (String) -> Unit,
) {
    var selected by remember { mutableStateOf<String?>(null) }
    val list = w.watchlists.find { it.id == selected } ?: w.watchlists.firstOrNull()
    var naming by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var choose by remember { mutableStateOf<Pair<Security, WatchItem?>?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("watchlist_options", 0) }
    var samePeriod by remember { mutableStateOf(prefs.getBoolean("same_period", false)) }
    var period by remember(list?.id, samePeriod) {
        mutableStateOf(runCatching { PerformancePeriod.valueOf(prefs.getString(
            if (samePeriod) "global_period" else "period:" + list?.id, "M3") ?: "M3") }
            .getOrDefault(PerformancePeriod.M3))
    }
    var columns by remember { mutableStateOf(MarketColumns.restore(
        prefs.getString("columns", null), MarketColumns.watchlistDefault)
        .filter { it != "extended" }.ifEmpty { MarketColumns.watchlistDefault }) }
    var showColumns by remember { mutableStateOf(false) }
    var followSpark by remember { mutableStateOf(prefs.getBoolean("follow_spark", true)) }
    var showName by remember { mutableStateOf(prefs.getBoolean("show_name", false)) }
    var showExtended by remember { mutableStateOf(prefs.getBoolean("show_extended", true)) }
    var sortKey by remember(list?.id) { mutableStateOf(prefs.getString("sort:" + list?.id, "manual") ?: "manual") }
    var descending by remember { mutableStateOf(true) }
    val horizontal = rememberScrollState()
    val items = w.items.filter { it.watchlistId == list?.id }.sortedBy { it.order }
    val securities = items.mapNotNull { w.security(it.securityId) }
    var facts by remember(list?.id) { mutableStateOf<Map<String, WatchFacts>>(emptyMap()) }
    LaunchedEffect(list?.id, securities, columns) {
        val financialKeys = setOf("cap", "pe", "forwardPe", "dividend")
        val analystKeys = setOf("upside", "target", "consensus")
        val needFinance = columns.any { it in financialKeys }
        val needAnalyst = columns.any { it in analystKeys }
        if (needFinance || needAnalyst) {
            val gate = Semaphore(2)
            val fresh = supervisorScope {
                securities.take(20).map { security -> async {
                    gate.withPermit {
                        val old = facts[security.id] ?: WatchFacts()
                        suspend fun <T> available(load: suspend () -> T): T? = try { load() }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { null }
                        security.id to WatchFacts(
                            if (needFinance) available { vm.services.market.fundamentals(security) }
                                ?: old.fundamentals else old.fundamentals,
                            if (needAnalyst) available { vm.services.market.analyst(security) }
                                ?: old.analyst else old.analyst,
                        )
                    }
                } }.awaitAll().toMap()
            }
            facts = facts + fresh
        }
    }
    DisposableEffect(list?.id, securities) {
        vm.services.foregroundSecurities.value = securities.take(20)
        onDispose { vm.services.foregroundSecurities.value = emptyList() }
    }
    LaunchedEffect(list?.id, period, columns) {
        if (period == PerformancePeriod.Y5 || columns.any { it == "5A" || it == "cagr5" }) {
            securities.forEach { security ->
                val history = w.prices.filter { it.securityId == security.id }
                if (MomentumEngine.performance(history, PerformancePeriod.Y5) == null)
                    runCatching { vm.services.repo.points(security.id,
                        vm.services.market.history(security, "10y")) }
            }
        }
    }
    val orderedItems = remember(items, w.quotes, w.prices, facts, period, sortKey, descending) {
        val sorted = when (sortKey) {
            "ticker" -> items.sortedBy { w.security(it.securityId)?.ticker }
            "price" -> items.sortedBy { w.quotes[it.securityId]?.price }
            "dayPercent" -> items.sortedBy { w.quotes[it.securityId]?.percent }
            "cap" -> items.sortedBy { facts[it.securityId]?.cap() }
            "pe" -> items.sortedBy { facts[it.securityId]?.pe(w.quotes[it.securityId]?.price) }
            "dividend" -> items.sortedBy { facts[it.securityId]?.dividend() }
            "upside" -> items.sortedBy { i ->
                facts[i.securityId]?.analyst?.target?.let { target ->
                    w.quotes[i.securityId]?.price?.takeIf { it.signum() > 0 }
                        ?.let { (target - it).divide(it, MC) }
                }
            }
            "pre", "after" -> items.sortedBy { i ->
                w.quotes[i.securityId]?.takeIf { q ->
                    w.security(i.securityId)?.let(::supportsUsExtendedHours) == true
                }?.let { q ->
                    val normalized = NormalizedQuote.from(q)
                    if (sortKey == "pre") normalized.preMarketChangePercent
                    else normalized.afterHoursChangePercent
                }
            }
            "momentum" -> items.sortedBy { i -> MomentumEngine.analyze(
                w.prices.filter { it.securityId == i.securityId }, period,
                w.quotes[i.securityId]?.let { q -> q.averageVolume?.multiply(q.price)?.toDouble() })?.score }
            "period", "1S", "1M", "3M", "6M", "1A", "5A" -> {
                val selectedPeriod = PerformancePeriod.entries.firstOrNull { it.label == sortKey } ?: period
                items.sortedBy { item -> MomentumEngine.performance(
                    w.prices.filter { it.securityId == item.securityId }, selectedPeriod) }
            }
            else -> items
        }
        if (sortKey != "manual" && descending) sorted.reversed() else sorted
    }
    fun move(item: WatchItem, delta: Int) {
        if (sortKey != "manual") return
        val index = items.indexOf(item)
        val other = items.getOrNull(index + delta) ?: return
        vm.run {
            vm.services.repo.put("watch_item", item.id, item.copy(order = other.order))
            vm.services.repo.put("watch_item", other.id, other.copy(order = item.order))
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Chips(w.watchlists.map { it.name }, w.watchlists.indexOf(list)) {
                        selected = w.watchlists[it].id
                    }
                }
                IconButton(
                    onClick = {
                        editing = false
                        name = ""
                        naming = true
                    }
                ) {
                    Icon(Icons.Outlined.AddCircleOutline, stringResource(R.string.watchlist_new), tint = Blue)
                }
                Box {
                    IconButton(onClick = { options = true }) {
                        Icon(Icons.Outlined.MoreVert, stringResource(R.string.watchlist_options))
                    }
                    DropdownMenu(options, { options = false }) {
                        DropdownMenuItem(text = { Text("⚙ Colonnes") }, onClick = {
                            options = false; showColumns = true })
                        DropdownMenuItem(text = { Text("Même période pour toutes : " +
                            if (samePeriod) "Oui" else "Non") }, onClick = {
                            samePeriod = !samePeriod
                            prefs.edit().putBoolean("same_period", samePeriod).apply()
                            options = false
                        })
                        DropdownMenuItem(text = { Text("Sparkline suit la période : " +
                            if (followSpark) "Oui" else "Non") }, onClick = {
                            followSpark = !followSpark
                            prefs.edit().putBoolean("follow_spark", followSpark).apply(); options = false
                        })
                        DropdownMenuItem(text = { Text("Nom complet : " + if (showName) "Oui" else "Non") },
                            onClick = { showName = !showName
                                prefs.edit().putBoolean("show_name", showName).apply(); options = false })
                        MarketColumns.presets.forEach { (name, preset) ->
                            DropdownMenuItem(text = { Text("Preset : " + name) }, onClick = {
                                columns = preset
                                prefs.edit().putString("columns", preset.joinToString("|")).apply()
                                options = false
                            })
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_renommer_8e8a8)) },
                            enabled = list != null,
                            onClick = {
                                options = false
                                editing = true
                                name = list!!.name
                                naming = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_deplacer_a_gauche_02df9)) },
                            enabled = list != null,
                            onClick = {
                                options = false
                                list?.let { l ->
                                    vm.run {
                                        val sorted = w.watchlists.toMutableList()
                                        val i = sorted.indexOf(l)
                                        if (i > 0) {
                                            java.util.Collections.swap(sorted, i, i - 1)
                                            sorted.forEachIndexed { index, item ->
                                                vm.services.repo.put(
                                                    "watchlist",
                                                    item.id,
                                                    item.copy(order = index),
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_supprimer_la_liste_bff90)) },
                            enabled = list != null,
                            onClick = {
                                options = false
                                deleting = true
                            },
                        )
                    }
                }
            }
            PeriodPills(period) { selectedPeriod ->
                period = selectedPeriod
                prefs.edit().putString(if (samePeriod) "global_period" else "period:" + list?.id,
                    selectedPeriod.name).apply()
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("TRI", color = Muted, fontSize = 10.sp)
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    listOf("manual", "ticker", "price", "dayPercent", "period", "1S", "1M",
                        "3M", "6M", "1A", "5A", "cap", "pe", "dividend", "momentum",
                        "upside", "pre", "after").forEach { key ->
                        TextButton(onClick = {
                            if (sortKey == key) descending = !descending
                            else { sortKey = key; descending = true }
                            prefs.edit().putString("sort:" + list?.id, sortKey).apply()
                        }) { Text(when (key) { "manual" -> "Manuel"; "pre" -> "Pre %";
                            "after" -> "After %"; else -> MarketColumns.label(key, period) },
                            fontSize = 10.sp, color = if (sortKey == key) Green else Muted) }
                    }
                }
            }
            WatchHeader(columns, period, horizontal)
        }
        if (items.isEmpty())
            item {
                Empty(
                    stringResource(R.string.ui_ta_liste_en_un_coup_dil_cc0e0),
                    stringResource(R.string.watchlist_empty),
                    stringResource(if (list == null) R.string.watchlist_create else R.string.ui_ajouter_un_titre_bf42c),
                ) {
                    if (list == null) {
                        editing = false
                        name = ""
                        naming = true
                    } else onAdd(list.id)
                }
            }
        items(orderedItems, key = { it.id }) { item ->
            w.security(item.securityId)?.let { s ->
                var open by remember { mutableStateOf(false) }
                WatchLine(w, s, item, facts[s.id], period, columns, horizontal, followSpark, showName,
                    showExtended, { onDetail(s) }, { move(item, it) }) {
                    Box {
                        IconButton(onClick = { open = true }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.watchlist_actions), Modifier.size(18.dp))
                        }
                        DropdownMenu(open, { open = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_creer_une_alerte_e1be5)) },
                                onClick = {
                                    open = false
                                    onAlert(s)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_ajouter_au_portefeuille_1080f)) },
                                onClick = {
                                    open = false
                                    onBuy(s)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_ajouter_a_une_autre_liste_a8e15)) },
                                onClick = {
                                    open = false
                                    choose = s to null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_deplacer_dans_une_liste_cd26f)) },
                                onClick = {
                                    open = false
                                    choose = s to item
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_monter_dd1b7)) },
                                onClick = {
                                    open = false
                                    vm.run {
                                        move(item, -1)
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_retirer_54ec2)) },
                                onClick = {
                                    open = false
                                    vm.run { vm.services.repo.remove(item.id) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_voir_le_titre_0e845)) },
                                onClick = {
                                    open = false
                                    onDetail(s)
                                },
                            )
                        }
                    }
                }
            }
        }
        if (list != null && items.isNotEmpty())
            item {
                TextButton(onClick = { onAdd(list.id) }) {
                    Icon(Icons.Outlined.Add, null)
                    Text(stringResource(R.string.ui_ajouter_un_titre_bf42c))
                }
            }
        item {
            Caption("Cours actualisés au premier plan si la source répond · période et fraîcheur affichées sans données simulées.")
        }
    }
    if (showColumns) ColumnPicker(columns, MarketColumns.all.keys.filter {
        it != "opportunity" && it != "extended" }, {
        columns = it
        prefs.edit().putString("columns", it.joinToString("|")).apply()
    }, { showColumns = false }, extendedSetting = showExtended to { value ->
        showExtended = value
        prefs.edit().putBoolean("show_extended", value).apply()
    })
    if (naming)
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(if (editing) R.string.ui_renommer_8e8a8 else R.string.watchlist_new)) },
            text = { TextEntry(stringResource(R.string.ui_nom_7ff20), name, { name = it }) },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val new =
                            Watchlist(
                                if (editing) list!!.id else uuid(),
                                name.trim(),
                                if (editing) list!!.order else w.watchlists.size,
                            )
                        vm.run { vm.services.repo.put("watchlist", new.id, new) }
                        selected = new.id
                        naming = false
                    },
                ) {
                    Text(stringResource(R.string.ui_enregistrer_f7c8b))
                }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(R.string.ui_annuler_49ba3)) } },
        )
    if (deleting && list != null)
        Confirm(
            stringResource(R.string.watchlist_delete, list.name),
            stringResource(R.string.watchlist_delete_caption),
            { deleting = false },
        ) {
            vm.run { vm.services.repo.remove(list.id) }
        }
    choose?.let { (security, item) ->
        ChooseWatchlist(w, { choose = null }) { target ->
            vm.run("Liste mise à jour") {
                vm.services.repo.watch(security, target)
                if (item != null && item.watchlistId != target) vm.services.repo.remove(item.id)
            }
            choose = null
        }
    }
}

private fun watchWidth(key: String): Dp = when (key) {
    "ticker" -> 70.dp
    "price" -> 68.dp
    "sparkline" -> 58.dp
    "dayPercent" -> 64.dp
    "period", "1S", "1M", "3M", "6M", "1A", "5A" -> 70.dp
    else -> (MarketColumns.all[key]?.width ?: 86).dp
}

@Composable
private fun WatchHeader(columns: List<String>, period: PerformancePeriod,
    scroll: androidx.compose.foundation.ScrollState) {
    Row(Modifier.fillMaxWidth().height(25.dp), verticalAlignment = Alignment.CenterVertically) {
        if ("ticker" in columns) Text("TICKER", Modifier.width(watchWidth("ticker")),
            fontSize = 9.sp, color = Muted)
        Row(Modifier.weight(1f).horizontalScroll(scroll)) {
            columns.filter { it != "ticker" }.forEach { key ->
                Text(MarketColumns.label(key, period).uppercase(), Modifier.width(watchWidth(key)),
                    fontSize = 9.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(26.dp))
    }
    HorizontalDivider(color = Border)
}

@Composable
private fun WatchLine(w: Wallet, s: Security, item: WatchItem, facts: WatchFacts?, period: PerformancePeriod,
    columns: List<String>, scroll: androidx.compose.foundation.ScrollState,
    followSpark: Boolean, showName: Boolean, showExtended: Boolean,
    click: () -> Unit, move: (Int) -> Unit, menu: @Composable () -> Unit) {
    val q = w.quotes[s.id]
    val points = remember(w.prices, s.id) { w.prices.filter { it.securityId == s.id } }
    val selected = if (followSpark) period else PerformancePeriod.M1
    val spark = remember(points, selected) { MomentumEngine.series(points, selected)
        ?.map { it.close } ?: emptyList() }
    val extended = q?.takeIf { showExtended && supportsUsExtendedHours(s) }
        ?.let { NormalizedQuote.from(it) }
    val extra = when (extended?.marketSession) {
        MarketSession.PRE_MARKET -> extended.preMarketPrice
        MarketSession.AFTER_HOURS -> extended.afterHoursPrice
        else -> null
    }
    val extraDelta = when (extended?.marketSession) {
        MarketSession.PRE_MARKET -> extended.preMarketChange
        MarketSession.AFTER_HOURS -> extended.afterHoursChange
        else -> null
    }
    val extraPercent = when (extended?.marketSession) {
        MarketSession.PRE_MARKET -> extended.preMarketChangePercent
        MarketSession.AFTER_HOURS -> extended.afterHoursChangePercent
        else -> null
    }
    val extraFreshness = when (extended?.marketSession) {
        MarketSession.PRE_MARKET -> extended.preMarketFreshness
        MarketSession.AFTER_HOURS -> extended.afterHoursFreshness
        else -> null
    }
    var drag by remember(item.id) { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxWidth().clickable(onClick = click)) {
    Row(Modifier.fillMaxWidth().height(if (showName) 44.dp else 39.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if ("ticker" in columns) {
            Row(Modifier.width(watchWidth("ticker")).pointerInput(item.id) {
                detectDragGesturesAfterLongPress(onDragEnd = { drag = 0f },
                    onDragCancel = { drag = 0f }) { change, amount ->
                    change.consume()
                    drag += amount.y
                    if (drag > 22.dp.toPx()) { move(1); drag = 0f }
                    if (drag < -22.dp.toPx()) { move(-1); drag = 0f }
                }
            }, verticalAlignment = Alignment.CenterVertically) {
                Logo(s, 24.dp)
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(s.ticker, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (showName) Text(s.name, fontSize = 9.sp, color = Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else Text(s.exchange.take(5) +
                        (if (w.alerts.any { it.securityId == s.id && it.enabled }) " ◦" else "") +
                        (if (w.transactions.any { it.securityId == s.id }) " •" else ""),
                        fontSize = 8.sp, color = Muted, maxLines = 1)
                }
            }
        }
        Row(Modifier.weight(1f).horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
            columns.filter { it != "ticker" }.forEach { key ->
                val size = watchWidth(key)
                when (key) {
                    "sparkline" -> Box(Modifier.width(size).height(23.dp)) {
                        if (spark.size > 1) Chart(spark, modifier = Modifier.fillMaxSize(),
                            color = tint(q?.change))
                        else Text("—", fontSize = 10.sp, color = Muted)
                    }
                    "price" -> Column(Modifier.width(size)) {
                        Text(number(q?.price), fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        if (q != null) Text(when (NormalizedQuote.from(q).freshness) {
                            QuoteFreshness.REALTIME -> "Temps réel"
                            QuoteFreshness.DELAYED -> "Diff. ${q.delay} min"
                            QuoteFreshness.CACHED -> "Cache"
                            QuoteFreshness.STALE -> "Cache ancien"
                        }, fontSize = 8.sp, color = Muted, maxLines = 1)
                    }
                    "dayPercent" -> Column(Modifier.width(size)) {
                        Text(percent(q?.percent), fontSize = 10.sp, color = tint(q?.change),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    else -> {
                        val value = watchCell(w, s, q, points, key, period, extended, facts)
                        Text(value, Modifier.width(size), fontSize = 10.sp,
                            color = if (key == "period" || PerformancePeriod.entries.any {
                                    it.label == key }) tint(when {
                                value.startsWith("+") -> BigDecimal.ONE
                                value.startsWith("-") -> BigDecimal.ONE.negate()
                                else -> null
                            })
                                else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Box(Modifier.width(26.dp)) { menu() }
    }
    if (extra != null) {
        Row(Modifier.fillMaxWidth().height(17.dp)
            .padding(start = if ("ticker" in columns) watchWidth("ticker") else 0.dp,
                end = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (extended?.marketSession == MarketSession.PRE_MARKET) "☀ PRE" else "☾ AFTER",
                Modifier.width(56.dp), fontSize = 10.sp, color = Blue, maxLines = 1)
            Text(number(extra), Modifier.width(70.dp), fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .75f), maxLines = 1)
            Text(percent(extraPercent) + when (extraFreshness) {
                QuoteFreshness.DELAYED -> " · Diff."
                QuoteFreshness.STALE -> " · Ancien"
                QuoteFreshness.CACHED -> " · Cache"
                else -> ""
            },
                Modifier.weight(1f), fontSize = 10.sp, color = tint(extraDelta), maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
    }
    HorizontalDivider(color = Border.copy(alpha = .45f))
}

private fun watchCell(w: Wallet, s: Security, q: Quote?, points: List<Point>, key: String,
    period: PerformancePeriod, extended: NormalizedQuote?, facts: WatchFacts?): String = when (key) {
    "name" -> s.name
    "exchange" -> s.exchange
    "currency" -> s.currency
    "dayAmount" -> signed(q?.change, s.currency)
    "period" -> percent(MomentumEngine.performance(points, period))
    "1S", "1M", "3M", "6M", "1A", "5A" -> percent(MomentumEngine.performance(
        points, PerformancePeriod.entries.first { it.label == key }))
    "cagr5" -> percent(MomentumEngine.analyze(points, PerformancePeriod.Y5,
        q?.averageVolume?.multiply(q.price)?.toDouble())?.cagr5y)
    "momentum" -> MomentumEngine.analyze(points, period,
        q?.averageVolume?.multiply(q.price)?.toDouble())?.score?.toString() ?: "—"
    "cap" -> facts?.cap()?.let { number(it.divide(BigDecimal("1000000000"), MC), 1) + " G" } ?: "—"
    "pe" -> number(facts?.pe(q?.price))
    "dividend" -> percent(facts?.dividend())
    "target" -> money(facts?.analyst?.target, s.currency)
    "upside" -> facts?.analyst?.target?.let { target ->
        q?.price?.takeIf { it.signum() > 0 }?.let { price ->
            percent((target - price).multiply(BigDecimal(100)).divide(price, MC)) }
    } ?: "—"
    "consensus" -> facts?.analyst?.let { analyst ->
        val buy = analyst.buy ?: 0
        val hold = analyst.hold ?: 0
        val sell = analyst.sell ?: 0
        if (buy > hold + sell) "Buy" else if (sell > buy + hold) "Sell" else "Hold"
    } ?: "—"
    "volume" -> number(q?.volume, 0)
    "avgVolume" -> number(q?.averageVolume, 0)
    "high52" -> money(q?.high52, s.currency)
    "extended" -> when (extended?.marketSession) {
        MarketSession.PRE_MARKET -> extended.preMarketPrice?.let { "PRE " + number(it) }
        MarketSession.AFTER_HOURS -> extended.afterHoursPrice?.let { "AFTER " + number(it) }
        else -> null
    } ?: "—"
    "portfolioWeight" -> w.result().holdings.find { it.securityId == s.id }?.let {
        if (w.result().value?.signum() == 1 && it.value != null)
            percent(it.value.multiply(BigDecimal(100)).divide(w.result().value!!, MC)) else "—"
    } ?: "—"
    // The provider does not supply these values for every watchlist symbol. Never invent them.
    else -> "—"
}

@Composable
fun ChooseWatchlist(w: Wallet, onClose: () -> Unit, onChoose: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.ui_choisir_une_watchlist_751e9)) },
        text = {
            Column {
                if (w.watchlists.isEmpty()) Caption(stringResource(R.string.ui_cree_une_liste_dans_watchlist_7260e))
                w.watchlists.forEach { list ->
                    TextButton(
                        onClick = { onChoose(list.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(list.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.ui_fermer_5ab4e)) } },
    )
}

@Composable
fun MarketsScreen(w: Wallet, vm: WalletViewModel, onDetail: (Security) -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    val markets =
        Catalog.markets
            .filter { it.symbol != "CAD=X" }
            .filter {
                when (selected) {
                    1 -> it.type == "INDEX"
                    2 -> it.type == "FX"
                    3 -> it.type == "COMMODITY"
                    4 -> it.type == "CRYPTO"
                    else -> true
                }
            }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Chips(listOf("Aperçu", "Indices", "Devises", "Matières", "Crypto"), selected) {
                selected = it
            }
        }
        items(markets.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                pair.forEach { s ->
                    val q = w.quotes[s.id]
                    Column(
                        Modifier.weight(1f)
                            .background(
                                Panel,
                                androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                            )
                            .clickable { onDetail(s) }
                            .padding(12.dp)
                    ) {
                        Caption(s.name)
                        Text(number(q?.price), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${signed(q?.change,s.currency)} (${percent(q?.percent)})",
                            color = tint(q?.change),
                            fontSize = 10.sp,
                        )
                        Chart(
                            w.prices.filter { it.securityId == s.id }.takeLast(30).map { it.close },
                            modifier = Modifier.height(36.dp),
                            color = tint(q?.change),
                        )
                        Caption(q?.let { time(it.timestamp) } ?: "Aucun cours")
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            Caption(
                stringResource(R.string.ui_les_marches_affichent_des_indices_et_des_inst_47f49)
            )
            Section(stringResource(R.string.ui_secteurs_etf_de_reference_b755b), "Actualiser") {
                vm.run { vm.services.refresh(Catalog.sectors) }
            }
            Caption(stringResource(R.string.ui_variation_des_etf_sectoriels_americains_et_no_cf45d))
        }
        items(Catalog.sectors) { s ->
            val q = w.quotes[s.id]
            Row(
                Modifier.fillMaxWidth().clickable { onDetail(s) }.padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(s.name, fontSize = 14.sp)
                Text(percent(q?.percent), color = tint(q?.change), fontSize = 14.sp)
            }
            HorizontalDivider(color = Border)
        }
    }
}
