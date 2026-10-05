package ca.monwallet.app.ui

import ca.monwallet.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import ca.monwallet.app.domain.*
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
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
            CardBlock {
                Caption(
                    if (selected == null) stringResource(R.string.all_portfolios_heading)
                    else w.portfolios.find { it.id == selected }?.name.orEmpty()
                )
                Spacer(Modifier.height(7.dp))
                val estimatedSession = if (w.settings["portfolio_extended"] == "LAST")
                    result?.holdings?.mapNotNull { h -> w.quotes[h.securityId]?.let { q ->
                        when {
                            q.marketSession == "PRE_MARKET" && q.preMarketPrice != null -> "Pre-market"
                            q.marketSession == "AFTER_HOURS" && q.afterHoursPrice != null -> "After-hours"
                            else -> null
                        }
                    } }?.distinct()?.singleOrNull() else null
                Caption(estimatedSession?.let { "Valeur estimée · $it" }
                    ?: stringResource(R.string.ui_valeur_actuelle_e0c0f))
                Text(money(result?.value), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    Metric(
                        stringResource(R.string.ui_capital_net_investi_59ad4),
                        money(result?.invested),
                        modifier = Modifier.weight(1f),
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Caption(stringResource(R.string.ui_gain_total_e7887))
                        Text(
                            signed(result?.pnl),
                            color = tint(result?.pnl),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(percent(result?.percent), color = tint(result?.pnl), fontSize = 12.sp)
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
        else if (result.holdings.none { it.quantity > ZERO })
            item {
                Empty(
                    stringResource(R.string.ui_ton_portefeuille_commence_ici_a7ba7),
                    stringResource(R.string.portfolio_empty),
                    stringResource(R.string.add_purchase),
                    onBuy,
                )
            }
        else
            items(result.holdings.filter { it.quantity > ZERO }, key = { it.securityId }) { h ->
                w.security(h.securityId)?.let { s -> HoldingRow(w, s, h, compact, result.value) { onDetail(s) } }
            }
        item {
            Caption(
                stringResource(R.string.ui_rendement_simple_en_cad_incluant_lencaisse_le_0b7d1)
            )
        }
    }
}

@Composable
fun HoldingRow(w: Wallet, s: Security, h: Holding, compact: Boolean, portfolioValue: java.math.BigDecimal?, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = if (compact) 5.dp else 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(s)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.ticker, fontWeight = FontWeight.Bold)
                if (compact) Text(s.name, fontSize = 11.sp, color = Muted, maxLines = 1)
                Caption(stringResource(R.string.shares_currency, number(h.quantity, 6), s.currency))
            }
            if (!compact)
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
                if (compact) Caption(stringResource(R.string.position_weight, percent(h.value?.let { v -> portfolioValue?.let { v.pct(it) } })))
            }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = Muted)
        }
        if (!compact) {
            Row(
                Modifier.fillMaxWidth().padding(start = 44.dp, top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Caption(s.name)
                Text(stringResource(R.string.day_value, signed(h.day)), fontSize = 11.sp, color = tint(h.day))
            }
        }
    }
    HorizontalDivider(color = Border)
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

@Composable
fun ReportsScreen(w: Wallet) {
    val r = runCatching { w.result() }.getOrNull()
    var allocationMode by remember { mutableIntStateOf(0) }
    var includeCash by remember { mutableStateOf(false) }
    val securities = w.securities.associateBy { it.id }
    val titleAllocation = r?.let { Allocation.byTitle(it, securities, includeCash) }
    val allocation = r?.let {
        if (allocationMode == 0) titleAllocation
        else if (allocationMode == 1) Allocation.bySector(it, securities, includeCash = includeCash)
        else null
    }
    val months =
        w.transactions
            .filter { it.type == TxType.BUY }
            .groupBy { it.date.take(7) }
            .mapValues { it.value.fold(ZERO) { a, t -> a + t.cad } }
            .toSortedMap()
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Section(stringResource(R.string.ui_vue_densemble_de0d9))
            CardBlock {
                Metric(stringResource(R.string.ui_capital_net_investi_59ad4), money(r?.invested))
                Metric(stringResource(R.string.ui_valeur_actuelle_e0c0f), money(r?.value))
                Metric(stringResource(R.string.ui_gain_perte_82fa5), signed(r?.pnl), tint(r?.pnl))
                Metric(stringResource(R.string.ui_rendement_simple_34243), percent(r?.percent), tint(r?.pnl))
                Metric(stringResource(R.string.ui_p_l_realise_f3b05), signed(r?.realized), tint(r?.realized))
                Metric(stringResource(R.string.ui_dividendes_recus_56af9), money(r?.dividends))
                Metric(stringResource(R.string.ui_p_l_du_jour_e22e7), signed(r?.day), tint(r?.day))
            }
        }
        item {
            Section(stringResource(R.string.allocation_heading))
            CardBlock {
                Chips(listOf(R.string.allocation_titles, R.string.allocation_sectors, R.string.allocation_geography,
                    R.string.allocation_currencies, R.string.allocation_assets).map { stringResource(it) }, allocationMode) {
                    allocationMode = it
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = includeCash, onCheckedChange = { includeCash = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.include_cash))
                }
                if (allocation?.complete == true && allocation.total != null) {
                    AllocationDonut(allocation)
                } else Caption(stringResource(if (allocationMode == 1) R.string.sector_allocation_unavailable else R.string.allocation_unavailable))
                if (allocationMode == 0 && titleAllocation?.complete == true) {
                    val positions = titleAllocation.slices.filter { it.label != "Cash" }
                    val max = positions.firstOrNull()
                    if (max != null) {
                        Metric(stringResource(R.string.largest_position), "${max.label} · ${percent(max.weight)}")
                        Metric(stringResource(R.string.top_three), percent(positions.take(3).fold(ZERO) { a, p -> a + p.weight }))
                        Metric(stringResource(R.string.top_five), percent(positions.take(5).fold(ZERO) { a, p -> a + p.weight }))
                    }
                }
            }
        }
        item { Section(stringResource(R.string.ui_performance_par_portefeuille_ed770)) }
        items(w.portfolios) { p ->
            val value = runCatching { w.result(p.id) }.getOrNull()
            CardBlock {
                Text(p.name, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric(stringResource(R.string.ui_valeur_68f92), money(value?.value))
                    Metric(stringResource(R.string.ui_gain_96dd9), signed(value?.pnl), tint(value?.pnl))
                }
            }
        }
        item { Section(stringResource(R.string.ui_achats_par_mois_c875a)) }
        items(months.entries.toList()) { entry ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.key)
                Text(money(entry.value))
            }
        }
        item { Section(stringResource(R.string.ui_performance_et_repartition_par_titre_48bff)) }
        items(r?.holdings?.filter { it.quantity > ZERO } ?: emptyList()) { h ->
            val s = w.security(h.securityId)
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(s?.ticker.orEmpty())
                    Text(percent(h.percent), color = tint(h.pnl))
                }
                Caption(stringResource(R.string.value_fx, money(h.value), signed(h.fxGain)))
                val part = h.value?.let { v -> r?.value?.let { v.divSafe(it) } }
                if (part != null)
                    LinearProgressIndicator(
                        progress = { part.toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
            }
        }
        item {
            Caption(
                stringResource(R.string.ui_les_repartitions_sectorielle_et_geographique__48bad)
            )
        }
    }
}
