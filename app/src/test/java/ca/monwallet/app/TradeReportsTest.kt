package ca.monwallet.app

import ca.monwallet.app.domain.*
import ca.monwallet.app.data.Csv
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class TradeReportsTest {
    private fun d(s: String) = s.toBigDecimal()
    private fun tx(id: String, type: TxType, quantity: String, price: String,
        date: String, fees: String = "0", fx: String = "1", portfolio: String = "p",
        currency: String = "CAD", sequence: Long = 1) =
        Transaction(id = id, portfolioId = portfolio, securityId = "s", type = type,
            quantity = d(quantity), price = d(price), date = date, fees = d(fees),
            fxRate = d(fx), currency = currency, createdAt = sequence)
    private fun equal(expected: String, actual: java.math.BigDecimal?) {
        assertNotNull(actual); assertEquals(0, d(expected).compareTo(actual))
    }
    private fun at(time: String) = LocalDateTime.parse(time).atZone(MarketClock.zone).toInstant().toEpochMilli()

    @Test fun retainedValueShowsTotalNotJustProfitAndUsesSoldQuantity() {
        val sale = TradeReports.sales(listOf(tx("b", TxType.BUY, "10", "100", "2026-10-01"),
            tx("s", TxType.SELL, "5", "113", "2026-10-02"))).single()
        val retained = sale.ifRetained(d("300"), "CAD", null)!!
        equal("500", retained.invested); equal("1500", retained.value)
        equal("1000", retained.profit); equal("200", retained.percent); equal("935", retained.versusSale)
        assertNull(sale.ifRetained(null, "CAD", null))
        assertNull(sale.ifRetained(d("300"), "USD", d("1.4")))
    }
    @Test fun retainedUsdUsesCurrentFxAgainstActualCadCostAndDoesNotInventMissingRate() {
        val sale = TradeReports.sales(listOf(tx("b", TxType.BUY, "10", "10", "2026-10-01", fees = "1", fx = "1.3", currency = "USD"),
            tx("s", TxType.SELL, "10", "12", "2026-10-02", fx = "1.35", currency = "USD"))).single()
        val retained = sale.ifRetained(d("9"), "USD", d("1.4"))!!
        equal("131.3", retained.invested); equal("126", retained.value); equal("-5.3", retained.profit)
        assertNull(sale.ifRetained(d("9"), "USD", null))
        assertNull(sale.ifRetained(ZERO, "USD", ONE))
    }
    @Test fun dollaramaExitShows13PercentAndMatchesTotalRealized() {
        val tx = listOf(tx("b", TxType.BUY, "10", "100", "2026-10-08"),
            tx("v", TxType.SELL, "10", "113", "2026-10-22"))
        val sale = TradeReports.sales(tx).single()
        equal("130", sale.pnl); equal("13", sale.percent); equal("100", sale.averageBuyPrice)
        assertTrue(sale.closed); assertEquals(14, sale.days.toInt())
        equal("130", Engine.calculate(tx).realized)
    }
    @Test fun averageCostPartialExitsAndFxMatchEngineWithoutDoubleCountingFees() {
        val tx = listOf(tx("a", TxType.BUY, "10", "10", "2026-10-01", "1", "1.30", currency = "USD"),
            tx("b", TxType.BUY, "10", "20", "2026-10-02", "2", "1.40", currency = "USD"),
            tx("c", TxType.SELL, "5", "25", "2026-10-03", "1", "1.35", currency = "USD"),
            tx("d", TxType.SELL, "15", "24", "2026-10-04", "2", "1.36", currency = "USD"))
        val sales = TradeReports.sales(tx)
        assertFalse(sales.last().closed); assertTrue(sales.first().closed)
        equal("15", sales.last().averageBuyPrice)
        equal("15", sales.last().remaining)
        val sum = sales.fold(ZERO) { n, t -> n + t.pnl }
        assertEquals(0, Engine.calculate(tx).realized.compareTo(sum))
        equal("8.17", sales.fold(ZERO) { n, t -> n + t.allocatedFeesCad })
    }
    @Test fun reentryStartsNewCycleAndAccountsStaySeparate() {
        val tx = listOf(tx("a", TxType.BUY, "10", "10", "2026-10-01"),
            tx("other", TxType.BUY, "10", "200", "2026-10-01", portfolio = "q"),
            tx("b", TxType.SELL, "10", "11", "2026-10-02"),
            tx("c", TxType.BUY, "3", "20", "2026-10-03"),
            tx("d", TxType.SELL, "3", "25", "2026-10-04"))
        val latest = TradeReports.sales(tx).first()
        assertEquals("2026-10-03", latest.firstBuyDate)
        equal("20", latest.averageBuyPrice); equal("15", latest.pnl)
    }
    @Test fun laterSameDayPurchaseDoesNotRewriteEarlierSaleCost() {
        val tx = listOf(tx("a", TxType.BUY, "10", "10", "2026-10-01"),
            tx("b", TxType.SELL, "5", "12", "2026-10-02", sequence = 2),
            tx("c", TxType.BUY, "10", "100", "2026-10-02", sequence = 3))
        equal("10", TradeReports.sales(tx).single().pnl)
        assertEquals(0, Engine.calculate(tx).realized.compareTo(TradeReports.sales(tx).single().pnl))
    }
    @Test fun inclusiveCsvFiltersSalesDateButKeepsOldBuyAndEscapesText() {
        val security = Security("s", "DOL.TO", "DOL", "TSX", "CAD")
        val tx = listOf(tx("a", TxType.BUY, "10", "100", "2025-12-31"),
            tx("b", TxType.SELL, "10", "113", "2026-10-22").copy(note = "=1+1,\"note\"\nligne"))
        val wallet = Wallet(securities = listOf(security), transactions = tx,
            portfolios = listOf(Portfolio(id = "p", name = "CELI, Fred")))
        val period = ReportPeriod(LocalDate.parse("2026-10-22"), LocalDate.parse("2026-10-22"))
        val sales = Csv.parse(ReportCsv.export(wallet, emptyList(), ReportExportKind.CLOSED_TRADES,
            period).removePrefix("\uFEFF"))
        assertEquals(2, sales.size)
        assertEquals("2025-12-31", sales[1][3]); assertEquals("130", sales[1][12])
        val trades = Csv.parse(ReportCsv.export(wallet, emptyList(), ReportExportKind.TRANSACTIONS,
            period).removePrefix("\uFEFF"))
        assertEquals(2, trades.size); assertEquals("b", trades[1][0])
        assertEquals("CELI, Fred", trades[1][11])
        assertEquals("'=1+1,\"note\"\nligne", trades[1][12])
        assertEquals(1, Csv.parse(ReportCsv.export(wallet, emptyList(), ReportExportKind.TRANSACTIONS,
            period, portfolioId = "not-this-account").removePrefix("\uFEFF")).size)
        assertEquals(LocalDate.parse("2024-02-29"), ReportPeriod.month(YearMonth.of(2024, 2)).end)
        assertEquals(LocalDate.parse("2026-12-31"), ReportPeriod.year(2026).end)
    }
    private fun snapshot(date: String, value: String, confirmed: Boolean = true) =
        PortfolioDailySnapshot(LocalDate.parse(date), d("100"), d(value), ZERO,
            if (confirmed) d(value) - d("100") else null,
            if (confirmed) d(value) - d("100") else null,
            d("100"), ZERO, ZERO, d(value) - d("100"), ZERO, emptyMap(), confirmed)
    @Test fun unfinishedTodayNeverEntersCalendarAndWeekendCarryIsExplicit() {
        val h = listOf(snapshot("2026-10-21", "0", false), snapshot("2026-10-22", "100"),
            snapshot("2026-10-23", "900"))
        val pending = TradeReports.walletDays(h, emptyList(), at("2026-10-23T08:00:00")).last()
        equal("100", pending.value); assertNull(pending.pnl)
        assertEquals(TradeReports.DayStatus.PENDING, pending.status)
        assertEquals(LocalDate.parse("2026-10-22"), pending.valueDate)
        val weekend = TradeReports.walletDays(h, emptyList(), at("2026-10-24T08:00:00")).last()
        equal("900", weekend.value); assertNull(weekend.percent)
        assertEquals(TradeReports.DayStatus.NO_SESSION, weekend.status)
        assertEquals(LocalDate.parse("2026-10-23"), weekend.valueDate)
    }
    @Test fun missingWeekdayCannotInventOrCarryAConfirmedValue() {
        val h = listOf(snapshot("2026-10-20", "0", false), snapshot("2026-10-21", "100"))
        val days = TradeReports.walletDays(h, emptyList(), at("2026-10-24T08:00:00"))
        val missing = days.find { it.date.toString() == "2026-10-22" }!!
        assertNull(missing.value); assertNull(missing.pnl)
        assertEquals(TradeReports.DayStatus.INCOMPLETE, missing.status)
        assertNull(days.last().value)
    }
}
