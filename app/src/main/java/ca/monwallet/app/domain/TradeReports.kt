package ca.monwallet.app.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class ReportPeriod(val start: LocalDate, val end: LocalDate) {
    init { require(start <= end) { "La date de début doit précéder la date de fin." } }
    fun contains(date: String): Boolean = LocalDate.parse(date) in start..end
    companion object {
        fun month(month: YearMonth) = ReportPeriod(month.atDay(1), month.atEndOfMonth())
        fun year(year: Int) = ReportPeriod(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
    }
}

data class ClosedTrade(
    val saleId: String, val portfolioId: String, val securityId: String,
    val firstBuyDate: String, val lastBuyDate: String, val saleDate: String,
    val quantity: BigDecimal, val averageBuyPrice: BigDecimal, val salePrice: BigDecimal,
    val currency: String, val costCad: BigDecimal, val proceedsCad: BigDecimal,
    val allocatedFeesCad: BigDecimal, val remaining: BigDecimal,
) {
    val pnl: BigDecimal get() = proceedsCad - costCad
    val percent: BigDecimal? get() = pnl.pct(costCad)
    val closed: Boolean get() = remaining.signum() == 0
    val days: Long get() = ChronoUnit.DAYS.between(LocalDate.parse(firstBuyDate), LocalDate.parse(saleDate))
}

/** Hypothetical mark-to-market for precisely the sold quantity, excluding new sale fees/dividends. */
data class RetainedTradeValue(val invested: BigDecimal, val value: BigDecimal, val saleProceeds: BigDecimal) {
    val profit get() = value - invested
    val percent get() = profit.pct(invested)
    val versusSale get() = value - saleProceeds
    val versusSalePercent get() = versusSale.pct(saleProceeds)
}

fun ClosedTrade.ifRetained(price: BigDecimal?, quoteCurrency: String?, cadRate: BigDecimal?): RetainedTradeValue? {
    if (price == null || price <= ZERO || quoteCurrency != currency || quantity <= ZERO || costCad <= ZERO) return null
    val rate = when (currency) { "CAD" -> ONE; "USD" -> cadRate?.takeIf { it > ZERO }; else -> null } ?: return null
    return RetainedTradeValue(costCad, quantity * price * rate, proceedsCad)
}

/** Average cost, isolated by account and security, in the accounting engine's exact ledger order.
 * Each sale is a realized event. An exit followed by a re-entry starts a new holding cycle.
 */
object TradeReports {
    private data class Position(
        var quantity: BigDecimal = ZERO, var costCad: BigDecimal = ZERO,
        var original: BigDecimal = ZERO, var grossOriginal: BigDecimal = ZERO,
        var buyFeesCad: BigDecimal = ZERO, var firstBuy: String = "", var lastBuy: String = "")
    fun sales(transactions: List<Transaction>): List<ClosedTrade> {
        val positions = mutableMapOf<Triple<String, String?, String>, Position>()
        val results = mutableListOf<ClosedTrade>()
        FamilyLedger.resolve(transactions).accounted.sortedWith(compareBy<Transaction> { it.date }
            .thenBy { when (it.type) { TxType.DEPOSIT -> 0; TxType.WITHDRAWAL -> 2; else -> 1 } }
            .thenBy { it.createdAt }.thenBy { it.id }).forEach { tx ->
            val id = tx.securityId ?: return@forEach
            if (tx.type !in setOf(TxType.BUY, TxType.SELL)) return@forEach
            require(tx.quantity > ZERO && tx.fxRate > ZERO && tx.price >= ZERO && tx.fees >= ZERO)
            val position = positions.getOrPut(Triple(tx.portfolioId, tx.memberId, id)) { Position() }
            if (tx.type == TxType.BUY) {
                if (position.quantity.signum() == 0) position.firstBuy = tx.date
                position.lastBuy = tx.date
                position.quantity += tx.quantity
                position.costCad += tx.cad
                position.original += tx.total
                position.grossOriginal += tx.gross
                position.buyFeesCad += tx.fees * tx.fxRate
            } else {
                require(position.quantity >= tx.quantity) { "Vente supérieure aux parts détenues le ${tx.date}." }
                val all = position.quantity.compareTo(tx.quantity) == 0
                fun allocate(value: BigDecimal) = if (all) value
                    else (value * tx.quantity).divide(position.quantity, MC)
                val cost = allocate(position.costCad)
                val original = allocate(position.original)
                val gross = allocate(position.grossOriginal)
                val fees = allocate(position.buyFeesCad)
                position.quantity -= tx.quantity
                position.costCad -= cost
                position.original -= original
                position.grossOriginal -= gross
                position.buyFeesCad -= fees
                results += ClosedTrade(tx.id, tx.portfolioId, id, position.firstBuy, position.lastBuy,
                    tx.date, tx.quantity, gross.divide(tx.quantity, MC), tx.price, tx.currency,
                    cost, tx.cad, fees + tx.fees * tx.fxRate, position.quantity)
            }
        }
        return results.asReversed()
    }

    /** Today's unconfirmed daily bar must not enter the calendar as a completed session. */
    fun confirmedHistory(history: List<PortfolioDailySnapshot>, now: Long): List<PortfolioDailySnapshot> {
        val local = Instant.ofEpochMilli(now).atZone(MarketClock.zone)
        return history.filter { it.date < local.toLocalDate() ||
            (it.date == local.toLocalDate() && local.toLocalTime() >= LocalTime.of(16, 0)) }
    }

    enum class DayStatus { CONFIRMED, NO_SESSION, PENDING, INCOMPLETE }
    data class WalletDay(val date: LocalDate, val snapshot: PortfolioDailySnapshot?,
        val value: BigDecimal?, val status: DayStatus, val valueDate: LocalDate?) {
        val pnl: BigDecimal? get() = snapshot?.dailyPnl?.takeIf { status == DayStatus.CONFIRMED }
        val percent: BigDecimal? get() = snapshot?.dailyReturn?.takeIf { status == DayStatus.CONFIRMED }
    }

    fun walletDays(history: List<PortfolioDailySnapshot>, securities: List<Security>,
        now: Long = System.currentTimeMillis()): List<WalletDay> {
        if (history.size < 2) return emptyList()
        val today = MarketClock.date(now)
        val first = history[1].date
        val confirmed = confirmedHistory(history, now).drop(1).associateBy { it.date }
        var previous: PortfolioDailySnapshot? = null
        return generateSequence(first) { if (it < today) it.plusDays(1) else null }
            .takeWhile { it <= today }.map { date ->
                val snapshot = confirmed[date]
                val noSession = date.dayOfWeek.value >= 6 || (securities.isNotEmpty() &&
                    securities.all { MarketHoliday.isHoliday(it, date) })
                val pending = date == today && Instant.ofEpochMilli(now).atZone(MarketClock.zone)
                    .toLocalTime() < LocalTime.of(16, 0) && !noSession
                val status = when {
                    pending -> DayStatus.PENDING
                    snapshot?.hasMarketClose == true && snapshot.closingValue != null -> DayStatus.CONFIRMED
                    noSession -> DayStatus.NO_SESSION
                    else -> DayStatus.INCOMPLETE
                }
                val value = when (status) {
                    DayStatus.CONFIRMED -> snapshot?.closingValue
                    DayStatus.PENDING, DayStatus.NO_SESSION -> snapshot?.closingValue ?: previous?.closingValue
                    DayStatus.INCOMPLETE -> null
                }
                val sourceDate = if (snapshot?.closingValue != null) date else previous?.date
                // Missing data breaks continuity; never carry an older value across an unknown close.
                if (!pending && snapshot != null) previous = snapshot
                else if (status == DayStatus.INCOMPLETE) previous = null
                WalletDay(date, snapshot, value, status, sourceDate)
            }.toList()
    }
}

enum class ReportExportKind(val label: String) {
    TRANSACTIONS("Transactions"), CLOSED_TRADES("Trades clôturés"), DAILY_WALLET("Wallet jour par jour")
}

object ReportCsv {
    private fun text(value: String): String =
        if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
    private fun row(values: List<Any?>) = values.joinToString(",") {
        val raw = it?.toString().orEmpty()
        "\"" + raw.replace("\"", "\"\"") + "\""
    }
    fun export(wallet: Wallet, history: List<PortfolioDailySnapshot>, kind: ReportExportKind,
        period: ReportPeriod, portfolioId: String? = null, now: Long = System.currentTimeMillis()): String {
        fun portfolio(id: String) = text(wallet.portfolios.find { it.id == id }?.name ?: id)
        fun symbol(id: String?) = text(wallet.security(id)?.symbol.orEmpty())
        val tx = wallet.transactions.filter { portfolioId == null || it.portfolioId == portfolioId }
        val lines = when (kind) {
            ReportExportKind.TRANSACTIONS -> listOf(listOf("id", "date", "titre", "operation",
                "quantite", "prix_unitaire", "devise", "frais", "taux_fx", "date_fx", "montant_net_cad",
                "portefeuille", "note")) + tx.filter { period.contains(it.date) }.sortedWith(
                compareBy<Transaction> { it.date }.thenBy { it.createdAt }.thenBy { it.id }).map {
                    listOf(text(it.id), it.date, symbol(it.securityId), it.type.name,
                        it.quantity, it.price, text(it.currency), it.fees, it.fxRate, it.fxDate,
                        it.cad, portfolio(it.portfolioId), text(it.note))
                }
            ReportExportKind.CLOSED_TRADES -> listOf(listOf("vente_id", "titre", "portefeuille",
                "premier_achat", "dernier_achat", "date_vente", "quantite_vendue", "achat_moyen",
                "prix_vente", "devise", "cout_cad", "produit_net_cad", "profit_net_cad",
                "profit_net_pct", "frais_alloues_cad", "jours_depuis_premier_achat",
                "quantite_restante_apres_vente", "statut")) +
                TradeReports.sales(tx).filter { period.contains(it.saleDate) }.map {
                    listOf(text(it.saleId), symbol(it.securityId), portfolio(it.portfolioId),
                        it.firstBuyDate, it.lastBuyDate, it.saleDate, it.quantity, it.averageBuyPrice,
                        it.salePrice, text(it.currency), it.costCad, it.proceedsCad, it.pnl, it.percent,
                        it.allocatedFeesCad, it.days, it.remaining,
                        if (it.closed) "Clôturée" else "Vente partielle")
                }
            ReportExportKind.DAILY_WALLET -> {
                val heldIds = tx.mapNotNull { it.securityId }.toSet()
                listOf(listOf("date", "valeur_wallet_cad", "pnl_jour_cad", "rendement_jour_pct",
                    "capital_net_cad", "encaisse_cad", "profit_realise_cumule_cad", "dividendes_cumules_cad",
                    "flux_externe_cad", "statut", "date_valeur")) +
                    TradeReports.walletDays(history, wallet.securities.filter { it.id in heldIds }, now)
                        .filter { period.contains(it.date.toString()) }.map {
                            listOf(it.date, it.value, it.pnl, it.percent, it.snapshot?.capitalInvested,
                                it.snapshot?.cash, it.snapshot?.realizedPnl, it.snapshot?.dividends,
                                it.snapshot?.netExternalFlow, it.status.name, it.valueDate)
                        }
            }
        }
        // BOM for Excel on French Windows; ISO dates and decimal points remain machine readable.
        return "\uFEFF" + lines.joinToString("\r\n", transform = ::row) + "\r\n"
    }
}
