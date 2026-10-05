package ca.monwallet.app

import ca.monwallet.app.domain.Point
import ca.monwallet.app.marketdata.MomentumEngine
import ca.monwallet.app.marketdata.PerformancePeriod
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MomentumEngineTest {
    private fun series(sessions: Int, value: (Int) -> Double): List<Point> {
        var date = LocalDate.of(2019, 1, 2)
        return (0 until sessions).map { i ->
            val p = Point("test", date.toString(), BigDecimal.valueOf(value(i)), 0L)
            date = date.plusDays(1)
            while (date.dayOfWeek.value > 5) date = date.plusDays(1)
            p
        }
    }

    @Test fun steadyRiseBeatsTwoDaySpike() {
        val smooth = series(180) { i -> 100.0 * (1.3).let { Math.pow(it, i / 179.0) } }
        val spike = series(180) { i -> when {
            i < 125 -> 100.0
            i == 125 -> 140.0
            i == 126 -> 196.0
            else -> 196.0 + (i - 126) * 0.08
        } }
        val clean = MomentumEngine.analyze(smooth, PerformancePeriod.M3, 20_000_000.0)!!
        val pumped = MomentumEngine.analyze(spike, PerformancePeriod.M3, 20_000_000.0)!!
        assertTrue(pumped.performance > clean.performance)
        assertTrue(pumped.bestDay > 20)
        assertTrue(pumped.gainConcentration > clean.gainConcentration)
        assertTrue(clean.score > pumped.score)
    }

    @Test fun fiveYearsNeedsRealFiveYearHistory() {
        val twoYears = series(520) { i -> 100.0 + i * .05 }
        assertNull(MomentumEngine.performance(twoYears, PerformancePeriod.Y5))
        assertNull(MomentumEngine.analyze(twoYears, PerformancePeriod.Y5, 10_000_000.0))
        val sixYears = series(1600) { i -> 100.0 * Math.pow(1.2, i / 260.0) }
        val five = MomentumEngine.analyze(sixYears, PerformancePeriod.Y5, 10_000_000.0)
        assertNotNull(five)
        assertTrue(five!!.cagr5y!!.toDouble() > 15)
    }

    @Test fun allPeriodsUseOnlyAvailableHistoricalSessions() {
        val enough = series(1600) { i -> 100.0 + i * .05 }
        PerformancePeriod.entries.forEach { p ->
            assertNotNull(p.label, MomentumEngine.performance(enough, p))
        }
        val recent = series(6) { i -> 100.0 + i }
        assertNull(MomentumEngine.performance(recent, PerformancePeriod.M3))
    }
}
