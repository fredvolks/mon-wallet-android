package ca.monwallet.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Exchange holidays used to distinguish a market holiday from a missing quote. */
enum class ExchangeCalendar { TSX, CSE, US_EQUITIES, NONE }

object MarketHoliday {
    private val toronto = ZoneId.of("America/Toronto")
    private val newYork = ZoneId.of("America/New_York")

    fun calendar(security: Security): ExchangeCalendar {
        val exchange = security.exchange.uppercase().replace(" ", "")
        val symbol = security.symbol.uppercase()
        return when {
            exchange in setOf("TSX", "TOR", "TORONTO", "TSE") ||
                (security.currency == "CAD" && (symbol.endsWith(".TO") || symbol.endsWith(".NE"))) ->
                ExchangeCalendar.TSX
            exchange in setOf("CSE", "CNQ", "CNSX") || symbol.endsWith(".CN") ->
                ExchangeCalendar.CSE
            exchange in setOf("NYSE", "NYQ", "NASDAQ", "NASDAQGS", "NASDAQGM", "NASDAQCM", "NMS", "NCM", "NGM") ||
                security.currency == "USD" -> ExchangeCalendar.US_EQUITIES
            else -> ExchangeCalendar.NONE
        }
    }

    fun isHoliday(security: Security, date: LocalDate): Boolean = when (calendar(security)) {
        ExchangeCalendar.TSX -> date in canadianHolidays(date.year)
        ExchangeCalendar.CSE -> date in canadianHolidays(date.year)
        ExchangeCalendar.US_EQUITIES -> date in usHolidays(date.year)
        ExchangeCalendar.NONE -> false
    }

    fun isHolidayToday(security: Security, now: Long = System.currentTimeMillis()): Boolean {
        val zone = when (calendar(security)) {
            ExchangeCalendar.TSX, ExchangeCalendar.CSE -> toronto
            ExchangeCalendar.US_EQUITIES -> newYork
            ExchangeCalendar.NONE -> return false
        }
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return isHoliday(security, today)
    }

    /** True only when this title is on an exchange holiday and its quote is from an earlier session. */
    fun excludesDailyPnl(security: Security, quote: Quote?, now: Long = System.currentTimeMillis()): Boolean {
        quote ?: return false
        if (!isHolidayToday(security, now)) return false
        val zone = when (calendar(security)) {
            ExchangeCalendar.TSX, ExchangeCalendar.CSE -> toronto
            ExchangeCalendar.US_EQUITIES -> newYork
            ExchangeCalendar.NONE -> return false
        }
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val sessionDate = runCatching { LocalDate.parse(quote.sessionDate) }.getOrNull() ?: return false
        return sessionDate < today
    }

    private fun canadianHolidays(year: Int): Set<LocalDate> {
        val dates = mutableSetOf(
            observed(LocalDate.of(year, 1, 1)),
            observed(LocalDate.of(year, 7, 1)),
            observed(LocalDate.of(year, 12, 25)),
            observed(LocalDate.of(year, 12, 26)),
        )
        dates += easterSunday(year).minusDays(2) // Good Friday
        dates += nthWeekday(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3) // Family Day
        dates += mondayBefore(year, Month.MAY, 25) // Victoria Day
        dates += nthWeekday(year, Month.AUGUST, DayOfWeek.MONDAY, 1) // Civic holiday
        dates += nthWeekday(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1) // Labour Day
        dates += nthWeekday(year, Month.OCTOBER, DayOfWeek.MONDAY, 2) // Thanksgiving
        return dates
    }

    private fun usHolidays(year: Int): Set<LocalDate> {
        val dates = mutableSetOf(
            observed(LocalDate.of(year, 1, 1)),
            observed(LocalDate.of(year, 6, 19)),
            observed(LocalDate.of(year, 7, 4)),
            observed(LocalDate.of(year, 12, 25)),
        )
        dates += nthWeekday(year, Month.JANUARY, DayOfWeek.MONDAY, 3) // Martin Luther King Jr. Day
        dates += nthWeekday(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3) // Presidents Day
        dates += easterSunday(year).minusDays(2) // Good Friday
        dates += lastWeekday(year, Month.MAY, DayOfWeek.MONDAY) // Memorial Day
        dates += nthWeekday(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1) // Labor Day
        dates += nthWeekday(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4) // Thanksgiving
        return dates
    }

    private fun observed(date: LocalDate): LocalDate = when (date.dayOfWeek) {
        DayOfWeek.SATURDAY -> date.minusDays(1)
        DayOfWeek.SUNDAY -> date.plusDays(1)
        else -> date
    }

    private fun nthWeekday(year: Int, month: Month, day: DayOfWeek, n: Int): LocalDate =
        LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, day))

    private fun lastWeekday(year: Int, month: Month, day: DayOfWeek): LocalDate =
        LocalDate.of(year, month, 1).with(TemporalAdjusters.lastInMonth(day))

    private fun mondayBefore(year: Int, month: Month, latestDay: Int): LocalDate =
        LocalDate.of(year, month, latestDay).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}
