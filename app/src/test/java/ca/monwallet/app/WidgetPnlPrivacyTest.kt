package ca.monwallet.app

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import ca.monwallet.app.domain.*
import ca.monwallet.app.widgets.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TestMonWallet::class)
class WidgetPnlPrivacyTest {
    @Test fun hidesBothDollarAmountsAndRestoresWithoutChangingPercentages() = runBlocking<Unit> {
        val c = ApplicationProvider.getApplicationContext<Context>()
        val id = 987
        WidgetPnlPrivacy.clear(c, id)
        val stock = Security.of("TSM", "TSM", "NYSE", "USD")
        val fx = Security.of("CAD=X", "USD/CAD", "FX", "CAD")
        val now = LocalDateTime.parse("2026-10-23T18:00:00")
            .atZone(ZoneId.of("America/New_York")).toInstant().toEpochMilli()
        val wallet = Wallet(securities = listOf(stock, fx), transactions = listOf(
            Transaction(portfolioId = "p", securityId = stock.id, quantity = "10".dec(),
                price = "90".dec(), currency = "USD", date = "2026-10-20")), quotes = mapOf(
            stock.id to Quote(stock.id, "100".dec(), "98".dec(), "USD", now - 7200000,
                "2026-10-23", "fixture", afterHoursPrice = "103".dec(), afterHoursTimestamp = now),
            fx.id to Quote(fx.id, ONE, ONE, "CAD", now, "2026-10-23", "fixture")))
        val settings = WidgetSettings(showTitles = false, logo = false)
        suspend fun render() = WidgetWideRenderer.render(c, id, null, settings, wallet,
            "p", null, false, emptyList(), 334 to 180, now).apply(c, null)
        val before = render()
        val amount = before.findViewById<TextView>(R.id.widget_amount).text.toString()
        val after = before.findViewById<TextView>(R.id.widget_after).text.toString()
        val percent = before.findViewById<TextView>(R.id.widget_percent).text.toString()
        assertTrue(amount.contains("20"))
        assertTrue(after.contains("30"))
        WidgetPnlPrivacy.toggle(c, id)
        assertTrue(WidgetPnlPrivacy.isHidden(c, id))
        assertFalse(WidgetPnlPrivacy.isHidden(c, id + 1))
        val masked = render()
        assertEquals("••• $", masked.findViewById<TextView>(R.id.widget_amount).text.toString())
        val maskedAfter = masked.findViewById<TextView>(R.id.widget_after)
        assertEquals(View.VISIBLE, maskedAfter.visibility)
        assertTrue(maskedAfter.text.contains("AFTER · ••• $"))
        assertFalse(maskedAfter.text.contains("30,00"))
        assertEquals(percent, masked.findViewById<TextView>(R.id.widget_percent).text.toString())
        assertEquals("Afficher les montants du jour et AFTER",
            masked.findViewById<View>(R.id.widget_toggle_pnl).contentDescription)
        WidgetPnlPrivacy.toggle(c, id)
        val restored = render()
        assertEquals(amount, restored.findViewById<TextView>(R.id.widget_amount).text.toString())
        assertEquals(after, restored.findViewById<TextView>(R.id.widget_after).text.toString())
        WidgetPnlPrivacy.toggle(c, id)
        WidgetSettings.delete(c, id)
        assertFalse(WidgetPnlPrivacy.isHidden(c, id))
    }
}
