package ca.monwallet.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import ca.monwallet.app.widgets.WidgetChartData
import ca.monwallet.app.data.Catalog
import ca.monwallet.app.widgets.WidgetWideRenderer
import ca.monwallet.app.marketdata.MarketSession
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TestMonWallet::class)
class Widget4x2LayoutTest {
    @Test fun titlePricesAndPercentsShareLeftAlignedColumnsEvenWithPreMarket() {
        val regular = WidgetWideRenderer.titleSummary("XEQT", "46,20", BigDecimal("0.54"),
            true, true, null).toString()
        val pre = WidgetWideRenderer.titleSummary("TSM", "472,50", BigDecimal("-2.03"),
            true, true, MarketSession.PRE_MARKET).toString()
        val large = WidgetWideRenderer.titleSummary("ASML", "1 859,86", BigDecimal("1.08"),
            true, true, MarketSession.AFTER_HOURS).toString()
        assertEquals(6, regular.indexOf("46,20"))
        assertEquals(6, pre.indexOf("472,50"))
        assertEquals(6, large.indexOf("1 859,86"))
        assertEquals(16, regular.indexOf("+0,54"))
        assertEquals(16, pre.indexOf("-2,03"))
        assertEquals(16, large.indexOf("+1,08"))
        assertTrue(pre.indexOf("☀") > pre.indexOf("-2,03"))
    }

    @Test fun previewCanInstallBesideTheExistingRelease() {
        assertEquals("ca.monwallet.app.preview2", BuildConfig.APPLICATION_ID)
    }

    @Test fun chartFallbackUsesOnlyRealHistoryAndNeverFabricatesPoints() {
        val week = listOf(102f, 103f)
        val month = listOf(98f, 99f, 104f)
        assertEquals(listOf(100f, 101f), WidgetChartData.choose(
            listOf(100f, 101f), week, month))
        assertEquals(week, WidgetChartData.choose(emptyList(), week, month))
        assertEquals(month, WidgetChartData.choose(emptyList(), listOf(102f), month))
        assertTrue(WidgetChartData.choose(emptyList(), emptyList(), emptyList()).isEmpty())
    }

    @Test fun indexTilesAreBackedByMarketSymbols() {
        assertEquals(setOf("^GSPC", "^IXIC", "^DJI"),
            Catalog.markets.map { it.symbol }
                .filter { it in setOf("^GSPC", "^IXIC", "^DJI") }.toSet())
    }

    @Test fun fourByTwoKeepsDailyHeroAndTotalBesideFiveTitles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val inflater = LayoutInflater.from(context)
        val widget = inflater.inflate(R.layout.wallet_widget_wide, null)
        widget.findViewById<TextView>(R.id.widget_total).text = "+0,52 %"
        widget.findViewById<TextView>(R.id.widget_total_label).text = "Rendement total"
        widget.findViewById<TextView>(R.id.widget_amount).text = "+48,36 $"
        widget.findViewById<TextView>(R.id.widget_percent).text = "+0,74 %"
        val rows = widget.findViewById<LinearLayout>(R.id.widget_rows)
        repeat(5) { rows.addView(inflater.inflate(R.layout.wallet_widget_wide_row, rows, false)) }
        val chart = widget.findViewById<View>(R.id.widget_chart)
        chart.visibility = View.GONE
        val px = context.resources.displayMetrics.density
        val width = (360 * px).toInt()
        val height = (160 * px).toInt()
        widget.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        widget.layout(0, 0, width, height)
        val total = widget.findViewById<View>(R.id.widget_total)
        val titleRows = widget.findViewById<View>(R.id.widget_rows)
        val footer = widget.findViewById<View>(R.id.widget_footer)
        val indices = widget.findViewById<View>(R.id.widget_indices)
        assertTrue(total.width > 0)
        assertTrue(titleRows.width > 0)
        assertTrue(total.right < titleRows.left)
        val hero = widget.findViewById<View>(R.id.widget_day_pill)
        assertTrue(hero.height > total.height)
        assertTrue(indices.height > 0)
        assertTrue(indices.bottom <= titleRows.bottom)
        assertTrue(rows.getChildAt(4).bottom <= rows.height)
        assertTrue(titleRows.bottom <= footer.top)
        assertTrue(footer.bottom <= height)
    }
}
