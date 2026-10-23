package ca.monwallet.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import ca.monwallet.app.Services
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.*
import kotlinx.coroutines.*
import java.math.BigDecimal
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun InvestmentAnalystDialog(s: Services, initial: Security? = null, onClose: () -> Unit) {
    val wallet by s.repo.state.collectAsState()
    val research = remember(s) { InvestmentResearch(s) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("portfolio_analyst", 0) }
    var tab by remember { mutableIntStateOf(if (initial == null) 0 else 1) }
    var memberId by remember { mutableStateOf<String?>(null) }
    var all by remember { mutableStateOf(false) }
    var portfolioId by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(initial) }
    var search by remember { mutableStateOf(false) }
    var budgetText by remember { mutableStateOf("500") }
    var country by remember { mutableStateOf("Canada") }
    var horizon by remember { mutableStateOf("3 à 5 ans") }
    var risk by remember { mutableStateOf("Modéré") }
    var threshold by remember { mutableFloatStateOf((prefs.getString("sector_cap", "35")?.toFloatOrNull() ?: 35f).coerceIn(10f, 70f)) }
    var fromCash by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var pack by remember { mutableStateOf<ResearchPack?>(null) }
    var answer by remember { mutableStateOf<InvestmentAnswer?>(null) }
    var loading by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var expandedSources by remember { mutableStateOf(false) }
    val mode = listOf("BALANCE", "TITLE", "IDEAS")[tab]
    val cacheKey = (if (all) "all" else memberId ?: "self") + ":" + (portfolioId ?: "all") + ":" + mode
    LaunchedEffect(cacheKey, s.repo.owner.value) {
        job?.cancel()
        pack = null; answer = null; error = null; loading = false; asking = false
        research.cached(cacheKey)?.let { pack = it.pack; answer = it.answer; fromCash = it.pack.fromCash }
    }
    LaunchedEffect(threshold) { prefs.edit().putString("sector_cap", threshold.toInt().toString()).apply() }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    FullDialog("Analyste IA", onClose) {
        CardBlock {
            Text("Comprendre avant d’investir", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Caption("Un bilan chiffré gratuit, puis une réponse IA à la demande avec les sources collectées. Aucune exécution d’ordre.")
            Caption("Le texte IA nécessite une connexion et un service IA disponible. Les données financières peuvent être partielles.")
        }
        Chips(listOf("Équilibre", "Analyser un titre", "Idées selon mon budget"), tab) { tab = it }
        val members = wallet.familyMembers()
        Choice("Pour qui?", if (all) "Compte complet" else wallet.memberName(memberId),
            listOf("Compte complet") + members.map { it.name }) { index ->
            all = index == 0; if (index > 0) memberId = members[index - 1].id
        }
        Choice("Portefeuille", wallet.portfolios.find { it.id == portfolioId }?.name ?: "Tous les comptes",
            listOf("Tous les comptes") + wallet.portfolios.map { it.name }) { index ->
            portfolioId = if (index == 0) null else wallet.portfolios[index - 1].id
        }
        if (tab == 1) OutlinedButton(onClick = { search = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.let { it.ticker + " · " + it.exchange + " · " + it.currency } ?: "Choisir un titre")
        }
        if (tab != 0) TextEntry("Budget disponible · CAD", budgetText, { budgetText = it }, true)
        if (tab == 2) Choice("Marché des idées", country, listOf("Canada", "États-Unis", "Tous")) {
            country = listOf("Canada", "États-Unis", "Tous")[it] }
        Choice("Horizon", horizon, listOf("Moins d’un an", "1 à 3 ans", "3 à 5 ans", "Plus de 5 ans")) {
            horizon = listOf("Moins d’un an", "1 à 3 ans", "3 à 5 ans", "Plus de 5 ans")[it] }
        Choice("Tolérance au risque", risk, listOf("Prudent", "Modéré", "Élevé")) {
            risk = listOf("Prudent", "Modéré", "Élevé")[it] }
        Text("Alerte de concentration sectorielle : " + threshold.toInt() + " %", fontWeight = FontWeight.SemiBold)
        Slider(threshold, { threshold = it }, valueRange = 10f..70f, steps = 11)
        Caption("Seuil personnel d’alerte, pas une allocation optimale universelle.")
        Button(enabled = !loading && !asking && (tab != 1 || selected != null), modifier = Modifier.fillMaxWidth(),
            onClick = {
                val amount = budgetText.replace(',', '.').toBigDecimalOrNull()
                if (tab != 0 && (amount == null || amount <= ZERO)) error = "Entre un budget positif en CAD."
                else {
                    job?.cancel()
                    job = scope.launch {
                        loading = true; error = null; answer = null
                        try {
                            val fresh = research.collect(wallet, mode, memberId, all, portfolioId, selected,
                                amount ?: ZERO, country, horizon, risk, threshold.toInt()) { status = it }
                            pack = fresh; research.save(cacheKey, fresh, null)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (e: Exception) { error = e.message }
                        finally { loading = false }
                    }
                }
            }) { Text(if (loading) "Collecte en cours…" else "Actualiser le dossier gratuit") }
        if (loading || asking) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Caption(if (asking) "Analyse IA en cours…" else status)
            TextButton(onClick = { job?.cancel(); loading = false; asking = false }) { Text("Annuler") }
        }
        error?.let { CardBlock { Text(it, color = Blue); Caption("Le dossier chiffré reste disponible. Aucun avis IA n’est fabriqué en cas d’erreur.") } }
        pack?.let { p ->
            Caption("Dossier du " + analystDate(p.createdAt) + " · actualise après un achat, une vente ou un changement de budget.")
            val budgetChanged = p.budget.compareTo(budgetText.replace(',', '.').toBigDecimalOrNull() ?: ZERO) != 0
            val stockChanged = tab == 1 && p.stocks.firstOrNull()?.security?.id != selected?.id
            val settingsChanged = p.threshold != threshold.toInt() || p.horizon != horizon || p.risk != risk || (tab == 2 && p.country != country)
            val outdated = System.currentTimeMillis() - p.createdAt > 6 * 3600_000L
            val ledgerChanged = p.ledgerKey != research.ledgerKey(wallet, memberId, all, portfolioId)
            if (budgetChanged || stockChanged || settingsChanged || outdated || ledgerChanged)
                Caption("Les réglages ont changé : actualise le dossier pour les appliquer à l’IA.")
            InvestmentBalanceCard(p.exposure, p.threshold)
            if (p.positions.isNotEmpty()) CardBlock {
                Text("Positions détenues", fontWeight = FontWeight.Bold)
                p.positions.sortedByDescending { it.valueCad }.take(15).forEach { position ->
                    Metric(position.symbol, money(position.valueCad) + " · " + exposurePercent(position.valueCad?.let { v -> p.exposure.total?.let { v.pct(it) } }))
                    Caption(number(position.quantity, 6) + " part(s) · " + position.currency)
                }
            }
            if (p.benchmarks.isNotEmpty()) CardBlock {
                Text("Tendance récente du marché", fontWeight = FontWeight.Bold)
                p.benchmarks.forEach { row ->
                    Metric(row.security.name + " · 1 mois", percent(row.monthReturn), tint(row.monthReturn))
                    Caption("Moyenne 50 séances : " + money(row.average50, row.security.currency) +
                        " · cours " + money(row.quote?.price, row.security.currency))
                }
                Caption("Variation historique, pas prévision. Des indicateurs peuvent manquer ou être retardés.")
            }
            p.stocks.forEach { row ->
                InvestmentStockCard(row)
                val impact = PortfolioResearch.simulate(p.exposure, row.security, row.quote,
                    budgetText.replace(',', '.').toBigDecimalOrNull() ?: ZERO, fromCash,
                    p.fxRate,
                    p.funds[row.security.id])
                if (tab != 0) CardBlock {
                    Text("Effet de cet achat", fontWeight = FontWeight.Bold)
                    Row { Checkbox(fromCash, { fromCash = it }); Text("Utiliser les liquidités existantes") }
                    if (impact == null) Caption("Simulation indisponible : budget, cours, FX, liquidités ou valeur totale manquants.")
                    else {
                        Metric("Quantité estimée · parts entières", number(impact.quantity, 0))
                        Metric("Montant estimé · CAD", money(impact.spent))
                        impact.after.sectors.filter { it.label !in setOf("Liquidités", "Non déterminé") }.take(4).forEach { line ->
                            val before = p.exposure.sectors.find { it.label == line.label }?.percent ?: ZERO
                            Metric(line.label, exposurePercent(before) + " → " + exposurePercent(line.percent))
                        }
                        Caption("Sans frais. " + if (fromCash) "Total inchangé; achat payé avec l’encaisse." else "Nouveaux fonds ajoutés au total; le solde non investi reste en liquidités.")
                    }
                }
            }
            p.notes.forEach { Caption(it) }
            if (fromCash && p.budget > p.exposure.cash) Caption("Budget supérieur aux liquidités : choisis un nouvel apport ou réduis le budget.")
            TextEntry("Ta question à l’IA", question, { question = it.take(500) })
            Button(enabled = !loading && !asking && !budgetChanged && !stockChanged && !settingsChanged && !outdated && !ledgerChanged && (!fromCash || p.budget <= p.exposure.cash), modifier = Modifier.fillMaxWidth(), onClick = {
                job = scope.launch {
                    asking = true; error = null
                    try {
                        val requestPack = p.copy(fromCash = fromCash)
                        val a = research.ask(requestPack, question); answer = a; pack = requestPack; research.save(cacheKey, requestPack, a)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (e: Exception) { error = e.message ?: "Service IA indisponible." }
                    finally { asking = false }
                }
            }) { Text("Demander l’analyse IA") }
            Caption("Envoie à l’IA les montants agrégés, secteurs, titres et données collectées. Aucun nom de membre, courriel ou détail individuel des transactions n’est ajouté automatiquement. Ta question est envoyée telle quelle.")
            OutlinedButton(enabled = !ledgerChanged && !budgetChanged && !stockChanged && !settingsChanged && !outdated, onClick = {
                val text = "Analyse ce portefeuille et ces titres en français à partir du dossier daté suivant. Signale les données manquantes. Compare acheter maintenant, attendre et achats progressifs sans prédire le marché.\n" + research.evidence(p.copy(fromCash = fromCash), question)
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Mon Wallet · dossier de recherche", text))
                status = "Dossier copié. Tu peux le coller dans ChatGPT."; android.widget.Toast.makeText(context, status, android.widget.Toast.LENGTH_LONG).show()
            }, modifier = Modifier.fillMaxWidth()) { Text("Copier le dossier pour ChatGPT") }
            answer?.let { a -> CardBlock {
                Text(a.verdict, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = when (a.verdict) { "Éviter" -> Red; "Attendre" -> Color(0xFFFFC45D); "Données insuffisantes" -> Muted; else -> Green })
                Text(a.summary)
                Text("Raisons", fontWeight = FontWeight.Bold); a.reasons.forEach { Text("• " + it) }
                Text("Risques", fontWeight = FontWeight.Bold); a.risks.forEach { Text("• " + it) }
                Text("À vérifier ensuite", fontWeight = FontWeight.Bold); a.nextSteps.forEach { Text("• " + it) }
                Caption("IA · " + a.model + " · " + analystDate(a.createdAt))
                a.sourceIds.forEach { id -> p.sources.find { it.id == id }?.let { Caption("Source : " + it.label) } }
            } }
            TextButton(onClick = { expandedSources = !expandedSources }) { Text("Sources datées · " + p.sources.size) }
            if (expandedSources) p.sources.forEach { source ->
                TextButton(onClick = {
                    if (source.url.startsWith("https://")) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)))
                }) { Text(source.label + "\n" + source.date) }
            }
        }
    }
    if (search) SearchDialog(s, wallet, { search = false }) { selected = it; search = false }
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
@Composable
internal fun InvestmentStockCard(row: ResearchStock) {
    CardBlock {
        Text(row.security.ticker + " · " + row.security.exchange, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        Caption(row.security.name + " · " + (PortfolioResearch.sector(row.security.sector) ?: "Secteur non déterminé"))
        Metric("Cours", money(row.quote?.price, row.security.currency))
        row.quote?.let { Caption(it.source + " · " + analystDate(it.timestamp)) }
        Metric("Variation 1 mois", percent(row.monthReturn), tint(row.monthReturn))
        Metric("Moyenne 50 séances", money(row.average50, row.security.currency))
        row.fundamentals?.let { f ->
            FinancialHealthProfile.assess(row.security, f).details.forEach { Caption(it) }
            f.quarterly.entries.take(3).forEach { (name, series) -> Caption(name + " · " + series.takeLast(2).joinToString { it.first + " : " + it.second.toPlainString() }) }
            Caption(f.source + " · états au " + (f.asOf ?: "date non fournie"))
        }
        row.earnings.forEach { e -> Caption("Résultats : " + (e.date ?: "date inconnue") +
            " · BPA publié " + (e.epsActual ?: "—") + " / attendu " + (e.epsEstimate ?: "—") + " · " + e.source) }
        row.news.take(4).forEach { Caption(it.title + " · " + it.source + " · " + analystDate(it.timestamp)) }
        row.gaps.forEach { Caption(it) }
    }
}
