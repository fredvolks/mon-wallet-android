package ca.monwallet.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.*
import java.math.BigDecimal

@Composable
internal fun ScreenerHero(count: Int, busy: Boolean, filters: Filters) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).background(
        Brush.linearGradient(listOf(Color(0xFF12303F), Color(0xFF10201F))), RoundedCornerShape(18.dp))
        .border(1.dp, Green.copy(alpha = .22f), RoundedCornerShape(18.dp)).padding(18.dp)) {
        Text("SCREENER", color = Green, fontSize = 11.sp, letterSpacing = 2.sp)
        Text(if (filters.needsDividend()) "Ton prochain revenu" else "Explore le marché",
            fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(if (busy) "Scan en cours · $count correspondances" else "$count titres correspondent à tes critères",
            color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Text(filters.exchange.replace(",", " · "), color = Blue, fontSize = 10.sp)
        if (filters.needsDividend()) Text(buildString {
            append("Dividendes / distributions")
            filters.dividendMin?.let { append(" ≥ ${number(it)} %") }
            filters.dividendMax?.let { append(" · ≤ ${number(it)} %") }
            if (filters.dividendFrequency.isNotBlank()) append(" · ${dividendFrequencyLabel(filters.dividendFrequency)}")
        }, color = Green, fontSize = 12.sp)
    }
}

internal fun dividendFrequencyLabel(code: String) = when (code) {
    "MONTHLY" -> "Mensuelle"; "QUARTERLY" -> "Trimestrielle"; "SEMI_ANNUAL" -> "Semestrielle"
    "ANNUAL" -> "Annuelle"; "IRREGULAR" -> "Irrégulière"; else -> "Non déterminée"
}

@Composable
internal fun DividendFilterPanel(filters: Filters, update: (Filters) -> Unit) {
    CardBlock {
        Text("Revenu et dividendes", fontWeight = FontWeight.Bold, fontSize = 19.sp)
        Caption("Rendement calculé sur les distributions historiques des 12 derniers mois quand elles sont disponibles.")
        FilterToggle("Titres qui versent un dividende", filters.dividendOnly) { update(filters.copy(dividendOnly = it)) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("1", "2", "3", "4", "5", "6", "8").forEach { n ->
                FilterChip(filters.dividendMin?.compareTo(BigDecimal(n)) == 0,
                    onClick = { update(filters.copy(dividendOnly = true, dividendMin = BigDecimal(n))) },
                    label = { Text("≥ $n %") })
            }
        }
        FilterNumber("Rendement dividende minimum (%)", filters.dividendMin) { update(filters.copy(dividendMin = it)) }
        FilterNumber("Rendement dividende maximum (%)", filters.dividendMax) { update(filters.copy(dividendMax = it)) }
        if (filters.dividendMin != null && filters.dividendMax != null && filters.dividendMin > filters.dividendMax)
            Text("Le minimum doit être inférieur ou égal au maximum.", color = Red, fontSize = 12.sp)
        Caption("Fréquence observée des détachements")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("" to "Toutes", "MONTHLY" to "Mensuelle", "QUARTERLY" to "Trimestrielle", "SEMI_ANNUAL" to "Semestrielle", "ANNUAL" to "Annuelle").forEach { (code,label) ->
                FilterChip(filters.dividendFrequency == code, onClick = { update(filters.copy(dividendFrequency = code)) }, label = { Text(label) })
            }
        }
        FilterNumber("Croissance du dividende sur 1 an minimum (%)", filters.dividendGrowthMin) { update(filters.copy(dividendGrowthMin = it)) }
        FilterNumber("Ratio de distribution maximum (%)", filters.payoutMax) { update(filters.copy(payoutMax = it)) }
        Caption("Les critères sans données excluent le titre. La fréquence est historique; les montants peuvent varier et inclure des distributions exceptionnelles.")
    }
}

@Composable
internal fun ScreenerStockCard(row: DiscoveryRow, quote: Quote?, period: PerformancePeriod, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp), color = Panel, border = BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Logo(row.security)
                Column(Modifier.weight(1f)) {
                    Text(row.security.ticker, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text(row.security.name, color = Muted, fontSize = 11.sp, maxLines = 2)
                    Text("${row.security.exchange} · ${row.security.currency}", color = Blue, fontSize = 10.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(money(quote?.price ?: row.price, row.security.currency), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("${period.label} · ${percent(row.performance(period))}", color = tint(row.performance(period)), fontSize = 11.sp)
                }
            }
            HorizontalDivider(color = Border)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Dividende / distribution", color = Muted, fontSize = 10.sp)
                    Text(allocationPercent(row.dividendYield), color = Green, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text("Capitalisation", color = Muted, fontSize = 10.sp)
                    Text(row.cap?.let { number(it.divide(BigDecimal("1000000000"), MC), 1) + " G" } ?: "—", fontWeight = FontWeight.SemiBold)
                    Text("P/E ${number(row.pe)}", color = Muted, fontSize = 11.sp)
                }
            }
            row.dividend?.let { div ->
                Text("${money(div.annualAmount, div.currency)} / part sur 12 mois · ${dividendFrequencyLabel(div.frequency)} observée", color = Blue, fontSize = 11.sp)
                Text("Croissance annuelle ${percent(div.growth)} · Dernier détachement ${div.lastExDate?.let(::date) ?: "—"}", color = Muted, fontSize = 10.sp)
                Text("${div.source} · consulté le ${date(div.asOf)}", color = Muted, fontSize = 9.sp)
            } ?: Text("Dividende selon couverture du fournisseur · — = donnée absente", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
internal fun ScreenerFilterGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    CardBlock {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(if (expanded) "−" else "+")
        }
        if (expanded) content()
    }
}
