package ca.monwallet.app

import ca.monwallet.app.data.Csv
import ca.monwallet.app.domain.*
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class EngineTest {
    private fun d(v: String) = BigDecimal(v)

    private fun equal(expected: String, value: BigDecimal?) {
        assertNotNull(value)
        assertEquals(expected, 0, d(expected).compareTo(value))
    }

    private fun tx(
        q: String = "120",
        price: String = "45.60",
        day: String = "2026-10-01",
        type: TxType = TxType.BUY,
        account: String = "CELI",
        fees: String = "0",
        currency: String = "CAD",
        fx: String = "1",
        security: String? = "XEQT",
    ) =
        Transaction(
            portfolioId = account,
            securityId = security,
            type = type,
            quantity = d(q),
            price = d(price),
            date = day,
            fees = d(fees),
            currency = currency,
            fxRate = d(fx),
        )

    private fun quote(
        price: String = "48",
        previous: String = "47",
        currency: String = "CAD",
        day: String = "2026-10-02",
    ) = Quote("XEQT", d(price), d(previous), currency, 0, day, "Test")

    @Test
    fun salePreviewUsesSharesOwnedAtHistoricalDate() {
        val transactions = listOf(tx("10", "100", "2026-01-01"), tx("10", "90", "2026-06-01"))
        val sale = tx("4", "120", "2026-02-01", TxType.SELL, fees = "4")
        val preview = Engine.salePreview(transactions, sale)
        equal("10", preview.available)
        equal("400", preview.costCad)
        equal("76", preview.realizedCad)
        equal("6", preview.remaining)
    }

    @Test(expected = IllegalArgumentException::class)
    fun salePreviewRejectsRetroactiveOversell() {
        Engine.salePreview(listOf(tx("10", "100", "2026-06-01")),
            tx("2", "120", "2026-02-01", TxType.SELL))
    }

    @Test
    fun mandatoryWeightedAverage() {
        val r = Engine.calculate(listOf(tx(), tx("20", "47", "2026-10-02")))
        equal("6412", r.invested)
        equal("140", r.holdings.single().quantity)
        equal("45.80", r.holdings.single().average)
    }

    @Test
    fun capitalDoesNotFollowMarket() {
        val t = listOf(tx())
        equal("5472", Engine.calculate(t, mapOf("XEQT" to quote("100"))).invested)
        equal("5472", Engine.calculate(t, mapOf("XEQT" to quote("1"))).invested)
    }

    @Test
    fun pnlAndPercent() {
        val r = Engine.calculate(listOf(tx("10", "100")), mapOf("XEQT" to quote("110")))
        equal("100", r.pnl)
        equal("10", r.percent)
    }

    @Test
    fun unavailableIsNotZero() {
        val r = Engine.calculate(listOf(tx()))
        assertNull(r.value)
        assertNull(r.pnl)
        assertNull(r.day)
    }

    @Test
    fun feesIncludedInAverage() {
        val r = Engine.calculate(listOf(tx("10", "100", fees = "10")))
        equal("101", r.holdings.single().average)
        equal("1010", r.invested)
    }

    @Test
    fun partialSaleRetainsBasisAndRealized() {
        val t = listOf(tx("10", "100"), tx("4", "120", "2026-10-02", TxType.SELL, fees = "4"))
        val r = Engine.calculate(t, mapOf("XEQT" to quote("120")))
        equal("6", r.holdings.single().quantity)
        equal("600", r.holdings.single().costCad)
        equal("76", r.realized)
        equal("476", r.cash)
        equal("1000", r.invested)
        equal("196", r.pnl)
    }

    @Test
    fun saleDoesNotEraseHistory() {
        val t = listOf(tx("10", "100"), tx("10", "120", "2026-10-02", TxType.SELL))
        val r = Engine.calculate(t)
        assertEquals(2, t.size)
        equal("0", r.holdings.single().quantity)
        equal("200", r.realized)
        equal("1200", r.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversellRejected() {
        Engine.validate(listOf(tx("10"), tx("11", day = "2026-10-02", type = TxType.SELL)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun saleBeforeBuyRejected() {
        Engine.validate(
            listOf(tx("10", day = "2026-10-02"), tx("5", day = "2026-10-01", type = TxType.SELL))
        )
    }

    @Test
    fun retroactiveBuySorted() {
        val r = Engine.calculate(listOf(tx("20", "47", "2026-10-02"), tx()))
        equal("45.80", r.holdings.single().average)
    }

    @Test
    fun usdFxDecomposition() {
        val r =
            Engine.calculate(
                listOf(tx("10", "100", currency = "USD", fx = "1.30")),
                mapOf("XEQT" to quote("110", "100", "USD")),
                Quote("FX", d("1.40"), d("1.35"), "CAD", 0, "2026-10-02", "Test"),
            )
        val h = r.holdings.single()
        equal("1300", r.invested)
        equal("1540", r.value)
        equal("140", h.priceGain)
        equal("100", h.fxGain)
        equal("240", h.pnl)
    }

    @Test
    fun missingFxMeansUnknownValue() {
        val r =
            Engine.calculate(
                listOf(tx("10", "100", currency = "USD", fx = "1.30")),
                mapOf("XEQT" to quote(currency = "USD")),
            )
        assertNull(r.value)
    }

    @Test
    fun cumulativeShares() {
        val r = Engine.calculate(listOf(tx(), tx("80", account = "REER")))
        equal("200", r.holdings.single().quantity)
        equal("9120", r.invested)
    }

    @Test
    fun accountsDoNotFundEachOther() {
        val deposit = tx("0", "1000", type = TxType.DEPOSIT, account = "REER", security = null)
        val r = Engine.calculate(listOf(deposit, tx("10", "100", account = "CELI")))
        equal("2000", r.invested)
        equal("1000", r.cash)
    }

    @Test
    fun depositsFundBuysWithoutDoubleCount() {
        val r =
            Engine.calculate(
                listOf(tx("0", "1000", type = TxType.DEPOSIT, security = null), tx("10", "100"))
            )
        equal("1000", r.invested)
        equal("0", r.cash)
    }

    @Test
    fun dividendAndWithdrawal() {
        val r =
            Engine.calculate(
                listOf(
                    tx("0", "1000", type = TxType.DEPOSIT, security = null),
                    tx("0", "50", "2026-10-02", TxType.DIVIDEND, fees = "5"),
                    tx("0", "100", "2026-10-03", TxType.WITHDRAWAL, security = null),
                )
            )
        equal("900", r.invested)
        equal("945", r.value)
        equal("45", r.pnl)
    }

    @Test
    fun latestHistoricalCloseSuppliesMissingPreviousQuote() {
        val security = Security("XEQT", "XEQT.TO", "XEQT", "TSX", "CAD")
        val wallet = Wallet(
            securities = listOf(security),
            transactions = listOf(tx("10", "100", "2026-10-07")),
            quotes = mapOf("XEQT" to Quote("XEQT", d("110"), null, "CAD",
                0, "2026-10-08", "Test")),
            prices = listOf(Point("XEQT", "2026-10-07", d("100"), 0)),
        )
        equal("100", wallet.result().day)
    }

    @Test
    fun dailyBuyFlowsNotProfit() {
        val r =
            Engine.calculate(
                listOf(tx("10", "100", "2026-10-02")),
                mapOf("XEQT" to quote("101", "99")),
            )
        equal("10", r.day)
        equal("1", r.dayPercent)
    }

    @Test
    fun dailySaleFlowsIncluded() {
        val r =
            Engine.calculate(
                listOf(tx("10", "90"), tx("4", "105", "2026-10-02", TxType.SELL)),
                mapOf("XEQT" to quote("106", "100")),
            )
        equal("56", r.day)
    }

    @Test
    fun dailyCurrencyEffect() {
        val r =
            Engine.calculate(
                listOf(tx("10", "100", currency = "USD", fx = "1.30")),
                mapOf("XEQT" to quote("100", "100", "USD")),
                Quote("FX", d("1.40"), d("1.30"), "CAD", 0, "2026-10-02", "Test"),
            )
        equal("100", r.day)
    }

    @Test
    fun retroactiveHistoryDates() {
        val s = Security("XEQT", "XEQT.TO", "Test", "TSX", "CAD")
        val points =
            listOf(Point("XEQT", "2026-10-01", d("46"), 0), Point("XEQT", "2026-10-02", d("48"), 0))
        val h = Engine.history(listOf(tx(), tx("20", "47", "2026-10-02")), points, listOf(s))
        equal("5472", h.first { it.date == "2026-10-01" }.invested)
        equal("6412", h.first { it.date == "2026-10-02" }.invested)
        equal("6720", h.first { it.date == "2026-10-02" }.value)
    }

    @Test
    fun csvRoundtripMultilineNotes() {
        val t = tx().copy(note = "bonjour, \"Mon Wallet\"\nligne 2")
        val w =
            Wallet(
                portfolios = listOf(Portfolio("CELI", "CELI")),
                securities = listOf(Security("XEQT", "XEQT.TO", "XEQT", "TSX", "CAD")),
                transactions = listOf(t),
            )
        val rows = Csv.parse(Csv.export(w))
        assertEquals(2, rows.size)
        assertEquals(t.note, rows[1][11])
    }

    @Test
    fun capitalPrecision() {
        val r = Engine.calculate(List(10) { tx("0.1", "0.1") })
        equal("0.1", r.invested)
        equal("1", r.holdings.single().quantity)
    }

    @Test
    fun cadFxScaleIsNumeric() {
        Engine.validate(listOf(tx(fx = "1.00000")))
    }

    @Test fun dailyCashDepositIsNotProfit() {
        val r=Engine.calculate(listOf(
            tx("0","1000",LocalDate.now().minusDays(1).toString(),TxType.DEPOSIT,security=null),
            tx("0","500",LocalDate.now().toString(),TxType.DEPOSIT,security=null)))
        equal("0",r.day)
        equal("1500",r.invested)
    }

    @Test fun standaloneFeeCountsAsDailyExpense() {
        val r=Engine.calculate(listOf(
            tx("10","100"),
            tx("0","5","2026-10-02",TxType.FEE,security=null)),
            mapOf("XEQT" to quote("100","100")))
        equal("-5",r.day)
    }

    @Test fun saleOfWholePositionKeepsDailyRealizedGain() {
        val r=Engine.calculate(listOf(tx("10","100"),tx("10","105","2026-10-02",TxType.SELL)),
            mapOf("XEQT" to quote("106","100")))
        equal("50",r.day)
    }

    @Test fun mixedMarketSessionsKeepTheLatestPerSecurityDailyPnl() {
        val transactions = listOf(
            tx("10", "100", "2026-10-01", security = "XEQT"),
            tx("10", "40", "2026-10-01", security = "VFV"),
        )
        val quotes = mapOf(
            "XEQT" to Quote("XEQT", d("110"), d("100"), "CAD", 0, "2026-10-02", "Test"),
            "VFV" to Quote("VFV", d("45"), d("40"), "CAD", 0, "2026-10-01", "Test"),
        )
        equal("150", Engine.calculate(transactions, quotes).day)
    }

    @Test fun staleFxDoesNotProduceDailyReturn() {
        val r=Engine.calculate(listOf(tx("10","100",currency="USD",fx="1.30")),
            mapOf("XEQT" to quote("110","100","USD")),
            Quote("FX",d("1.40"),d("1.35"),"CAD",0,"2026-10-01","Test"))
        assertNull(r.day)
    }

    @Test fun cumulativeDayRejectsCashFlowsAfterLastMarketSession() {
        val r=Engine.calculate(listOf(
            tx("10","100",account="CELI"),
            tx("0","500","2026-10-03",TxType.DEPOSIT,account="REER",security=null)),
            mapOf("XEQT" to quote("110","100",day="2026-10-02")))
        assertNull(r.day)
    }
}
