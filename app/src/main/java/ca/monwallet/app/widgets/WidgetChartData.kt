package ca.monwallet.app.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import ca.monwallet.app.domain.Engine
import ca.monwallet.app.domain.MarketClock
import ca.monwallet.app.domain.MarketHoliday
import ca.monwallet.app.domain.Point
import ca.monwallet.app.domain.Wallet
import ca.monwallet.app.domain.ZERO
import ca.monwallet.app.domain.pct
import java.time.LocalDate

/** Uses the same accounting engine as the portfolio page; never synthesizes market points. */
internal object WidgetChartData {
    fun choose(day: List<Float>, week: List<Float>, month: List<Float>): List<Float> =
        when {
            day.size >= 2 -> day
            week.size >= 2 -> week
            month.size >= 2 -> month
            else -> emptyList()
        }

    fun values(wallet: Wallet, portfolio: String?, period: String, cached: List<Point>): List<Float> {
        if (period != "Jour") {
            val days = mapOf("1S" to 7L, "1M" to 31L, "3M" to 93L,
                "6M" to 186L, "1A" to 366L)[period] ?: 7L
            return history(wallet, portfolio, days)
        }
        val day = intraday(wallet, portfolio, cached)
        if (day.size >= 2) return day
        // The 4x2 labels this plot intraday. Weekly or monthly history must not
        // masquerade as the last trading session when its intraday cache is empty.
        return emptyList()
    }

    private fun history(wallet: Wallet, portfolio: String?, days: Long): List<Float> {
        if (wallet.prices.isEmpty()) return emptyList()
        val start = LocalDate.now().minusDays(days).toString()
        return runCatching {
            Engine.history(wallet.transactions, wallet.prices, wallet.securities, portfolio)
                .asSequence().filter { it.date >= start }.mapNotNull { it.value?.toFloat() }
                .filter { it.isFinite() }.toList()
        }.getOrDefault(emptyList())
    }

    internal data class DaySeries(val values: List<Float>, val afterStart: Int? = null)

    internal fun sessionStart(now: Long): Long {
        val date = if (MarketClock.offSession(now)) MarketClock.date(MarketClock.lastClose(now))
            else MarketClock.date(now)
        return date.atTime(9, 30).atZone(MarketClock.zone).toInstant().toEpochMilli()
    }

    internal fun regularResult(wallet: Wallet, portfolio: String?, now: Long): ca.monwallet.app.domain.Result {
        val date = MarketClock.date(now).toString()
        val quotes = wallet.quotes.mapValues { (_, q) ->
            if (q.sessionDate == date) q else q.copy(previous = q.price, sessionDate = date)
        }
        val excluded = wallet.securities.filter { MarketHoliday.excludesDailyPnl(it, now) }.map { it.id }.toSet()
        return Engine.calculate(wallet.transactions.filter { portfolio == null || it.portfolioId == portfolio },
            quotes, wallet.securities.find { it.symbol == "CAD=X" }?.let { quotes[it.id] }, excluded,
            dailyDate = date)
    }

    /** Retain actual extended prints through midnight/weekends, until the next regular open. */
    internal fun afterResult(wallet: Wallet, portfolio: String?, now: Long): Pair<java.math.BigDecimal?, java.math.BigDecimal?>? {
        if (!MarketClock.offSession(now)) return null
        val close = MarketClock.lastClose(now)
        val result = wallet.result(portfolio, now)
        var pnl = ZERO
        if (result.value == null) return Pair(null, null)
        var hasPrint = false
        for (h in result.holdings.filter { it.quantity > ZERO }) {
            val q = wallet.quotes[h.securityId] ?: return Pair(null, null)
            val security = wallet.security(h.securityId)
            if (security == null || !ca.monwallet.app.marketdata.supportsUsExtendedHours(security)) continue
            val prints = listOfNotNull(
                q.afterHoursTimestamp?.let { t -> q.afterHoursPrice?.let { Pair(t, it) } },
                q.preMarketTimestamp?.let { t -> q.preMarketPrice?.let { Pair(t, it) } }
            ).filter { it.first in close..now }
            val price = prints.maxByOrNull { it.first }?.second ?: continue
            val fx = if (q.currency == "CAD") java.math.BigDecimal.ONE else wallet.securities
                .find { it.symbol == "CAD=X" }?.let { wallet.quotes[it.id]?.price } ?: return Pair(null, null)
            pnl += h.quantity * (price - q.price) * fx
            hasPrint = true
        }
        if (!hasPrint) return Pair(null, null)
        // Use regular close portfolio value as the percentage denominator.
        val base = Engine.calculate(wallet.transactions.filter { portfolio == null || it.portfolioId == portfolio },
            wallet.quotes, wallet.securities.find { it.symbol == "CAD=X" }?.let { wallet.quotes[it.id] }).value
            ?: return Pair(null, null)
        return Pair(pnl, base.takeIf { it.signum() > 0 }?.let { pnl.pct(it) })
    }

    internal fun daySeries(wallet: Wallet, portfolio: String?, cached: List<Point>, now: Long): DaySeries {
        val tx = wallet.transactions.filter { portfolio == null || it.portfolioId == portfolio }
        val held = wallet.result(portfolio, now).holdings.filter { it.quantity > ZERO }.map { it.securityId }.toSet()
        val start = sessionStart(now)
        val close = MarketClock.date(start).atTime(16, 0).atZone(MarketClock.zone).toInstant().toEpochMilli()
        val points = cached.filter { it.securityId in held && it.timestamp in start..now }
        val times = points.map { it.timestamp }.distinct().sorted()
        val bySecurity = points.groupBy { it.securityId }.mapValues { it.value.sortedBy { p -> p.timestamp } }
        val fx = wallet.securities.find { it.symbol == "CAD=X" }
        val values = mutableListOf<Float>()
        var after: Int? = null
        for (time in times) {
            val quotes = wallet.quotes.toMutableMap()
            var complete = true
            for (id in held) {
                val original = quotes[id]
                if (original == null) { complete = false; break }
                val point = bySecurity[id]?.lastOrNull { it.timestamp <= time }
                val price = point?.close ?: original.previous
                if (price == null) { complete = false; break }
                quotes[id] = original.copy(price = price, sessionDate = MarketClock.date(start).toString())
            }
            if (!complete) continue
            val value = Engine.calculate(tx, quotes, fx?.let { quotes[it.id] },
                dailyDate = MarketClock.date(start).toString()).day?.toFloat() ?: continue
            if (!value.isFinite()) continue
            if (time > close && after == null) after = values.size
            values.add(value)
        }
        return DaySeries(values, after)
    }

    private fun intraday(wallet: Wallet, portfolio: String?, cached: List<Point>): List<Float> =
        daySeries(wallet, portfolio, cached, System.currentTimeMillis()).values

    internal data class SegmentPiece(val startFraction: Float, val endFraction: Float, val sign: Int)

    /** Splits each plotted interval where daily P&L crosses zero. */
    internal fun segmentPieces(start: Float, end: Float): List<SegmentPiece> {
        if (!start.isFinite() || !end.isFinite()) return emptyList()
        val startSign = start.compareTo(0f)
        val endSign = end.compareTo(0f)
        if (startSign * endSign < 0) {
            val crossing = kotlin.math.abs(start) /
                (kotlin.math.abs(start) + kotlin.math.abs(end))
            return listOf(
                SegmentPiece(0f, crossing, startSign),
                SegmentPiece(crossing, 1f, endSign),
            )
        }
        val sign = if (startSign != 0) startSign else endSign
        return listOf(SegmentPiece(0f, 1f, sign))
    }

    fun bitmap(values: List<Float>, width: Int = 720, height: Int = 55, afterStart: Int? = null): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (values.size < 2) return bitmap
        val min = minOf(values.minOrNull() ?: 0f, 0f)
        val max = maxOf(values.maxOrNull() ?: 0f, 0f)
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val top = 4f
        val bottom = height - 4f
        fun y(value: Float) = bottom - ((value - min) / range) * (bottom - top)
        val zeroY = y(0f)
        val canvas = Canvas(bitmap)

        values.zipWithNext().forEachIndexed { index, (start, end) ->
            if (!start.isFinite() || !end.isFinite()) return@forEachIndexed
            val x1 = 2f + index * (width - 4f) / (values.size - 1)
            val x2 = 2f + (index + 1) * (width - 4f) / (values.size - 1)
            val y1 = y(start)
            val y2 = y(end)
            segmentPieces(start, end).forEach { piece ->
                if (piece.sign == 0 && (afterStart == null || index + 1 < afterStart)) return@forEach
                val px1 = x1 + (x2 - x1) * piece.startFraction
                val px2 = x1 + (x2 - x1) * piece.endFraction
                val py1 = y1 + (y2 - y1) * piece.startFraction
                val py2 = y1 + (y2 - y1) * piece.endFraction
                val color = if (afterStart != null && index + 1 >= afterStart) Color.rgb(181, 150, 241)
                    else if (piece.sign > 0) Color.rgb(79, 239, 142)
                    else Color.rgb(255, 93, 117)

                val fill = Path().apply {
                    moveTo(px1, py1)
                    lineTo(px2, py2)
                    lineTo(px2, zeroY)
                    lineTo(px1, zeroY)
                    close()
                }
                canvas.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.argb(35, Color.red(color), Color.green(color), Color.blue(color))
                    style = Paint.Style.FILL
                })
                canvas.drawLine(px1, py1, px2, py2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    style = Paint.Style.STROKE
                    strokeWidth = 2.6f
                    strokeCap = Paint.Cap.ROUND
                })
            }
        }
        return bitmap
    }
}
