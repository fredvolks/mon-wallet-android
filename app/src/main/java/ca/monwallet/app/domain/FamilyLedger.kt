package ca.monwallet.app.domain

import java.math.BigDecimal
import java.time.LocalDate

data class FamilyMember(val id: String?, val name: String)
fun Wallet.familyMembers(): List<FamilyMember> {
    val saved = settings.filterKeys { it.startsWith("family_member_") }.map { (key, name) ->
        FamilyMember(key.removePrefix("family_member_"), name)
    }.filter { it.name.isNotBlank() }.sortedBy { it.name.lowercase() }
    val missing = transactions.mapNotNull { it.memberId }.distinct().filter { id -> saved.none { it.id == id } }
        .map { FamilyMember(it, "Membre") }
    return listOf(FamilyMember(null, "Moi")) + saved + missing
}
fun Wallet.memberName(id: String?) = familyMembers().find { it.id == id }?.name ?: "Membre"
fun Wallet.forMember(id: String?) = copy(transactions = transactions.filter { it.memberId == id })

/** Never fabricate missing purchases. Pending sales reconcile when the history is corrected. */
object FamilyLedger {
    data class Resolution(val accounted: List<Transaction>, val pending: List<Transaction>)
    fun ordered(tx: List<Transaction>) = tx.sortedWith(compareBy<Transaction> { it.date }
        .thenBy { when (it.type) { TxType.DEPOSIT -> 0; TxType.WITHDRAWAL -> 2; else -> 1 } }
        .thenBy { it.createdAt }.thenBy { it.id })
    fun resolve(tx: List<Transaction>): Resolution {
        val quantities = mutableMapOf<Triple<String, String?, String?>, BigDecimal>()
        val tainted = mutableSetOf<Triple<String, String?, String?>>()
        val accounted = mutableListOf<Transaction>()
        val pending = mutableListOf<Transaction>()
        ordered(tx).forEach { t ->
            require(t.price >= ZERO && t.fees >= ZERO && t.fxRate > ZERO) { "Montants invalides." }
            require(t.currency != "CAD" || t.fxRate.compareTo(ONE) == 0) { "Taux CAD invalide." }
            LocalDate.parse(t.date); LocalDate.parse(t.fxDate)
            val key = Triple(t.portfolioId, t.memberId, t.securityId)
            val available = quantities[key] ?: ZERO
            if (t.type in listOf(TxType.BUY, TxType.SELL))
                require(t.quantity > ZERO && t.securityId != null) { "Quantité et titre obligatoires." }
            when (t.type) {
                TxType.BUY -> quantities[key] = available + t.quantity
                TxType.SELL -> {
                    require(t.total >= ZERO) { "Le produit net doit être positif." }
                    if (key in tainted || t.quantity > available) {
                        require(key in tainted || t.allowUnmatchedSale == true) { "Vente supérieure aux parts de ce membre le ${t.date}." }
                        pending += t; tainted += key; quantities[key] = ZERO
                        return@forEach
                    }
                    quantities[key] = available - t.quantity
                }
                else -> Unit
            }
            accounted += t
        }
        return Resolution(accounted, pending)
    }
    fun availableAt(tx: List<Transaction>, portfolio: String, security: String, member: String?, date: String): BigDecimal {
        LocalDate.parse(date)
        return ordered(tx.filter { it.portfolioId == portfolio && it.securityId == security && it.memberId == member && it.date <= date })
            .fold(ZERO) { n, t -> when (t.type) { TxType.BUY -> n + t.quantity; TxType.SELL -> (n - t.quantity).max(ZERO); else -> n } }
    }
}
