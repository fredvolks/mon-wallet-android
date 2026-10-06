package ca.monwallet.app

import android.content.Intent
import android.content.ContentValues
import android.graphics.Bitmap
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

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
        val symbols = listOf("AAPL" to "NASDAQ", "NVDA" to "NASDAQ", "TSM" to "NYSE")
        val securities = symbols.map { (symbol, exchange) ->
            Security.of(symbol, symbol, exchange, "USD")
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
        Log.i("WatchlistNetworkTest", "AAPL/NVDA/TSM extended prints: $visible; " +
            "provider status: ${services.marketStatus.value}")

        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val device = UiDevice.getInstance(instrumentation)
        val tab = device.wait(Until.findObject(By.text(context.getString(R.string.nav_watchlist))), 20_000)
        assertNotNull("Watchlist tab missing", tab)
        tab!!.click()
        assertTrue("AAPL row missing", device.wait(Until.hasObject(By.text("AAPL")), 20_000))
        if (visible > 0) {
            assertTrue("Provider returned PRE/AFTER but no UI line is visible",
                device.wait(Until.hasObject(By.textContains("PRE")), 5_000) ||
                    device.wait(Until.hasObject(By.textContains("AFTER")), 5_000))
        }
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "watchlist-real.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MonWallet")
        }
        val uri = requireNotNull(context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        requireNotNull(context.contentResolver.openOutputStream(uri)).use {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()
        Log.i("WatchlistNetworkTest", "Screenshot: $uri; extended=$visible")
        Unit
    }
}
