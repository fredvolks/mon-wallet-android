package ca.monwallet.app.domain

/** Positions still owned; fully sold symbols do not subscribe the user indefinitely. */
fun Wallet.heldSecurityIds(): Set<String> = transactions.filter {
    it.securityId != null && it.type in setOf(TxType.BUY, TxType.SELL)
}.groupBy { it.securityId!! }.filterValues { trades ->
    trades.fold(ZERO) { total, t -> total + if (t.type == TxType.BUY) t.quantity else -t.quantity } > ZERO
}.keys

fun Wallet.monitoring(securityId: String): NotificationPreference =
    notifications.firstOrNull { it.securityId == securityId } ?: NotificationPreference(
        "notification:$securityId", securityId, earnings = securityId in heldSecurityIds(),
        news = securityId in heldSecurityIds())
