package ca.monwallet.app

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

/** Exercises the same real daily closes and portfolio report engine used by Reports > Calendar. */
@RunWith(AndroidJUnit4::class)
class CalendarHistoryNetworkTest {
    @Test fun canadianMcdCdrClosesProduceDailyCalendarPnl() = runBlocking {
        val security = Security.of(
            "MCD.TO", "McDonald's CDR", "Cboe Canada", "CAD",
        )
        val first = LocalDate.parse("2026-10-01")
        val last = LocalDate.parse("2026-10-07")
        val points = withTimeout(30_000) {
            Yahoo().historyBetween(security, first, last)
        }
        val expectedSessions = setOf(
            "2026-10-01", "2026-10-02", "2026-10-05", "2026-10-06", "2026-10-07",
        )
        assertEquals("Yahoo must return every Canadian market close for Oct 1–7",
            expectedSessions, points.map { it.date }.toSet())
        assertTrue("Every real close must be positive", points.all { it.close > BigDecimal.ZERO })

        // A one-share purchase at the real Oct 1 close makes subsequent close-to-close P&L
        // directly comparable to the app's report calendar calculations.
        val initialClose = requireNotNull(points.single { it.date == first.toString() }.close)
        val transaction = Transaction(
            portfolioId = "calendar-emulator",
            securityId = security.id,
            type = TxType.BUY,
            quantity = BigDecimal.ONE,
            price = initialClose,
            currency = "CAD",
            date = first.toString(),
        )
        val wallet = Wallet(
            securities = listOf(security),
            transactions = listOf(transaction),
            prices = points,
        )
        val history = ReportEngine.history(wallet)
        val summary = ReportEngine.summary(
            wallet, history, ReportRange.MONTH, LocalDate.parse("2026-10-08"),
        )
        val monthDays = summary.days.filter { it.date in first..last }
        assertEquals("Calendar should have a value for each market session",
            expectedSessions, monthDays.map { it.date.toString() }.toSet())
        assertTrue("All market sessions must have a closing portfolio value",
            monthDays.all { it.hasMarketClose && it.closingValue != null })
        assertTrue("All market sessions must expose daily P&L in dollars",
            monthDays.all { it.dailyPnl != null })
        assertTrue("All market sessions must expose daily P&L percentage",
            monthDays.all { it.dailyReturn != null })
        assertTrue("Reconstructed calendar period should be complete", summary.complete)
    }
}
