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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val reportTabs = listOf("Vue d’ensemble", "Performance", "Calendrier", "Allocation", "Flux")
private val benchmarkNames = linkedMapOf(
    "^GSPC" to "S&P 500", "^IXIC" to "NASDAQ", "^GSPTSE" to "TSX", "XEQT.TO" to "XEQT")
private val benchmarkColors = listOf(Blue, Color(0xFF9F76FF), Color(0xFFFFA04D), Color(0xFFFF70A6))

@Composable
fun ReportsScreen(w: Wallet, vm: WalletViewModel, initialPortfolio: String?) {
    val savedPortfolio = w.settings["reports_portfolio"]
    val validId = savedPortfolio?.takeIf { id -> w.portfolios.any { it.id == id } }
    var selected by remember(savedPortfolio, initialPortfolio) {
        mutableStateOf(if (savedPortfolio == "all") null else validId ?: initialPortfolio)
    }
    var portfolioMenu by remember { mutableStateOf(false) }
    var tab by remember(w.settings["reports_tab"]) {
        mutableIntStateOf(w.settings["reports_tab"]?.toIntOrNull()?.coerceIn(0, 4) ?: 0)
    }
    var range by remember(w.settings["reports_range"]) {
        mutableStateOf(runCatching { ReportRange.valueOf(w.settings["reports_range"].orEmpty()) }
            .getOrDefault(ReportRange.SIX_MONTHS))
    }
    var mode by remember(w.settings["reports_chart"]) {
        mutableIntStateOf(w.settings["reports_chart"]?.toIntOrNull()?.coerceIn(0, 2) ?: 0)
    }
    var month by remember(w.settings["reports_month"]) {
        mutableStateOf(runCatching { YearMonth.parse(w.settings["reports_month"]) }
            .getOrDefault(YearMonth.now()))
    }
    var benchmarks by remember(w.settings["reports_benchmarks"]) {
        mutableStateOf(w.settings["reports_benchmarks"]?.split('|')?.filter { it in benchmarkNames }?.toSet()
            ?: setOf("^GSPC", "^IXIC", "^GSPTSE", "XEQT.TO"))
    }
    fun selectTab(index: Int) {
        tab = index
        vm.run { vm.services.repo.setting("reports_tab", index.toString()) }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snapshots by produceState(emptyList<PortfolioDailySnapshot>(), w.transactions, w.prices, selected) {
        value = withContext(Dispatchers.Default) { runCatching {
            ReportEngine.history(w, selected)
        }.getOrDefault(emptyList()) }
    }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(selected, range, w.transactions.map { it.securityId }.distinct()) {
        loading = true
        try { vm.services.refreshReportHistory(selected,
            if (range == ReportRange.TEN_YEARS || range == ReportRange.TOTAL) 10 else 5)
        } finally { loading = false }
    }
    val summary = remember(w.transactions, w.prices, selected, range, snapshots) {
        ReportEngine.summary(w, snapshots, range, portfolioId = selected)
    }
    val current = remember(w.transactions, w.quotes, selected) {
        runCatching { w.result(selected) }.getOrNull()
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
                    out.appendLine("date,valeur,capital_investi,pnl_jour,rendement_jour_pct,dividendes_cumules,flux_externe,rendement_cumule_pct")
                    val cumulative = summary.cumulative.toMap()
                    summary.days.forEach { day -> out.appendLine(listOf(day.date, day.closingValue,
                        day.capitalInvested, day.dailyPnl, day.dailyReturn, day.dividends,
                        day.netExternalFlow, cumulative[day.date]).joinToString(",") { it?.toString().orEmpty() }) }
                } ?: error("Fichier inaccessible")
            } }.onFailure { Toast.makeText(context, "Export impossible : ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AssistChip(onClick = { portfolioMenu = true },
                        label = { Text(w.portfolios.firstOrNull { it.id == selected }?.name ?: "Tous") })
                    DropdownMenu(portfolioMenu, { portfolioMenu = false }) {
                        (listOf(null) + w.portfolios.map { it.id }).forEach { id ->
                            DropdownMenuItem(text = { Text(w.portfolios.firstOrNull { it.id == id }?.name ?: "Tous") },
                                onClick = {
                                    portfolioMenu = false
                                    selected = id
                                    vm.run { vm.services.repo.setting("reports_portfolio", id ?: "all") }
                                })
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.run { vm.services.refreshReportHistory(selected, 10, true) } }) {
                    Text("↻ Actualiser", color = Blue, fontSize = 12.sp)
                }
                TextButton(onClick = { csvLauncher.launch("MonWallet-Rapports-${LocalDate.now()}.csv") }) {
                    Text("CSV", color = Blue, fontSize = 12.sp)
                }
            }
            Chips(reportTabs, tab, ::selectTab)
            Chips(ReportRange.entries.map { it.label }, range.ordinal) { index ->
                range = ReportRange.entries[index]
                vm.run { vm.services.repo.setting("reports_range", range.name) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
        }
        when (tab) {
            0 -> {
                item { ReportHero(summary, mode, onMode = { mode = it; vm.run {
                    vm.services.repo.setting("reports_chart", it.toString()) } }) }
                item { ReportMetrics(summary, current) }
                item { Comparison(w, summary, benchmarks) { selectTab(1) } }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) { ReportMini("Calendrier", "Rendement quotidien", { selectTab(2) }) }
                        Box(Modifier.weight(1f)) { ReportMini("Allocation", "Positions actuelles", { selectTab(3) }) }
                    }
                }
            }
            1 -> {
                item { ReportHero(summary, mode, onMode = { mode = it; vm.run {
                    vm.services.repo.setting("reports_chart", it.toString()) } }) }
                item { ReportMetrics(summary, current) }
                item { Comparison(w, summary, benchmarks) { symbol ->
                    benchmarks = if (symbol in benchmarks) benchmarks - symbol else benchmarks + symbol
                    vm.run { vm.services.repo.setting("reports_benchmarks", benchmarks.joinToString("|")) }
                } }
                item { ReportStatistics(summary, snapshots) }
                item { ReportContributors(w, summary) }
                item { ReportMonthYear(snapshots) }
            }
            2 -> {
                item { ReportCalendar(w, snapshots,
                    w.transactions.filter { selected == null || it.portfolioId == selected },
                    month, { month = it; vm.run { vm.services.repo.setting("reports_month", month.toString()) } }) }
                item { ReportMonthYear(snapshots) }
            }
            3 -> item { ReportAllocation(w, selected, current) }
            4 -> item { ReportFlows(w, selected, summary, snapshots) }
        }
        item { Caption("Rendement ajusté des flux : clôtures historiques en CAD. Les transactions n’ayant pas d’heure, les flux du jour sont supposés avant la clôture; le TWR exact exige une valorisation à chaque flux.") }
    }
}

@Composable
private fun ReportMini(title: String, subtitle: String, onClick: () -> Unit) {
    CardBlock {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
            Text("$title  ›", fontWeight = FontWeight.SemiBold)
            Caption(subtitle)
        }
    }
}

@Composable
private fun ReportHero(summary: ReportSummary, mode: Int, onMode: (Int) -> Unit) {
    CardBlock {
        Caption("Rendement du portefeuille · ${summary.range.label}")
        if (!summary.complete) {
            Spacer(Modifier.height(6.dp))
            Text(summary.reason ?: "Historique insuffisant", color = Muted,
                fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Caption("Aucune performance reconstruite à partir de cours manquants.")
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
private fun ReportCalendar(wallet: Wallet, history: List<PortfolioDailySnapshot>, transactions: List<Transaction>,
    month: YearMonth, onMonth: (YearMonth) -> Unit) {
    var day by remember { mutableStateOf<PortfolioDailySnapshot?>(null) }
    var yearMenu by remember { mutableStateOf(false) }
    val byDate = history.associateBy { it.date }
    CardBlock {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onMonth(month.minusMonths(1)) }) { Text("‹", fontSize = 24.sp) }
            Box {
                TextButton(onClick = { yearMenu = true }) {
                    Text("${month.month.getDisplayName(TextStyle.FULL, Locale.FRENCH)} ${month.year} ▾",
                        color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                DropdownMenu(yearMenu, { yearMenu = false }) {
                    val oldest = history.getOrNull(1)?.date?.year ?: LocalDate.now().year
                    (LocalDate.now().year downTo oldest).forEach { year ->
                        DropdownMenuItem(text = { Text(year.toString()) }, onClick = {
                            yearMenu = false
                            onMonth(YearMonth.of(year, month.month).coerceAtMost(YearMonth.now()))
                        })
                    }
                }
            }
            TextButton(onClick = { onMonth(month.plusMonths(1)) },
                enabled = month < YearMonth.now()) { Text("›", fontSize = 24.sp) }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("D", "L", "M", "M", "J", "V", "S").forEach { label ->
                Text(label, Modifier.weight(1f), color = Muted, textAlign = TextAlign.Center, fontSize = 11.sp)
            }
        }
        val offset = month.atDay(1).dayOfWeek.value % 7
        (0 until ((offset + month.lengthOfMonth() + 6) / 7)).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { weekday ->
                    val n = week * 7 + weekday - offset + 1
                    if (n !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(47.dp))
                    else {
                        val snapshot = byDate[month.atDay(n)]
                        val daily = snapshot?.dailyReturn
                        val shade = if (daily == null) Color.Transparent
                            else tint(daily).copy(alpha = (.10f + (daily.abs().toFloat() / 8f).coerceIn(0f, .38f)))
                        Column(Modifier.weight(1f).height(47.dp).padding(2.dp)
                            .clip(RoundedCornerShape(7.dp)).background(shade)
                            .clickable(enabled = snapshot != null) { day = snapshot },
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$n", fontSize = 11.sp)
                            if (daily != null) Text(percent(daily), fontSize = 9.sp,
                                color = tint(daily), maxLines = 1)
                        }
                    }
                }
            }
        }
        Caption("Les jours sans cours confirmé n’affichent aucun rendement.")
    }
    day?.let { selected ->
        ModalBottomSheet(onDismissRequest = { day = null }, containerColor = Panel) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(date(selected.date.toString()), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Metric("P&L du jour", signed(selected.dailyPnl), tint(selected.dailyPnl))
                Metric("Rendement du jour", percent(selected.dailyReturn), tint(selected.dailyReturn))
                Metric("Valeur début", money(selected.openingValue))
                Metric("Valeur fin", money(selected.closingValue))
                Metric("Capital net investi", money(selected.capitalInvested))
                val today = transactions.filter { it.date == selected.date.toString() }
                Metric("Dépôts", money(today.filter { it.type == TxType.DEPOSIT }.fold(ZERO) { a, t -> a + t.cad }))
                Metric("Retraits", money(today.filter { it.type == TxType.WITHDRAWAL }.fold(ZERO) { a, t -> a + t.price * t.fxRate }))
                Metric("Dividendes", money(today.filter { it.type == TxType.DIVIDEND }.fold(ZERO) { a, t -> a + t.cad }))
                Caption("Achats : ${today.count { it.type == TxType.BUY }} · Ventes : ${today.count { it.type == TxType.SELL }}")
                val previous = history.lastOrNull { it.date < selected.date && it.closingValue != null }
                if (selected.dailyReturn != null && previous != null) {
                    val contributions = (selected.positions.keys + previous.positions.keys +
                        today.mapNotNull { it.securityId }).distinct().map { id ->
                        val changes = today.filter { it.securityId == id }
                        val buy = changes.filter { it.type == TxType.BUY }.fold(ZERO) { a, t -> a + t.cad }
                        val sale = changes.filter { it.type == TxType.SELL }.fold(ZERO) { a, t -> a + t.cad }
                        val income = changes.filter { it.type == TxType.DIVIDEND }.fold(ZERO) { a, t -> a + t.cad }
                        id to ((selected.positions[id] ?: ZERO) - (previous.positions[id] ?: ZERO)
                            - buy + sale + income)
                    }
                    contributions.maxByOrNull { it.second }?.let { (id, pnl) ->
                        Metric("Meilleur titre", "${wallet.security(id)?.ticker ?: "—"} · ${signed(pnl)}", tint(pnl)) }
                    contributions.minByOrNull { it.second }?.let { (id, pnl) ->
                        Metric("Pire titre", "${wallet.security(id)?.ticker ?: "—"} · ${signed(pnl)}", tint(pnl)) }
                }
                if (selected.dailyReturn == null) Caption("Aucune séance ou historique incomplet ce jour-là.")
            }
        }
    }
}

@Composable
private fun ReportAllocation(wallet: Wallet, portfolioId: String?, result: Result?) {
    var grouping by remember { mutableIntStateOf(0) }
    var cash by remember { mutableStateOf(false) }
    val names = listOf("Titres", "Secteur", "Géographie", "Devise", "Portefeuille")
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
            if (grouping <= 1) {
                val allocation = if (grouping == 0) Allocation.byTitle(result, securities, cash)
                    else Allocation.bySector(result, securities, includeCash = cash)
                if (allocation.complete && allocation.total != null) AllocationDonut(allocation)
                else Caption("Composition indisponible : données manquantes")
            } else {
                val positions = result.holdings.filter { it.quantity > ZERO && it.value != null }
                val grouped = when (grouping) {
                    2 -> positions.groupBy { h -> securities[h.securityId]?.country ?: "Non déterminé" }
                        .mapValues { (_, rows) -> rows.fold(ZERO) { a, h -> a + h.value!! } }
                    3 -> positions.groupBy { h -> securities[h.securityId]?.currency ?: "Inconnue" }
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
