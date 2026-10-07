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
    @Test fun fiveRowsAndDailyHeroRenderWithoutClipping() { runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val portfolio = Portfolio(name = "Disnat")
        val tickers = listOf("XEQT", "TSM", "GURU", "PHOS", "MCD")
        val amounts = listOf("46.20", "485.80", "2.80", "2.06", "19.55")
        val changes = listOf("0.54", "3.01", "-2.96", "-2.83", "0.31")
        val securities = tickers.mapIndexed { index, ticker ->
            Security.of(ticker, ticker, if (index == 1 || index == 4) "NYSE" else "TSX",
                if (index == 1 || index == 4) "USD" else "CAD")
        }
        val holdings = securities.mapIndexed { index, security ->
            Holding(security.id, ONE, BigDecimal("100"), BigDecimal("100"), ZERO, ZERO,
                BigDecimal("105"), ZERO, ZERO, BigDecimal(changes[index]), BigDecimal("100"))
        }
        val now = System.currentTimeMillis()
        val wallet = Wallet(portfolios = listOf(portfolio), securities = securities,
            quotes = securities.mapIndexed { index, security ->
                security.id to Quote(security.id, BigDecimal(amounts[index]),
                    BigDecimal(amounts[index]).divide(BigDecimal.ONE.add(
                        BigDecimal(changes[index]).divide(BigDecimal("100"))), MathContext.DECIMAL128),
                    security.currency, now, LocalDate.now().toString(), "exemple visuel")
            }.toMap())
        val result = Result(holdings, BigDecimal("9300"), ZERO, BigDecimal("9348.36"),
            ZERO, ZERO, BigDecimal("48.36"), BigDecimal("6535.135"))
        val settings = WidgetSettings(style = "Daily + Titres", titleCount = 5,
            custom = true, titleIds = securities.map { it.id }, price = true, chart = false,
            logo = true, titleTotalPercent = false)
        val remote = WidgetWideRenderer.render(context, 101, null, settings, wallet,
            portfolio.id, result, false, emptyList(), 360 to 160)
        var screenshot: Bitmap? = null
        instrumentation.runOnMainSync {
            val view = remote.apply(context, null)
            val scale = context.resources.displayMetrics.density
            val width = (360 * scale).roundToInt()
            val height = (160 * scale).roundToInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            val rows = view.findViewById<LinearLayout>(R.id.widget_rows)
            assertEquals(5, rows.childCount)
            val amount = view.findViewById<TextView>(R.id.widget_amount)
            assertTrue(amount.textSize >
                view.findViewById<TextView>(R.id.widget_total).textSize)
            assertTrue("Daily amount clipped: ${amount.text}",
                amount.paint.measureText(amount.text.toString()) <= amount.width)
            assertTrue(rows.getChildAt(4).bottom <= rows.height)
            for (index in 0 until 5) {
                val row = rows.getChildAt(index)
                assertNotNull(row.findViewById<ImageView>(R.id.widget_row_logo).drawable)
                val text = row.findViewById<TextView>(R.id.widget_row_ticker)
                assertTrue("Missing title ${tickers[index]} in ${text.text}",
                    text.text.contains(tickers[index]))
                assertTrue("Missing price in ${text.text}",
                    text.text.contains(amounts[index].replace('.', ',')))
                assertTrue("Missing day percent in ${text.text}", text.text.contains("%"))
                assertTrue("Row $index clipped: ${text.text}, width ${text.width}",
                    text.right <= row.width && text.paint.measureText(text.text.toString()) <= text.width)
            }
            screenshot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
            }
            val body = view.findViewById<View>(R.id.widget_body)
            val first = rows.getChildAt(0)
            val title = first.findViewById<TextView>(R.id.widget_row_ticker)
            val x = body.left + rows.left + first.left + title.left +
                title.paint.measureText("XEQT ").toInt()
            val y = body.top + rows.top + first.top + title.top
            val bounds = (x.coerceAtLeast(0) until (body.left + rows.left + first.left +
                title.right).coerceAtMost(width))
            val lines = (y.coerceAtLeast(0) until (y + title.height).coerceAtMost(height))
            val painted = lines.any { py -> bounds.any { px ->
                val color = requireNotNull(screenshot).getPixel(px, py)
                android.graphics.Color.red(color) > 100 &&
                    android.graphics.Color.green(color) > 100
            } }
            assertTrue("Price not painted: body ${body.width} at ${body.left}, " +
                "rows ${rows.width} at ${rows.left}, row ${first.width} at ${first.left}, " +
                "bitmap rect $x,$y, text=${title.text}", painted)
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "widget-4x2-daily-fixture.png")
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
