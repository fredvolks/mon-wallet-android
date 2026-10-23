package ca.monwallet.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.*
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal

@Composable
internal fun PortfolioAnalyst(vm: WalletViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("portfolio_analyst", 0) }
    val wallet by vm.wallet.collectAsState()
    val members = wallet.familyMembers()
    var memberIndex by remember { mutableIntStateOf(1) }
    var portfolioIndex by remember { mutableIntStateOf(0) }
    var funds by remember { mutableStateOf<Map<String, ExposureData>>(emptyMap()) }
    var sources by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var budget by remember { mutableStateOf("500") }
    var chosenSector by remember { mutableIntStateOf(0) }
    var external by remember { mutableStateOf(true) }
    var cap by remember { mutableStateOf(prefs.getString("sector_cap", "30") ?: "30") }
    LaunchedEffect(cap) { prefs.edit().putString("sector_cap", cap).apply() }
    val member = members.getOrNull(memberIndex - 1)
    val scoped = (if (memberIndex == 0) wallet else wallet.forMember(member?.id)).let { w ->
        val id = wallet.portfolios.getOrNull(portfolioIndex - 1)?.id
        if (id == null) w else w.copy(transactions = w.transactions.filter { it.portfolioId == id })
    }
    val result = remember(scoped.transactions, wallet.quotes, wallet.settings) { runCatching { scoped.result() }.getOrNull() }
    val held = result?.holdings.orEmpty().filter { it.quantity > ZERO }.map { it.securityId }.toSet()
    val heldFunds = wallet.securities.filter { it.id in held && it.type.equals("ETF", true) }
    LaunchedEffect(heldFunds.map { it.id }, retry) {
        busy = true
        val loaded = mutableMapOf<String, ExposureData>()
        val provenance = mutableListOf<String>()
        try {
            for (security in heldFunds) {
                try {
                    val response = vm.services.etfDetails(security, retry > 0)
                    val detail = response.details ?: continue
                    val underlying = detail.underlying
                    val sectors = detail.sectorExposure?.weights ?: underlying?.weights(true)?.toMap().orEmpty()
                    val countries = detail.countryExposure?.weights ?: underlying?.weights(false)?.toMap().orEmpty()
                    loaded[security.id] = ExposureData(sectors, countries, detail.sourceUrl,
                        detail.sectorExposure?.asOf ?: underlying?.asOf ?: detail.direct.asOf)
                    provenance += "${security.ticker} · ${loaded[security.id]!!.asOf} · ${detail.sourceUrl}" +
                        if (response.cached) " · cache" else ""
                    response.error?.let { provenance += "${security.ticker} : $it" }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { provenance += "${security.ticker} : composition indisponible" }
            }
            funds = loaded; sources = provenance
        } finally { busy = false }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Analyste IA · Équilibre du portefeuille", style = MaterialTheme.typography.titleLarge)
        Caption("Calculs locaux gratuits. Le service conversationnel IA n’est pas encore connecté; aucune recommandation IA n’est simulée.")
        Choice("Pour qui?", if (memberIndex == 0) "Compte complet" else member?.name ?: "Moi",
            listOf("Compte complet") + members.map { it.name }) { memberIndex = it }
        Choice("Portefeuille", wallet.portfolios.getOrNull(portfolioIndex - 1)?.name ?: "Tous les comptes",
            listOf("Tous les comptes") + wallet.portfolios.map { it.name }) { portfolioIndex = it }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedButton(onClick = { retry++ }, enabled = !busy) { Text("Actualiser les compositions") }
        val classified = wallet.securities.associate { security ->
            val known = SectorUniverse.stocks.find { it.symbol == security.symbol &&
                it.exchange == security.exchange && it.currency == security.currency && it.type == security.type }
            security.id to if (security.sector.isNullOrBlank() && known != null)
                security.copy(sector = known.sector) else security
        }
        val report = result?.let { PortfolioBalance.analyze(it, classified, funds) }
        if (report == null) Caption("Historique non exploitable. Vérifie tes transactions.")
        else {
            CardBlock {
                Metric("Valeur totale · CAD", money(report.total))
                Metric("Liquidités", money(report.cash))
                Caption("Cours du cache du portefeuille; compositions datées séparément. Les prix ne sont pas garantis temps réel.")
                held.mapNotNull { wallet.quotes[it] }.minOfOrNull { it.timestamp }?.let { stamp ->
                    Caption("Plus ancien cours utilisé : " + java.time.Instant.ofEpochMilli(stamp).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toString())
                }
            }
            report.warnings.forEach { Caption(it) }
            CardBlock {
                Text("Secteurs · portefeuille entier")
                Caption("Actions : classification enregistrée ou catalogue local; FNB : exposition publiée par le fournisseur indiqué ci-dessous.")
                report.sectors.forEach { Metric(it.label, money(it.value) + if (report.total != null) " · ${percent(it.weight)}" else " · % indisponible") }
                if (report.unknownSector > ZERO) Caption("La part non déterminée reste dans le total. Elle n’est pas redistribuée aux secteurs connus.")
            }
            CardBlock {
                Text("Pays des émetteurs · pas une répartition des revenus")
                report.countries.forEach { Metric(it.label, money(it.value) + if (report.total != null) " · ${percent(it.weight)}" else "") }
            }
            CardBlock {
                Text("Concentration sectorielle")
                TextEntry("Seuil personnel d’alerte (%)", cap, { cap = it }, true)
                val threshold = cap.replace(',', '.').toBigDecimalOrNull()?.takeIf { it > ZERO && it <= BigDecimal("100") }
                if (threshold == null) Caption("Entre un seuil entre 0 et 100 %.")
                else if (report.total != null) {
                    val over = report.sectors.filter { it.label in PortfolioBalance.sectors && it.weight > threshold }
                    if (over.isEmpty()) Caption("Aucun secteur connu ne dépasse ce seuil. Ce n’est pas une preuve de diversification suffisante.")
                    over.forEach { Caption("${it.label} : ${percent(it.weight)}, au-dessus de ton seuil de ${percent(threshold)}. Compare l’effet d’un prochain apport dans un autre secteur.") }
                    if (report.unknownSector > ZERO) Caption("Diagnostic partiel : exposition inconnue à compléter avant de conclure.")
                }
                Caption("Le seuil est un choix personnel, pas une allocation optimale universelle.")
            }
            CardBlock {
                Text("Simuler mon prochain achat")
                TextEntry("Montant CAD", budget, { budget = it }, true)
                Choice("Secteur simulé", PortfolioBalance.sectors[chosenSector], PortfolioBalance.sectors) { chosenSector = it }
                Choice("Financement", if (external) "Nouvel apport" else "Liquidités existantes",
                    listOf("Nouvel apport", "Liquidités existantes")) { external = it == 0 }
                Caption("Hypothèse : 100 % du montant dans ce secteur, sans frais. Aucun ordre n’est envoyé. Pour un FNB, son achat réel doit être décomposé par secteur.")
                val simulation = runCatching { PortfolioBalance.simulate(report,
                    budget.replace(',', '.').toBigDecimal(), PortfolioBalance.sectors[chosenSector], external) }
                simulation.getOrNull()?.forEach { row ->
                    val before = report.sectors.find { it.label == row.label }?.weight ?: ZERO
                    Metric(row.label, "${percent(before)} → ${percent(row.weight)}")
                }
                simulation.exceptionOrNull()?.let { Caption(it.message ?: "Simulation indisponible.") }
            }
            sources.forEach { Caption(it) }
        }
    }
}
