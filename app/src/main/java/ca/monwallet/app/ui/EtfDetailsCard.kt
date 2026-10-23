package ca.monwallet.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.*
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal

@Composable
internal fun EtfDetailsCard(security: Security, walletValue: BigDecimal? = null,
    walletWeight: BigDecimal? = null,
    load: suspend (Security, Boolean) -> EtfLoad = { s, _ -> EtfLoad(EtfData.fetch(s)) }) {
    var state by remember(security.id) { mutableStateOf<EtfLoad?>(null) }
    var retry by remember(security.id) { mutableIntStateOf(0) }
    var loading by remember(security.id) { mutableStateOf(true) }
    var tab by remember(security.id) { mutableIntStateOf(0) }
    var expanded by remember(security.id, tab) { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(security.id, retry) {
        loading = true
        try { state = load(security, retry > 0) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { state = EtfLoad(state?.details, state?.details != null, e.message) }
        finally { loading = false }
    }
    CardBlock {
        Text("Composition du FNB · ${security.ticker}", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        if (walletValue != null) {
            Metric("Dans ton portefeuille", "${money(walletValue)} · ${allocationPercent(walletWeight)}", Green)
            Caption("Le poids dans le wallet est distinct des pondérations à l’intérieur du fonds.")
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        state?.error?.let { Caption(it) }
        val fund = state?.details
        if (fund != null) {
            Caption("${fund.issuer}${if (state?.cached == true) " · en cache" else ""}")
            Chips(listOf("Positions", "Sous-jacents", "Secteurs", "Pays"), tab) { tab = it }
            val composition = if (tab == 0) fund.direct else fund.underlying
            if (composition == null) {
                Caption("Détail sous-jacent non fourni par cette source. Les positions directes ne sont pas utilisées comme exposition sectorielle ou géographique.")
            } else {
                Caption("Composition au ${date(composition.asOf)}")
                if (tab == 0) Caption("Positions détenues directement par le fonds, incluant ses FNB et ses liquidités.")
                else Caption("Exposition sous-jacente publiée par l’émetteur, sans ajouter à nouveau les FNB intermédiaires.")
                val rows = if (tab <= 1) composition.positions.sortedByDescending { it.weight }
                    .map { Triple(it.symbol, it.name, it.weight) }
                    else composition.weights(tab == 2).map { Triple(it.first, "", it.second) }
                val limit = if (expanded) 100 else 10
                rows.take(limit).forEach { (name, description, weight) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.SemiBold)
                            if (description.isNotBlank()) Text(description, color = Muted, fontSize = 11.sp)
                        }
                        Text(allocationPercent(weight), color = Blue, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = Border, thickness = .5.dp)
                }
                if (rows.size > 10 && !expanded) TextButton(onClick = { expanded = true }) {
                    Text("Afficher plus (${rows.size})") }
                val shown = rows.take(limit).fold(ZERO) { total, row -> total + row.third }
                Caption("${minOf(rows.size, limit)} lignes affichées · ${allocationPercent(shown)} du fonds. Poids publiés, sans renormalisation.")
                if (rows.size > 100 && expanded) Caption("Les autres positions sont disponibles sur la fiche officielle.")
            }
            Spacer(Modifier.height(12.dp))
            Text("Détails du fonds", fontWeight = FontWeight.Bold)
            Metric("Frais de gestion", fund.managementFee ?: "Non disponibles")
            Metric("Ratio des frais de gestion (RFG)", fund.mer ?: "Non disponible")
            Caption("RFG et frais de gestion sont deux mesures distinctes; ils ne s’additionnent pas.")
            fund.distributionFrequency?.let { frequency ->
                Metric("Distributions", when (frequency.lowercase()) {
                    "quarterly" -> "Trimestrielles"; "monthly" -> "Mensuelles"
                    "annually", "annual" -> "Annuelles"; "semi-annually" -> "Semestrielles"
                    else -> frequency
                })
            }
            fund.netAssets?.let { Metric("Actif net du fonds", it) }
            Caption("Consulté le ${time(fund.fetchedAt)}")
        } else if (!loading) Caption("Les pondérations ne sont pas disponibles pour ce FNB pour le moment.")
        Row {
            TextButton(onClick = { retry++ }, enabled = !loading) { Text("Actualiser") }
            val url = fund?.sourceUrl ?: EtfData.officialPage(security)
            if (url != null) TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                Text("Fiche officielle ↗") }
        }
    }
}

/** Allocation percentages are shares of a total, not signed investment returns. */
internal fun allocationPercent(value: BigDecimal?): String = value?.let { "${number(it, 2)} %" } ?: "—"
