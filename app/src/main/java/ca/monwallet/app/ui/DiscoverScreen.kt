package ca.monwallet.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.*
import com.google.gson.Gson
import java.math.BigDecimal

private val periods = PerformancePeriod.entries.toList()
private val analystColumns = setOf("analystCount", "target", "targetHigh", "targetLow",
    "upside", "consensus", "opportunity")

@Composable
fun DiscoverScreen(vm: WalletViewModel, onDetail: (Security) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("discover_options", 0) }
    val gson = remember { Gson() }
    var tab by remember { mutableIntStateOf(0) }
    var period by remember { mutableStateOf(runCatching {
        PerformancePeriod.valueOf(prefs.getString("period", "M3") ?: "M3")
    }.getOrDefault(PerformancePeriod.M3)) }
    var filters by remember { mutableStateOf(prefs.getString("filters", null)?.let {
        runCatching { gson.fromJson(it, Filters::class.java) }.getOrNull()
    } ?: Filters()) }
    var columns by remember { mutableStateOf(MarketColumns.restore(
        prefs.getString("columns", null), MarketColumns.discoverDefault)) }
    var mode by remember { mutableStateOf("momentum") }
    var rows by remember { mutableStateOf<List<DiscoveryRow>>(emptyList()) }
    var status by remember { mutableStateOf("Chargement du classement…") }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var sort by remember { mutableStateOf("momentum") }
    var descending by remember { mutableStateOf(true) }
    var showColumns by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val wallet by vm.wallet.collectAsState()
    LaunchedEffect(period, filters, columns, mode, refresh, tab) {
        prefs.edit().putString("period", period.name).putString("filters", gson.toJson(filters))
            .putString("columns", columns.joinToString("|")).apply()
        if (tab != 0) return@LaunchedEffect
        busy = true
        try {
            val found = vm.services.discovery.screen(filters.copy(columns = columns), period) {
                status = it
            }
            rows = if (mode == "analyst" || columns.any { it in analystColumns })
                vm.services.discovery.analysts(found, filters,
                    onProgress = { status = it }, applyFilters = mode == "analyst")
            else found
            status = rows.size.toString() + " titres · échantillon FMP de 50 par bourse · " +
                if (mode == "analyst") "couverture analystes US selon disponibilité" else "cours non garantis temps réel"
            vm.services.foregroundSecurities.value = rows.take(16).map { it.security }
        } catch (e: Exception) {
            rows = emptyList()
            status = e.message ?: "Données de marché indisponibles."
        } finally {
            busy = false
        }
    }
    DisposableEffect(Unit) {
        onDispose { vm.services.foregroundSecurities.value = emptyList() }
    }
    val sorted = remember(rows, wallet.quotes, sort, descending, period) {
        val ordered = when (sort) {
            "ticker" -> rows.sortedBy { it.security.ticker }
            "name" -> rows.sortedBy { it.security.name }
            "exchange" -> rows.sortedBy { it.security.exchange }
            "price" -> rows.sortedBy { wallet.quotes[it.security.id]?.price ?: it.price }
            "dayAmount" -> rows.sortedBy { wallet.quotes[it.security.id]?.change }
            "dayPercent" -> rows.sortedBy { wallet.quotes[it.security.id]?.percent }
            "period" -> rows.sortedBy { it.performance(period) }
            "1S", "1M", "3M", "6M", "1A", "5A" -> rows.sortedBy { r ->
                r.performance(PerformancePeriod.entries.first { it.label == sort }) }
            "cagr5" -> rows.sortedBy { r -> MomentumEngine.analyze(r.points,
                PerformancePeriod.Y5, r.averageVolume?.multiply(r.price)?.toDouble())?.cagr5y }
            "cap" -> rows.sortedBy { it.cap }
            "pe" -> rows.sortedBy { it.pe }
            "dividend" -> rows.sortedBy { it.dividendYield }
            "momentum" -> rows.sortedBy { it.metrics?.score }
            "upside" -> rows.sortedBy { it.analyst?.target?.let { target ->
                (target - it.price).divide(it.price, java.math.MathContext.DECIMAL128) } }
            "opportunity" -> rows.sortedBy { it.opportunityScore }
            else -> rows.sortedBy { it.metrics?.score }
        }
        if (descending) ordered.reversed() else ordered
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(bottom = 70.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Chips(listOf("Top performance", "Filtres", "Idées"), tab) { tab = it } }
                if (tab == 0) TextButton(onClick = { refresh++ }, enabled = !busy) { Text("↻") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(status, fontSize = 10.sp, color = Muted)
        }
        when (tab) {
            1 -> item {
                FilterPanel(filters, { filters = it }, prefs, gson) { tab = 0; refresh++ }
            }
            2 -> item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Idea("Momentum soutenu", "Régularité et liquidité; anti-pump actif") {
                        filters = Filters(minMomentum = 75); mode = "momentum"; period = PerformancePeriod.M3; tab = 0
                    }
                    Idea("Large Caps en forme", "Cap. ≥ 10 G$ · 3M ≥ 10 % · 6M ≥ 15 %") {
                        filters = Filters(cap = BigDecimal("10000000000"), minPerformance = 10.0,
                            minPerformance6M = 15.0)
                        mode = "momentum"; period = PerformancePeriod.M3; tab = 0
                    }
                    Idea("Dividendes + momentum", "Rendement ≥ 2 % et tendance positive") {
                        filters = Filters(dividendMin = BigDecimal("2"), minPerformance = 0.0)
                        mode = "momentum"; period = PerformancePeriod.M3; tab = 0
                    }
                    Idea("Près du sommet 52 semaines", "Tendance positive et titres liquides") {
                        filters = Filters(minPerformance = 0.0, distanceHigh52Max = 10.0)
                        mode = "momentum"; period = PerformancePeriod.M3; tab = 0
                    }
                    Idea("Croissance des revenus", "Croissance positive, tendance positive") {
                        filters = Filters(revenueGrowthMin = BigDecimal.ZERO, minPerformance = 0.0)
                        mode = "momentum"; period = PerformancePeriod.M3; tab = 0
                    }
                    Idea("Qualité + stabilité", "ROE positif, drawdown ≤ 20 %") {
                        filters = Filters(roeMin = BigDecimal.ZERO, maxDrawdown = 20.0)
                        mode = "momentum"; period = PerformancePeriod.M3; tab = 0
                    }
                    Idea("Meilleure projection analystes", "≥ 5 analystes · upside ≥ 10 % · anti-pump") {
                        filters = Filters(); mode = "analyst"; period = PerformancePeriod.M3; tab = 0
                        sort = "opportunity"
                    }
                }
            }
            else -> {
                item {
                    PeriodPills(period) { period = it }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (mode == "analyst") "PROJECTION ANALYSTES" else "MOMENTUM SOUTENU",
                            Modifier.weight(1f), fontSize = 11.sp, color = Muted)
                        TextButton(onClick = { showColumns = true }) { Text("⚙ Colonnes", fontSize = 11.sp) }
                    }
                    DiscoverHeader(columns, period, scroll) { key ->
                        if (sort == key) descending = !descending
                        else { sort = key; descending = true }
                    }
                }
                itemsIndexed(sorted, key = { _, row -> row.security.id }) { index, row ->
                    val quote = wallet.quotes[row.security.id]
                    DiscoverLine(index + 1, row, quote, columns, period, scroll) {
                        vm.run {
                            vm.services.repo.put("security", row.security.id, row.security)
                            vm.services.repo.points(row.security.id, row.points)
                        }
                        onDetail(row.security)
                    }
                }
                if (sorted.isEmpty() && !busy) item {
                    Text(if (status.contains("Clé FMP")) status
                        else "Aucun titre ne satisfait ces filtres avec les données disponibles.",
                        Modifier.padding(18.dp), color = Muted)
                }
            }
        }
    }
    if (showColumns) ColumnPicker(columns, MarketColumns.all.keys.toList(), { columns = it },
        { showColumns = false })
}

@Composable
fun PeriodPills(selected: PerformancePeriod, choose: (PerformancePeriod) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        periods.forEach { p ->
            FilterChip(selected == p, onClick = { choose(p) }, label = { Text(p.label, fontSize = 11.sp) },
                modifier = Modifier.height(32.dp))
        }
    }
}

@Composable private fun Idea(title: String, subtitle: String, click: () -> Unit) {
    Surface(onClick = click, color = Panel, shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = Muted, fontSize = 11.sp)
            }
            Text("›", color = Green)
        }
    }
}

@Composable private fun DiscoverHeader(columns: List<String>, period: PerformancePeriod,
    scroll: androidx.compose.foundation.ScrollState, onSort: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        if ("ticker" in columns)
            Text("TICKER", Modifier.width(78.dp).clickable { onSort("ticker") },
                fontSize = 9.sp, color = Muted)
        Row(Modifier.horizontalScroll(scroll)) {
            columns.filter { it != "ticker" }.forEach { key ->
                Text(MarketColumns.label(key, period).uppercase(),
                    Modifier.width((MarketColumns.all[key]?.width ?: 86).dp)
                        .clickable { onSort(key) }, fontSize = 9.sp, color = Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    HorizontalDivider(color = Border)
}

@Composable private fun DiscoverLine(rank: Int, row: DiscoveryRow, quote: Quote?,
    columns: List<String>, period: PerformancePeriod,
    scroll: androidx.compose.foundation.ScrollState, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(43.dp).clickable(onClick = click),
        verticalAlignment = Alignment.CenterVertically) {
        if ("ticker" in columns)
            Row(Modifier.width(78.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(rank.toString(), Modifier.width(20.dp), color = Muted, fontSize = 10.sp)
                Text(row.security.ticker, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        Row(Modifier.horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
            columns.filter { it != "ticker" }.forEach { key ->
                val value = discoveryCell(row, quote, key, period)
                val width = (MarketColumns.all[key]?.width ?: 86).dp
                if (key == "price") Column(Modifier.width(width)) {
                    Text(value, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(quote?.let { when (NormalizedQuote.from(it).freshness) {
                        QuoteFreshness.REALTIME -> "Temps réel"
                        QuoteFreshness.DELAYED -> "Diff. ${it.delay} min"
                        QuoteFreshness.CACHED -> "Cache"
                        QuoteFreshness.STALE -> "Cache ancien"
                    } } ?: "FMP · délai ?", color = Muted, fontSize = 8.sp, maxLines = 1)
                } else Text(value, Modifier.width(width),
                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (key in listOf("period", "momentum", "opportunity") &&
                        !value.startsWith("-") && value != "—") Green else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    HorizontalDivider(color = Border.copy(alpha = .6f))
}

private fun discoveryCell(r: DiscoveryRow, q: Quote?, key: String,
    period: PerformancePeriod): String = when (key) {
    "name" -> r.security.name
    "exchange" -> r.security.exchange
    "currency" -> r.security.currency
    "price" -> money(q?.price ?: r.price, r.security.currency)
    "dayAmount" -> signed(q?.change)
    "dayPercent" -> percent(q?.percent)
    "period" -> percent(r.performance(period))
    "1S", "1M", "3M", "6M", "1A", "5A" ->
        percent(r.performance(PerformancePeriod.entries.first { it.label == key }))
    "cagr5" -> percent(MomentumEngine.analyze(r.points, PerformancePeriod.Y5,
        r.averageVolume?.multiply(r.price)?.toDouble())?.cagr5y)
    "momentum" -> r.metrics?.score?.toString() ?: "—"
    "opportunity" -> r.opportunityScore?.toString() ?: "—"
    "cap" -> r.cap?.let { number(it.divide(BigDecimal("1000000000"),
        java.math.MathContext.DECIMAL128), 1) + " G" } ?: "—"
    "pe" -> number(r.pe)
    "forwardPe", "ps", "pb", "evEbitda", "roe", "roa", "revenueGrowth",
    "epsGrowth", "beta" -> number(r.fundamentals[key])
    "dividend" -> percent(r.dividendYield)
    "sector" -> r.sector.ifBlank { "—" }
    "industry" -> r.industry.ifBlank { "—" }
    "volume" -> number(r.volume, 0)
    "avgVolume" -> number(r.averageVolume, 0)
    "dollarVolume" -> number(r.averageVolume?.multiply(r.price), 0)
    "high52" -> money(q?.high52, r.security.currency)
    "distance52" -> q?.high52?.let { if (it.signum() > 0)
        percent((r.price - it).multiply(BigDecimal(100)).divide(it,
            java.math.MathContext.DECIMAL128)) else "—" } ?: "—"
    "analystCount" -> r.analyst?.let {
        listOfNotNull(it.buy, it.hold, it.sell).sum().toString() } ?: "—"
    "target" -> money(r.analyst?.target, r.security.currency)
    "targetHigh" -> money(r.analyst?.high, r.security.currency)
    "targetLow" -> money(r.analyst?.low, r.security.currency)
    "upside" -> r.analyst?.target?.let {
        percent((it - r.price).multiply(BigDecimal(100)).divide(r.price,
            java.math.MathContext.DECIMAL128)) } ?: "—"
    "consensus" -> r.analyst?.let { a ->
        if ((a.buy ?: 0) > (a.hold ?: 0) + (a.sell ?: 0)) "Buy"
        else if ((a.sell ?: 0) > (a.buy ?: 0) + (a.hold ?: 0)) "Sell" else "Hold"
    } ?: "—"
    else -> "—"
}

@Composable private fun FilterPanel(initial: Filters, update: (Filters) -> Unit,
    prefs: android.content.SharedPreferences, gson: Gson, apply: () -> Unit) {
    var presetName by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(prefs.all.keys.filter { it.startsWith("preset:") }.sorted()) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("MARCHÉS", color = Green)
        Row {
            listOf("Canada", "USA", "Tous").forEach { label ->
                val codes = when (label) {
                    "Canada" -> "TSX,TSXV"; "USA" -> "NASDAQ,NYSE,AMEX"
                    else -> "NASDAQ,NYSE,AMEX,TSX,TSXV"
                }
                FilterChip(initial.exchange == codes, onClick = { update(initial.copy(exchange = codes)) },
                    label = { Text(label) })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("TSX", "TSXV", "NASDAQ", "NYSE", "AMEX").forEach { e ->
                val values = initial.exchange.split(',').filter { it.isNotBlank() }
                FilterChip(e in values, onClick = { update(initial.copy(exchange =
                    (if (e in values) values - e else values + e).joinToString(","))) },
                    label = { Text(e) })
            }
        }
        Text("CAPITALISATION MINIMUM", color = Green)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("0", "100000000", "500000000", "1000000000", "5000000000",
                "10000000000", "50000000000", "100000000000").forEach { v ->
                FilterChip(initial.cap.toPlainString() == v, onClick = {
                    update(initial.copy(cap = BigDecimal(v))) }, label = { Text(
                    when (v) { "0" -> "Toutes"; "100000000" -> ">100 M";
                        "500000000" -> ">500 M"; else -> ">" +
                        number(BigDecimal(v).divide(BigDecimal("1000000000")), 0) + " G" }) })
            }
        }
        FilterNumber("Cap. personnalisée", initial.cap, false) { update(initial.copy(cap = it ?: BigDecimal.ZERO)) }
        FilterNumber("Prix minimum", initial.price, false) { update(initial.copy(price = it ?: BigDecimal.ZERO)) }
        FilterNumber("Prix maximum", initial.priceMax) { update(initial.copy(priceMax = it)) }
        FilterNumber("Volume moyen min.", initial.volume, false) { update(initial.copy(volume = it ?: BigDecimal.ZERO)) }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("100000", "250000", "500000", "1000000", "5000000").forEach { n ->
                FilterChip(initial.volume == BigDecimal(n), onClick = {
                    update(initial.copy(volume = BigDecimal(n))) },
                    label = { Text(if (n.length < 7) n.dropLast(3) + "k" else n.dropLast(6) + "M") })
            }
        }
        FilterNumber("Dollar volume min.", initial.dollarVolumeMin) { update(initial.copy(dollarVolumeMin = it)) }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("5000000", "10000000", "25000000").forEach { n ->
                FilterChip(initial.dollarVolumeMin == BigDecimal(n), onClick = {
                    update(initial.copy(dollarVolumeMin = BigDecimal(n))) },
                    label = { Text(n.dropLast(6) + " M$") })
            }
        }
        Text("VALEUR ET CROISSANCE", color = Green)
        FilterNumber("P/E minimum", initial.peMin) { update(initial.copy(peMin = it)) }
        FilterNumber("P/E maximum", initial.peMax) { update(initial.copy(peMax = it)) }
        FilterToggle("P/E positif seulement", initial.pePositive) { update(initial.copy(pePositive = it)) }
        FilterNumber("Dividend Yield min. (%)", initial.dividendMin) { update(initial.copy(dividendMin = it)) }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf(null, 0, 1, 2, 3, 4, 5).forEach { n ->
                FilterChip(initial.dividendMin == n?.let { BigDecimal(it) }, onClick = {
                    update(initial.copy(dividendMin = n?.let { BigDecimal(it) })) },
                    label = { Text(n?.let { "> $it %" } ?: "Tous") })
            }
        }
        FilterNumber("Revenue Growth min.", initial.revenueGrowthMin) { update(initial.copy(revenueGrowthMin = it)) }
        FilterNumber("EPS Growth min.", initial.epsGrowthMin) { update(initial.copy(epsGrowthMin = it)) }
        FilterNumber("FCF Growth min.", initial.fcfGrowthMin) { update(initial.copy(fcfGrowthMin = it)) }
        FilterNumber("ROE min.", initial.roeMin) { update(initial.copy(roeMin = it)) }
        FilterNumber("ROA min.", initial.roaMin) { update(initial.copy(roaMin = it)) }
        FilterNumber("ROIC min.", initial.roicMin) { update(initial.copy(roicMin = it)) }
        FilterNumber("Marge nette min.", initial.netMarginMin) { update(initial.copy(netMarginMin = it)) }
        FilterNumber("Debt/Equity max.", initial.debtEquityMax) { update(initial.copy(debtEquityMax = it)) }
        Text("SECTEURS", color = Green)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("Technology", "Financial Services", "Healthcare", "Industrials",
                "Consumer Cyclical", "Consumer Defensive", "Energy", "Basic Materials",
                "Communication Services", "Real Estate", "Utilities").forEach { sector ->
                FilterChip(initial.sector.split(',').contains(sector), onClick = {
                    val active = initial.sector.split(',').filter { it.isNotBlank() }
                    update(initial.copy(sector = (if (sector in active) active - sector
                        else active + sector).joinToString(",")))
                }, label = { Text(sector) })
            }
        }
        Text("MOMENTUM", color = Green)
        FilterNumber("Performance min. (%)", initial.minPerformance?.let(BigDecimal::valueOf)) {
            update(initial.copy(minPerformance = it?.toDouble())) }
        FilterNumber("Performance 6M min. (%)", initial.minPerformance6M?.let(BigDecimal::valueOf)) {
            update(initial.copy(minPerformance6M = it?.toDouble())) }
        FilterNumber("Momentum minimum", initial.minMomentum?.let { BigDecimal(it) }) {
            update(initial.copy(minMomentum = it?.toInt())) }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf(null, 10.0, 15.0, 20.0, 25.0, 30.0).forEach { n ->
                FilterChip(initial.maxSingleDay == n, onClick = { update(initial.copy(maxSingleDay = n)) },
                    label = { Text(n?.let { it.toInt().toString() + " %" } ?: "Sans limite") })
            }
        }
        FilterNumber("Drawdown max. (%)", initial.maxDrawdown?.let(BigDecimal::valueOf)) {
            update(initial.copy(maxDrawdown = it?.toDouble())) }
        FilterNumber("Distance 52W High max. (%)", initial.distanceHigh52Max?.let(BigDecimal::valueOf)) {
            update(initial.copy(distanceHigh52Max = it?.toDouble())) }
        FilterNumber("Semaines positives min. (%)", initial.minPositiveWeeks?.let(BigDecimal::valueOf)) {
            update(initial.copy(minPositiveWeeks = it?.toDouble())) }
        FilterToggle("Anti-pump", initial.antiPump) { update(initial.copy(antiPump = it)) }
        FilterToggle("Historique suffisant", initial.requireHistory) { update(initial.copy(requireHistory = it)) }
        FilterToggle("Exclure ETF", initial.excludeEtf) { update(initial.copy(excludeEtf = it)) }
        Text("ANALYSTES", color = Green)
        FilterNumber("Analystes minimum", BigDecimal(initial.analystMin), false) {
            update(initial.copy(analystMin = it?.toInt() ?: 0)) }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf(1, 3, 5, 10, 20).forEach { n ->
                FilterChip(initial.analystMin == n, onClick = {
                    update(initial.copy(analystMin = n)) }, label = { Text(n.toString()) })
            }
        }
        FilterNumber("Upside moyen min. (%)", BigDecimal.valueOf(initial.upsideMin), false) {
            update(initial.copy(upsideMin = it?.toDouble() ?: 0.0)) }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf(0, 5, 10, 15, 20, 25).forEach { n ->
                FilterChip(initial.upsideMin == n.toDouble(), onClick = {
                    update(initial.copy(upsideMin = n.toDouble())) },
                    label = { Text("$n %") })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("Tous", "Hold+", "Buy+", "Strong Buy").forEach { c ->
                FilterChip(initial.consensusMin == c, onClick = { update(initial.copy(consensusMin = c)) },
                    label = { Text(c) })
            }
        }
        Row {
            listOf(30, 90, 180).forEach { days ->
                FilterChip(initial.targetAgeDays == days, onClick = {
                    update(initial.copy(targetAgeDays = days)) }, label = { Text(days.toString() + " j") })
            }
        }
        FilterToggle("Exclure forte dispersion", initial.excludeDispersion) {
            update(initial.copy(excludeDispersion = it)) }
        OutlinedTextField(presetName, { presetName = it }, label = { Text("Nom du preset") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Row {
            TextButton(onClick = {
                if (presetName.isNotBlank()) {
                    prefs.edit().putString("preset:" + presetName.trim(), gson.toJson(initial)).apply()
                    saved = prefs.all.keys.filter { it.startsWith("preset:") }.sorted()
                }
            }) { Text("Enregistrer comme preset") }
        }
        saved.forEach { key ->
            TextButton(onClick = {
                prefs.getString(key, null)?.let { raw ->
                    runCatching { gson.fromJson(raw, Filters::class.java) }.getOrNull()?.let(update)
                }
            }) { Text(key.removePrefix("preset:")) }
        }
        Button(onClick = apply, modifier = Modifier.fillMaxWidth()) { Text("Appliquer les filtres") }
    }
}

@Composable private fun FilterNumber(label: String, value: BigDecimal?,
    optional: Boolean = true, update: (BigDecimal?) -> Unit) {
    var raw by remember { mutableStateOf(value?.stripTrailingZeros()?.toPlainString() ?: "") }
    LaunchedEffect(value) {
        if (raw.replace(',', '.').toBigDecimalOrNull() != value)
            raw = value?.stripTrailingZeros()?.toPlainString() ?: ""
    }
    OutlinedTextField(raw, { text ->
        raw = text
        val parsed = text.replace(',', '.').toBigDecimalOrNull()
        if (parsed != null || optional && text.isBlank()) update(parsed)
    }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable private fun FilterToggle(label: String, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp)
        Switch(enabled, onCheckedChange = change)
    }
}
