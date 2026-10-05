package ca.monwallet.app.widgets

import android.content.Context
import ca.monwallet.app.domain.*
import com.google.gson.Gson

data class WidgetSettings(
    val portfolio: String = "all", val style: String = "Mixte",
    val totalPercent: Boolean = true, val dayPercent: Boolean = true,
    val dayAmount: Boolean = true, val totalAmount: Boolean = false,
    val value: Boolean = false, val invested: Boolean = false,
    val showTitles: Boolean = true, val custom: Boolean = false,
    val titleIds: List<String> = emptyList(), val sort: String = "Valeur",
    val ticker: Boolean = true, val logo: Boolean = true,
    val titleDayPercent: Boolean = true, val titleDayAmount: Boolean = false,
    val titleTotalPercent: Boolean = true, val price: Boolean = false,
    val titleValue: Boolean = false, val weight: Boolean = false,
    val chart: Boolean = false, val period: String = "Jour",
    val hideAmounts: Boolean = false, val refresh: Int = 0,
) {
    fun titles(result: Result): List<Holding> {
        val held = result.holdings.filter { it.quantity > ZERO }
        if (custom) return titleIds.mapNotNull { key -> held.find { it.securityId == key } }
        return when (sort) {
            "Gain jour" -> held.sortedByDescending { it.day }
            "Perte jour" -> held.sortedBy { it.day }
            "Variation %" -> held.sortedByDescending { it.dayPercent }
            "Rendement %" -> held.sortedByDescending { it.percent }
            "Pire rendement" -> held.sortedBy { it.percent }
            "Portefeuille" -> held
            else -> held.sortedByDescending { it.value }
        }
    }
    companion object {
        fun load(c: Context, id: Int): WidgetSettings {
            val p = c.getSharedPreferences("widget_config", 0)
            return p.getString("config:$id", null)?.let {
                runCatching { Gson().fromJson(it, WidgetSettings::class.java) }.getOrNull()
            } ?: WidgetSettings(portfolio = p.getString("portfolio:$id", "all") ?: "all")
        }
        fun save(c: Context, id: Int, settings: WidgetSettings, owner: String) {
            c.getSharedPreferences("widget_config", 0).edit()
                .putString("config:$id", Gson().toJson(settings))
                .putString("owner:$id", owner).remove("portfolio:$id").apply()
        }
        fun delete(c: Context, id: Int) {
            c.getSharedPreferences("widget_config", 0).edit().remove("config:$id")
                .remove("portfolio:$id").remove("owner:$id").apply()
        }
    }
}
