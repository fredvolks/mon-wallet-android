package ca.monwallet.app

import android.content.Intent
import android.content.ContentValues
import android.graphics.Bitmap
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import ca.monwallet.app.domain.Security
import ca.monwallet.app.domain.Watchlist
import ca.monwallet.app.marketdata.NormalizedQuote
import ca.monwallet.app.marketdata.MarketSession
import ca.monwallet.app.ui.number
import ca.monwallet.app.ui.percent
import ca.monwallet.app.ui.signed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** Device screenshot with actual provider requests. No synthetic quotes are written. */
@RunWith(AndroidJUnit4::class)
class WatchlistNetworkScreenshotTest {
    @Test fun captureRealProviderWatchlist() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val services = (context.applicationContext as MonWallet).services
        withTimeout(30_000) { services.initialized.first { it } }
        val list = Watchlist(name = "Séance US")
        services.repo.put("watchlist", list.id, list)
        val symbols = listOf("TSLA" to "NASDAQ", "NVDA" to "NASDAQ",
            "MSFT" to "NASDAQ", "ASML" to "NASDAQ", "VFV.TO" to "TSX", "DOL.TO" to "TSX")
        val securities = symbols.map { (symbol, exchange) ->
            Security.of(symbol, symbol, exchange, if (exchange == "TSX") "CAD" else "USD")
        }
        securities.forEach { services.repo.watch(it, list.id) }
        services.secure.preference("onboarded", "true")
        // refreshForeground can be skipped while a scheduled full refresh owns
        // the busy flag. Exercise each real provider request explicitly here.
        securities.forEach { security ->
            runCatching { services.refreshQuote(security) }
                .onFailure { Log.w("WatchlistNetworkTest", "${security.symbol}: ${it.message}") }
        }
        val quotes = services.repo.current().quotes
        val visible = securities.count { security ->
            quotes[security.id]?.let { q ->
                val normalized = NormalizedQuote.from(q)
                normalized.preMarketPrice != null || normalized.afterHoursPrice != null
            } == true
        }
        Log.i("WatchlistNetworkTest", "US extended prints: $visible; " +
            "provider status: ${services.marketStatus.value}")

        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val device = UiDevice.getInstance(instrumentation)
        val tab = device.wait(Until.findObject(By.text(context.getString(R.string.nav_watchlist))), 20_000)
        if (tab == null) {
            val hierarchy = ByteArrayOutputStream()
            device.dumpWindowHierarchy(hierarchy)
            Log.w("WatchlistNetworkTest", "Navigation absent: " +
                hierarchy.toString(Charsets.UTF_8.name()).take(3500))
            capture(context, "watchlist-diagnostic.png")
        }
        assertNotNull("Watchlist tab missing", tab)
        tab!!.click()
        assertTrue("TSLA row missing", device.wait(Until.hasObject(By.text("TSLA")), 20_000))
        assertFalse("Exchange must be hidden on the minimalist Watchlist",
            device.hasObject(By.text("NASDAQ")) || device.hasObject(By.text("TSX")))
        assertFalse("Cached quotes must not repeat Cache on every Watchlist row",
            device.hasObject(By.text("Cache")))
        if (visible > 0) {
            assertTrue("Provider returned PRE/AFTER but no UI line is visible",
                device.wait(Until.hasObject(By.textContains("PRE")), 5_000) ||
                    device.wait(Until.hasObject(By.textContains("AFTER")), 5_000))
            // Compare actual rendered cell bounds, not separate layout calculations.
            securities.firstNotNullOfOrNull { security ->
                val q = quotes[security.id] ?: return@firstNotNullOfOrNull null
                val extended = NormalizedQuote.from(q)
                val extra = when (extended.marketSession) {
                    MarketSession.PRE_MARKET -> extended.preMarketPrice
                    MarketSession.AFTER_HOURS -> extended.afterHoursPrice
                    else -> null
                } ?: return@firstNotNullOfOrNull null
                val delta = if (extended.marketSession == MarketSession.PRE_MARKET)
                    extended.preMarketChange else extended.afterHoursChange
                val changePercent = if (extended.marketSession == MarketSession.PRE_MARKET)
                    extended.preMarketChangePercent else extended.afterHoursChangePercent
                if (q.change == null || delta == null || q.percent == null ||
                    changePercent == null || q.price == extra || q.percent == changePercent ||
                    q.change == delta)
                    return@firstNotNullOfOrNull null
                listOf(number(q.price) to number(extra),
                    percent(q.percent) to percent(changePercent),
                    signed(q.change, security.currency) to signed(delta, security.currency))
            }?.forEach { (regular, after) ->
                val mainCell = device.wait(Until.findObject(By.text(regular)), 5_000)
                val subCell = device.wait(Until.findObject(By.text(after)), 5_000)
                assertNotNull("Regular value not rendered: $regular", mainCell)
                assertNotNull("Extended value not rendered: $after", subCell)
                assertEquals("Right edges of $regular / $after differ",
                    mainCell!!.visibleBounds.right, subCell!!.visibleBounds.right)
            }
        }
        capture(context, "watchlist-real.png")
        Unit
    }

    private fun capture(context: Context, fileName: String) {
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MonWallet")
        }
        val uri = requireNotNull(context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        requireNotNull(context.contentResolver.openOutputStream(uri)).use {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()
        Log.i("WatchlistNetworkTest", "Screenshot: $uri")
    }
}
