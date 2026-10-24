package ca.monwallet.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ca.monwallet.app.Services
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.*
import kotlinx.coroutines.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AnalystPurple = Color(0xFFB9A2FA)

/** Quantity is independent of portfolio coverage: incomplete sectors must not hide affordable shares. */
internal fun analystPurchase(row: ResearchStock, budget: BigDecimal, fx: BigDecimal?): Pair<BigDecimal, BigDecimal>? {
    val q = row.quote ?: return null
    if (budget <= ZERO || q.price <= ZERO || q.currency != row.security.currency) return null
    val rate = when (q.currency) { "CAD" -> ONE; "USD" -> fx?.takeIf { it > ZERO }; else -> null } ?: return null
    val unit = q.price * rate
    val quantity = budget.divide(unit, 0, RoundingMode.DOWN)
    return quantity.takeIf { it > ZERO }?.let { it to it * unit }
}
private fun shareLabel(quantity: BigDecimal?) = quantity?.let {
    number(it, 0) + if (it.compareTo(ONE) == 0) " action" else " actions"
} ?: "Quantité indisponible"

@Composable
internal fun InvestmentAnalystDialog(s: Services, initial: Security? = null, onClose: () -> Unit) {
    val wallet by s.repo.state.collectAsState()
    val research = remember(s) { InvestmentResearch(s) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("portfolio_analyst", 0) }
    var tab by remember { mutableIntStateOf(if (initial == null) 2 else 1) }
    var memberId by remember { mutableStateOf<String?>(null) }
    var all by remember { mutableStateOf(false) }
    var portfolioId by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(initial) }
    var search by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var handledRefresh by remember { mutableIntStateOf(0) }
    var budgetText by remember { mutableStateOf("500") }
    var country by remember { mutableStateOf("Tous") }
    var horizon by remember { mutableStateOf("3 à 5 ans") }
    var risk by remember { mutableStateOf("Modéré") }
    var threshold by remember { mutableFloatStateOf((prefs.getString("sector_cap", "35")?.toFloatOrNull() ?: 35f).coerceIn(10f, 70f)) }
    var financialFilters by remember { mutableStateOf(true) }
    var page by remember { mutableIntStateOf(0) }
    var fromCash by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var pack by remember { mutableStateOf<ResearchPack?>(null) }
    var answer by remember { mutableStateOf<InvestmentAnswer?>(null) }
    var loading by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var detailId by remember { mutableStateOf<String?>(initial?.id) }
    var detailTab by remember { mutableIntStateOf(0) }
    var simulate by remember { mutableStateOf(false) }
    var assistantOpen by remember { mutableStateOf(false) }
    val mode = listOf("BALANCE", "TITLE", "IDEAS")[tab]
    val cacheKey = (if (all) "all" else memberId ?: "self") + ":" + (portfolioId ?: "all") + ":" + mode
    val budget = budgetText.replace(',', '.').toBigDecimalOrNull() ?: ZERO

    fun refresh() {
        if (tab != 0 && budget <= ZERO) { error = "Entre un budget positif en CAD."; return }
        if (tab == 1 && selected == null) { search = true; return }
        job?.cancel()
        val key = cacheKey
        val snapshot = wallet
        val target = selected
        job = scope.launch {
            loading = true; error = null; answer = null; page = 0
            try {
                val fresh = research.collect(snapshot, mode, memberId, all, portfolioId, target,
                    budget, country, horizon, risk, threshold.toInt(), { status = it }, financialFilters)
                pack = fresh; research.save(key, fresh, null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { error = e.message ?: "Collecte indisponible. Réessaie." }
            finally { loading = false }
        }
    }
    LaunchedEffect(cacheKey, selected?.id, refreshToken, s.repo.owner.value) {
        job?.cancel(); pack = null; answer = null; error = null; loading = false; asking = false
        detailId = if (tab == 1) selected?.id else null
        simulate = false; assistantOpen = false
        val saved = research.cached(cacheKey)
        val force = handledRefresh != refreshToken
        handledRefresh = refreshToken
        if (saved != null && !force && (tab != 2 || saved.pack.screening?.version == StockScreener.VERSION) && (tab != 1 || saved.pack.stocks.firstOrNull()?.security?.id == selected?.id)) {
            pack = saved.pack; answer = saved.answer; fromCash = saved.pack.fromCash
            budgetText = saved.pack.budget.toPlainString(); country = saved.pack.country
            horizon = saved.pack.horizon; risk = saved.pack.risk; financialFilters = saved.pack.financialFilters
        } else if (tab != 1 || selected != null) refresh()
    }
    LaunchedEffect(threshold) { prefs.edit().putString("sector_cap", threshold.toInt().toString()).apply() }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    val p = pack
    val row = p?.stocks?.find { it.security.id == detailId }
    val stale = p != null && (p.budget.compareTo(budget) != 0 || p.threshold != threshold.toInt() ||
        p.horizon != horizon || p.risk != risk || (tab == 2 && (p.country != country || p.financialFilters != financialFilters)) ||
        System.currentTimeMillis() - p.createdAt > (if (tab == 2) 31L * 24 * 3600_000 else 6 * 3600_000L) ||
        p.ledgerKey != research.ledgerKey(wallet, memberId, all, portfolioId) ||
        (tab == 1 && p.stocks.firstOrNull()?.security?.id != selected?.id))
    val scroll = rememberScrollState()
    LaunchedEffect(detailId, detailTab, tab) { scroll.scrollTo(0) }
    LaunchedEffect(error) { if (error != null) scroll.animateScrollTo(0) }
    fun back() { if (row != null) { detailId = null; simulate = false; assistantOpen = false } else onClose() }

    Dialog(onDismissRequest = ::back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler { back() }
        Surface(Modifier.fillMaxSize(), color = Night) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (row != null) IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour aux idées") }
                    else Icon(Icons.Default.AutoAwesome, null, tint = AnalystPurple, modifier = Modifier.padding(8.dp))
                    Text(if (row == null) "Analyste IA" else row.security.name,
                        Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = onClose) { Text("Fermer") }
                }
                Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (row == null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text((if (all) "Compte complet" else wallet.memberName(memberId)) + " · " +
                                (wallet.portfolios.find { it.id == portfolioId }?.name ?: "Tous les comptes"),
                                Modifier.weight(1f).clickable { filters = true }, color = Muted, fontSize = 12.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            OutlinedButton(onClick = { filters = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                Icon(Icons.Default.Tune, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Filtres")
                            }
                        }
                        AnalystPanel {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountBalanceWallet, null, tint = Green, modifier = Modifier.size(26.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) { Text("Budget disponible", color = Muted, fontSize = 12.sp)
                                    Text(money(budget) + " CAD", fontSize = 23.sp, fontWeight = FontWeight.Bold) }
                                TextButton(onClick = { filters = true }) { Text("Modifier") }
                            }
                        }
                    }
                    if (loading || asking) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (asking && status.isBlank()) "Analyse IA en cours…" else status, Modifier.weight(1f), fontSize = 12.sp, color = Muted)
                            TextButton(onClick = { job?.cancel(); loading = false; asking = false }) { Text("Annuler") }
                        }
                    }
                    error?.let { AnalystPanel { Text(it, color = Blue); TextButton(onClick = { refresh() }) { Text("Réessayer") } } }
                    if (stale && !loading) {
                        AnalystPanel {
                            Text("Dossier à actualiser", color = AnalystPurple, fontWeight = FontWeight.Bold)
                            Text("Tes réglages, transactions ou données ont changé.", fontSize = 12.sp, color = Muted)
                            TextButton(onClick = { refresh() }) { Text("Actualiser les suggestions") }
                        }
                    }
                    if (row != null && p != null) {
                        AnalystTitleHeader(row, p, budget)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Résumé", "Chiffres", "Sources").forEachIndexed { index, label ->
                                val active = detailTab == index
                                Surface(Modifier.weight(1f).clickable { detailTab = index },
                                    shape = RoundedCornerShape(12.dp), color = if (active) AnalystPurple else Panel,
                                    border = BorderStroke(1.dp, if (active) AnalystPurple else Border)) {
                                    Text(label, Modifier.padding(vertical = 11.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        color = if (active) Night else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        when (detailTab) {
                            0 -> {
                                val health = FinancialHealthProfile.assess(row.security, row.fundamentals)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AnalystTile("Secteur", PortfolioResearch.sector(row.security.sector) ?: "Non déterminé", Modifier.weight(1f))
                                    AnalystTile("Taille de l’entreprise", row.fundamentals?.metrics?.get("Capitalisation (M)")
                                        ?.let { financialValue("Capitalisation (M)", it, row.security.currency) } ?: "Non disponible", Modifier.weight(1f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AnalystTile("Ton profil", p.risk, Modifier.weight(1f))
                                    AnalystTile("Marché", row.security.exchange + " · " + row.security.currency, Modifier.weight(1f))
                                }
                                AnalystAccordion("Pourquoi ce titre ?", Icons.Default.Lightbulb, "analyst-reason") {
                                    val opinion = p.opinions.orEmpty()[row.screen?.listing]
                                    Text(opinion?.reason ?: "Candidat du préfiltre quantitatif. Sa présence ne constitue pas un avis d’achat.", fontSize = 14.sp)
                                    opinion?.let { Text(it.verdict, color = AnalystPurple); Caption(it.nextCheck) }
                                    row.screen?.let { Caption("Score du préfiltre : " + (it.score?.let { v -> String.format(Locale.CANADA_FRENCH, "%.1f /100", v) } ?: "Données incomplètes")) }
                                    Caption("Secteur : " + (PortfolioResearch.sector(row.security.sector) ?: "non déterminé"))
                                    if (row.fundamentals != null) Caption(health.title.substringAfter(" · "))
                                    else Caption("Ratios non disponibles pour le moment; consulte Chiffres ou actualise ce titre.")
                                }
                                AnalystAccordion("À surveiller", Icons.Default.WarningAmber, "analyst-risks") {
                                    health.details.filterNot { it.startsWith("Seuils") }.forEach { Caption(it) }
                                    row.earnings.firstOrNull()?.let { Caption("Résultats : " + (it.date ?: "date non fournie")) }
                                    row.gaps.forEach { Caption(it) }
                                    row.screen?.flags?.forEach { Caption(it) }
                                    p.opinions.orEmpty()[row.screen?.listing]?.let { Caption("IA · " + it.risk); Caption("Analyse des ratios fournis, sans vérification des nouvelles · " + it.model) }
                                }
                                AnalystAccordion("Effet sur mon portefeuille", Icons.Default.PieChart, "analyst-impact") {
                                    AnalystImpact(p, row, budget, fromCash)
                                }
                                if (p.exposure.unknownSector > ZERO) Caption("ⓘ Données sectorielles partielles")
                                TextButton(onClick = { assistantOpen = !assistantOpen }) { Text("Analyse IA et copie pour ChatGPT") }
                            }
                            1 -> {
                                AnalystFigures(row)
                                OutlinedButton(enabled = !loading && !asking, onClick = {
                                    job?.cancel()
                                    val current = p
                                    val key = cacheKey
                                    job = scope.launch {
                                        loading = true; error = null; answer = null
                                        try {
                                            val detail = research.collect(wallet, "TITLE", memberId, all, portfolioId,
                                                row.security, budget, country, horizon, risk, threshold.toInt(), { status = it })
                                            val updated = current.copy(stocks = current.stocks.map { old ->
                                                if (old.security.id == row.security.id) detail.stocks.firstOrNull()?.copy(screen = old.screen) ?: old else old
                                            }, sources = current.sources.filterNot { it.id.startsWith("detail:" + row.security.id + ":") } +
                                                    detail.sources.map { it.copy(id = "detail:" + row.security.id + ":" + it.id) },
                                                funds = current.funds + detail.funds)
                                            pack = updated; research.save(key, updated, null)
                                        } catch (cancelled: CancellationException) { throw cancelled }
                                        catch (e: Exception) { error = e.message ?: "Détails indisponibles." }
                                        finally { loading = false }
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text("Actualiser ce titre") }
                            }
                            else -> AnalystSources(p, row)
                        }
                        if (simulate) AnalystPanel {
                            Text("Simulation de cet achat", fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(fromCash, { fromCash = it }); Text("Utiliser les liquidités existantes", Modifier.weight(1f), fontSize = 14.sp)
                            }
                            AnalystImpact(p, row, budget, fromCash)
                        }
                    } else if (tab == 2) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Idées pour ton budget", Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            IconButton(enabled = !loading, onClick = { refresh() }) { Icon(Icons.Default.Refresh, "Actualiser les idées") }
                        }
                        if (p != null) {
                            p.screening?.let { scan ->
                                AnalystPanel {
                                    Text("${p.stocks.size} candidats · US / Canada", fontWeight = FontWeight.Bold)
                                    Caption("${scan.received} reçus · ${scan.eligible} admissibles · ${scan.excludedBiotech} biotech/pharma exclues")
                                    Caption("Aucun OTC · cours ≥ 1 $ · toutes capitalisations")
                                    val done = p.opinions.orEmpty().size
                                    Text("IA : $done/${p.stocks.size} titres", color = AnalystPurple)
                                    Button(enabled = !stale && !loading && !asking && p.stocks.isNotEmpty() && done < p.stocks.size, onClick = {
                                        job?.cancel()
                                        val key = cacheKey; val owner = s.repo.owner.value
                                        job = scope.launch {
                                            asking = true; error = null
                                            try {
                                                research.analyzeScreen(p, { status = it }) { checkpoint ->
                                                    ensureActive()
                                                    if (owner != s.repo.owner.value) throw CancellationException()
                                                    pack = checkpoint; research.save(key, checkpoint, null)
                                                }
                                            } catch (cancelled: CancellationException) { throw cancelled }
                                            catch (e: Exception) { error = e.message ?: "IA indisponible. Progression conservée." }
                                            finally { asking = false }
                                        }
                                    }, modifier = Modifier.fillMaxWidth()) { Text(if (done == 0) "Analyser les candidats avec l’IA" else "Reprendre l’analyse IA") }
                                    Caption("Utilise les crédits IA du service. Étudie les ratios fournis; ne recherche pas les nouvelles ni les profits exceptionnels dans les dépôts.")
                                    if (done > 0) Caption(if (done == p.stocks.size) "Tri par priorité de recherche IA. Ce score n’est pas une probabilité de gain." else "Analyse partielle : le tri reste celui du préfiltre jusqu’à la fin.")
                                }
                            }
                            val fullyAnalyzed = p.stocks.isNotEmpty() && p.stocks.all { p.opinions.orEmpty().containsKey(it.screen?.listing) }
                            val ordered = if (fullyAnalyzed) p.stocks.sortedWith(compareByDescending<ResearchStock> {
                                p.opinions.orEmpty()[it.screen?.listing]?.priority ?: -1 }.thenByDescending { it.screen?.score ?: -1.0 }) else p.stocks
                            val lastPage = ((ordered.size - 1).coerceAtLeast(0)) / 20
                            val currentPage = page.coerceAtMost(lastPage)
                            if (ordered.size > 20) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(enabled = currentPage > 0, onClick = { page = currentPage - 1; scope.launch { scroll.scrollTo(0) } }) { Text("Précédents") }
                                Text("${currentPage * 20 + 1}–${minOf((currentPage + 1) * 20, ordered.size)} / ${ordered.size}", Modifier.weight(1f), fontSize = 12.sp)
                                TextButton(enabled = currentPage < lastPage, onClick = { page = currentPage + 1; scope.launch { scroll.scrollTo(0) } }) { Text("Suivants") }
                            }
                            ordered.drop(currentPage * 20).take(20).forEachIndexed { index, idea ->
                                AnalystIdeaCard(idea, analystPurchase(idea, budget, p.fxRate),
                                    currentPage * 20 + index + 1, p.opinions.orEmpty()[idea.screen?.listing]) {
                                    detailId = idea.security.id; detailTab = 0; simulate = false; assistantOpen = false; answer = null
                                }
                            }
                            if (p.stocks.isEmpty() && !loading) AnalystPanel {
                                Text("Aucun titre ne correspond aux données reçues.")
                                TextButton(onClick = { filters = true }) { Text("Modifier le budget ou les filtres") }
                            }
                            Caption("Options individuelles · montants estimés, sans frais. Dossier du " + analystDate(p.createdAt))
                            AnalystAccordion("Comment ces idées sont choisies", Icons.Default.Info, "analyst-method") {
                                p.notes.forEach { Caption(it) }
                            }
                        } else if (!loading) Button(onClick = { refresh() }, modifier = Modifier.fillMaxWidth()) { Text("Trouver mes idées") }
                    } else if (tab == 0 && p != null) {
                        InvestmentBalanceCard(p.exposure, p.threshold)
                        AnalystAccordion("Mes positions", Icons.Default.AccountBalanceWallet, "analyst-positions") {
                            p.positions.sortedByDescending { it.valueCad }.forEach { position ->
                                Metric(position.symbol, money(position.valueCad) + " · " + number(position.quantity, 6) + " parts")
                            }
                        }
                        AnalystAccordion("Tendance du marché", Icons.Default.ShowChart, "analyst-market") {
                            p.benchmarks.forEach { Metric(it.security.name + " · 1 mois", percent(it.monthReturn), tint(it.monthReturn)) }
                        }
                        TextButton(onClick = { assistantOpen = !assistantOpen }) { Text("Analyse IA et copie pour ChatGPT") }
                        TextButton(onClick = { refresh() }, enabled = !loading) { Text("Actualiser mon équilibre") }
                    } else if (tab == 1 && !loading) {
                        Button(onClick = { search = true }, modifier = Modifier.fillMaxWidth()) { Text("Choisir un titre à analyser") }
                    }
                    if (assistantOpen && p != null) {
                        val focused = if (row == null) p else p.copy(mode = "TITLE", stocks = listOf(row))
                        AnalystPanel {
                            Text("Analyse IA", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            TextEntry("Ta question à l’IA", question, { question = it.take(500) })
                            Button(enabled = !stale && !loading && !asking && (!fromCash || p.budget <= p.exposure.cash), onClick = {
                                job = scope.launch {
                                    asking = true; error = null
                                    try {
                                        val request = focused.copy(fromCash = fromCash)
                                        val a = research.ask(request, question); answer = a
                                        // A response about a single card is not saved as an analysis of all alternatives.
                                    } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (e: Exception) { error = e.message ?: "Service IA indisponible." }
                                    finally { asking = false }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Demander l’analyse IA") }
                            Caption("Envoie les montants, secteurs et titres du dossier, ainsi que ta question. Aucun nom de membre ni courriel n’est ajouté automatiquement. Le service IA doit être disponible.")
                            OutlinedButton(enabled = !stale, onClick = {
                                val text = "Analyse ce dossier daté en français. Signale les données manquantes et les risques.\n" + research.evidence(focused.copy(fromCash = fromCash), question)
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Mon Wallet · dossier de recherche", text))
                                android.widget.Toast.makeText(context, "Dossier copié pour ChatGPT", android.widget.Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.fillMaxWidth()) { Text("Copier le dossier pour ChatGPT") }
                            answer?.let { a -> Text(a.verdict, color = AnalystPurple, fontWeight = FontWeight.Bold); Text(a.summary)
                                a.reasons.forEach { Caption("• " + it) }; a.risks.forEach { Caption("Risque : " + it) }
                                a.nextSteps.forEach { Caption("À vérifier : " + it) }
                                Caption("IA · " + a.model + " · " + analystDate(a.createdAt)) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (row != null) {
                    Button(enabled = !stale && !loading && analystPurchase(row, budget, p?.fxRate) != null,
                        onClick = { simulate = !simulate; scope.launch { scroll.animateScrollTo(scroll.maxValue) } },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp).testTag("analyst-simulate"), shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Default.BarChart, null); Spacer(Modifier.width(8.dp)); Text(if (simulate) "Masquer la simulation" else "Simuler cet achat", Modifier.padding(vertical = 5.dp))
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(22.dp)).background(Panel).border(1.dp, Border, RoundedCornerShape(22.dp))) {
                        listOf(2 to "Idées", 0 to "Mon équilibre", 1 to "Analyser").forEach { (index, label) ->
                            Surface(Modifier.weight(1f).clickable { tab = index; detailId = null; answer = null },
                                shape = RoundedCornerShape(22.dp), color = if (tab == index) Green else Color.Transparent) {
                                Text(label, Modifier.padding(vertical = 13.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = if (tab == index) Night else Muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    if (filters) {
        var draftMember by remember { mutableStateOf(memberId) }
        var draftAll by remember { mutableStateOf(all) }
        var draftPortfolio by remember { mutableStateOf(portfolioId) }
        var draftBudget by remember { mutableStateOf(budgetText) }
        var draftCountry by remember { mutableStateOf(country) }
        var draftHorizon by remember { mutableStateOf(horizon) }
        var draftRisk by remember { mutableStateOf(risk) }
        var draftFinancialFilters by remember { mutableStateOf(financialFilters) }
        var draftThreshold by remember { mutableFloatStateOf(threshold) }
        FullDialog("Filtres des idées", { filters = false }) {
            Caption("Choisis le budget et le profil. Les cartes restent sur l’écran principal.")
            val members = wallet.familyMembers()
            Choice("Pour qui?", if (draftAll) "Compte complet" else wallet.memberName(draftMember), listOf("Compte complet") + members.map { it.name }) { index ->
                draftAll = index == 0; if (index > 0) draftMember = members[index - 1].id
            }
            Choice("Portefeuille", wallet.portfolios.find { it.id == draftPortfolio }?.name ?: "Tous les comptes", listOf("Tous les comptes") + wallet.portfolios.map { it.name }) {
                draftPortfolio = if (it == 0) null else wallet.portfolios[it - 1].id
            }
            TextEntry("Budget disponible · CAD", draftBudget, { draftBudget = it }, true)
            Choice("Marché des idées", draftCountry, listOf("Canada", "États-Unis", "Tous")) { draftCountry = listOf("Canada", "États-Unis", "Tous")[it] }
            Choice("Préfiltre financier", if (draftFinancialFilters) "Entreprises rentables" else "Aucun filtre financier", listOf("Entreprises rentables", "Aucun filtre financier")) { draftFinancialFilters = it == 0 }
            Caption("US/Canada, aucun OTC, seuil 1 $ et exclusion biotech restent toujours appliqués. Aucun filtre financier autorise les sociétés déficitaires; données incomplètes non notées.")
            Choice("Horizon", draftHorizon, listOf("Moins d’un an", "1 à 3 ans", "3 à 5 ans", "Plus de 5 ans")) { draftHorizon = listOf("Moins d’un an", "1 à 3 ans", "3 à 5 ans", "Plus de 5 ans")[it] }
            Choice("Tolérance au risque", draftRisk, listOf("Prudent", "Modéré", "Élevé")) { draftRisk = listOf("Prudent", "Modéré", "Élevé")[it] }
            AnalystAccordion("Réglages avancés", Icons.Default.Tune, "analyst-advanced") {
                Text("Seuil d’alerte sectorielle : ${draftThreshold.toInt()} %")
                Slider(draftThreshold, { draftThreshold = it }, valueRange = 10f..70f, steps = 11)
                Caption("Seuil personnel, pas une allocation optimale universelle.")
            }
            Button(onClick = {
                memberId = draftMember; all = draftAll; portfolioId = draftPortfolio
                budgetText = draftBudget; country = draftCountry; financialFilters = draftFinancialFilters; horizon = draftHorizon; risk = draftRisk; threshold = draftThreshold
                filters = false; detailId = null; refreshToken++
            }, enabled = !loading && (draftBudget.replace(',', '.').toBigDecimalOrNull() ?: ZERO) > ZERO, modifier = Modifier.fillMaxWidth()) {
                Text("Afficher les résultats")
            }
        }
    }
    if (search) SearchDialog(s, wallet, { search = false }) { selected = it; detailId = it.id; detailTab = 0; search = false; tab = 1 }
}

@Composable
private fun AnalystPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel)
        .border(1.dp, Color(0xFF23414B), RoundedCornerShape(18.dp)).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun AnalystIdeaCard(row: ResearchStock, purchase: Pair<BigDecimal, BigDecimal>?, rank: Int, opinion: ScreenOpinion?, onClick: () -> Unit) {
    AnalystPanel(Modifier.clickable(onClick = onClick).testTag("analyst-idea-${row.security.symbol}")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Logo(row.security, 42.dp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("#$rank · " + row.security.ticker, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(row.security.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(shape = RoundedCornerShape(24.dp), color = Green) {
                Text(shareLabel(purchase?.first), Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Night, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(PortfolioResearch.sector(row.security.sector) ?: "Secteur non déterminé", Modifier.weight(1f),
                color = AnalystPurple, fontSize = 12.sp, maxLines = 2)
            Text(money(purchase?.second) + " CAD", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted)
        }
        row.screen?.let { e ->
            Caption(if (opinion != null) "IA · ${opinion.verdict} · priorité ${opinion.priority?.toString() ?: "ND"}/100"
                else "Préfiltre · " + (e.score?.let { String.format(Locale.CANADA_FRENCH, "%.1f /100", it) } ?: "Non noté · données incomplètes"))
            opinion?.let { Text(it.reason, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            if (purchase == null) Caption("Budget insuffisant ou taux de change indisponible")
        }
        HorizontalDivider(color = Border)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BarChart, null, Modifier.size(17.dp), tint = AnalystPurple)
            Spacer(Modifier.width(8.dp)); Text("Voir l’analyse", color = AnalystPurple, fontSize = 13.sp)
            Spacer(Modifier.weight(1f)); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AnalystPurple)
        }
    }
}

@Composable
private fun AnalystTitleHeader(row: ResearchStock, pack: ResearchPack, budget: BigDecimal) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Logo(row.security, 48.dp); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(row.security.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(row.security.ticker + " · " + row.security.exchange, fontSize = 13.sp, color = Muted)
        }
    }
    val purchase = analystPurchase(row, budget, pack.fxRate)
    AnalystPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) { Text(shareLabel(purchase?.first), fontSize = 23.sp, fontWeight = FontWeight.Bold); Caption("Quantité proposée") }
            Column(Modifier.weight(1.25f)) { Text(money(purchase?.second) + " CAD", fontSize = 21.sp, fontWeight = FontWeight.Bold); Caption("Coût estimé total") }
        }
        HorizontalDivider(color = Border)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Budget restant", Modifier.weight(1f), fontSize = 13.sp, color = Muted)
            Text(money(purchase?.let { budget - it.second }), color = Green, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}

@Composable
private fun AnalystTile(label: String, value: String, modifier: Modifier, color: Color = Color.White) {
    AnalystPanel(modifier) { Text(label, fontSize = 11.sp, color = Muted); Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color) }
}

@Composable
private fun AnalystAccordion(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, content: @Composable ColumnScope.() -> Unit) {
    var open by remember(title) { mutableStateOf(false) }
    AnalystPanel {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.testTag(tag).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AnalystPurple, modifier = Modifier.size(21.dp)); Spacer(Modifier.width(10.dp))
            Text(title, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Icon(if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Muted)
        }
        if (open) content()
    }
}

@Composable
private fun AnalystImpact(p: ResearchPack, row: ResearchStock, budget: BigDecimal, fromCash: Boolean) {
    val impact = PortfolioResearch.simulate(p.exposure, row.security, row.quote, budget, fromCash, p.fxRate, p.funds[row.security.id])
    if (impact == null) Caption("Simulation indisponible : vérifie le budget, les cours, le taux de change ou les liquidités.")
    else {
        Metric("Quantité estimée", shareLabel(impact.quantity))
        Metric("Coût estimé · CAD", money(impact.spent))
        impact.after.sectors.filter { it.label !in setOf("Liquidités", "Non déterminé") }.take(4).forEach { line ->
            val before = p.exposure.sectors.find { it.label == line.label }?.percent ?: ZERO
            Metric(line.label, exposurePercent(before) + " → " + exposurePercent(line.percent))
        }
        Caption(if (fromCash) "Achat payé avec les liquidités existantes; total inchangé." else "Nouvel apport; le reste du budget demeure en liquidités.")
        if (p.exposure.unknownSector > ZERO) Caption("Couverture sectorielle partielle : la part inconnue reste inconnue.")
    }
}

@Composable
private fun AnalystFigures(row: ResearchStock) {
    AnalystPanel {
        Text("Cours et tendance", fontWeight = FontWeight.Bold)
        Metric("Cours · " + row.security.currency, money(row.quote?.price, row.security.currency))
        Metric("Variation 1 mois", percent(row.monthReturn), tint(row.monthReturn))
        Metric("Moyenne 50 séances", money(row.average50, row.security.currency))
        row.quote?.let { Caption(it.source + " · " + analystDate(it.timestamp)) }
    }
    AnalystPanel {
        Text("Ratios financiers", fontWeight = FontWeight.Bold)
        val metrics = row.fundamentals?.metrics.orEmpty()
        if (metrics.isEmpty()) Caption("Ratios indisponibles pour ce titre dans le dossier actuel.")
        metrics.entries.take(12).forEach { Metric(it.key, financialValue(it.key, it.value, row.security.currency)) }
        row.fundamentals?.let { Caption(it.source + " · " + (it.ratiosAsOf ?: it.asOf ?: "date non fournie")) }
    }
    AnalystAccordion("Résultats financiers", Icons.Default.Assessment, "analyst-earnings") {
        if (row.earnings.isEmpty()) Caption("Aucun résultat fourni par la source.")
        row.earnings.forEach { Metric(it.date ?: "Date inconnue", "BPA : " + (it.epsActual?.let { n -> number(n) } ?: "—") + " / attendu " + (it.epsEstimate?.let { n -> number(n) } ?: "—")); Caption(it.source) }
        row.fundamentals?.quarterly?.forEach { (name, series) ->
            Text(name, fontWeight = FontWeight.SemiBold)
            series.takeLast(4).forEach { Metric(it.first, number(it.second, 2) + " " + row.security.currency) }
        }
    }
    row.gaps.forEach { Caption(it) }
}

@Composable
private fun AnalystSources(p: ResearchPack, row: ResearchStock) {
    val context = LocalContext.current
    AnalystPanel {
        Text("Sources du titre", fontWeight = FontWeight.Bold)
        row.quote?.let { Caption("Cours : " + it.source + " · " + analystDate(it.timestamp)) }
        row.fundamentals?.let { Caption("États et ratios : " + it.source + " · " + (it.asOf ?: "date non fournie")) }
        row.news.forEach { article ->
            TextButton(onClick = { if (article.url.startsWith("https://")) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url))) }) { Text(article.title) }
            Caption(article.source + " · " + analystDate(article.timestamp))
        }
    }
    AnalystAccordion("Dossier complet · sources datées", Icons.Default.Link, "analyst-sources") {
        p.sources.forEach { source -> TextButton(onClick = { if (source.url.startsWith("https://")) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url))) }) { Text(source.label + "\n" + source.date) } }
    }
}

private fun exposurePercent(value: BigDecimal?) = value?.let { number(it) + " %" } ?: "—"
private fun analystDate(timestamp: Long) = Instant.ofEpochMilli(timestamp).atZone(MarketClock.zone)
    .format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.CANADA_FRENCH))

@Composable
internal fun InvestmentBalanceCard(exposure: PortfolioExposure, threshold: Int) {
    CardBlock {
        Text("Équilibre du portefeuille", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Metric("Valeur totale · CAD", money(exposure.total))
        Metric("Secteurs identifiés", exposurePercent(exposure.sectorCoverage))
        Metric("Liquidités · CAD", money(exposure.cash))
        exposure.sectors.forEach { line ->
            Metric(line.label, exposurePercent(line.percent.takeIf { exposure.total != null }) + " · " + money(line.value),
                if (line.percent > BigDecimal(threshold) && line.label !in setOf("Non déterminé", "Liquidités")) Blue else Color.White)
            if (exposure.total != null) LinearProgressIndicator(progress = { (line.percent.toFloat() / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp))
        }
        val concentrated = exposure.sectors.filter { it.percent > BigDecimal(threshold) && it.label !in setOf("Non déterminé", "Liquidités") }
        concentrated.forEach { Caption(it.label + " dépasse ton seuil d’alerte de " + threshold + " %.") }
        if (exposure.unknownSector > ZERO) Caption("La part non déterminée reste dans le total : aucune extrapolation à partir des seuls secteurs connus.")
        if (exposure.countries.isNotEmpty()) { Text("Pays / domicile", fontWeight = FontWeight.Bold); exposure.countries.take(8).forEach { Metric(it.label, exposurePercent(it.percent.takeIf { exposure.total != null })) } }
        if (exposure.currencies.isNotEmpty()) { Text("Devises de cotation", fontWeight = FontWeight.Bold); exposure.currencies.forEach { Metric(it.label, percent(it.percent)) } }
        exposure.notes.forEach { Caption(it) }
    }
}
