@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ca.monwallet.app.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import ca.monwallet.app.domain.*
import kotlinx.coroutines.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val ReportAccent = Color(0xFF51E5A1)
private val ReportPanel = Color(0xFF09212D)

@Composable
private fun ReportCaption(text: String) {
    Text(text, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
}

@Composable
private fun ReportChips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, label ->
            Surface(onClick = { onSelect(i) }, shape = RoundedCornerShape(9.dp),
                color = if (selected == i) ReportAccent.copy(alpha = .13f) else Night,
                border = BorderStroke(1.dp, if (selected == i) ReportAccent else Border)) {
                Text(label, Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    fontSize = 11.sp, lineHeight = 16.sp,
                    color = if (selected == i) ReportAccent else Muted)
            }
        }
    }
}

@Composable
private fun ReportCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = ReportPanel,
        border = BorderStroke(1.dp, Color(0xFF1C4350))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
    }
}

@Composable
internal fun ReportsDashboard(
    wallet: Wallet, history: List<PortfolioDailySnapshot>, portfolioId: String?,
    loading: Boolean = false, status: String = "",
    onPortfolio: (String?) -> Unit = {}, onPreference: (String, String) -> Unit = { _, _ -> },
    onRefresh: () -> Unit = {}, now: Long = System.currentTimeMillis(),
    initialTab: Int? = null, advanced: @Composable (ReportSummary) -> Unit = {},
) {
    val today = MarketClock.date(now)
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(initialTab ?: wallet.settings["reports_tab_v2"]?.toIntOrNull()
        ?.coerceIn(0, 3) ?: 0) }
    var range by remember { mutableStateOf(runCatching {
        ReportRange.valueOf(wallet.settings["reports_range"].orEmpty())
    }.getOrDefault(ReportRange.MONTH)) }
    var portfolioMenu by remember { mutableStateOf(false) }
    var export by remember { mutableStateOf(false) }
    var dates by remember { mutableStateOf(false) }
    var customPeriod by remember { mutableStateOf<ReportPeriod?>(null) }
    var query by remember { mutableStateOf("") }
    var type by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableIntStateOf(10) }
    var advancedOpen by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<TradeReports.WalletDay?>(null) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var calendarMoney by remember { mutableStateOf(false) }
    val tx = remember(wallet.transactions, portfolioId) {
        wallet.transactions.filter { portfolioId == null || it.portfolioId == portfolioId } }
    val confirmed = remember(history, today, MarketClock.offSession(now)) {
        TradeReports.confirmedHistory(history, now) }
    val summary = remember(confirmed, tx, range, portfolioId) {
        ReportEngine.summary(wallet, confirmed, range, today, portfolioId) }
    val first = tx.minOfOrNull { LocalDate.parse(it.date) } ?: today
    val period = customPeriod ?: ReportPeriod(range.start(today) ?: first, today)
    val days = remember(history, wallet.securities, tx, today, MarketClock.offSession(now)) {
        val ids = tx.mapNotNull { it.securityId }.toSet()
        TradeReports.walletDays(history, wallet.securities.filter { it.id in ids }, now) }
    val salesResult = remember(tx) { runCatching { TradeReports.sales(tx) } }
    val allSales = salesResult.getOrDefault(emptyList())
    val sales = allSales.filter { period.contains(it.saleDate) &&
        (query.isBlank() || wallet.security(it.securityId)?.let { s ->
            s.symbol.contains(query, true) || s.name.contains(query, true) } == true) }
    val daily = days.filter { period.contains(it.date.toString()) }.asReversed()
    val transactions = tx.filter {
        period.contains(it.date) && (type == 0 || it.type == TxType.entries[type - 1]) &&
            (query.isBlank() || wallet.security(it.securityId)?.let { s ->
                s.symbol.contains(query, true) || s.name.contains(query, true) } == true ||
                it.note.contains(query, true))
    }.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt }.thenBy { it.id })
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun changeTab(index: Int) {
        tab = index; query = ""; type = 0
        onPreference("reports_tab_v2", index.toString())
        scope.launch { listState.scrollToItem(0) }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("reports_list"), state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { portfolioMenu = true }) {
                        Text(wallet.portfolios.find { it.id == portfolioId }?.let { "${it.name} · ${it.type}" }
                            ?: "Tous les portefeuilles", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("  ⌄")
                    }
                    DropdownMenu(portfolioMenu, { portfolioMenu = false }) {
                        (listOf(null) + wallet.portfolios.map { it.id }).forEach { id ->
                            DropdownMenuItem(text = { Text(wallet.portfolios.find { it.id == id }?.name ?: "Tous") },
                                onClick = { portfolioMenu = false; onPortfolio(id) })
                        }
                    }
                }
                TextButton(onClick = onRefresh, modifier = Modifier.testTag("reports_refresh")) { Text("↻") }
                OutlinedButton(onClick = { export = true }, modifier = Modifier.testTag("reports_export")) {
                    Text("CSV ↓", color = ReportAccent) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("Résumé", "Calendrier", "Trades clôturés", "Transactions").forEachIndexed { index, label ->
                    Surface(onClick = { changeTab(index) }, modifier = Modifier.weight(1f)
                        .testTag("reports_tab_${index}"), color = if (tab == index) ReportAccent.copy(alpha = .13f) else Night,
                        shape = RoundedCornerShape(9.dp),
                        border = BorderStroke(1.dp, if (tab == index) ReportAccent else Border)) {
                        Box(Modifier.heightIn(min = 46.dp).padding(horizontal = 4.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center) {
                            Text(label, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2,
                                textAlign = TextAlign.Center, color = if (tab == index) ReportAccent else Muted)
                        }
                    }
                }
            }
            if (tab != 1) {
                ReportChips(ReportRange.entries.map { it.label }, if (customPeriod == null) range.ordinal else -1) {
                    range = ReportRange.entries[it]; customPeriod = null
                    onPreference("reports_range", range.name) }
                if (tab >= 2) TextButton(onClick = { dates = true }) {
                    Text("Dates : ${date(period.start.toString())} → ${date(period.end.toString())}",
                        fontSize = 12.sp, color = Blue) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = ReportAccent)
            // Provider diagnostics belong to an expandable status, not a permanent wall of text.
            if (status.isNotBlank() && (history.size < 2 || status.contains("incomplet", true) ||
                status.contains("indisponible", true))) ReportCaption(status)
        }
        when (tab) {
            0 -> {
                item {
                    val last = confirmed.drop(1).lastOrNull { it.hasMarketClose && it.closingValue != null }
                    ReportCard {
                        ReportCaption("VALEUR DU WALLET · CAD")
                        Text(money(last?.closingValue), fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("reports_wallet_value"))
                        ReportCaption(last?.let { "À la clôture du ${date(it.date.toString())}" }
                            ?: if (loading) "Chargement des clôtures…" else "Clôtures historiques indisponibles")
                        Text("${signed(summary.gain)} · ${percent(summary.performance)}",
                            color = tint(summary.gain), fontWeight = FontWeight.SemiBold)
                        ReportCaption("Rendement ${range.label}" + if (summary.partialPeriod) " · depuis le premier achat" else "")
                        Chart(summary.cumulative.map { it.second }, modifier = Modifier.height(76.dp), color = ReportAccent)
                        HorizontalDivider(color = Border)
                        val base = summary.base
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallReportMetric("Réalisé", signed(if (summary.complete && last != null && base != null)
                                last.realizedPnl - base.realizedPnl else null), Modifier.weight(1f))
                            SmallReportMetric("Non réalisé", signed(if (summary.complete && last != null && base != null)
                                last.unrealizedPnl?.let { end -> base.unrealizedPnl?.let { end - it } } else null), Modifier.weight(1f))
                            SmallReportMetric("Dividendes", money(summary.dividends), Modifier.weight(1f))
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Mon wallet par jour", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        TextButton(onClick = { expanded += 50 }) { Text("Voir plus ›", color = Blue) }
                    }
                    ReportCaption("Valeur à la clôture · toucher une date pour le détail")
                }
                if (daily.isEmpty()) item { ReportCard { ReportCaption("Aucun historique sur cette période.") } }
                items(daily.take(expanded), key = { "day:${it.date}" }) { day ->
                    ReportDailyRow(day) { selectedDay = day }
                }
                if (daily.size > expanded) item {
                    OutlinedButton(onClick = { expanded += 50 }, modifier = Modifier.fillMaxWidth()) {
                        Text("Afficher les journées suivantes") }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { changeTab(1) }, modifier = Modifier.weight(1f)) { Text("Calendrier") }
                        OutlinedButton(onClick = { advancedOpen = true }, modifier = Modifier.weight(1f)) { Text("Analyse détaillée") }
                    }
                }
            }
            1 -> {
                item { ReportCalendarV2(days, month, calendarMoney, { month = it }, { calendarMoney = it },
                    { selectedDay = it }, today) }
                item {
                    val monthHistory = confirmed.filter { YearMonth.from(it.date) == month && it.hasMarketClose }
                    ReportCard {
                        Text("Bilan du mois", fontWeight = FontWeight.Bold)
                        val complete = monthHistory.isNotEmpty() && monthHistory.all { it.dailyReturn != null }
                        val returnPct = if (complete) (monthHistory.fold(ONE) { n, d ->
                            n * (ONE + d.dailyReturn!!.divide(BigDecimal(100), MC)) } - ONE) * BigDecimal(100) else null
                        Row(Modifier.fillMaxWidth()) {
                            SmallReportMetric("Rendement", percent(returnPct), Modifier.weight(1f))
                            SmallReportMetric("Gain / perte", signed(if (complete)
                                monthHistory.fold(ZERO) { n, d -> n + (d.dailyPnl ?: ZERO) } else null), Modifier.weight(1f))
                        }
                        weeklyReports(confirmed, month, today).forEachIndexed { i, week ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                ReportCaption("Semaine ${i + 1}")
                                Text("${percent(week.performance)} · ${signed(week.gain)}",
                                    fontSize = 12.sp, color = tint(week.gain))
                            }
                        }
                    }
                }
            }
            2 -> {
                item {
                    OutlinedTextField(query, { query = it }, singleLine = true,
                        label = { Text("Rechercher un titre") }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReportCard(Modifier.weight(1f)) { ReportCaption("Profit réalisé · CAD")
                            Text(signed(sales.fold(ZERO) { n, t -> n + t.pnl }),
                                color = tint(sales.fold(ZERO) { n, t -> n + t.pnl }), fontWeight = FontWeight.Bold) }
                        ReportCard(Modifier.weight(1f)) { ReportCaption("Ventes / clôtures")
                            Text("${sales.size} / ${sales.count { it.closed }}", fontWeight = FontWeight.Bold) }
                    }
                }
                salesResult.exceptionOrNull()?.let { error -> item { ReportCaption("Calcul indisponible : ${error.message}") } }
                if (sales.isEmpty() && salesResult.isSuccess) item { Empty("Aucune vente sur cette période",
                    "Les achats sont visibles dans Transactions. Les ventes apparaîtront avec leur profit net.") }
                items(sales, key = { it.saleId }) { trade -> ReportTradeCard(wallet, trade) }
                item { ReportCaption("Coût moyen et frais inclus. Profit net en CAD, avec les taux FX enregistrés. La période filtre les ventes; les achats antérieurs restent inclus dans le coût.") }
            }
            3 -> {
                item {
                    OutlinedTextField(query, { query = it }, singleLine = true,
                        label = { Text("Rechercher un titre ou une note") }, modifier = Modifier.fillMaxWidth())
                    ReportChips(listOf("Tout", "Achats", "Ventes", "Dividendes", "Frais", "Dépôts", "Retraits"), type) { type = it }
                    ReportCaption("${transactions.size} transaction(s) · dates inclusives")
                }
                if (transactions.isEmpty()) item { Empty("Aucune transaction", "Modifie les dates ou les filtres.") }
                items(transactions, key = { it.id }) { transaction -> ReportTransactionRow(wallet, transaction) }
            }
        }
        item { ReportCaption("Clôtures régulières uniquement. Le PRE/AFTER informatif n’entre pas dans le calendrier. Les cours ou taux FX absents restent signalés.") }
    }
    if (selectedDay != null) ReportDayDetail(wallet, tx, selectedDay!!) { selectedDay = null }
    if (dates) ReportPeriodDialog(first, today, period, { dates = false }) { customPeriod = it; dates = false }
    if (export) ReportExportDialog(wallet, history, portfolioId, first, today, period, now) { export = false }
    if (advancedOpen) FullDialog("Analyse détaillée", { advancedOpen = false }) { advanced(summary) }
}

@Composable
private fun SmallReportMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier) { Text(label, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = if (value.startsWith("+")) ReportAccent else if (value.startsWith("-")) Red else Color.White,
            maxLines = 2, overflow = TextOverflow.Ellipsis) }
}

private fun dayStatus(day: TradeReports.WalletDay): String = when (day.status) {
    TradeReports.DayStatus.CONFIRMED -> "Clôture confirmée"
    TradeReports.DayStatus.PENDING -> "En attente de clôture"
    TradeReports.DayStatus.NO_SESSION -> "Sans séance"
    TradeReports.DayStatus.INCOMPLETE -> "Historique incomplet"
}

@Composable
private fun ReportDailyRow(day: TradeReports.WalletDay, onClick: () -> Unit) {
    ReportCard(Modifier.clickable(onClick = onClick).testTag("report_day_${day.date}")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1.1f)) { Text(date(day.date.toString()), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(dayStatus(day), fontSize = 10.sp, color = Muted) }
            Text(money(day.value), Modifier.weight(1.2f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(signed(day.pnl), fontSize = 12.sp, color = tint(day.pnl))
                Text(percent(day.percent), fontSize = 11.sp, color = tint(day.percent))
            }
        }
    }
}

@Composable
internal fun ReportTradeCard(wallet: Wallet, trade: ClosedTrade) {
    ReportCard(Modifier.testTag("closed_trade_${trade.saleId}")) {
        val security = wallet.security(trade.securityId)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (security != null) Logo(security)
            Column(Modifier.weight(1f)) { Text(security?.ticker ?: "Titre supprimé", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                ReportCaption(security?.name ?: trade.securityId) }
            Surface(shape = RoundedCornerShape(7.dp), color = Color.Transparent,
                border = BorderStroke(1.dp, if (trade.closed) ReportAccent else Blue)) {
                Text(if (trade.closed) "Clôturée" else "Vente partielle",
                    Modifier.padding(6.dp), fontSize = 10.sp, lineHeight = 14.sp,
                    color = if (trade.closed) ReportAccent else Blue)
            }
        }
        Text("${signed(trade.pnl)} · ${percent(trade.percent)} net",
            color = tint(trade.pnl), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { ReportCaption("ACHAT MOYEN")
                Text(date(trade.firstBuyDate), fontSize = 13.sp, lineHeight = 18.sp)
                Text(money(trade.averageBuyPrice, trade.currency) + " / action", fontSize = 13.sp, lineHeight = 18.sp)
                if (trade.lastBuyDate != trade.firstBuyDate) ReportCaption("Dernier achat ${date(trade.lastBuyDate)}")
            }
            Text("→", color = Muted)
            Column(Modifier.weight(1f)) { ReportCaption("VENTE")
                Text(date(trade.saleDate), fontSize = 13.sp, lineHeight = 18.sp)
                Text(money(trade.salePrice, trade.currency) + " / action", fontSize = 13.sp, lineHeight = 18.sp) }
        }
        HorizontalDivider(color = Border)
        Text("${number(trade.quantity, 6)} action(s) · ${trade.days} jour(s) · Frais ${money(trade.allocatedFeesCad)}",
            fontSize = 11.sp, color = Muted)
        Row(Modifier.fillMaxWidth()) {
            SmallReportMetric("Coût vendu · CAD", money(trade.costCad), Modifier.weight(1f))
            SmallReportMetric("Produit net · CAD", money(trade.proceedsCad), Modifier.weight(1f))
        }
        if (!trade.closed) ReportCaption("Reste après cette vente : ${number(trade.remaining, 6)} action(s)")
        ReportCaption(wallet.portfolios.find { it.id == trade.portfolioId }?.name.orEmpty())
    }
}

@Composable
internal fun ReportTransactionRow(wallet: Wallet, tx: Transaction) {
    ReportCard(Modifier.testTag("report_transaction_${tx.id}")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(wallet.security(tx.securityId)?.ticker ?: transactionLabel(tx.type), fontWeight = FontWeight.Bold)
            Text(date(tx.date), fontSize = 12.sp, color = Muted)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(transactionLabel(tx.type), color = if (tx.type == TxType.SELL) ReportAccent else Blue, fontSize = 13.sp, lineHeight = 18.sp)
            Text(money(tx.cad), fontWeight = FontWeight.SemiBold)
        }
        if (tx.type in setOf(TxType.BUY, TxType.SELL))
            ReportCaption("${number(tx.quantity, 6)} × ${money(tx.price, tx.currency)} · Frais ${money(tx.fees, tx.currency)}")
        ReportCaption(wallet.portfolios.find { it.id == tx.portfolioId }?.name.orEmpty() +
            if (tx.currency == "USD") " · FX ${number(tx.fxRate, 6)} CAD/USD" else "")
        if (tx.note.isNotBlank()) Text(tx.note, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun ReportCalendarV2(days: List<TradeReports.WalletDay>, month: YearMonth, moneyMode: Boolean,
    onMonth: (YearMonth) -> Unit, onMoney: (Boolean) -> Unit,
    onDay: (TradeReports.WalletDay) -> Unit, today: LocalDate) {
    val byDate = days.associateBy { it.date }
    ReportCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onMonth(month.minusMonths(1)) }) { Text("‹", fontSize = 23.sp) }
            Text("${month.month.getDisplayName(TextStyle.FULL, Locale.FRENCH)} ${month.year}",
                Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            TextButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month < YearMonth.from(today)) { Text("›", fontSize = 23.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ReportChips(listOf("$", "%"), if (moneyMode) 0 else 1) { onMoney(it == 0) }

        }
        Row { listOf("D", "L", "M", "M", "J", "V", "S").forEach {
            Text(it, Modifier.weight(1f), color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center) } }
        val offset = month.atDay(1).dayOfWeek.value % 7
        repeat((offset + month.lengthOfMonth() + 6) / 7) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { weekday ->
                    val n = week * 7 + weekday - offset + 1
                    if (n !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(48.dp))
                    else {
                        val day = byDate[month.atDay(n)]
                        val value = if (moneyMode) day?.pnl else day?.percent
                        val color = tint(value)
                        Surface(Modifier.weight(1f).padding(2.dp).height(48.dp)
                            .testTag("calendar_day_${month.atDay(n)}")
                            .clickable(enabled = day != null) { day?.let(onDay) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (value != null) color.copy(alpha = .14f) else Night) {
                            Column(Modifier.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(n.toString(), fontSize = 12.sp, color = if (month.atDay(n) > today) Muted else Color.White)
                                if (value != null) Text(if (moneyMode) number(value, 0) + "$" else number(value) + "%",
                                    fontSize = 9.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                else if (day?.status == TradeReports.DayStatus.INCOMPLETE) Text("—", color = Muted)
                            }
                        }
                    }
                }
            }
        }
        ReportCaption("Touche une journée pour voir ton wallet et les transactions.")
    }
}

@Composable
private fun ReportDayDetail(wallet: Wallet, transactions: List<Transaction>, day: TradeReports.WalletDay,
    onClose: () -> Unit) {
    FullDialog(date(day.date.toString()), onClose) {
        ReportCard {
            ReportCaption("VALEUR DU WALLET · CAD")
            Text(money(day.value), fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
            ReportCaption(dayStatus(day))
            if (day.status != TradeReports.DayStatus.CONFIRMED && day.value != null)
                ReportCaption("Dernière valeur connue au ${day.valueDate?.let { date(it.toString()) } ?: "—"}")
            Text("${signed(day.pnl)} · ${percent(day.percent)}", color = tint(day.pnl), fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth()) {
                SmallReportMetric("Début", money(day.snapshot?.openingValue), Modifier.weight(1f))
                SmallReportMetric("Fin", money(day.snapshot?.closingValue), Modifier.weight(1f)) }
            Metric("Capital net investi", money(day.snapshot?.capitalInvested))
            Metric("Encaisse", money(day.snapshot?.cash))
        }
        Text("Transactions du jour", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        val tx = transactions.filter { it.date == day.date.toString() }.sortedBy { it.createdAt }
        if (tx.isEmpty()) ReportCaption("Aucune transaction enregistrée.")
        tx.forEach { ReportTransactionRow(wallet, it) }
        ReportCaption("Le P&L journalier inclut l’effet des ventes et des frais. Une vente conservée en encaisse n’est pas un retrait.")
    }
}

@Composable
internal fun ReportPeriodDialog(first: LocalDate, today: LocalDate, initial: ReportPeriod,
    onClose: () -> Unit, onApply: (ReportPeriod) -> Unit) {
    FullDialog("Choisir la période", onClose) { ReportPeriodFields(first, today, initial, onApply) }
}

@Composable
private fun ReportPeriodFields(first: LocalDate, today: LocalDate, initial: ReportPeriod,
    onApply: (ReportPeriod) -> Unit) {
    var mode by remember { mutableIntStateOf(0) }
    var start by remember { mutableStateOf(initial.start) }
    var end by remember { mutableStateOf(initial.end) }
    var year by remember { mutableIntStateOf(initial.end.year) }
    var month by remember { mutableIntStateOf(initial.end.monthValue) }
    ReportChips(listOf("Dates", "Mois", "Année", "Tout"), mode) { mode = it }
    if (mode == 0) {
        ReportDateButton("Du", start) { start = it }
        ReportDateButton("Au", end) { end = it }
    }
    if (mode in 1..2) {
        Choice("Année", year.toString(), (today.year downTo minOf(first.year, year)).map { it.toString() }) {
            year = (today.year downTo minOf(first.year, year)).elementAt(it) }
    }
    if (mode == 1) {
        val names = (1..12).map { java.time.Month.of(it).getDisplayName(TextStyle.FULL, Locale.FRENCH) }
        Choice("Mois", names[month - 1], names) { month = it + 1 }
    }
    val period = runCatching { when (mode) {
        1 -> ReportPeriod.month(YearMonth.of(year, month))
        2 -> ReportPeriod.year(year)
        3 -> ReportPeriod(first, today)
        else -> ReportPeriod(start, end)
    } }.getOrNull()
    ReportCaption(period?.let { "${date(it.start.toString())} → ${date(it.end.toString())} · dates incluses" }
        ?: "La date de début doit précéder la date de fin.")
    Button(onClick = { period?.let(onApply) }, enabled = period != null,
        modifier = Modifier.fillMaxWidth().testTag("report_period_apply"),
        colors = ButtonDefaults.buttonColors(containerColor = ReportAccent)) { Text("Appliquer la période") }
}

@Composable
private fun ReportDateButton(label: String, value: LocalDate, onDate: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        DatePickerDialog(context, { _, y, m, d -> onDate(LocalDate.of(y, m + 1, d)) },
            value.year, value.monthValue - 1, value.dayOfMonth).show()
    }, modifier = Modifier.fillMaxWidth()) { Text("$label : ${date(value.toString())}") }
}

@Composable
internal fun ReportExportDialog(wallet: Wallet, history: List<PortfolioDailySnapshot>, portfolioId: String?,
    first: LocalDate, today: LocalDate, initial: ReportPeriod, now: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(ReportExportKind.TRANSACTIONS) }
    var period by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var content by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val snapshot = content
        if (uri != null && snapshot != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("Fichier inaccessible.")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(snapshot) }
                }
                message = "CSV enregistré."
                Toast.makeText(context, "CSV enregistré.", Toast.LENGTH_SHORT).show()
            } catch (error: Exception) { message = "Export impossible : ${error.message}" }
            finally { busy = false; content = null }
        }
    }
    FullDialog("Exporter mes rapports", onClose) {
        ReportCaption(wallet.portfolios.find { it.id == portfolioId }?.name ?: "Tous les portefeuilles")
        ReportExportKind.entries.forEach { choice ->
            FilterChip(kind == choice, { kind = choice }, label = { Text(choice.label) },
                modifier = Modifier.fillMaxWidth().testTag("export_kind_${choice.name}")) }
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
            Text("${date(period.start.toString())} → ${date(period.end.toString())}") }
        ReportCaption("Choisir des dates, un mois, une année ou tout l’historique.")
        if (kind == ReportExportKind.CLOSED_TRADES) ReportCaption("Filtré par date de vente. Les achats plus anciens sont conservés dans le calcul du coût et dans le CSV.")
        if (kind == ReportExportKind.DAILY_WALLET) ReportCaption("Une ligne par jour, avec le statut de la valeur. Le PRE/AFTER n’est pas exporté comme clôture.")
        Button(onClick = {
            scope.launch {
                busy = true
                try {
                    // Capture full transaction history before filtering sales, so cost bases stay correct.
                    content = withContext(Dispatchers.Default) {
                        ReportCsv.export(wallet, history, kind, period, portfolioId, now) }
                    launcher.launch("MonWallet-${kind.name.lowercase()}-${period.start}-${period.end}.csv")
                } catch (error: Exception) { message = "Export impossible : ${error.message}" }
                finally { busy = false }
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("export_download"),
            colors = ButtonDefaults.buttonColors(containerColor = ReportAccent)) {
            Text(if (busy) "Préparation…" else "Télécharger en CSV") }
        message?.let { ReportCaption(it) }
    }
    if (picking) ReportPeriodDialog(first, today, period, { picking = false }) { period = it; picking = false }
}
