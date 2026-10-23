package ca.monwallet.app.domain

import ca.monwallet.app.marketdata.EtfDetails
import ca.monwallet.app.marketdata.SectorUniverse
import java.math.BigDecimal
import java.math.RoundingMode

data class ExposureLine(val label: String, val value: BigDecimal, val percent: BigDecimal)
data class PortfolioExposure(val total: BigDecimal?, val sectors: List<ExposureLine>,
    val countries: List<ExposureLine>, val currencies: List<ExposureLine>,
    val unknownSector: BigDecimal, val unknownCountry: BigDecimal, val cash: BigDecimal,
    val notes: List<String>) {
    val sectorCoverage get() = total?.let { (it - unknownSector).pct(it) }
}
data class PurchaseImpact(val quantity: BigDecimal, val spent: BigDecimal, val after: PortfolioExposure)

object PortfolioResearch {
    fun sector(raw: String?): String? = raw?.trim()?.takeIf { it.isNotBlank() && it !in setOf("-", "Non déterminé", "Unknown") }?.let {
        when (it.lowercase()) {
            "information technology", "technology", "technologie" -> "Technologie"
            "financials", "financial services", "services financiers" -> "Services financiers"
            "health care", "healthcare", "santé" -> "Santé"
            "consumer discretionary", "consumer cyclical", "consommation cyclique" -> "Consommation cyclique"
            "consumer staples", "consumer defensive", "consommation défensive" -> "Consommation défensive"
            "energy", "énergie" -> "Énergie"
            "materials", "basic materials", "matériaux" -> "Matériaux"
            "industrials", "industrie" -> "Industrie"
            "communication services", "communication" -> "Communication"
            "utilities", "services publics" -> "Services publics"
            "real estate", "immobilier" -> "Immobilier"
            "cash and/or derivatives", "cash", "liquidités", "cash equivalents" -> "Liquidités / dérivés"
            else -> it
        }
    }
    fun classified(security: Security): Security = if (security.sector != null) security else
        security.copy(sector = SectorUniverse.stocks.find {
            it.symbol.equals(security.symbol, true) && it.currency == security.currency }?.sector)

    /** Published percentages, including partial coverage. Never normalize the known portion to 100. */
    fun fundWeights(fund: EtfDetails?, sectorMode: Boolean): Map<String, BigDecimal> {
        if (fund == null) return emptyMap()
        val explicit = if (sectorMode) fund.sectorExposure else fund.countryExposure
        val raw = explicit?.weights ?: fund.underlying?.weights(sectorMode)?.toMap() ?: emptyMap()
        val map = raw.entries.mapNotNull { (key, v) ->
            val label = if (sectorMode) sector(key) else key.takeIf { it !in setOf("Non déterminé", "-", "Unknown") && it.isNotBlank() }
            if (label == null || v < ZERO) null else label to v
        }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.fold(ZERO, BigDecimal::add) }
        // Rounding above 100 is not silently re-scaled; leave the composition unknown.
        return map.takeIf { it.values.fold(ZERO, BigDecimal::add) <= BigDecimal("100") } ?: emptyMap()
    }
    private fun lines(values: Map<String, BigDecimal>, total: BigDecimal) = values.map { (key, value) ->
        ExposureLine(key, value, value.pct(total) ?: ZERO) }.sortedByDescending { it.value }
    fun build(result: Result, securities: List<Security>, funds: Map<String, EtfDetails> = emptyMap()): PortfolioExposure {
        val total = result.value?.takeIf { it >= ZERO }
        if (total == null) return PortfolioExposure(null, emptyList(), emptyList(), emptyList(), ZERO, ZERO, result.cash,
            listOf("Valeur totale indisponible : cours, taux FX ou historique à compléter."))
        val sectors = mutableMapOf<String, BigDecimal>(); val countries = mutableMapOf<String, BigDecimal>()
        val currencies = mutableMapOf<String, BigDecimal>(); val notes = mutableListOf<String>()
        var unknownSector = ZERO; var unknownCountry = ZERO
        fun add(map: MutableMap<String, BigDecimal>, key: String, value: BigDecimal) { map[key] = (map[key] ?: ZERO) + value }
        result.holdings.filter { it.quantity > ZERO }.forEach { h ->
            val s = securities.find { it.id == h.securityId }?.let(::classified)
            val value = h.value ?: return@forEach
            add(currencies, s?.currency ?: "Inconnue", value)
            if (s?.type?.uppercase() == "ETF") {
                val fund = funds[s.id]
                val sw = fundWeights(fund, true); val cw = fundWeights(fund, false)
                sw.forEach { (label, weight) -> add(sectors, label, value * weight / BigDecimal("100")) }
                cw.forEach { (label, weight) -> add(countries, label, value * weight / BigDecimal("100")) }
                unknownSector += value * (BigDecimal("100") - sw.values.fold(ZERO, BigDecimal::add)) / BigDecimal("100")
                unknownCountry += value * (BigDecimal("100") - cw.values.fold(ZERO, BigDecimal::add)) / BigDecimal("100")
                notes += s.ticker + " : " + (fund?.issuer ?: "composition indisponible") +
                    " · " + (fund?.underlying?.asOf ?: fund?.sectorExposure?.asOf ?: "date non fournie") +
                    (fund?.compositionNote?.let { " · " + it } ?: "")
            } else {
                val label = sector(s?.sector)
                if (label == null) unknownSector += value else add(sectors, label, value)
                val country = s?.country?.takeIf { it.isNotBlank() && it != "-" }
                if (country == null) unknownCountry += value else add(countries, country, value)
            }
        }
        if (result.cash > ZERO) {
            add(sectors, "Liquidités", result.cash); add(countries, "Liquidités", result.cash)
            add(currencies, "Liquidités en CAD (valeur comptable)", result.cash)
        }
        if (unknownSector > ZERO) add(sectors, "Non déterminé", unknownSector)
        if (unknownCountry > ZERO) add(countries, "Non déterminé", unknownCountry)
        return PortfolioExposure(total, lines(sectors, total), lines(countries, total), lines(currencies, total),
            unknownSector, unknownCountry, result.cash, notes)
    }
    fun simulate(before: PortfolioExposure, security: Security, quote: Quote?, budget: BigDecimal,
        fromCash: Boolean, fx: BigDecimal?, fund: EtfDetails? = null): PurchaseImpact? {
        val total = before.total ?: return null
        if (budget <= ZERO || (fromCash && budget > before.cash) || quote == null ||
            quote.currency != security.currency || quote.price <= ZERO) return null
        val rate = when (quote.currency) { "CAD" -> ONE; "USD" -> fx?.takeIf { it > ZERO }; else -> null } ?: return null
        val unit = quote.price * rate
        val qty = budget.divide(unit, 0, RoundingMode.DOWN)
        if (qty <= ZERO) return null
        val spent = qty * unit
        val afterTotal = if (fromCash) total else total + budget
        val sectorValues = before.sectors.associate { it.label to it.value }.toMutableMap()
        val countryValues = before.countries.associate { it.label to it.value }.toMutableMap()
        val currencyValues = before.currencies.associate { it.label to it.value }.toMutableMap()
        fun add(map: MutableMap<String, BigDecimal>, key: String, value: BigDecimal) { map[key] = (map[key] ?: ZERO) + value }
        val cashDelta = if (fromCash) -spent else budget - spent
        add(sectorValues, "Liquidités", cashDelta); add(countryValues, "Liquidités", cashDelta)
        add(currencyValues, "Liquidités en CAD (valeur comptable)", cashDelta)
        add(currencyValues, security.currency, spent)
        val s = classified(security)
        if (s.type.uppercase() == "ETF") {
            val sw = fundWeights(fund, true); val cw = fundWeights(fund, false)
            sw.forEach { (label, v) -> add(sectorValues, label, spent * v / BigDecimal("100")) }
            cw.forEach { (label, v) -> add(countryValues, label, spent * v / BigDecimal("100")) }
            add(sectorValues, "Non déterminé", spent * (BigDecimal("100") - sw.values.fold(ZERO, BigDecimal::add)) / BigDecimal("100"))
            add(countryValues, "Non déterminé", spent * (BigDecimal("100") - cw.values.fold(ZERO, BigDecimal::add)) / BigDecimal("100"))
        } else {
            add(sectorValues, sector(s.sector) ?: "Non déterminé", spent)
            add(countryValues, s.country ?: "Non déterminé", spent)
        }
        return PurchaseImpact(qty, spent, PortfolioExposure(afterTotal, lines(sectorValues, afterTotal),
            lines(countryValues, afterTotal), lines(currencyValues, afterTotal), sectorValues["Non déterminé"] ?: ZERO,
            countryValues["Non déterminé"] ?: ZERO, before.cash + cashDelta, before.notes))
    }
}
