package ca.monwallet.app

import ca.monwallet.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ReportEngineTest {
    private val cad = Security.of("AAA.TO", "Example", "TSX", "CAD")
    private val usd = Security.of("BBB", "Example US", "NYSE", "USD")
    private val fx = Security.of("CAD=X", "USD/CAD", "FX", "CAD", "FX")
    private fun bd(s: String) = BigDecimal(s)
    private fun tx(type: TxType, date: String, amount: String, security: Security? = null,
        quantity: String = "0", currency: String = "CAD", exchange: String = "1") =
        Transaction(portfolioId = "p", securityId = security?.id, type = type,
            price = bd(amount), quantity = bd(quantity), currency = currency,
            fxRate = bd(exchange), date = date)
    private fun point(security: Security, date: String, close: String) =
        Point(security.id, date, bd(close), LocalDate.parse(date).toEpochDay() * 86_400_000)
    private fun summary(wallet: Wallet) = ReportEngine.summary(wallet, ReportEngine.history(wallet), ReportRange.TOTAL)
    private fun approx(expected: String, actual: BigDecimal?) {
        assertNotNull(actual)
        assertTrue("Expected $expected, got $actual",
            bd(expected).subtract(actual).abs() <= bd("0.02"))
    }

    @Test fun depositAfterMarketGainDoesNotBecomeProfit() {
        val wallet = Wallet(securities = listOf(cad), transactions = listOf(
            tx(TxType.DEPOSIT, "2026-01-05", "1000"),
            tx(TxType.BUY, "2026-01-05", "100", cad, "10"),
            tx(TxType.DEPOSIT, "2026-01-07", "1000"),
        ), prices = listOf(point(cad, "2026-01-05", "100"),
            point(cad, "2026-01-06", "110"), point(cad, "2026-01-07", "110")))
        val result = summary(wallet)
        assertTrue(result.complete)
        approx("100", result.gain)
        approx("10", result.performance)
        approx("2100", result.value)
        approx("2000", result.capital)
        assertEquals(ZERO, result.days.last().dailyPnl)
    }

    @Test fun purchasesThisWeekReconstructCalendarAndShowPartialWeekSinceFirstBuy() {
        val wallet = Wallet(securities = listOf(cad), transactions = listOf(
            tx(TxType.BUY, "2026-01-05", "100", cad, "10"),
            tx(TxType.BUY, "2026-01-06", "100", cad, "5"),
        ), prices = listOf(point(cad, "2026-01-05", "101"),
            point(cad, "2026-01-06", "110")))
        val history = ReportEngine.history(wallet)
        approx("10", history.single { it.date.toString() == "2026-01-05" }.dailyPnl)
        approx("140", history.single { it.date.toString() == "2026-01-06" }.dailyPnl)
        val week = ReportEngine.summary(wallet, history, ReportRange.WEEK,
            LocalDate.parse("2026-01-07"))
        assertTrue(week.complete)
        assertTrue(week.partialPeriod)
        approx("150", week.gain)
        approx("1500", week.capital)
    }

    @Test fun depositAndWithdrawalInSamePeriodAreExternalFlows() {
        val wallet = Wallet(securities = listOf(cad), transactions = listOf(
            tx(TxType.DEPOSIT, "2026-01-05", "1000"),
            tx(TxType.DEPOSIT, "2026-01-06", "500"),
            tx(TxType.WITHDRAWAL, "2026-01-07", "300"),
        ), prices = listOf(point(cad, "2026-01-05", "100"),
            point(cad, "2026-01-06", "100"), point(cad, "2026-01-07", "100")))
        val result = summary(wallet)
        assertTrue(result.complete)
        approx("0", result.gain)
        approx("0", result.performance)
        approx("1200", result.capital)
        approx("1500", result.deposits)
        approx("300", result.withdrawals)
    }

    @Test fun dividendsAndPartialSaleRemainInternalAndContributeToReturn() {
        val wallet = Wallet(securities = listOf(cad), transactions = listOf(
            tx(TxType.DEPOSIT, "2026-01-05", "1000"),
            tx(TxType.BUY, "2026-01-05", "100", cad, "10"),
            tx(TxType.SELL, "2026-01-06", "110", cad, "5"),
            tx(TxType.DIVIDEND, "2026-01-07", "20", cad),
        ), prices = listOf(point(cad, "2026-01-05", "100"),
            point(cad, "2026-01-06", "110"), point(cad, "2026-01-07", "110")))
        val result = summary(wallet)
        assertTrue(result.complete)
        approx("120", result.gain)
        approx("12", result.performance)
        approx("20", result.dividends)
        approx("1000", result.capital)
        approx("120", result.contributions.single().pnl)
    }

    @Test fun retroactiveTransactionRebuildsPastSnapshots() {
        val base = Wallet(securities = listOf(cad), transactions = listOf(
            tx(TxType.DEPOSIT, "2026-01-05", "1000"),
        ), prices = listOf(point(cad, "2026-01-05", "100"),
            point(cad, "2026-01-06", "110")))
        approx("0", summary(base).gain)
        val revised = base.copy(transactions = base.transactions +
            tx(TxType.BUY, "2026-01-05", "100", cad, "10"))
        assertSame(ReportEngine.history(base), ReportEngine.history(base))
        assertNotSame(ReportEngine.history(base), ReportEngine.history(revised))
        approx("100", summary(revised).gain)
        approx("1100", ReportEngine.history(revised).last().closingValue)
    }

    @Test fun onePointHistoryIsNotMarkedCompleteAndWiderRetryCanCoverTheMonth() {
        val first = LocalDate.parse("2026-10-01")
        val today = LocalDate.parse("2026-10-08")
        val sparse = listOf(point(cad, "2026-10-08", "100"))
        assertFalse(reportHistoryHasCoverage(sparse, first, today))
        assertEquals("3mo", reportHistoryFallbackRange("1mo"))
        val closes = listOf(
            point(cad, "2026-10-01", "100"), point(cad, "2026-10-02", "101"),
            point(cad, "2026-10-05", "102"), point(cad, "2026-10-06", "103"),
            point(cad, "2026-10-07", "104"), point(cad, "2026-10-08", "105"),
        )
        assertTrue(reportHistoryHasCoverage(closes, first, today))
    }

    @Test fun scatteredTickerClosesDoNotCountAsCompleteHistory() {
        val first = LocalDate.parse("2026-10-01")
        val today = LocalDate.parse("2026-10-08")
        val fourOfSixWeekdays = listOf(
            point(cad, "2026-10-01", "100"), point(cad, "2026-10-02", "101"),
            point(cad, "2026-10-07", "104"), point(cad, "2026-10-08", "105"),
        )
        val fiveOfSixWeekdays = fourOfSixWeekdays + point(cad, "2026-10-05", "102")
        assertFalse(reportHistoryHasCoverage(fourOfSixWeekdays, first, today))
        assertTrue(reportHistoryHasCoverage(fiveOfSixWeekdays, first, today))
    }

    @Test fun sameDayPurchaseNeedsOnlyItsDatedQuoteForCalendarCoverage() {
        val today = LocalDate.parse("2026-10-08")
        assertTrue(reportHistoryHasCoverage(listOf(point(cad, today.toString(), "100")), today, today))
    }

    @Test fun reportHistoryRefreshUsesSuccessfulFetchAgeForThrottle() {
        assertTrue(reportHistoryRefreshDue(force = false, lastSuccessfulRefresh = null, nowMillis = 1_000L))
        assertFalse(reportHistoryRefreshDue(force = false, lastSuccessfulRefresh = 1_000L,
            nowMillis = 1_000L + 60 * 60_000L))
        assertTrue(reportHistoryRefreshDue(force = false, lastSuccessfulRefresh = 1_000L,
            nowMillis = 1_000L + 6 * 60 * 60_000L))
        assertTrue(reportHistoryRefreshDue(force = true, lastSuccessfulRefresh = 1_000L,
            nowMillis = 2_000L))
    }

    @Test fun backdatedPurchaseMovesCalendarStartAndRequiresOlderCloses() {
        val today = LocalDate.of(2026, 10, 7)
        assertEquals("1mo", reportHistoryRange(today.minusDays(6), today))
        assertEquals("5y", reportHistoryRange(today.minusYears(2), today))
        val original = Wallet(securities = listOf(cad), transactions = listOf(
            tx(TxType.BUY, "2026-01-06", "100", cad, "1")),
            prices = listOf(point(cad, "2026-01-05", "100"),
                point(cad, "2026-01-06", "110"), point(cad, "2026-01-07", "120")))
        val earlier = original.copy(transactions = original.transactions +
            tx(TxType.BUY, "2026-01-05", "100", cad, "1"))
        val before = ReportEngine.history(original)
        val after = ReportEngine.history(earlier)
        assertEquals(LocalDate.parse("2026-01-06"), before[1].date)
        assertEquals(LocalDate.parse("2026-01-05"), after[1].date)
        approx("0", after.single { it.date.toString() == "2026-01-05" }.dailyPnl)
        approx("20", after.single { it.date.toString() == "2026-01-06" }.dailyPnl)
        assertNotSame(before, after)
    }

    @Test fun historicalFxIsUsedInsteadOfCurrentRate() {
        val wallet = Wallet(securities = listOf(usd, fx), transactions = listOf(
            tx(TxType.DEPOSIT, "2026-01-05", "1400"),
            tx(TxType.BUY, "2026-01-05", "100", usd, "10", "USD", "1.40"),
        ), prices = listOf(point(usd, "2026-01-05", "100"), point(usd, "2026-01-06", "100"),
            point(fx, "2026-01-05", "1.40"), point(fx, "2026-01-06", "1.50")))
        val result = summary(wallet)
        assertTrue(result.complete)
        approx("100", result.gain)
        approx("7.142857", result.performance)
        val missingFx = wallet.copy(prices = wallet.prices.filter { it.securityId != fx.id })
        assertFalse(summary(missingFx).complete)
    }

    @Test fun fiveAndTenYearsRequireActualPortfolioHistory() {
        val wallet = Wallet(securities = listOf(cad), transactions = listOf(
            tx(TxType.DEPOSIT, "2026-01-05", "1000")),
            prices = listOf(point(cad, "2026-01-05", "100"), point(cad, "2026-01-06", "110")))
        val history = ReportEngine.history(wallet)
        assertFalse(ReportEngine.summary(wallet, history, ReportRange.FIVE_YEARS,
            LocalDate.of(2026, 10, 7)).complete)
        assertFalse(ReportEngine.summary(wallet, history, ReportRange.TEN_YEARS,
            LocalDate.of(2026, 10, 7)).complete)
    }

    @Test fun missingSecurityCloseCannotBeReplacedWithZeroOrTodayQuote() {
        val wallet = Wallet(securities = listOf(cad), transactions = listOf(
            tx(TxType.DEPOSIT, "2026-01-05", "1000"),
            tx(TxType.BUY, "2026-01-05", "100", cad, "10"),
        ), prices = emptyList())
        assertFalse(summary(wallet).complete)
        assertNull(ReportEngine.history(wallet).last().closingValue)
    }

    @Test fun benchmarkNormalizesBothEndpointsAndNeedsHistoricalFx() {
        val index = Security.of("^GSPC", "S&P 500", "S&P", "USD", "INDEX")
        val wallet = Wallet(securities = listOf(index, fx), prices = listOf(
            point(index, "2026-01-05", "100"), point(index, "2026-01-06", "110"),
            point(fx, "2026-01-05", "1.40"), point(fx, "2026-01-06", "1.40")))
        approx("10", ReportEngine.benchmark(wallet, "^GSPC", LocalDate.parse("2026-01-05"),
            LocalDate.parse("2026-01-06")))
        assertNull(ReportEngine.benchmark(wallet.copy(prices = wallet.prices.filter { it.securityId != fx.id }),
            "^GSPC", LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-06")))
    }

    @Test fun closedWeekendDoesNotGenerateFalseDailyPnl() {
        val wallet = Wallet(securities = listOf(cad), transactions = listOf(
            tx(TxType.DEPOSIT, "2026-01-09", "1000"),
            tx(TxType.BUY, "2026-01-09", "100", cad, "10"),
            tx(TxType.DEPOSIT, "2026-01-10", "200"),
        ), prices = listOf(point(cad, "2026-01-09", "100"),
            point(cad, "2026-01-12", "110")))
        val days = ReportEngine.history(wallet)
        val saturday = days.single { it.date == LocalDate.parse("2026-01-10") }
        assertNull(saturday.dailyReturn)
        assertNull(saturday.dailyPnl)
        approx("100", summary(wallet).gain)
    }

    @Test fun xirrUsesDatedCashFlowsAndTerminalValue() {
        val start = LocalDate.of(2025, 1, 1)
        val end = start.plusYears(1)
        approx("10", ReportEngine.xirr(listOf(start to bd("1000")), end, bd("1100")))
        val withLaterDeposit = ReportEngine.xirr(listOf(start to bd("1000"),
            start.plusMonths(6) to bd("1000")), end, bd("2100"))
        assertNotNull(withLaterDeposit)
        assertTrue(withLaterDeposit!! > ZERO && withLaterDeposit < bd("10"))
        assertNull(ReportEngine.xirr(emptyList(), end, bd("1100")))
    }
}
