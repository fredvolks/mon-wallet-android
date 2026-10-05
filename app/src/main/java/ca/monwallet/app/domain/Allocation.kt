package ca.monwallet.app.domain

import java.math.BigDecimal

data class AllocationSlice(val label: String, val value: BigDecimal, val weight: BigDecimal)
data class AllocationResult(val slices: List<AllocationSlice>, val complete: Boolean, val total: BigDecimal?)

/** Allocations use current market value. Unknown quotes and unknown fund exposure remain unknown. */
object Allocation {
    fun byTitle(result: Result, securities: Map<String, Security>, includeCash: Boolean): AllocationResult {
        val held = result.holdings.filter { it.quantity > ZERO }
        val values = held.mapNotNull { h -> h.value?.let { (securities[h.securityId]?.ticker ?: h.securityId) to it } }
        val complete = values.size == held.size
        val cash = if (includeCash && result.cash > ZERO) listOf("Cash" to result.cash) else emptyList()
        return portions(values + cash, complete)
    }

    fun bySector(
        result: Result,
        securities: Map<String, Security>,
        fundSectors: Map<String, Map<String, BigDecimal>> = emptyMap(),
        includeCash: Boolean = false,
    ): AllocationResult {
        val values = mutableListOf<Pair<String, BigDecimal>>()
        var complete = true
        result.holdings.filter { it.quantity > ZERO }.forEach { holding ->
            val value = holding.value
            val security = securities[holding.securityId]
            if (value == null || security == null) { complete = false; return@forEach }
            if (security.type == "ETF") {
                val sectors = fundSectors[security.id]
                if (sectors == null || sectors.isEmpty() || sectors.values.any { it < ZERO } ||
                    sectors.values.fold(ZERO, BigDecimal::add).compareTo(ONE) != 0) {
                    complete = false
                } else sectors.forEach { (sector, fraction) -> values += sector to value * fraction }
            } else {
                val sector = security.sector?.takeIf { it.isNotBlank() }
                if (sector == null) complete = false else values += sector to value
            }
        }
        if (includeCash && result.cash > ZERO) values += "Cash" to result.cash
        return portions(values, complete)
    }

    private fun portions(values: List<Pair<String, BigDecimal>>, complete: Boolean): AllocationResult {
        val groups = values.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.fold(ZERO, BigDecimal::add) }
        val total = groups.values.fold(ZERO, BigDecimal::add)
        return AllocationResult(
            groups.map { (name, value) -> AllocationSlice(name, value, value.pct(total) ?: ZERO) }
                .sortedByDescending { it.value },
            complete,
            total.takeIf { it > ZERO && complete },
        )
    }
}
