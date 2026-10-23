package ca.monwallet.app

import ca.monwallet.app.domain.*
import ca.monwallet.app.widgets.WidgetChartData
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class WidgetDayAfterTest {
    private fun at(s: String) = LocalDateTime.parse(s).atZone(ZoneId.of("America/New_York")).toInstant().toEpochMilli()
    private val stock = Security.of("TSM", "TSM", "NYSE", "USD")
    private val fx = Security.of("CAD=X", "USD/CAD", "FX", "CAD")
    private fun wallet() = Wallet(securities = listOf(stock, fx), transactions = listOf(
        Transaction(portfolioId = "p", securityId = stock.id, quantity = "10".dec(),
            price = "90".dec(), currency = "USD", date = "2026-10-20")), quotes = mapOf(
        stock.id to Quote(stock.id, "100".dec(), "98".dec(), "USD", at("2026-10-23T16:00:00"),
            "2026-10-23", "fixture", afterHoursPrice = "103".dec(), afterHoursTimestamp = at("2026-10-23T19:59:00")),
        fx.id to Quote(fx.id, ONE, ONE, "CAD", at("2026-10-23T16:00:00"), "2026-10-23", "fixture")))

    @Test fun dayFreezesWhileAfterIsSeparateAndSurvivesWeekend() {
        val w = wallet()
        val friday = at("2026-10-23T20:00:00")
        assertEquals(0, WidgetChartData.regularResult(w, "p", friday).day!!.compareTo("20".dec()))
        assertEquals(0, WidgetChartData.afterResult(w, "p", friday)!!.first!!.compareTo("30".dec()))
        val saturday = at("2026-10-24T00:00:00")
        assertEquals(0, WidgetChartData.regularResult(w, "p", saturday).day!!.compareTo(ZERO))
        assertEquals(0, WidgetChartData.afterResult(w, "p", saturday)!!.first!!.compareTo("30".dec()))
        assertEquals(WidgetChartData.sessionStart(friday), WidgetChartData.sessionStart(saturday))
        assertNotNull(WidgetChartData.afterResult(w, "p", at("2026-10-26T09:29:59")))
        assertNull(WidgetChartData.afterResult(w, "p", at("2026-10-26T09:30:00")))
    }

    @Test fun chartContinuesFromDayProfitRatherThanRestartingAtZeroAfterClose() {
        val w = wallet()
        val points = listOf(
            Point(stock.id, "2026-10-23", "98".dec(), at("2026-10-23T09:30:00")),
            Point(stock.id, "2026-10-23", "100".dec(), at("2026-10-23T16:00:00")),
            Point(stock.id, "2026-10-23", "103".dec(), at("2026-10-23T19:59:00")))
        val friday = WidgetChartData.daySeries(w, "p", points, at("2026-10-23T20:00:00"))
        assertEquals(listOf(0f, 20f, 50f), friday.values)
        assertEquals(2, friday.afterStart)
        assertEquals(friday, WidgetChartData.daySeries(w, "p", points, at("2026-10-26T09:29:59")))
        val refreshed = w.copy(quotes = w.quotes + (stock.id to w.quotes.getValue(stock.id)
            .copy(previous = "100".dec())), prices = listOf(
            Point(stock.id, "2026-10-22", "98".dec(), at("2026-10-22T16:00:00"))))
        assertEquals(friday, WidgetChartData.daySeries(refreshed, "p", points, at("2026-10-26T09:29:59")))
        assertTrue(WidgetChartData.daySeries(w, "p", points, at("2026-10-26T09:30:00")).values.isEmpty())
    }
}
