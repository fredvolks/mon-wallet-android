package ca.monwallet.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.Yahoo
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Tests every holding visible in the user's portfolio against real closes and calendar P&L. */
@RunWith(AndroidJUnit4::class)
class CalendarHistoryNetworkTest {
    @Test fun everyOwnedHoldingProducesCalendarClosePnl() = runBlocking {
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
        val last = LocalDate.parse("2026-10-07")
        val expectedSessions = setOf(
            "2026-10-01", "2026-10-02", "2026-10-05", "2026-10-06", "2026-10-07",
        )
        val yahoo = Yahoo()
        val historyBySecurity = linkedMapOf<String, List<Point>>()

        for (security in holdings + fx) {
            val points = withTimeout(45_000) {
                yahoo.historyBetween(security, first, last)
            }
            Log.i("CalendarHistoryNetworkTest",
                "${security.symbol}: ${points.size} closes: ${points.map { it.date }}")
            assertTrue("${security.symbol}: no real historical closes returned", points.isNotEmpty())
            assertTrue("${security.symbol}: missing the Oct 1 starting close",
                points.any { it.date == first.toString() })
            historyBySecurity[security.id] = points
        }

        assertEquals("Each holding must have a dated close for every market session",
            expectedSessions, historyBySecurity.values.first().filter {
                it.date in expectedSessions
            }.map { it.date }.toSet())
        val fxPoints = historyBySecurity.getValue(fx.id)
        val firstFx = requireNotNull(fxPoints.singleOrNull { it.date == first.toString() }) {
            "CAD=X: missing Oct 1 exchange rate"
        }.close
        val transactions = holdings.map { security ->
            val close = requireNotNull(historyBySecurity.getValue(security.id)
                .singleOrNull { it.date == first.toString() }) {
                "${security.symbol}: missing Oct 1 purchase valuation"
            }.close
            Transaction(
                portfolioId = "calendar-emulator",
                securityId = security.id,
                type = TxType.BUY,
                quantity = BigDecimal.ONE,
                price = close,
                currency = security.currency,
                fxRate = if (security.currency == "USD") firstFx else BigDecimal.ONE,
                date = first.toString(),
            )
        }
        val wallet = Wallet(
            securities = holdings + fx,
            transactions = transactions,
            prices = historyBySecurity.values.flatten(),
        )
        val history = ReportEngine.history(wallet)
        val summary = ReportEngine.summary(
            wallet, history, ReportRange.MONTH, LocalDate.parse("2026-10-08"),
        )
        val days = summary.days.filter { it.date in first..last }
        assertEquals("Calendar must reconstruct Oct 1–7 market sessions",
            expectedSessions, days.map { it.date.toString() }.toSet())
        assertTrue("Every market session needs a portfolio close",
            days.all { it.hasMarketClose && it.closingValue != null })
        assertTrue("Every market session needs P&L in dollars",
            days.all { it.dailyPnl != null })
        assertTrue("Every market session needs P&L percent",
            days.all { it.dailyReturn != null })
        assertTrue("Calendar needs all eight holding valuations on every market session",
            days.all { it.positions.keys.containsAll(holdings.map { security -> security.id }) })
        assertTrue("The full portfolio calendar period should be complete", summary.complete)
    }
}
