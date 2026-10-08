package ca.monwallet.app.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import ca.monwallet.app.domain.Engine
import ca.monwallet.app.domain.Point
import ca.monwallet.app.domain.Wallet
import ca.monwallet.app.domain.ZERO
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

    private fun intraday(wallet: Wallet, portfolio: String?, cached: List<Point>): List<Float> {
        val tx = wallet.transactions.filter { portfolio == null || it.portfolioId == portfolio }
        val held = wallet.result(portfolio).holdings
            .filter { it.quantity > ZERO }.map { it.securityId }.toSet()
        if (held.isEmpty()) return emptyList()
        val session = wallet.quotes.values.filter { it.securityId in held }
            .minOfOrNull { it.sessionDate } ?: return emptyList()
        val points = cached.filter { it.securityId in held && it.date == session }
        val times = points.map { it.timestamp }.distinct().sorted()
        val bySecurity = points.groupBy { it.securityId }
            .mapValues { (_, series) -> series.sortedBy { it.timestamp } }
        val fx = wallet.securities.find { it.symbol == "CAD=X" }
        return times.mapNotNull { timestamp ->
            val quotes = wallet.quotes.toMutableMap()
            if (held.any { it !in quotes }) return@mapNotNull null
            for (securityId in held) {
                val point = bySecurity[securityId]?.lastOrNull { it.timestamp <= timestamp }
                    ?: return@mapNotNull null
                quotes[securityId] = quotes.getValue(securityId)
                    .copy(price = point.close, timestamp = point.timestamp)
            }
            // Keep the existing FX quote; no inferred exchange rate is introduced.
            runCatching { Engine.calculate(tx, quotes, fx?.let { quotes[it.id] }).day?.toFloat() }
                .getOrNull()?.takeIf { it.isFinite() }
        }
    }

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

    fun bitmap(values: List<Float>, width: Int = 720, height: Int = 55): Bitmap {
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
                if (piece.sign == 0) return@forEach
                val px1 = x1 + (x2 - x1) * piece.startFraction
                val px2 = x1 + (x2 - x1) * piece.endFraction
                val py1 = y1 + (y2 - y1) * piece.startFraction
                val py2 = y1 + (y2 - y1) * piece.endFraction
                val color = if (piece.sign > 0) Color.rgb(79, 239, 142)
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
