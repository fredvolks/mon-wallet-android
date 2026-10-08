package ca.monwallet.app.domain

import java.math.BigDecimal
import java.time.LocalDate

object Engine {
    private data class Lot(
        var q: BigDecimal = ZERO,
        var original: BigDecimal = ZERO,
        var cad: BigDecimal = ZERO,
        var realized: BigDecimal = ZERO,
        var dividends: BigDecimal = ZERO,
    )

    private data class Ledger(
        val lots: MutableMap<String, Lot> = mutableMapOf(),
        var cash: BigDecimal = ZERO,
        var capital: BigDecimal = ZERO,
    )

    private fun ledger(tx: List<Transaction>): Ledger {
        val l = Ledger()
        tx.sortedWith(compareBy<Transaction> { it.date }.thenBy { when (it.type) { TxType.DEPOSIT -> 0; TxType.WITHDRAWAL -> 2; else -> 1 } }.thenBy { it.createdAt }.thenBy { it.id })
            .forEach { t ->
                require(t.price >= ZERO && t.fees >= ZERO && t.fxRate > ZERO) {
                    "Montants invalides."
                }
                require(t.currency != "CAD" || t.fxRate.compareTo(ONE) == 0) {
                    "Taux CAD invalide."
                }
                LocalDate.parse(t.date)
                LocalDate.parse(t.fxDate)
                if (t.type in listOf(TxType.BUY, TxType.SELL))
                    require(t.quantity > ZERO && t.securityId != null) {
                        "Quantité et titre obligatoires."
                    }
                val lot = t.securityId?.let { l.lots.getOrPut(it) { Lot() } }
                fun spend(amount: BigDecimal) {
                    if (l.cash < amount) {
                        l.capital += amount - l.cash
                        l.cash = ZERO
                    } else l.cash -= amount
                }
                when (t.type) {
                    TxType.BUY -> {
                        spend(t.cad)
                        lot!!.q += t.quantity
                        lot.original += t.total
                        lot.cad += t.cad
                    }
                    TxType.SELL -> {
                        require(lot != null && lot.q >= t.quantity) {
                            "Vente supérieure aux parts détenues le ${t.date}."
                        }
                        require(t.total >= ZERO)
                        val cost = if (t.quantity == lot.q) lot.cad else (lot.cad * t.quantity).divide(lot.q, MC)
                        val original =
                            if (t.quantity == lot.q) lot.original else (lot.original * t.quantity).divide(lot.q, MC)
                        lot.q -= t.quantity
                        lot.cad -= cost
                        lot.original -= original
                        lot.realized += t.cad - cost
                        l.cash += t.cad
                    }
                    TxType.DIVIDEND -> {
                        require(t.total >= ZERO)
                        l.cash += t.cad
                        lot?.let { it.dividends += t.cad }
                    }
                    TxType.DEPOSIT -> {
                        require(t.fees == ZERO)
                        l.cash += t.cad
                        l.capital += t.cad
                    }
                    TxType.WITHDRAWAL -> {
                        require(l.cash >= t.cad) { "Encaisse insuffisante à cette date." }
                        l.cash -= t.cad
                        l.capital -= t.price * t.fxRate
                    }
                    TxType.FEE -> spend(t.cad)
                }
            }
        return l
    }

    fun validate(tx: List<Transaction>) {
        tx.groupBy { it.portfolioId }.values.forEach { ledger(it) }
    }

    data class SalePreview(
        val available: BigDecimal,
        val gross: BigDecimal,
        val fees: BigDecimal,
        val netCad: BigDecimal,
        val costCad: BigDecimal,
        val realizedCad: BigDecimal,
        val realizedPercent: BigDecimal?,
        val remaining: BigDecimal,
    )

    fun availableAt(tx: List<Transaction>, portfolioId: String, securityId: String, date: String): BigDecimal =
        ledger(tx.filter { it.portfolioId == portfolioId && it.date <= date })
            .lots[securityId]?.q ?: ZERO

    fun salePreview(tx: List<Transaction>, sale: Transaction): SalePreview {
        require(sale.type == TxType.SELL && sale.securityId != null && sale.quantity > ZERO)
        val prior = tx.filter { it.id != sale.id && it.portfolioId == sale.portfolioId && it.date <= sale.date }
        val lot = ledger(prior).lots[sale.securityId]
        val available = lot?.q ?: ZERO
        require(available >= sale.quantity) { "Vente supérieure aux parts détenues le ${sale.date}." }
        require(sale.total >= ZERO) { "Le produit net doit être positif." }
        val cost = if (available == sale.quantity) lot!!.cad else (lot!!.cad * sale.quantity).divide(available, MC)
        val gain = sale.cad - cost
        return SalePreview(available, sale.gross, sale.fees, sale.cad, cost, gain, gain.pct(cost), available - sale.quantity)
    }

    fun calculate(
        tx: List<Transaction>,
        quotes: Map<String, Quote> = emptyMap(),
        usd: Quote? = null,
    ): Result {
        if (tx.map { it.portfolioId }.distinct().size > 1) {
            val combined = combine(tx.groupBy { it.portfolioId }.values.map { calculate(it, quotes, usd) })
            val sessions = tx.mapNotNull { it.securityId }.distinct().mapNotNull { quotes[it]?.sessionDate }.distinct()
            val latestSession = sessions.singleOrNull()
            return if (sessions.size > 1 || (latestSession != null && tx.any { it.date > latestSession }))
                combined.copy(day = null, dayBase = null) else combined
        }
        val l = ledger(tx)
        val holdings =
            l.lots.map { (id, lot) ->
                val q = quotes[id]
                val fx =
                    if (q?.currency == "CAD") ONE
                    else if (q?.currency == "USD") usd?.price else null
                val prevFx = if (q?.currency == "CAD") ONE else usd?.takeIf { it.sessionDate == q?.sessionDate }?.previous
                val value =
                    if (lot.q.signum() == 0) ZERO
                    else q?.let { p -> fx?.let { lot.q * p.price * it } }
                var day: BigDecimal? = null
                var dayBase: BigDecimal? = null
                if (q != null && q.previous != null && fx != null && prevFx != null) {
                    val before = ledger(tx.filter { it.date < q.sessionDate }).lots[id]?.q ?: ZERO
                    val end = ledger(tx.filter { it.date <= q.sessionDate }).lots[id]?.q ?: ZERO
                    val during = tx.filter { it.date == q.sessionDate && it.securityId == id }
                    val buy =
                        during.filter { it.type == TxType.BUY }.fold(ZERO) { a, t -> a + t.cad }
                    val sale =
                        during
                            .filter { it.type in listOf(TxType.SELL, TxType.DIVIDEND) }
                            .fold(ZERO) { a, t -> a + t.cad }
                    val start = before * q.previous * prevFx
                    day = end * q.price * fx - start + sale - buy
                    dayBase = start + buy
                }
                Holding(
                    id,
                    lot.q,
                    lot.original,
                    lot.cad,
                    lot.realized,
                    lot.dividends,
                    value,
                    q?.let { p -> fx?.let { (lot.q * p.price - lot.original) * it } },
                    fx?.let { lot.original * it - lot.cad },
                    day,
                    dayBase,
                )
            }
        fun sum(f: (Holding) -> BigDecimal) = holdings.fold(ZERO) { a, h -> a + f(h) }
        fun nullable(f: (Holding) -> BigDecimal?) =
            if (holdings.all { f(it) != null }) sum { f(it)!! } else null
        val held = holdings.filter { h ->
            h.quantity > ZERO || tx.any { t ->
                t.securityId == h.securityId && t.type == TxType.SELL &&
                    t.date == quotes[h.securityId]?.sessionDate
            }
        }
        val sessions = held.mapNotNull { quotes[it.securityId]?.sessionDate }.distinct()
        val session = sessions.singleOrNull() ?: LocalDate.now().toString()
        val allPrices = held.all { h ->
            val quote = quotes[h.securityId]
            quote != null && quote.previous != null && quote.sessionDate == session &&
                (quote.currency == "CAD" || (usd?.sessionDate == session && usd.previous != null))
        }
        val noLateTrades = tx.none { it.date > session }
        val before = ledger(tx.filter { it.date < session })
        val end = ledger(tx.filter { it.date <= session })
        val opening = held.fold(ZERO) { a, h ->
            val q = quotes[h.securityId]
            val units = before.lots[h.securityId]?.q ?: ZERO
            a + units * (q?.previous ?: ZERO) * (if (q?.currency == "CAD") ONE else usd?.previous ?: ZERO)
        } + before.cash
        val closing = held.fold(ZERO) { a, h ->
            val q = quotes[h.securityId]
            val units = end.lots[h.securityId]?.q ?: ZERO
            a + units * (q?.price ?: ZERO) * (if (q?.currency == "CAD") ONE else usd?.price ?: ZERO)
        } + end.cash
        val withdrawals = tx.filter { it.date == session && it.type == TxType.WITHDRAWAL }
            .fold(ZERO) { a, t -> a + t.price * t.fxRate }
        val contributions = end.capital - before.capital + withdrawals
        val day = if (allPrices && noLateTrades && sessions.size <= 1)
            closing - opening - (end.capital - before.capital) else null
        val dayBase = day?.let { opening + contributions }
        return Result(
            holdings,
            l.capital,
            l.cash,
            nullable { it.value }?.plus(l.cash),
            sum { it.realized },
            sum { it.dividends },
            day,
            dayBase,
        )
    }

    fun combine(results: List<Result>): Result {
        fun sum(f: (Result) -> BigDecimal) = results.fold(ZERO) { a, r -> a + f(r) }
        fun nullable(f: (Result) -> BigDecimal?) =
            if (results.all { f(it) != null }) sum { f(it)!! } else null
        val holdings =
            results
                .flatMap { it.holdings }
                .groupBy { it.securityId }
                .map { (id, list) ->
                    fun s(f: (Holding) -> BigDecimal) = list.fold(ZERO) { a, h -> a + f(h) }
                    fun n(f: (Holding) -> BigDecimal?) =
                        if (list.all { f(it) != null }) s { f(it)!! } else null
                    Holding(
                        id,
                        s { it.quantity },
                        s { it.costOriginal },
                        s { it.costCad },
                        s { it.realized },
                        s { it.dividends },
                        n { it.value },
                        n { it.priceGain },
                        n { it.fxGain },
                        n { it.day },
                        n { it.dayBase },
                    )
                }
        return Result(
            holdings,
            sum { it.invested },
            sum { it.cash },
            nullable { it.value },
            sum { it.realized },
            sum { it.dividends },
            nullable { it.day },
            nullable { it.dayBase },
        )
    }

    fun history(
        tx: List<Transaction>,
        points: List<Point>,
        securities: List<Security>,
        portfolio: String? = null,
    ): List<Snapshot> {
        val transactions = tx.filter { portfolio == null || it.portfolioId == portfolio }
        if (transactions.isEmpty()) return emptyList()
        val start = LocalDate.parse(transactions.minOf { it.date })
        val today = LocalDate.now()
        if (start > today) return emptyList()
        val grouped =
            points.groupBy { it.securityId }.mapValues { it.value.sortedBy { p -> p.date } }
        val usd = securities.find { it.symbol == "CAD=X" }
        return generateSequence(start) { if (it < today) it.plusDays(1) else null }
            .map { date ->
                val quotes =
                    securities
                        .mapNotNull { s ->
                            grouped[s.id]
                                ?.lastOrNull { it.date <= date.toString() }
                                ?.takeIf { LocalDate.parse(it.date).plusDays(7) >= date }
                                ?.let {
                                    s.id to
                                        Quote(
                                            s.id,
                                            it.close,
                                            null,
                                            s.currency,
                                            it.timestamp,
                                            it.date,
                                            "Clôture historique",
                                        )
                                }
                        }
                        .toMap()
                val r =
                    calculate(
                        transactions.filter { it.date <= date.toString() },
                        quotes,
                        usd?.let { quotes[it.id] },
                    )
                Snapshot(date.toString(), portfolio ?: "all", r.value, r.invested, r.cash)
            }
            .toList()
    }
}

