package ca.monwallet.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Device-local session clock override. Provider timestamps always remain absolute instants. */
object MarketClock {
    @Volatile var mode: String = "AUTO"
    fun zoneFor(mode: String): ZoneId = when (mode) {
        "SUMMER" -> ZoneOffset.ofHours(-4)
        "WINTER" -> ZoneOffset.ofHours(-5)
        else -> ZoneId.of("America/New_York")
    }
    val zone: ZoneId get() = zoneFor(mode)
    fun date(now: Long): LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    private val calendar = Security.of("SPY", "US calendar", "NYSE", "USD")
    fun tradingDay(date: LocalDate): Boolean =
        date.dayOfWeek.value <= 5 && !MarketHoliday.isHoliday(calendar, date)
    fun offSession(now: Long): Boolean {
        val local = Instant.ofEpochMilli(now).atZone(zone)
        return !tradingDay(local.toLocalDate()) ||
            local.toLocalTime() < LocalTime.of(9, 30) ||
            local.toLocalTime() >= LocalTime.of(16, 0)
    }
    /** Last scheduled close, unchanged across midnight, weekends and holidays. */
    fun lastClose(now: Long): Long {
        val local = Instant.ofEpochMilli(now).atZone(zone)
        var date = local.toLocalDate()
        if (local.toLocalTime() < LocalTime.of(16, 0)) date = date.minusDays(1)
        while (!tradingDay(date)) date = date.minusDays(1)
        return date.atTime(16, 0).atZone(zone).toInstant().toEpochMilli()
    }
    fun chartStart(now: Long): Long = if (offSession(now)) lastClose(now)
        else date(now).atTime(9, 30).atZone(zone).toInstant().toEpochMilli()
}
