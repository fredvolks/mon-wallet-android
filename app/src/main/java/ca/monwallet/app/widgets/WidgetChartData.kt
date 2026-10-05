package ca.monwallet.app.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
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
        if (wallet.prices.isEmpty()) return emptyList()
        return choose(day, history(wallet, portfolio, 7L), history(wallet, portfolio, 31L))
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

    fun bitmap(values: List<Float>, positive: Boolean, width: Int = 720, height: Int = 55): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (values.size < 2) return bitmap
        val color = if (positive) Color.rgb(79, 239, 142) else Color.rgb(255, 93, 117)
        val min = values.minOrNull() ?: return bitmap
        val range = ((values.maxOrNull() ?: min) - min).takeIf { it > 0 } ?: 1f
        val baseline = height - 2f
        val path = Path()
        values.forEachIndexed { i, value ->
            val x = 2f + i * (width - 4f) / (values.size - 1)
            val y = height - 5f - (value - min) / range * (height - 12f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val canvas = Canvas(bitmap)
        val fill = Path(path).apply {
            lineTo(width - 2f, baseline)
            lineTo(2f, baseline)
            close()
        }
        val area = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                Color.argb(65, Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        canvas.drawPath(fill, area)
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 2.6f
        })
        return bitmap
    }
}
