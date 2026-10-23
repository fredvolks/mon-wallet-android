package ca.monwallet.app

import ca.monwallet.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class ReportHistoryRefreshTest {
    private fun at(value: String) = LocalDateTime.parse(value).atZone(MarketClock.zone).toInstant().toEpochMilli()
    @Test fun lastDailyBarIsReplacedByRealClosingBarWithoutLosingOlderHistory() {
        val cached = listOf(Point("s", "2026-10-21", "100".dec(), 1),
            Point("s", "2026-10-22", "101".dec(), 2))
        val fresh = listOf(Point("s", "2026-10-22", "113".dec(), 3))
        val merged = mergeReportHistory(cached, fresh)
        assertEquals(2, merged.size)
        assertEquals("100".dec(), merged.first().close)
        assertEquals("113".dec(), merged.last().close)
        assertEquals(3L, merged.last().timestamp)
    }
    @Test fun marketCloseInvalidatesSixHourCacheButOrdinaryMinutesDoNot() {
        val before = at("2026-10-22T15:50:00")
        assertFalse(reportHistoryRefreshDue(false, before, at("2026-10-22T15:55:00")))
        assertTrue(reportHistoryRefreshDue(false, before, at("2026-10-22T16:01:00")))
        val closed = at("2026-10-22T16:01:00")
        assertFalse(reportHistoryRefreshDue(false, closed, at("2026-10-22T16:10:00")))
        assertTrue(reportHistoryRefreshDue(true, closed, at("2026-10-22T16:10:00")))
    }
}
