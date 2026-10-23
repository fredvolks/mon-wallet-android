package ca.monwallet.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.MediaStore
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ca.monwallet.app.domain.*
import ca.monwallet.app.widgets.WidgetSettings
import ca.monwallet.app.widgets.WidgetWideRenderer
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Captures the actual RemoteViews layout with explicit example holdings. */
@RunWith(AndroidJUnit4::class)
class WidgetRemoteViewsScreenshotTest {
    // Reproduces the 616x333 ratio measured from the user's One UI screenshot.
    @Test fun fiveRowsAndDailyHeroRenderWithoutClipping() = captureTitles(5, 334, 180)
    @Test fun sixRowsFillTheColumnWithoutClipping() = captureTitles(6, 334, 181)

    private fun captureTitles(count: Int, widthDp: Int, heightDp: Int) { runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val portfolio = Portfolio(name = "Disnat")
        val preMarket = count == 5
        val tickers = listOf("XEQT", "TSM", "GURU", "PHOS", "MCD", "SPCX").take(count)
        val amounts = listOf("46.20", "485.80", "2.80", "2.06", "19.55", "184.33")
        val changes = listOf("0.54", "3.01", "-2.96", "-2.83", "0.31", "0.85")
        val securities = tickers.mapIndexed { index, ticker ->
            val symbol = listOf("XEQT.TO", "TSM", "GURU.TO", "PHOS.CN", "MCD.TO", "SPCX")[index]
            val exchange = when (index) { 1 -> "NYSE"; 3 -> "CSE"; 5 -> "NASDAQ"; else -> "TSX" }
            Security.of(symbol, ticker, exchange, if (index in setOf(1, 5)) "USD" else "CAD",
                if (index == 0) "ETF" else "STOCK")
        }
        val holdings = securities.mapIndexed { index, security ->
            Holding(security.id, ONE, BigDecimal("100"), BigDecimal("100"), ZERO, ZERO,
                BigDecimal("105"), ZERO, ZERO, BigDecimal(changes[index]), BigDecimal("100"))
        }
        val printAt = java.time.ZonedDateTime.of(2026, 10, 22, 17, 30, 0, 0,
            java.time.ZoneId.of("America/New_York")).toInstant().toEpochMilli()
        val now = java.time.ZonedDateTime.of(2026, 10, 23, 9, 29, 0, 0,
            java.time.ZoneId.of("America/New_York")).toInstant().toEpochMilli()
        val fx = Security.of("CAD=X", "USD/CAD", "FX", "CAD")
        val wallet = Wallet(portfolios = listOf(portfolio), securities = securities + fx,
            transactions = securities.map { Transaction(portfolioId = portfolio.id, securityId = it.id,
                quantity = ONE, price = BigDecimal("10"), currency = it.currency, date = "2026-10-20") },
            quotes = securities.mapIndexed { index, security ->
                security.id to Quote(security.id, BigDecimal(amounts[index]),
                    BigDecimal(amounts[index]).divide(BigDecimal.ONE.add(
                        BigDecimal(changes[index]).divide(BigDecimal("100"))), MathContext.DECIMAL128),
                    security.currency, now, "2026-10-22", "exemple visuel",
                    preMarketPrice = if (preMarket && index in setOf(1, 5))
                        BigDecimal(amounts[index]) + BigDecimal("0.50") else null,
                    preMarketTimestamp = if (preMarket && index in setOf(1, 5)) now else null,
                    preMarketChangePercent = if (preMarket && index in setOf(1, 5)) BigDecimal("0.27") else null,
                    afterHoursPrice = if (index in setOf(1, 5)) BigDecimal(amounts[index]) + BigDecimal("0.50") else null,
                    afterHoursTimestamp = if (index in setOf(1, 5)) printAt else null,
                    afterHoursChangePercent = if (index in setOf(1, 5)) BigDecimal("0.27") else null,
                    marketSession = "CLOSED")
            }.toMap() + (fx.id to Quote(fx.id, ONE, ONE, "CAD", now, "2026-10-22", "fixture")))
        val result = Result(holdings, BigDecimal("9300"), ZERO, BigDecimal("9348.36"),
            ZERO, ZERO, BigDecimal("48.36"), BigDecimal("6535.135"))
        val settings = WidgetSettings(style = "Daily + Titres", titleCount = count,
            custom = true, titleIds = securities.map { it.id }, price = true, chart = true,
            logo = true, titleTotalPercent = false)
        val points = securities.flatMap { security ->
            val q = wallet.quotes.getValue(security.id)
            listOf(Point(security.id, "2026-10-22", q.previous!!, printAt - 8 * 3600000L),
                Point(security.id, "2026-10-22", q.price, printAt - 90 * 60000L),
                Point(security.id, "2026-10-22", q.afterHoursPrice ?: q.price, printAt))
        }
        val remote = WidgetWideRenderer.render(context, 101, null, settings, wallet,
            portfolio.id, result, false, points, widthDp to heightDp, quoteNow = now)
        var screenshot: Bitmap? = null
        instrumentation.runOnMainSync {
            val parcel = android.os.Parcel.obtain()
            remote.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = android.widget.RemoteViews.CREATOR.createFromParcel(parcel)
            parcel.recycle()
            val view = restored.apply(context, null)
            val scale = context.resources.displayMetrics.density
            val width = (widthDp * scale).roundToInt()
            val height = (heightDp * scale).roundToInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            val rows = view.findViewById<LinearLayout>(R.id.widget_rows)
            assertEquals(count, rows.childCount)
            assertEquals(context.getString(R.string.widget_today), view.findViewById<TextView>(R.id.widget_day).text.toString())
            val after = view.findViewById<TextView>(R.id.widget_after)
            assertEquals(View.VISIBLE, after.visibility)
            assertTrue(after.text.toString().startsWith("AFTER"))
            assertTrue(after.paint.measureText(after.text.toString()) <= after.width)
            assertEquals(View.VISIBLE, view.findViewById<ImageView>(R.id.widget_chart).visibility)
            val amount = view.findViewById<TextView>(R.id.widget_amount)
            assertTrue(amount.textSize >
                view.findViewById<TextView>(R.id.widget_total).textSize)
            assertTrue("Daily amount clipped: ${amount.text}",
                amount.paint.measureText(amount.text.toString()) <= amount.width)
            assertEquals(rows.height, rows.getChildAt(count - 1).bottom)
            val commonTextSize = rows.getChildAt(0)
                .findViewById<TextView>(R.id.widget_row_ticker).textSize
            for (index in 0 until count) {
                val row = rows.getChildAt(index)
                assertNotNull(row.findViewById<ImageView>(R.id.widget_row_logo).drawable)
                val text = row.findViewById<TextView>(R.id.widget_row_ticker)
                assertEquals(View.GONE, row.findViewById<TextView>(R.id.widget_row_after).visibility)
                if (index in setOf(1, 5)) {
                    assertTrue(text.text.contains((if (preMarket) "☀ " else "☾ ") + "+0,27 %"))
                    assertEquals("One complete line", 1, text.layout.lineCount)
                    assertEquals("No clipped percentage", 0, text.layout.getEllipsisCount(0))
                    assertTrue("Extended return reaches the right edge: " +
                        text.layout.getLineRight(0) + "/" + text.width,
                        kotlin.math.abs(text.layout.getLineRight(0) - text.width) <= 8)
                }
                assertTrue("Compact widget text must stay readable",
                    text.textSize / context.resources.displayMetrics.scaledDensity >= 10f)
                assertEquals("All price columns need the same character width",
                    commonTextSize, text.textSize, 0.1f)
                assertTrue("Missing title ${tickers[index]} in ${text.text}",
                    text.text.contains(tickers[index]))
                // Main columns retain regular-close price/change, matching the stock page.
                val visiblePrice = amounts[index]
                assertTrue("Missing price in ${text.text}",
                    text.text.contains(visiblePrice.replace('.', ',')))
                assertTrue("Missing day percent in ${text.text}", text.text.contains("%"))
                assertTrue("Row $index clipped: ${text.text}, width ${text.width}, " +
                    "painted ${text.paint.measureText(text.text.toString())}",
                    text.right <= row.width && text.paint.measureText(text.text.toString()) <= text.width)
            }
            screenshot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
            }
            for (index in listOf(1, 5).filter { it < count }) {
                val cell = rows.getChildAt(index).findViewById<TextView>(R.id.widget_row_ticker)
                val rect = android.graphics.Rect()
                cell.getDrawingRect(rect)
                (view as android.view.ViewGroup).offsetDescendantRectToMyCoords(cell, rect)
                val extraWidth = cell.paint.measureText("+0,27 %").toInt() + 4
                val xStart = (rect.right - extraWidth).coerceAtLeast(rect.left)
                val drawn = (rect.top until rect.bottom).any { py ->
                    (xStart until rect.right).any { px ->
                        val color = requireNotNull(screenshot).getPixel(px, py)
                        val r = android.graphics.Color.red(color)
                        val g = android.graphics.Color.green(color)
                        val b = android.graphics.Color.blue(color)
                        if (preMarket) b > 150 && b > r + 40 && g > 100
                        else b > 180 && b > g + 40 && r > 100
                    }
                }
                assertTrue("PRE/AFTER numbers must actually be painted at the right edge", drawn)
            }
            val body = view.findViewById<View>(R.id.widget_body)
            val first = rows.getChildAt(0)
            val title = first.findViewById<TextView>(R.id.widget_row_ticker)
            val rect = android.graphics.Rect()
            title.getDrawingRect(rect)
            (view as android.view.ViewGroup).offsetDescendantRectToMyCoords(title, rect)
            val x = rect.left + title.paint.measureText("XEQT ").toInt()
            val y = rect.top
            val bounds = (x.coerceAtLeast(0) until rect.right.coerceAtMost(width))
            val lines = (y.coerceAtLeast(0) until rect.bottom.coerceAtMost(height))
            val painted = lines.any { py -> bounds.any { px ->
                val color = requireNotNull(screenshot).getPixel(px, py)
                android.graphics.Color.red(color) > 100 && android.graphics.Color.green(color) > 100
            } }
            assertTrue("Price not painted: body ${body.width} at ${body.left}, " +
                "rows ${rows.width} at ${rows.left}, row ${first.width} at ${first.left}, " +
                "bitmap rect $x,$y, text=${title.text}", painted)
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,
                if (count == 5) "widget-4x2-daily-fixture.png"
                else "widget-4x2-six-titles.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MonWallet")
        }
        val uri = requireNotNull(context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        requireNotNull(context.contentResolver.openOutputStream(uri)).use {
            assertTrue(requireNotNull(screenshot).compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot?.recycle()
    } }
}
