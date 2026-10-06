package ca.monwallet.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import ca.monwallet.app.widgets.WidgetChartData
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TestMonWallet::class)
class Widget4x2LayoutTest {
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
        assertTrue(total.width > 0)
        assertTrue(titleRows.width > 0)
        assertTrue(total.right < titleRows.left)
        val hero = widget.findViewById<View>(R.id.widget_day_pill)
        assertTrue(hero.height > total.height)
        assertTrue(rows.getChildAt(4).bottom <= rows.height)
        assertTrue(titleRows.bottom <= footer.top)
        assertTrue(footer.bottom <= height)
    }
}
