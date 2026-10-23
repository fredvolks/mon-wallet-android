package ca.monwallet.app.domain

import java.math.BigDecimal

/** Fractions are of the whole account, never of only the classified positions. */
data class ExposureData(val sectors: Map<String, BigDecimal>, val countries: Map<String, BigDecimal>,
    val source: String, val asOf: String)
data class BalanceReport(val total: BigDecimal?, val cash: BigDecimal,
    val sectors: List<AllocationSlice>, val countries: List<AllocationSlice>,
    val unknownSector: BigDecimal, val unknownCountry: BigDecimal,
    val warnings: List<String>)

object PortfolioBalance {
    const val UNKNOWN = "Non déterminé"
    fun sector(raw: String): String = when (raw.trim().lowercase().replace("_", " ")) {
        "technology", "information technology", "technologie" -> "Technologie"
        "financials", "financial services", "finance", "services financiers" -> "Finance"
        "healthcare", "health care", "santé" -> "Santé"
        "consumer discretionary", "consumer cyclical", "consommation discrétionnaire" -> "Consommation discrétionnaire"
        "consumer staples", "consumer defensive", "consommation de base" -> "Consommation de base"
        "industrials", "industrie" -> "Industrie"
        "energy", "énergie" -> "Énergie"
        "materials", "basic materials", "matériaux" -> "Matériaux"
        "communication services", "communications" -> "Communications"
        "utilities", "services publics" -> "Services publics"
        "real estate", "immobilier" -> "Immobilier"
        "cash", "cash and/or derivatives", "cash and equivalents", "liquidités" -> "Liquidités / dérivés"
        else -> UNKNOWN
    }
    val sectors = listOf("Technologie", "Finance", "Santé", "Consommation discrétionnaire",
        "Consommation de base", "Industrie", "Énergie", "Matériaux", "Communications", "Services publics", "Immobilier")

    fun analyze(result: Result, securities: Map<String, Security>, funds: Map<String, ExposureData>): BalanceReport {
        val warnings = mutableListOf<String>()
        val held = result.holdings.filter { it.quantity > ZERO }
        val total = if (result.value != null && held.all { it.value != null && it.value >= ZERO } && result.cash >= ZERO)
            held.fold(result.cash) { n, h -> n + h.value!! }.takeIf { it > ZERO } else null
        if (total == null) warnings += "Valeur totale indisponible : cours, conversion, historique ou liquidités à vérifier."
        val sectorValues = mutableMapOf<String, BigDecimal>()
        val countryValues = mutableMapOf<String, BigDecimal>()
        fun add(map: MutableMap<String, BigDecimal>, label: String, value: BigDecimal) {
            map[label] = (map[label] ?: ZERO) + value
        }
        fun distribute(value: BigDecimal, weights: Map<String, BigDecimal>?, map: MutableMap<String, BigDecimal>, canonical: (String) -> String) {
            val sum = weights?.values?.fold(ZERO, BigDecimal::add) ?: ZERO
            if (weights.isNullOrEmpty() || weights.values.any { it < ZERO } || sum > BigDecimal("100")) {
                add(map, UNKNOWN, value); return
            }
            weights.forEach { (label, weight) -> add(map, canonical(label), value * weight.divide(BigDecimal("100"), MC)) }
            add(map, UNKNOWN, value * (BigDecimal("100") - sum).divide(BigDecimal("100"), MC))
        }
        held.forEach { h ->
            val value = h.value ?: return@forEach
            val security = securities[h.securityId]
            if (security?.type.equals("ETF", true)) {
                val fund = funds[h.securityId]
                distribute(value, fund?.sectors, sectorValues, ::sector)
                distribute(value, fund?.countries, countryValues) { it.takeIf { s -> s.isNotBlank() && s != "-" } ?: UNKNOWN }
                if (fund == null) warnings += "${security?.ticker} : exposition sous-jacente indisponible."
            } else {
                add(sectorValues, security?.sector?.let(::sector) ?: UNKNOWN, value)
                // Listing exchange/currency is not the company's country.
                add(countryValues, security?.country?.takeIf { it.isNotBlank() } ?: UNKNOWN, value)
            }
        }
        if (result.cash > ZERO) {
            add(sectorValues, "Liquidités", result.cash)
            add(countryValues, "Liquidités", result.cash)
        }
        fun slices(map: Map<String, BigDecimal>) = map.filterValues { it > ZERO }.map { (label, value) ->
            AllocationSlice(label, value, total?.let { value.pct(it) } ?: ZERO)
        }.sortedByDescending { it.value }
        return BalanceReport(total, result.cash, slices(sectorValues), slices(countryValues),
            sectorValues[UNKNOWN] ?: ZERO, countryValues[UNKNOWN] ?: ZERO, warnings)
    }

    /** Simulates an allocation in CAD, not a trade; external money increases the denominator. */
    fun simulate(report: BalanceReport, budget: BigDecimal, targetSector: String, external: Boolean): List<AllocationSlice> {
        require(budget > ZERO && targetSector in sectors) { "Montant positif et secteur requis." }
        val total = requireNotNull(report.total) { "Valeur totale incomplète." }
        require(external || budget <= report.cash) { "Liquidités insuffisantes : choisir un nouvel apport." }
        val denominator = if (external) total + budget else total
        val values = report.sectors.associate { it.label to it.value }.toMutableMap()
        values[targetSector] = (values[targetSector] ?: ZERO) + budget
        if (!external) values["Liquidités"] = report.cash - budget
        return values.filterValues { it > ZERO }.map { (label, value) -> AllocationSlice(label, value, value.pct(denominator)!!) }
            .sortedByDescending { it.value }
    }
}
