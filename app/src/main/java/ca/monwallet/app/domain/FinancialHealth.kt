package ca.monwallet.app.domain

import java.math.BigDecimal

data class FinancialHealth(val rating: FinancialMetricRating, val title: String,
    val details: List<String>)

/** A transparent screening profile, never an automatic buy/sell recommendation. */
object FinancialHealthProfile {
    fun assess(security: Security, fundamentals: Fundamentals?): FinancialHealth {
        if (security.type.uppercase() != "STOCK") return FinancialHealth(
            FinancialMetricRating.CONTEXT, "Profil financier non applicable", listOf("Ces seuils visent les actions, pas les FNB."))
        val m = fundamentals?.metrics.orEmpty()
        fun num(key: String) = m[key]?.replace(",", "")?.toBigDecimalOrNull()
        val pe = num("P/E")
        val debt = num("Dette / capitaux propres")
        val cap = num("Capitalisation (M)")
        val peLabel = pe?.toPlainString() ?: if (fundamentals?.unavailableMetrics?.contains("P/E") == true)
            "non fourni par la source" else "indisponible"
        val details = listOf("P/E : $peLabel · seuil 27",
            "Dette / capitaux propres : ${debt ?: "indisponible"} · seuil 2",
            "Capitalisation : ${cap?.divide(BigDecimal(1000), 2, java.math.RoundingMode.HALF_UP)?.toPlainString() ?: "indisponible"} G ${security.currency} · seuil 1 G")
        val income = num("Bénéfice net")
        val adverse = (pe != null && pe <= ZERO) || (debt != null && (debt < ZERO || debt > BigDecimal("2"))) ||
            (income != null && income < ZERO)
        val missing = listOf(pe, debt, cap).any { it == null }
        val rating = when {
            adverse -> FinancialMetricRating.UNFAVORABLE
            missing -> FinancialMetricRating.CONTEXT
            pe!! > BigDecimal("27") || cap!! < BigDecimal("1000") -> FinancialMetricRating.WATCH
            else -> FinancialMetricRating.FAVORABLE
        }
        val title = when (rating) {
            FinancialMetricRating.FAVORABLE -> "Vert · critères de sélection respectés"
            FinancialMetricRating.WATCH -> "Jaune · valorisation ou taille à surveiller"
            FinancialMetricRating.UNFAVORABLE -> "Rouge · dette ou rentabilité à surveiller"
            else -> "Gris · données insuffisantes"
        }
        return FinancialHealth(rating, title, details +
            listOfNotNull(if (income != null && income < ZERO) "Dernier bénéfice net négatif" else null,
                if (missing) "Évaluation partielle : au moins un critère manque." else null,
                "Seuils de sélection généraux; pertinence variable selon le secteur. Ce n’est pas un signal d’achat ou de vente."))
    }
}
