package ca.monwallet.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ca.monwallet.app.domain.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Seeds the visible holdings, then exercises Reports through the wallet's stored tickers. */
@RunWith(AndroidJUnit4::class)
class CalendarHistoryNetworkTest {
    @Test fun everyWalletTickerReturnsCalendarClosesAndPnl() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val services = (context.applicationContext as MonWallet).services
        withTimeout(30_000) { services.initialized.first { it } }

        val holdings = listOf(
            Security.of("XEQT.TO", "XEQT", "TSX", "CAD", "ETF"),
            Security.of("TSM", "TSM", "NYSE", "USD"),
            Security.of("GURU.TO", "GURU", "TSX", "CAD"),
            Security.of("PHOS.CN", "PHOS", "CSE", "CAD"),
            Security.of("MCD-C", "McDonald's CDR", "TSX", "CAD"),
            Security.of("DOL.TO", "Dollarama", "TSX", "CAD"),
            Security.of("BABA", "Alibaba", "NYSE", "USD"),
            Security.of("BLDP.TO", "Ballard Power", "TSX", "CAD"),
        )
        val fx = Security.of("CAD=X", "USD/CAD", "FX", "CAD", "FX")
        val first = LocalDate.parse("2026-10-01")
        val portfolio = Portfolio(id = "calendar-emulator-${System.currentTimeMillis()}",
            name = "Calendar emulator")
        services.repo.put("portfolio", portfolio.id, portfolio)
        holdings.forEach { services.repo.put("security", it.id, it) }
        val transactions = holdings.map { security ->
            Transaction(
                portfolioId = portfolio.id,
                securityId = security.id,
                type = TxType.BUY,
                quantity = BigDecimal.ONE,
                // The test checks history coverage and non-null daily P&L; real dated
                // closes are fetched from each ticker stored in this wallet below.
                price = BigDecimal.ONE,
                currency = security.currency,
                fxRate = if (security.currency == "USD") BigDecimal("1.35") else BigDecimal.ONE,
                date = first.toString(),
            )
        }
        transactions.forEach { services.repo.put("transaction", it.id, it) }

        services.refreshReportHistory(portfolio.id, force = true)
        val wallet = services.repo.current()
        val walletTransactions = wallet.transactions.filter { it.portfolioId == portfolio.id }
        val walletIds = walletTransactions.mapNotNull { it.securityId }.toSet()
        val walletHoldings = wallet.securities.filter { it.id in walletIds }
        assertEquals("Calendar must use all eight tickers stored in this wallet",
            holdings.map { it.id }.toSet(), walletHoldings.map { it.id }.toSet())
        assertEquals(holdings.map { it.symbol }.toSet(), walletHoldings.map { it.symbol }.toSet())

        val today = LocalDate.now()
        val perTicker = walletHoldings.associate { security ->
            val points = wallet.prices.filter { it.securityId == security.id }
            Log.i("CalendarHistoryNetworkTest",
                "${security.symbol}: ${points.size} stored closes")
            assertTrue("${security.symbol}: no real closes saved by Reports history refresh",
                points.isNotEmpty())
            assertTrue("${security.symbol}: calendar history coverage is incomplete",
                reportHistoryHasCoverage(points, first, today))
            security.id to points
        }
        val fxPoints = wallet.prices.filter { it.securityId == fx.id }
        assertTrue("USD holdings require real historical CAD/USD closes",
            reportHistoryHasCoverage(fxPoints, first, today))

        val history = ReportEngine.history(wallet, portfolio.id)
        val summary = ReportEngine.summary(
            wallet, history, ReportRange.MONTH, today, portfolio.id,
        )
        val firstFiveSessions = setOf(
            "2026-10-01", "2026-10-02", "2026-10-05", "2026-10-06", "2026-10-07",
        )
        val days = summary.days.filter { it.date.toString() in firstFiveSessions }
        assertEquals("Calendar must reconstruct Oct 1–7 market sessions",
            firstFiveSessions, days.map { it.date.toString() }.toSet())
        assertTrue("Each session needs a portfolio close",
            days.all { it.hasMarketClose && it.closingValue != null })
        assertTrue("Each session needs P&L in dollars",
            days.all { it.dailyPnl != null })
        assertTrue("Each session needs P&L percent",
            days.all { it.dailyReturn != null })
        assertTrue("Each session needs all eight wallet holdings valued",
            days.all { it.positions.keys.containsAll(walletHoldings.map { s -> s.id }) })
        assertEquals(8, perTicker.size)
        assertTrue("Wallet calendar report should be complete", summary.complete)
    }
}
