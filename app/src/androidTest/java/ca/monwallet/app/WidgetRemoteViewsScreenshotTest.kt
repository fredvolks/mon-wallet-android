package ca.monwallet.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.MediaStore
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ca.monwallet.app.domain.*
import ca.monwallet.app.widgets.WidgetSettings
import ca.monwallet.app.widgets.WidgetWideRenderer
import java.math.BigDecimal
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
                security.id to Quote(security.id, BigDecimal(amounts[index]), null,
                    security.currency, now, LocalDate.now().toString(), "exemple visuel")
            }.toMap())
        val result = Result(holdings, BigDecimal("9300"), ZERO, BigDecimal("9348.36"),
            ZERO, ZERO, BigDecimal("48.36"), BigDecimal("6535.135"))
        val settings = WidgetSettings(style = "Daily + Titres", titleCount = 5,
            custom = true, titleIds = securities.map { it.id }, price = true, chart = false,
            logo = false, titleTotalPercent = false)
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
            assertTrue(view.findViewById<TextView>(R.id.widget_amount).textSize >
                view.findViewById<TextView>(R.id.widget_total).textSize)
            assertTrue(rows.getChildAt(4).bottom <= rows.height)
            screenshot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
            }
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
