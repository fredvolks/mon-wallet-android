package ca.monwallet.app.widgets

/** The same height budget drives the launcher and the live configuration preview. */
internal object WidgetTitleLayout {
    enum class Density(val rowHeight: Int) { SPACIOUS(27), COMPACT(20), ULTRA(18) }

    data class Plan(
        val limit: Int,
        val visible: Int,
        val density: Density,
        val chart: Boolean,
        val footer: Boolean,
    )

    fun plan(config: WidgetSettings, kind: String, heightDp: Int, available: Int): Plan {
        val height = heightDp.coerceAtLeast(0)
        val isWide = kind.endsWith("4x2")
        val automatic = when {
            isWide -> when {
                height < 132 -> 3
                height < 147 -> 4
                else -> 5
            }
            kind.endsWith("4x3") -> if (height >= 200) 6 else 5
            kind.endsWith("2x3") -> if (height >= 170) 3 else 2
            else -> if (height < 110) 0 else if (height < 135) 1 else 2
        }
        val capacity = when {
            isWide -> when {
                height < 132 -> 3
                height < 147 -> 4
                height < 167 -> 5
                else -> 6
            }
            kind.endsWith("4x3") -> 6
            kind.endsWith("2x3") -> 3
            else -> if (height < 110) 0 else if (height < 135) 1 else 2
        }
        val limit = (if (config.titleCount == 0) automatic else config.titleCount.coerceIn(1, 6))
            .coerceAtMost(capacity)
        val visible = if (config.showTitles && config.style !in
            setOf("Résumé", "Graphique", "Ultra compact")) available.coerceAtMost(limit) else 0
        val density = when {
            visible >= 6 -> Density.ULTRA
            visible >= 4 -> Density.COMPACT
            else -> Density.SPACIOUS
        }
        // Wide chart now lives inside the Daily card. Hide it when the Daily
        // amount and percentage need that vertical space.
        val chart = config.chart && config.style in
            setOf("Mixte", "Mixte premium", "Daily + Titres", "Graphique") &&
            (!isWide || height >= 163 || (visible <= 3 && height >= 145))
        val footer = config.showUpdated &&
            (!isWide || height >= 139)
        return Plan(limit, visible, density, chart, footer)
    }
}
