package ca.monwallet.app.ui

import ca.monwallet.app.domain.pct
import java.math.BigDecimal

internal data class ChartPeriodReturn(
    val amount: BigDecimal,
    val percent: BigDecimal,
)

/** Calculates price return from the first visible chart close to the latest quote. */
internal fun chartPeriodReturn(current: BigDecimal?, start: BigDecimal?): ChartPeriodReturn? {
    if (current == null || start == null || start.signum() == 0) return null
    val amount = current - start
    val percent = amount.pct(start) ?: return null
    return ChartPeriodReturn(amount, percent)
}
