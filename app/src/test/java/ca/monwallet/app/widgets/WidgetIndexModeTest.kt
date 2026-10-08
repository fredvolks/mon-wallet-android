package ca.monwallet.app.widgets

import ca.monwallet.app.data.Catalog
import ca.monwallet.app.domain.Quote
import ca.monwallet.app.domain.Wallet
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetIndexModeTest {
    private val eastern = ZoneId.of("America/Toronto")
    private fun at(hour: Int, minute: Int) =
        ZonedDateTime.of(LocalDate.of(2026, 10, 8), LocalTime.of(hour, minute), eastern)

    @Test fun futuresWindowStartsAtFourAndEndsAtNineThirtyEastern() {
        assertFalse(WidgetIndexMode.isOvernightWindow(at(15, 59)))
        assertTrue(WidgetIndexMode.isOvernightWindow(at(16, 0)))
        assertTrue(WidgetIndexMode.isOvernightWindow(at(9, 29)))
        assertFalse(WidgetIndexMode.isOvernightWindow(at(9, 30)))
    }

    @Test fun futuresAreShownOnlyWhenAllThreeQuotesAreFresh() {
        val now = at(17, 0)
        val quotes = Catalog.markets.filter { it.symbol in setOf("ES=F", "NQ=F", "YM=F") }
            .associate { security ->
                security.id to Quote(security.id, BigDecimal("100"), BigDecimal("99"),
                    security.currency, now.toInstant().toEpochMilli(),
                    now.toLocalDate().toString(), "Test")
            }
        assertTrue(WidgetIndexMode.useFutures(Wallet(quotes = quotes), now))
        val stale = quotes.toMutableMap()
        val security = Catalog.markets.first { it.symbol == "NQ=F" }
        stale[security.id] = stale.getValue(security.id).copy(
            timestamp = now.minusHours(3).toInstant().toEpochMilli())
        assertFalse(WidgetIndexMode.useFutures(Wallet(quotes = stale), now))
    }

    @Test fun dailyPnlChartLineChangesColorAtZeroCrossings() {
        val pieces = listOf(10f to -5f, -5f to -4f, -4f to 8f)
            .flatMap { (start, end) -> WidgetChartData.segmentPieces(start, end) }

        assertEquals(listOf(1, -1, -1, 1), pieces.map { it.sign })
        assertEquals(2f / 3f, pieces[0].endFraction, 0.0001f)
        assertEquals(1f / 3f, pieces[2].startFraction, 0.0001f)
    }

    @Test fun chartSegmentsStayGreenAboveZeroAndRedBelowZero() {
        assertEquals(listOf(1), WidgetChartData.segmentPieces(2f, 5f).map { it.sign })
        assertEquals(listOf(-1), WidgetChartData.segmentPieces(-2f, -5f).map { it.sign })
    }
}
