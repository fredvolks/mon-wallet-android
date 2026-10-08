package ca.monwallet.app.ui

import ca.monwallet.app.R

import androidx.compose.ui.res.stringResource

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import ca.monwallet.app.database.Cache
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.Finnhub
import ca.monwallet.app.marketdata.NoFinancialCoverage
import ca.monwallet.app.marketdata.NormalizedQuote
import ca.monwallet.app.marketdata.MarketSession
import ca.monwallet.app.marketdata.quoteFreshnessLabel
import kotlinx.coroutines.*

@Composable
fun DetailScreen(
    security: Security,
    w: Wallet,
    vm: WalletViewModel,
    onBuy: (Security) -> Unit,
    onSell: (Security) -> Unit,
    onAlert: (Security) -> Unit,
    onWatch: (Security) -> Unit,
    onEdit: (Transaction) -> Unit,
    onNotify: (Security) -> Unit,
) {
    val s = vm.services
    val context = LocalContext.current
    val q = w.quotes[security.id]
    var tab by remember { mutableIntStateOf(0) }
    var range by remember { mutableIntStateOf(2) }
    var chartKind by remember { mutableIntStateOf(0) }
    var position by remember { mutableStateOf<String?>(null) }
    var prices by remember { mutableStateOf<List<Point>>(emptyList()) }
    var fundamentals by remember { mutableStateOf<Fundamentals?>(null) }
    var analyst by remember { mutableStateOf<Analyst?>(null) }
    var financeLoading by remember { mutableStateOf(false) }
    var analystLoading by remember { mutableStateOf(false) }
    var analystLoaded by remember { mutableStateOf(false) }
    var financeError by remember { mutableStateOf<String?>(null) }
    var financeNoData by remember { mutableStateOf(false) }
    var financeFetchedAt by remember { mutableStateOf<Long?>(null) }
    var analystError by remember { mutableStateOf<String?>(null) }
    var financeRetry by remember { mutableIntStateOf(0) }
    var analystRetry by remember { mutableIntStateOf(0) }
    var financePeriod by remember { mutableIntStateOf(0) }
    var news by remember { mutableStateOf<List<News>>(emptyList()) }
    var earnings by remember { mutableStateOf<List<Earnings>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val ranges = listOf("1J", "1S", "1M", "3M", "6M", "1A", "5A", "Tout")
    val codes = listOf("1d", "5d", "1mo", "3mo", "6mo", "1y", "5y", "max")
    LaunchedEffect(security.id, range) {
        loading = true
        status = null
        prices = emptyList()
        try {
            runCatching { s.refreshQuote(security) }
            prices = s.market.history(security, codes[range], if (range == 0) "5m" else "1d")
            if (range >= 6) s.repo.points(security.id, prices)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = e.message
            val days = listOf(1, 7, 31, 92, 184, 366, 1827, 50000)[range]
            prices =
                w.prices.filter {
                    it.securityId == security.id &&
                        it.date >= java.time.LocalDate.now().minusDays(days.toLong()).toString()
                }
        } finally {
            loading = false
        }
    }
    LaunchedEffect(security.id, tab, financeRetry, analystRetry) {
        if (tab in listOf(0, 1, 2, 3)) {
            status = null
            try {
                if (tab == 1 && (fundamentals == null || financeRetry > 0)) {
                    financeLoading = true
                    financeError = null
                    financeNoData = false
                    val cached = s.repo.dao.cache().find { it.id == "financials:v2:${security.id}" }
                    if (cached != null && financeRetry == 0) {
                        fundamentals = s.repo.gson.fromJson(cached.payload, Fundamentals::class.java)
                        financeFetchedAt = cached.fetchedAt
                    }
                    if (cached == null || financeRetry > 0 || System.currentTimeMillis() - cached.fetchedAt > 6 * 3600_000L) {
                        try {
                            val fresh = s.market.fundamentals(security)
                            fundamentals = fresh
                            financeFetchedAt = System.currentTimeMillis()
                            s.repo.dao.cache(Cache("financials:v2:${security.id}", "fundamentals", s.repo.gson.toJson(fresh), financeFetchedAt!!))
                        } catch (e: CancellationException) { throw e }
                        catch (e: NoFinancialCoverage) { financeNoData = true }
                        catch (e: Exception) { financeError = e.message ?: "Service indisponible." }
                    }
                    financeLoading = false
                    financeRetry = 0
                }
                if (tab == 2 && (!analystLoaded || analystRetry > 0)) {
                    analystLoading = true
                    analystError = null
                    val cached = s.repo.dao.cache().find { it.id == "analyst:v2:${security.id}" }
                    if (cached != null && analystRetry == 0) {
                        analyst = s.repo.gson.fromJson(cached.payload, Analyst::class.java)
                        analystLoaded = true
                    }
                    if (cached == null || analystRetry > 0 || System.currentTimeMillis() - cached.fetchedAt > 3600_000L) {
                        try {
                            analyst = s.market.analyst(security)
                            analystLoaded = true
                            analyst?.let { a -> s.repo.dao.cache(Cache("analyst:v2:${security.id}", "analyst", s.repo.gson.toJson(a), System.currentTimeMillis())) }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { analystError = e.message ?: "Service indisponible." }
                    }
                    analystLoading = false
                    analystRetry = 0
                }
                if (tab == 3) news = s.market.news(security)
                if (tab == 0) {
                    val key = s.secure.get("finnhub_key")
                    if (!key.isNullOrBlank() && security.type == "STOCK")
                        earnings = Finnhub(key).earnings(security)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = e.message
                financeLoading = false
                analystLoading = false
            }
        }
    }
    val result = runCatching { w.result(position) }.getOrNull()
    val holding = result?.holdings?.find { it.securityId == security.id }
    val own =
        w.portfolios.filter { p ->
            w.transactions.any { it.securityId == security.id && it.portfolioId == p.id }
        }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(security)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(security.ticker, fontWeight = FontWeight.Bold, fontSize = 25.sp)
                    Caption(security.name)
                    Badge("${security.exchange} · ${security.currency} · ${security.type}")
                }
                IconButton(
                    onClick = {
                        vm.run {
                            s.repo.setting(
                                "favorite:${security.id}",
                                (w.settings["favorite:${security.id}"] != "true").toString(),
                            )
                        }
                    }
                ) {
                    Icon(
                        if (w.settings["favorite:${security.id}"] == "true") Icons.Outlined.Star
                        else Icons.Outlined.StarOutline,
                        "Favori",
                        tint = Green,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(money(q?.price, security.currency), fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(
                "${signed(q?.change,security.currency)}  (${percent(q?.percent)})",
                color = tint(q?.change),
                fontSize = 17.sp,
            )
            val normalized = q?.takeIf { security.currency == "USD" }?.let { NormalizedQuote.from(it) }
            val extendedPrice = when (normalized?.marketSession) {
                MarketSession.PRE_MARKET -> normalized.preMarketPrice
                MarketSession.AFTER_HOURS -> normalized.afterHoursPrice
                else -> null
            }
            if (extendedPrice != null && normalized != null) {
                val delta = if (normalized.marketSession == MarketSession.PRE_MARKET)
                    normalized.preMarketChange else normalized.afterHoursChange
                val changePercent = if (normalized.marketSession == MarketSession.PRE_MARKET)
                    normalized.preMarketChangePercent else normalized.afterHoursChangePercent
                val timestamp = if (normalized.marketSession == MarketSession.PRE_MARKET)
                    normalized.preMarketTimestamp else normalized.afterHoursTimestamp
                Caption((if (normalized.marketSession == MarketSession.PRE_MARKET) "☀ Pre-market" else "☾ After-hours") +
                    " · ${money(extendedPrice,security.currency)} · ${signed(delta,security.currency)} (${percent(changePercent)})" +
                    (timestamp?.let { " · ${time(it)}" } ?: ""))
            }
            Caption(
                when (normalized?.marketSession) {
                    MarketSession.PRE_MARKET -> "PRE"
                    MarketSession.REGULAR -> "OUVERT"
                    MarketSession.AFTER_HOURS -> "AFTER"
                    MarketSession.CLOSED -> "FERMÉ"
                    null -> if (q?.marketOpen == true) "Marché ouvert" else "État du marché indisponible"
                }
            )
            Caption(
                q?.let {
                    "${it.source} · ${quoteFreshnessLabel(it)}\nDernier cours : ${time(it.timestamp)}"
                } ?: "Donnée indisponible"
            )
            Chips(ranges, range) { range = it }
            Chips(listOf(R.string.chart_line, R.string.chart_area, R.string.chart_candles,
                R.string.chart_volume).map { stringResource(it) }, chartKind) { chartKind = it }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val supported = when (chartKind) {
                2 -> prices.isNotEmpty() && prices.all { it.open != null && it.high != null && it.low != null }
                3 -> prices.isNotEmpty() && prices.all { it.volume != null }
                else -> true
            }
            if (!supported) Caption(stringResource(R.string.chart_data_unavailable))
            else if (prices.isNotEmpty()) TradingViewChartView(prices, listOf("line", "area", "candles", "volume")[chartKind])
            else Caption(stringResource(R.string.ui_historique_indisponible_fb840))
            if (prices.isNotEmpty())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Caption(date(prices.first().date))
                    Caption(date(prices.last().date))
                }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onBuy(security) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_ajouter_des_parts_1fe20), fontSize = 12.sp)
                }
                OutlinedButton(onClick = { onSell(security) }, modifier = Modifier.weight(1f),
                    enabled = own.isNotEmpty()) {
                    Text(stringResource(R.string.sell_shares), color = Red, fontSize = 12.sp)
                }
                IconButton(onClick = { onAlert(security) }) { Icon(Icons.Outlined.NotificationsNone, stringResource(R.string.ui_creer_une_alerte_e1be5)) }
            }
        }
        item {
            if (holding != null) {
                Section(stringResource(R.string.ui_ma_position_d062c))
                Chips(
                    listOf("Tous") + own.map { it.name },
                    if (position == null) 0 else own.indexOfFirst { it.id == position } + 1,
                ) {
                    position = if (it == 0) null else own[it - 1].id
                }
                CardBlock {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric(stringResource(R.string.ui_quantite_09a38), number(holding.quantity, 6))
                        Metric(stringResource(R.string.ui_prix_moyen_662b2), money(holding.average, security.currency))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric(stringResource(R.string.ui_cout_des_parts_78a7d), money(holding.costCad))
                        Metric(stringResource(R.string.ui_valeur_actuelle_e0c0f), money(holding.value))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric(stringResource(R.string.ui_gain_non_realise_d8ef5), signed(holding.pnl), tint(holding.pnl))
                        Metric(stringResource(R.string.ui_jour_240ce), signed(holding.day), tint(holding.day))
                    }
                    Caption(
                        "Gain titre : ${signed(holding.priceGain)} · Effet FX : ${signed(holding.fxGain)}"
                    )
                    Caption(
                        "Gain réalisé : ${signed(holding.realized)} · Dividendes : ${money(holding.dividends)}"
                    )
                }
            }
            Chips(
                listOf("Aperçu", "Finances", "Analystes", "Actualités", "Alertes", "Historique"),
                tab,
            ) {
                tab = it
            }
            status?.let { Caption(it) }
        }
        when (tab) {
            0 -> {
                item {
                    Row {
                        TextButton(onClick = { onWatch(security) }) { Text(stringResource(R.string.ui_watchlist_f9624)) }
                        TextButton(onClick = { onNotify(security) }) { Text(stringResource(R.string.ui_notifications_753a2)) }
                    }
                }
                item {
                    Section(stringResource(R.string.ui_donnees_de_marche_919e8))
                    CardBlock {
                        Metric(stringResource(R.string.ui_volume_3b18e), number(q?.volume, 0))
                        Metric(stringResource(R.string.ui_volume_moyen_75b6a), number(q?.averageVolume, 0))
                        Metric(stringResource(R.string.ui_sommet_52_semaines_60b01), money(q?.high52, security.currency))
                        Metric(stringResource(R.string.ui_creux_52_semaines_2d067), money(q?.low52, security.currency))
                    }
                }
                item {
                    Section(stringResource(R.string.ui_earnings_ad772))
                    if (earnings.isEmpty())
                        Caption(
                            stringResource(R.string.earnings_unavailable)
                        )
                }
                items(earnings) { e ->
                    CardBlock {
                        Text(e.date?.let(::date) ?: "Date indisponible")
                        Caption("BPA : ${number(e.epsActual)} · attendu ${number(e.epsEstimate)}")
                        Caption(
                            "Revenus : ${number(e.revenueActual,0)} · attendus ${number(e.revenueEstimate,0)}"
                        )
                        val surprise =
                            e.epsActual?.let { actual ->
                                e.epsEstimate
                                    ?.takeIf { it.signum() != 0 }
                                    ?.let { (actual - it).pct(it.abs()) }
                            }
                        if (surprise != null)
                            Text(
                                "${if(surprise>=ZERO)"Beat"else"Miss"} ${percent(surprise)}",
                                color = tint(surprise),
                            )
                    }
                }
            }
            1 -> {
                item {
                    Section(
                        if (security.type == "ETF") "Données du fonds" else "Données financières"
                    )
                    if (financeLoading && fundamentals == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (financeError != null && fundamentals == null) {
                        Caption(stringResource(R.string.finance_load_failed))
                        Caption(financeError.orEmpty())
                        TextButton(onClick = { financeRetry++ }) { Text(stringResource(R.string.action_retry)) }
                    }
                    if (!financeLoading && fundamentals == null && (financeNoData || financeError == null))
                        Caption(stringResource(R.string.finance_no_coverage))
                    fundamentals?.asOf?.let { Caption(stringResource(R.string.finance_as_of, it)) }
                }
                val metrics =
                    if (security.type == "ETF")
                        listOf(
                            "Émetteur",
                            "AUM",
                            "MER",
                            "Distribution",
                            "Rendement distribution",
                            "Nombre de holdings",
                            "NAV",
                        )
                    else
                        listOf(
                            "Capitalisation (M)",
                            "P/E",
                            "Forward P/E",
                            "P/S",
                            "P/B",
                            "EV/EBITDA",
                            "Revenus",
                            "Bénéfice net",
                            "BPA",
                            "Marge nette %",
                            "Marge brute %",
                            "Cash",
                            "Dette totale",
                            "Dette nette",
                            "Free cash flow",
                            "ROE %",
                            "ROA %",
                            "Croissance revenus %",
                            "Croissance BPA %",
                            "Rendement dividende %",
                            "Payout %",
                            "Volume",
                            "Volume moyen",
                            "Sommet 52 semaines",
                            "Creux 52 semaines",
                            "Dividende annuel",
                            "Ex-dividend date",
                            "Payment date",
                            "Operating cash flow",
                            "Dette long terme",
                            "Dette court terme",
                        )
                items(metrics.filter { fundamentals?.metrics?.containsKey(it) == true }) { label ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val period = fundamentals?.annual?.get(label)?.lastOrNull()?.first
                        Caption(if (period != null) "$label · $period" else label)
                        Text(financialValue(label, fundamentals?.metrics?.get(label).orEmpty(), security.currency), fontSize = 14.sp)
                    }
                    HorizontalDivider(color = Border)
                }
                if (fundamentals?.quarterly?.isNotEmpty() == true) item {
                    Chips(listOf(stringResource(R.string.finance_annual), stringResource(R.string.finance_quarterly)), financePeriod) { financePeriod = it }
                }
                val seriesMap = if (financePeriod == 1) fundamentals?.quarterly else fundamentals?.annual
                items(seriesMap?.entries?.toList() ?: emptyList()) { (name, series) ->
                    Section(name)
                    val sorted = series.sortedBy { it.first }.takeLast(8)
                    Chart(
                        sorted.map { it.second },
                        modifier = Modifier.height(100.dp),
                        labels = true,
                    )
                    sorted.asReversed().forEach { (period, value) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Caption(period)
                            Text(financialValue(name, value.toPlainString(), security.currency),
                                fontSize = 12.sp)
                        }
                    }
                }
                item {
                    fundamentals?.let {
                        Caption(stringResource(R.string.finance_source, it.source))
                        financeFetchedAt?.let { fetched ->
                            Caption(stringResource(R.string.finance_updated_at, time(fetched)))
                        }
                    }
                }
            }
            2 -> {
                val a = analyst
                item {
                    Section(stringResource(R.string.ui_consensus_des_analystes_f88e4))
                    if (analystLoading && !analystLoaded) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (analystError != null) {
                        Caption(stringResource(R.string.analyst_load_failed))
                        Caption(analystError.orEmpty())
                        TextButton(onClick = { analystRetry++ }) { Text(stringResource(R.string.action_retry)) }
                    }
                    if (analystLoaded && a == null && analystError == null)
                        Caption(stringResource(R.string.analyst_no_coverage))
                    if (a != null) CardBlock {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Metric(stringResource(R.string.ui_buy_7ceca), a?.buy?.toString() ?: "—", Green)
                            Metric(stringResource(R.string.ui_hold_3bd32), a?.hold?.toString() ?: "—", Muted)
                            Metric(stringResource(R.string.ui_sell_fa3cf), a?.sell?.toString() ?: "—", Red)
                        }
                        Spacer(Modifier.height(18.dp))
                        val total = listOfNotNull(a.buy, a.hold, a.sell).sum()
                        Caption(stringResource(R.string.analyst_total, total))
                        Spacer(Modifier.height(8.dp))
                        Metric(stringResource(R.string.analyst_current), money(q?.price, security.currency))
                        Metric(stringResource(R.string.ui_objectif_moyen_34d60), money(a?.target, security.currency))
                        Metric(stringResource(R.string.ui_objectif_haut_0aa0c), money(a?.high, security.currency))
                        Metric(stringResource(R.string.ui_objectif_bas_45187), money(a?.low, security.currency))
                        Metric(
                            stringResource(R.string.ui_ecart_au_cours_10ea3),
                            percent(
                                a?.target?.let { target ->
                                    q?.let { (target - it.price).pct(it.price) }
                                }
                            ),
                        )
                        Caption(
                            "${a.source} · ${a.date ?: stringResource(R.string.date_unavailable)}"
                        )
                    }
                    Caption(
                        stringResource(R.string.ui_ces_opinions_appartiennent_aux_analystes_cite_0affe)
                    )
                }
            }
            3 -> {
                if (news.isEmpty())
                    item {
                        Empty(
                            stringResource(R.string.ui_aucune_actualite_disponible_06a1e),
                            "Réessaie plus tard ou vérifie la source de données.",
                        )
                    }
                items(news) { n ->
                    CardBlock {
                        Text(n.title, fontWeight = FontWeight.SemiBold)
                        Caption("${n.source} · ${time(n.timestamp)}")
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.url)))
                            }
                        ) {
                            Text(stringResource(R.string.ui_lire_larticle_8e862))
                        }
                    }
                }
            }
            4 -> {
                item { Section(stringResource(R.string.ui_mes_alertes_f109e), "+ Nouvelle") { onAlert(security) } }
                items(w.alerts.filter { it.securityId == security.id }) { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${a.kind} · ${number(a.threshold)}")
                            Caption(a.triggered?.let { "Déclenchée ${time(it)}" } ?: "En attente")
                        }
                        Switch(
                            a.enabled,
                            { enabled ->
                                vm.run {
                                    s.repo.put(
                                        "alert",
                                        a.id,
                                        a.copy(
                                            enabled = enabled,
                                            triggered = if (enabled) null else a.triggered,
                                        ),
                                    )
                                }
                            },
                        )
                        IconButton(onClick = { deleting = a.id }) {
                            Icon(Icons.Outlined.DeleteOutline, "Supprimer")
                        }
                    }
                }
                item { Section(stringResource(R.string.ui_historique_des_alertes_bbd53)) }
                items(
                    w.events
                        .filter { it.securityId == security.id }
                        .sortedByDescending { it.timestamp }
                ) { e ->
                    CardBlock {
                        Text(e.title)
                        Caption(time(e.timestamp))
                        Caption(e.body)
                    }
                }
            }
            5 -> {
                items(
                    w.transactions
                        .filter { it.securityId == security.id }
                        .sortedByDescending { it.date }
                ) { t ->
                    TextButton(onClick = { onEdit(t) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                "${date(t.date)} · ${t.type} · ${w.portfolios.find{it.id==t.portfolioId}?.name}"
                            )
                            Caption(
                                "${number(t.quantity,6)} × ${money(t.price,t.currency)} · ${money(t.total,t.currency)}"
                            )
                        }
                    }
                }
            }
        }
    }
    deleting?.let { id ->
        Confirm(stringResource(R.string.ui_supprimer_lalerte_e5ed4), "Son historique restera disponible.", { deleting = null }) {
            vm.run { s.repo.remove(id) }
        }
    }
}
