@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ca.monwallet.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import ca.monwallet.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val benchmarkNames = linkedMapOf(
    "^GSPC" to "S&P 500", "^IXIC" to "NASDAQ", "^GSPTSE" to "TSX", "XEQT.TO" to "XEQT")
private val benchmarkColors = listOf(Blue, Color(0xFF9F76FF), Color(0xFFFFA04D), Color(0xFFFF70A6))

@Composable
fun ReportsScreen(w: Wallet, vm: WalletViewModel, initialPortfolio: String?) {
    val saved = w.settings["reports_portfolio"]
    var selected by remember(saved, initialPortfolio) { mutableStateOf(
        if (saved == "all") null else saved?.takeIf { id -> w.portfolios.any { it.id == id } }
            ?: initialPortfolio) }
    var reportNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val reportDate = MarketClock.date(reportNow)
    val snapshots by produceState(emptyList<PortfolioDailySnapshot>(), w.transactions, w.prices,
        w.securities, selected, reportDate) {
        value = withContext(Dispatchers.Default) { runCatching {
            ReportEngine.history(w, selected)
        }.getOrDefault(emptyList()) }
    }
    val status by vm.services.reportHistoryStatus.collectAsState()
    var loading by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(selected, w.transactions, lifecycle) {
        loading = true
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                reportNow = System.currentTimeMillis()
                try { vm.services.refreshReportHistory(selected) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { vm.services.reportHistoryStatus.value =
                    "Historique indisponible : ${error.message ?: "erreur inconnue"}" }
                finally { loading = false }
                delay(60_000)
            }
        }
    }
    val regularHistory = TradeReports.confirmedHistory(snapshots, reportNow)
    ReportsDashboard(w, snapshots, selected, loading, status, now = reportNow,
        onPortfolio = { id -> selected = id
            vm.run { vm.services.repo.setting("reports_portfolio", id ?: "all") } },
        onPreference = { key, value -> vm.run { vm.services.repo.setting(key, value) } },
        onRefresh = { vm.run { vm.services.refreshReportHistory(selected, true) } },
        advanced = { summary ->
            var mode by remember { mutableIntStateOf(0) }
            val current = remember(w.transactions, w.quotes, selected) {
                runCatching { w.result(selected) }.getOrNull() }
            ReportHero(summary, current, mode) { mode = it }
            ReportMetrics(summary, current)
            var benchmarks by remember { mutableStateOf(benchmarkNames.keys.toSet()) }
            Comparison(w, summary, benchmarks) { symbol ->
                benchmarks = if (symbol in benchmarks) benchmarks - symbol else benchmarks + symbol }
            ReportStatistics(summary, regularHistory)
            ReportContributors(w, summary)
            ReportMonthYear(regularHistory)
            ReportAllocation(w, selected, current, vm.services::etfDetails)
            ReportFlows(w, selected, summary, regularHistory)
        })
}

@Composable
private fun ReportHero(summary: ReportSummary, current: Result?, mode: Int, onMode: (Int) -> Unit) {
    CardBlock {
        Caption("Rendement du portefeuille · " + if (summary.partialPeriod)
            "depuis le premier achat (${summary.range.label} partielle)" else summary.range.label)
        if (!summary.complete) {
            Spacer(Modifier.height(6.dp))
            Text(summary.reason ?: "Historique insuffisant", color = Muted,
                fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            if (current?.pnl != null && current.invested > ZERO) {
                Spacer(Modifier.height(8.dp))
                Caption("Profit total depuis les achats · cours actuels")
                Text("${signed(current.pnl)}  ·  ${percent(current.percent)}",
                    color = tint(current.pnl), fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold)
            }
            Caption("Le rendement de la période attend les clôtures et les taux FX manquants.")
            return@CardBlock
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(percent(summary.performance), color = tint(summary.performance),
                fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Text(signed(summary.gain), color = tint(summary.gain), fontWeight = FontWeight.SemiBold)
        }
        Chips(listOf("Performance %", "Gain / perte $", "Valeur vs capital"), mode, onMode)
        val base = summary.base!!
        val gain = summary.days.map { day -> day.closingValue?.let { value ->
            value - base.closingValue!! - (day.capitalInvested - base.capitalInvested)
        } }
        val dates = listOf(base.date) + summary.days.map { it.date }
        val values = when (mode) {
            0 -> summary.cumulative.map { it.second }
            1 -> listOf(ZERO) + gain
            else -> listOf(base.closingValue) + summary.days.map { it.closingValue }
        }
        val capital = if (mode == 2) listOf(base.capitalInvested) + summary.days.map { it.capitalInvested }
            else emptyList()
        var width by remember { mutableIntStateOf(1) }
        var inspected by remember(mode, summary) { mutableIntStateOf(-1) }
        Chart(values, capital,
            modifier = Modifier.fillMaxWidth().height(190.dp).onSizeChanged { width = it.width }
                .pointerInput(values) { detectTapGestures { offset ->
                    inspected = ((offset.x / width) * (values.size - 1)).toInt().coerceIn(0, values.lastIndex)
                } },
            color = tint(summary.gain), labels = true)
        if (inspected in dates.indices) {
            val snap = summary.days.getOrNull(inspected - 1) ?: base
            Caption("${date(dates[inspected].toString())} · ${percent(summary.cumulative.getOrNull(inspected)?.second)}" +
                " · P&L ${signed(gain.getOrNull(inspected - 1) ?: ZERO)}" +
                " · Valeur ${money(snap.closingValue)} · Capital ${money(snap.capitalInvested)}")
        }
        Caption(if (mode == 2) "Valeur : ligne pleine · Capital net : pointillé"
            else "Toucher la courbe pour inspecter une date")
    }
}

@Composable
private fun ReportMetrics(summary: ReportSummary, current: Result?) {
    CardBlock {
        val rows = listOf(
            listOf(Triple("Rendement période", percent(summary.performance), tint(summary.performance)),
                Triple("Gain / perte", signed(summary.gain), tint(summary.gain)),
                Triple("Valeur actuelle", money(current?.value), Color.White)),
            listOf(Triple("Capital net investi", money(current?.invested), Color.White),
                Triple("Dividendes période", money(summary.dividends), Color.White),
                Triple("Max drawdown", percent(summary.maxDrawdown), tint(summary.maxDrawdown))))
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value, color) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, color = Muted, fontSize = 10.sp, maxLines = 2)
                        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (summary.complete) {
            val last = summary.days.last()
            val base = summary.base!!
            val realizedChange = last.realizedPnl - base.realizedPnl
            val unrealizedChange = last.unrealizedPnl?.let { end -> base.unrealizedPnl?.let { end - it } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Gain réalisé", signed(realizedChange), tint(realizedChange))
                Metric("Gain non réalisé", signed(unrealizedChange), tint(unrealizedChange))
            }
            Caption("Réalisé et non réalisé : variations sur la période; dividendes et frais sont comptés séparément.")
        }
        if (summary.complete) Caption("Rapport arrêté au ${date(summary.days.last().date.toString())} (dernière clôture disponible).")
    }
}

@Composable
private fun Comparison(wallet: Wallet, summary: ReportSummary, active: Set<String>,
    onToggle: (String) -> Unit) {
    CardBlock {
        Text("Comparaison", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Caption("Indices en CAD · cours de clôture, dividendes exclus · départ à 0 %")
        if (!summary.complete) { Caption("Historique insuffisant"); return@CardBlock }
        val base = summary.base!!.date
        val end = summary.days.last().date
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            benchmarkNames.entries.forEachIndexed { index, (symbol, name) ->
                val selected = symbol in active
                val performance = ReportEngine.benchmark(wallet, symbol, base, end)
                Column(Modifier.width(116.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Border else Night).clickable { onToggle(symbol) }
                    .padding(9.dp)) {
                    Text("${if (selected) "●" else "○"} $name", color = benchmarkColors[index], fontSize = 12.sp)
                    Text(if (selected) percent(performance) else "Masqué",
                        color = if (performance == null) Muted else tint(performance),
                        fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    if (selected && performance != null) {
                        val samples = summary.days.filterIndexed { i, _ ->
                            i % (summary.days.size / 24).coerceAtLeast(1) == 0
                        }.takeLast(24)
                        Chart(listOf(ZERO) + samples.map { ReportEngine.benchmark(wallet, symbol, base, it.date) },
                            modifier = Modifier.height(32.dp), color = benchmarkColors[index])
                    }
                }
            }
        }
        val sp = ReportEngine.benchmark(wallet, "^GSPC", base, end)
        if (sp != null && summary.performance != null)
            Caption("Écart vs S&P 500 : ${percent(summary.performance - sp)} points")
    }
}

@Composable
private fun ReportStatistics(summary: ReportSummary, history: List<PortfolioDailySnapshot>) {
    CardBlock {
        Text("Statistiques", fontWeight = FontWeight.SemiBold)
        if (!summary.complete) { Caption(summary.reason ?: "Historique insuffisant"); return@CardBlock }
        Metric("Meilleur jour", "${summary.bestDay?.date?.let { date(it.toString()) } ?: "—"} · ${signed(summary.bestDay?.dailyPnl)} · ${percent(summary.bestDay?.dailyReturn)}", Green)
        Metric("Pire jour", "${summary.worstDay?.date?.let { date(it.toString()) } ?: "—"} · ${signed(summary.worstDay?.dailyPnl)} · ${percent(summary.worstDay?.dailyReturn)}", Red)
        Metric("Drawdown actuel", percent(summary.currentDrawdown), Red)
        Metric("Rendement personnel annualisé (MWR / XIRR)", percent(summary.personalReturn), tint(summary.personalReturn))
        Caption("Le rendement personnel tient compte du moment des apports et retraits; le rendement de la courbe neutralise leurs montants.")
        val months = history.drop(1).groupBy { YearMonth.from(it.date) }
            .filterValues { days -> days.any { it.dailyReturn != null } &&
                days.none { it.closingValue == null || (it.hasMarketClose && it.dailyReturn == null) } }
            .mapValues { (_, days) ->
                days.mapNotNull { it.dailyReturn }.fold(ONE) { acc, daily ->
                    acc * (ONE + daily.divide(BigDecimal(100), MC)) }
            }
        if (months.isNotEmpty()) {
            val best = months.maxByOrNull { it.value }
            val worst = months.minByOrNull { it.value }
            Metric("Meilleur mois", "${best?.key ?: "—"} · ${percent(best?.value?.minus(ONE)?.times(BigDecimal(100)))}", Green)
            Metric("Pire mois", "${worst?.key ?: "—"} · ${percent(worst?.value?.minus(ONE)?.times(BigDecimal(100)))}", Red)
        }
    }
}

@Composable
private fun ReportContributors(wallet: Wallet, summary: ReportSummary) {
    CardBlock {
        Text("Contribution par titre", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        if (!summary.complete) { Caption("Historique insuffisant"); return@CardBlock }
        summary.contributions.forEach { contribution ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(wallet.security(contribution.securityId)?.ticker ?: "Titre indisponible")
                Text(signed(contribution.pnl), color = tint(contribution.pnl))
            }
            HorizontalDivider(color = Border, thickness = .5.dp)
        }
        if (summary.contributions.isEmpty()) Caption("Aucun titre sur cette période")
        Caption("Valeur fin − valeur début − achats + ventes + dividendes. Frais généraux à part.")
    }
}

@Composable
private fun ReportMonthYear(history: List<PortfolioDailySnapshot>) {
    val days = history.drop(1)
    if (days.isEmpty()) return
    CardBlock {
        Text("Performance mensuelle", fontWeight = FontWeight.SemiBold)
        days.groupBy { YearMonth.from(it.date) }.toSortedMap(reverseOrder()).entries.take(12).forEach { (month, list) ->
            val valid = list.any { it.dailyReturn != null } &&
                list.none { it.closingValue == null || (it.hasMarketClose && it.dailyReturn == null) }
            val factor = list.mapNotNull { it.dailyReturn }.fold(ONE) { a, r ->
                a * (ONE + r.divide(BigDecimal(100), MC)) }
            val change = if (valid) (factor - ONE) * BigDecimal(100) else null
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${month.month.getDisplayName(TextStyle.FULL, Locale.FRENCH)} ${month.year}")
                Text(if (valid) "${percent(change)} · ${signed(list.mapNotNull { it.dailyPnl }.fold(ZERO, BigDecimal::add))}"
                    else "Historique insuffisant", color = tint(change), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Performance annuelle", fontWeight = FontWeight.SemiBold)
        days.groupBy { it.date.year }.toSortedMap(reverseOrder()).forEach { (year, list) ->
            val valid = list.any { it.dailyReturn != null } &&
                list.none { it.closingValue == null || (it.hasMarketClose && it.dailyReturn == null) }
            val factor = list.mapNotNull { it.dailyReturn }.fold(ONE) { a, r ->
                a * (ONE + r.divide(BigDecimal(100), MC)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$year${if (year == LocalDate.now().year) " YTD" else ""}")
                Text(if (valid) percent((factor - ONE) * BigDecimal(100)) else "Historique insuffisant")
            }
        }
    }
}

@Composable
internal fun ReportAllocation(wallet: Wallet, portfolioId: String?, result: Result?,
    loadEtf: suspend (Security, Boolean) -> ca.monwallet.app.marketdata.EtfLoad = { s, _ ->
        ca.monwallet.app.marketdata.EtfLoad(ca.monwallet.app.marketdata.EtfData.fetch(s)) }) {
    var grouping by remember { mutableIntStateOf(1) }
    var cash by remember { mutableStateOf(false) }
    var selectedEtf by remember { mutableStateOf<Security?>(null) }
    val names = listOf("Titres", "FNB / Actions", "Secteur", "Géographie", "Devise", "Portefeuille")
    selectedEtf?.let { fund ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { selectedEtf = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxWidth().fillMaxHeight(.94f).padding(10.dp),
                shape = RoundedCornerShape(18.dp), color = Night) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Détail du FNB", Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                        TextButton(onClick = { selectedEtf = null }) { Text("Fermer") }
                    }
                    val value = result?.holdings?.find { it.securityId == fund.id }?.value
                    val total = result?.holdings?.filter { it.quantity > ZERO }?.fold(ZERO) { a, h -> a + (h.value ?: ZERO) }
                        ?.plus(if (cash) result.cash.max(ZERO) else ZERO)
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
                        EtfDetailsCard(fund, value, total?.let { value?.pct(it) }, loadEtf)
                    }
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CardBlock {
            Text("Allocation actuelle", fontWeight = FontWeight.SemiBold)
            Chips(names, grouping) { grouping = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(cash, { cash = it })
                Spacer(Modifier.width(8.dp)); Caption("Inclure les liquidités")
            }
            if (result?.value == null) {
                Caption("Données de valorisation indisponibles")
                return@CardBlock
            }
            val securities = wallet.securities.associateBy { it.id }
            if (grouping <= 2) {
                val allocation = when (grouping) {
                    0 -> Allocation.byTitle(result, securities, cash)
                    1 -> Allocation.byAssetType(result, securities, cash)
                    else -> Allocation.bySector(result, securities, includeCash = cash)
                }
                if (allocation.complete && allocation.total != null) AllocationDonut(allocation,
                    onSlice = if (grouping == 0) { label -> selectedEtf = wallet.securities.firstOrNull {
                        it.ticker == label && it.type.uppercase() == "ETF" } } else null)
                else Caption("Composition indisponible : données manquantes")
            } else {
                val positions = result.holdings.filter { it.quantity > ZERO && it.value != null }
                val grouped = when (grouping) {
                    3 -> positions.groupBy { h -> securities[h.securityId]?.country ?: "Non déterminé" }
                        .mapValues { (_, rows) -> rows.fold(ZERO) { a, h -> a + h.value!! } }
                    4 -> positions.groupBy { h -> securities[h.securityId]?.currency ?: "Inconnue" }
                        .mapValues { (_, rows) -> rows.fold(ZERO) { a, h -> a + h.value!! } }
                    else -> wallet.portfolios.filter { portfolioId == null || it.id == portfolioId }
                        .associate { p -> p.name to (runCatching { wallet.result(p.id).value }.getOrNull() ?: ZERO) }
                }
                val total = grouped.values.fold(ZERO, BigDecimal::add)
                grouped.entries.sortedByDescending { it.value }.forEach { (name, value) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name); Text(percent(value.pct(total)))
                    }
                }
                if (grouped.isEmpty()) Caption("Aucune position valorisée")
            }
            if (grouping in 0..1) {
                Caption("FNB = parts de fonds détenues; Actions = titres détenus directement. Touchez un FNB pour sa composition.")
                val funds = result.holdings.filter { it.quantity > ZERO && securities[it.securityId]?.type?.uppercase() == "ETF" }
                funds.forEach { h ->
                    val fund = securities.getValue(h.securityId)
                    TextButton(onClick = { selectedEtf = fund }, modifier = Modifier.fillMaxWidth()) {
                        Text("${fund.ticker} · Composition et pondérations  ›")
                    }
                }
            }
            val owned = result.holdings.filter { it.quantity > ZERO && it.value != null }
                .sortedByDescending { it.value!! }
            val investedInTitles = owned.fold(ZERO) { a, h -> a + h.value!! }
            if (owned.isNotEmpty()) {
                Metric("Plus grosse position", "${wallet.security(owned.first().securityId)?.ticker ?: "—"} · ${percent(owned.first().value?.pct(investedInTitles))}")
                Metric("Top 3", percent(owned.take(3).fold(ZERO) { a, h -> a + h.value!! }.pct(investedInTitles)))
                Metric("Top 5", percent(owned.take(5).fold(ZERO) { a, h -> a + h.value!! }.pct(investedInTitles)))
            }
        }
    }
}

@Composable
private fun ReportFlows(wallet: Wallet, portfolioId: String?, summary: ReportSummary,
    history: List<PortfolioDailySnapshot>) {
    val allTransactions = wallet.transactions.filter { portfolioId == null || it.portfolioId == portfolioId }
    val tx = wallet.transactions.filter { portfolioId == null || it.portfolioId == portfolioId }
        .filter { summary.base == null || it.date > summary.base.date.toString() }
        .filter { it.type in setOf(TxType.DEPOSIT, TxType.WITHDRAWAL, TxType.DIVIDEND, TxType.FEE) }
        .sortedByDescending { it.date }
    CardBlock {
        Text("Flux de capitaux", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Metric("Dépôts", money(summary.deposits))
        Metric("Retraits", money(summary.withdrawals))
        Metric("Achats financés directement", money(summary.implicitFunding.takeIf { summary.complete }))
        Metric("Dividendes", money(summary.dividends))
        val thisYear = LocalDate.now().year.toString()
        val dividendsYtd = allTransactions.filter { it.type == TxType.DIVIDEND && it.date.startsWith(thisYear) }
            .fold(ZERO) { a, t -> a + t.cad }
        val dividendsTotal = allTransactions.filter { it.type == TxType.DIVIDEND }
            .fold(ZERO) { a, t -> a + t.cad }
        Metric("Dividendes YTD", money(dividendsYtd))
        Metric("Dividendes total", money(dividendsTotal))
        Metric("Frais", money(summary.fees))
        Caption("Un achat financé directement augmente le capital net si l’encaisse ne suffit pas. Une vente reste dans l’encaisse : elle n’est pas un retrait.")
    }
    CardBlock {
        Text("Capital net investi par mois", fontWeight = FontWeight.SemiBold)
        history.drop(1).groupBy { YearMonth.from(it.date) }.toSortedMap(reverseOrder())
            .entries.take(18).forEach { (month, days) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(month.toString())
                    Text(signed(days.fold(ZERO) { a, d -> a + d.netExternalFlow }),
                        color = tint(days.fold(ZERO) { a, d -> a + d.netExternalFlow }))
                }
            }
        Spacer(Modifier.height(8.dp))
        Text("Capital ajouté par année", fontWeight = FontWeight.SemiBold)
        history.drop(1).groupBy { it.date.year }.toSortedMap(reverseOrder()).forEach { (year, days) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(year.toString())
                Text(signed(days.fold(ZERO) { a, d -> a + d.netExternalFlow }))
            }
        }
    }
    CardBlock {
        Text("Historique du capital net", fontWeight = FontWeight.SemiBold)
        history.drop(1).filter { it.netExternalFlow != ZERO }.asReversed().take(30).forEach { day ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${date(day.date.toString())}  ${signed(day.netExternalFlow)}", fontSize = 12.sp)
                Text(money(day.capitalInvested), fontSize = 12.sp)
            }
        }
    }
    CardBlock {
        Text("Mouvements", fontWeight = FontWeight.SemiBold)
        tx.forEach { item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${date(item.date)} · ${when (item.type) {
                    TxType.DEPOSIT -> "Dépôt"; TxType.WITHDRAWAL -> "Retrait"
                    TxType.DIVIDEND -> "Dividende"; else -> "Frais"
                }}", fontSize = 12.sp)
                Text(signed(if (item.type in setOf(TxType.WITHDRAWAL, TxType.FEE)) -item.cad else item.cad),
                    fontSize = 12.sp)
            }
        }
        if (tx.isEmpty()) Caption("Aucun mouvement sur cette période")
    }
}
