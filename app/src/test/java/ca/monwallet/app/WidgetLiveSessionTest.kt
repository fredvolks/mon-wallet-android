package ca.monwallet.app

import ca.monwallet.app.widgets.WidgetLiveService
import ca.monwallet.app.marketdata.OfficialLogoProvider
import ca.monwallet.app.domain.Security
import kotlinx.coroutines.runBlocking
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class WidgetLiveSessionTest {
    @Test fun noPollingWhenScreenIsOffOrKeyguardIsLocked() {
        assertTrue(WidgetLiveService.screenAllowsRefresh(true, false))
        assertFalse(WidgetLiveService.screenAllowsRefresh(false, false))
        assertFalse(WidgetLiveService.screenAllowsRefresh(true, true))
        assertFalse(WidgetLiveService.screenAllowsRefresh(false, true))
    }
    @Test fun heldIssuerLogosLoadOfflineFromBundledImages() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for ((symbol, exchange, currency) in listOf(
            Triple("XEQT.TO", "TSX", "CAD"), Triple("GURU.TO", "TSX", "CAD"),
            Triple("TSM", "NYSE", "USD"), Triple("PHOS.CN", "CSE", "CAD"),
            Triple("MCD.TO", "TSX", "CAD"), Triple("SPCX", "NASDAQ", "USD"))) {
            val security = Security.of(symbol, symbol, exchange, currency,
                if (symbol == "XEQT.TO") "ETF" else "STOCK")
            val logo = OfficialLogoProvider.load(context, security)
            assertNotNull("No offline logo for $symbol", logo)
            assertTrue(logo!!.width >= 16 && logo.height >= 16)
        }
        Unit
    }
}
