package ca.monwallet.app.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ca.monwallet.app.marketdata.PerformancePeriod

data class MarketColumn(val id: String, val label: String, val width: Int = 86)

object MarketColumns {
    val all = listOf(
        MarketColumn("ticker", "Ticker", 78), MarketColumn("name", "Nom", 170),
        MarketColumn("exchange", "Exchange"), MarketColumn("currency", "Devise", 65),
        MarketColumn("price", "Prix"), MarketColumn("dayAmount", "Jour $"),
        MarketColumn("dayPercent", "Jour %"), MarketColumn("sparkline", "Sparkline", 80),
        MarketColumn("period", "Performance période", 88),
        MarketColumn("1S", "1S"), MarketColumn("1M", "1M"), MarketColumn("3M", "3M"),
        MarketColumn("6M", "6M"), MarketColumn("1A", "1A"), MarketColumn("5A", "5A"),
        MarketColumn("cagr5", "CAGR 5A"), MarketColumn("momentum", "Momentum", 72),
        MarketColumn("cap", "Market Cap", 94), MarketColumn("pe", "P/E"),
        MarketColumn("forwardPe", "Forward P/E"), MarketColumn("ps", "P/S"),
        MarketColumn("pb", "P/B"), MarketColumn("evEbitda", "EV/EBITDA"),
        MarketColumn("dividend", "Dividend Yield"), MarketColumn("sector", "Secteur", 130),
        MarketColumn("industry", "Industrie", 150), MarketColumn("volume", "Volume"),
        MarketColumn("avgVolume", "Volume moyen"), MarketColumn("dollarVolume", "Dollar Volume"),
        MarketColumn("beta", "Beta"), MarketColumn("high52", "52W High"),
        MarketColumn("distance52", "Distance 52W High"), MarketColumn("roe", "ROE"),
        MarketColumn("roa", "ROA"), MarketColumn("revenueGrowth", "Revenue Growth"),
        MarketColumn("epsGrowth", "EPS Growth"), MarketColumn("analystCount", "Nombre analystes"),
        MarketColumn("target", "Target moyen"), MarketColumn("targetHigh", "Target haut"),
        MarketColumn("targetLow", "Target bas"), MarketColumn("upside", "Analyst Upside"),
        MarketColumn("consensus", "Consensus analystes", 110),
        MarketColumn("portfolioWeight", "Poids portefeuille"),
        MarketColumn("extended", "Pre-market / After-hours", 140),
        MarketColumn("opportunity", "Score analystes", 90),
    ).associateBy { it.id }
    val discoverDefault = listOf("ticker", "price", "period", "cap", "momentum")
    val watchlistDefault = listOf("ticker", "price", "sparkline", "dayPercent", "period")
    val presets = mapOf(
        "Minimal" to watchlistDefault,
        "Ultra minimal" to listOf("ticker", "price", "dayPercent"),
        "Momentum" to listOf("ticker", "price", "dayPercent", "period", "momentum"),
        "Long terme" to listOf("ticker", "price", "1A", "5A", "cagr5", "dividend"),
        "Analyse" to listOf("ticker", "price", "period", "cap", "pe", "upside"),
    )

    fun label(id: String, period: PerformancePeriod): String =
        if (id == "period") period.label else all[id]?.label ?: id

    fun restore(raw: String?, default: List<String>): List<String> =
        raw?.split('|')?.filter { it in all }?.distinct()?.takeIf { it.isNotEmpty() } ?: default
}

@Composable
fun ColumnPicker(columns: List<String>, allowed: List<String>, onChange: (List<String>) -> Unit,
    onClose: () -> Unit, extendedSetting: Pair<Boolean, (Boolean) -> Unit>? = null) {
    val selected by rememberUpdatedState(columns)
    AlertDialog(onDismissRequest = onClose, title = { Text("⚙ Colonnes") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            extendedSetting?.let { (enabled, change) ->
                Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(enabled, onCheckedChange = change)
                    Text("Pre-market / After-hours")
                }
                HorizontalDivider()
            }
            Text("Maintiens ☰ puis glisse pour réordonner", style = MaterialTheme.typography.bodySmall)
            val ordered = columns + allowed.filter { it !in columns }
            ordered.forEach { id ->
                key(id) {
                    var offset by remember { mutableFloatStateOf(0f) }
                    Row(Modifier.fillMaxWidth().height(42.dp).pointerInput(id) {
                        detectDragGesturesAfterLongPress(onDragEnd = { offset = 0f },
                            onDragCancel = { offset = 0f }) { change, drag ->
                            change.consume()
                            offset += drag.y
                            val index = selected.indexOf(id)
                            val target = if (offset > 25.dp.toPx()) index + 1
                                else if (offset < -25.dp.toPx()) index - 1 else index
                            if (index >= 0 && target in selected.indices && target != index) {
                                onChange(selected.toMutableList().apply { add(target, removeAt(index)) })
                                offset = 0f
                            }
                        }
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(id in columns, onCheckedChange = { yes ->
                            onChange(if (yes) columns + id else columns - id)
                        })
                        Text((if (id in columns) "☰  " else "") + (MarketColumns.all[id]?.label ?: id))
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Terminé") } })
}
