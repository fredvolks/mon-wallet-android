package ca.monwallet.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import ca.monwallet.app.domain.Holding
import ca.monwallet.app.domain.Result
import ca.monwallet.app.domain.ZERO
import ca.monwallet.app.domain.ONE
import ca.monwallet.app.widgets.WidgetSettings
import ca.monwallet.app.widgets.WidgetTitleLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TestMonWallet::class)
class WidgetTitleCountTest {
    @Test fun automaticAndManualCountsRespondToHeight() {
        val automatic = WidgetSettings(titleCount = 0, chart = true, style = "Mixte premium")
        assertEquals(5, WidgetTitleLayout.plan(automatic, "Widget4x2", 150, 6).visible)
        assertEquals(3, WidgetTitleLayout.plan(automatic, "Widget4x2", 130, 6).visible)
        assertEquals(4, WidgetTitleLayout.plan(automatic, "Widget4x2", 140, 6).visible)
        assertEquals(6, WidgetTitleLayout.plan(automatic, "Widget4x3", 240, 6).visible)
        assertEquals(3, WidgetTitleLayout.plan(automatic, "Widget2x3", 180, 6).visible)
        assertEquals(0, WidgetTitleLayout.plan(automatic, "Widget2x2", 100, 6).visible)
        val six = WidgetTitleLayout.plan(automatic.copy(titleCount = 6), "Widget4x2", 172, 6)
        assertEquals(6, six.visible)
        assertEquals(WidgetTitleLayout.Density.ULTRA, six.density)
        assertTrue(six.footer)
        val five = WidgetTitleLayout.plan(automatic.copy(titleCount = 5), "Widget4x2", 150, 6)
        assertEquals(WidgetTitleLayout.Density.COMPACT, five.density)
        assertFalse(five.chart)
        assertTrue(five.footer)
        assertTrue(WidgetTitleLayout.plan(automatic.copy(titleCount = 3), "Widget4x2", 160, 6).chart)
    }

    @Test fun selectedOrderSkipsSoldHoldingAndFillsWithNextSelection() {
        val order = listOf("XEQT", "TSM", "GURU", "MCD", "DOL", "PHOS")
        fun holding(id: String, quantity: java.math.BigDecimal = ONE) = Holding(
            id, quantity, ONE, ONE, ZERO, ZERO, ONE, ZERO, ZERO, ZERO, ONE)
        val result = Result(order.map { holding(it, if (it == "MCD") ZERO else ONE) },
            ONE, ZERO, ONE, ZERO, ZERO, ZERO, ONE)
        val settings = WidgetSettings(custom = true, titleIds = order, titleCount = 5)
        val visible = settings.titles(result).take(
            WidgetTitleLayout.plan(settings, "Widget4x2", 150, settings.titles(result).size).limit)
        assertEquals(listOf("XEQT", "TSM", "GURU", "DOL", "PHOS"),
            visible.map { it.securityId })
    }

    @Test fun fiveAndSixRowsFitBesideTheTotalAtMinimumWidgetHeight() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val inflater = LayoutInflater.from(context)
        for ((count, layout, widgetHeight) in listOf(
            Triple(5, R.layout.wallet_widget_wide_row, 150),
            Triple(6, R.layout.wallet_widget_wide_row_ultra, 172))) {
            val widget = inflater.inflate(R.layout.wallet_widget_wide, null)
            widget.findViewById<TextView>(R.id.widget_total).text = "+0,52 %"
            widget.findViewById<TextView>(R.id.widget_total_label).text = "Rendement total"
            widget.findViewById<TextView>(R.id.widget_amount).text = "+48,36 $"
            widget.findViewById<TextView>(R.id.widget_percent).text = "+0,74 %"
            val rows = widget.findViewById<LinearLayout>(R.id.widget_rows)
            repeat(count) { index ->
                val row = inflater.inflate(layout, rows, false)
                row.findViewById<TextView>(R.id.widget_row_ticker).text =
                    listOf("XEQT", "TSM", "GURU", "PHOS", "MCD", "DOL")[index]
                row.findViewById<TextView>(R.id.widget_row_price).text = "485,80 USD"
                row.findViewById<TextView>(R.id.widget_row_values).text = "+0,54 %"
                if (index == 1) {
                    row.findViewById<TextView>(R.id.widget_row_session).apply {
                        text = "☀"; visibility = View.VISIBLE
                    }
                }
                rows.addView(row)
            }
            widget.findViewById<View>(R.id.widget_chart).visibility = View.GONE
            widget.findViewById<View>(R.id.widget_footer).visibility = View.GONE
            val px = context.resources.displayMetrics.density
            val width = (360 * px).toInt()
            val height = (widgetHeight * px).toInt()
            widget.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            widget.layout(0, 0, width, height)
            assertEquals(count, rows.childCount)
            assertTrue(widget.findViewById<View>(R.id.widget_total).width > 0)
            assertTrue(widget.findViewById<View>(R.id.widget_total).right < rows.left)
            val amount = widget.findViewById<android.widget.TextView>(R.id.widget_amount)
            val total = widget.findViewById<android.widget.TextView>(R.id.widget_total)
            assertTrue(amount.textSize > total.textSize)
            assertTrue(rows.getChildAt(count - 1).bottom <= rows.height)
            for (index in 0 until count) {
                val row = rows.getChildAt(index)
                for (cell in listOf(R.id.widget_row_ticker, R.id.widget_row_price,
                    R.id.widget_row_values)) {
                    val text = row.findViewById<TextView>(cell)
                    assertTrue("$count rows: ${text.text} clipped", text.paint.measureText(
                        text.text.toString()) <= text.width - text.paddingLeft - text.paddingRight + 1)
                }
            }
            assertTrue(rows.bottom <= height - widget.paddingBottom)
        }
    }
}
