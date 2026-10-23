package ca.monwallet.app

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.*
import ca.monwallet.app.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EtfDetailsTest {
    @Test fun issuerCompositionAndClickableAllocation() = runBlocking {
        val fund = Security.of("XEQT.TO", "iShares Core Equity ETF Portfolio", "TSX", "CAD", "ETF")
        val data = EtfData.fetch(fund)
        assertTrue(data.direct.positions.any { it.symbol == "XIC" })
        assertTrue(data.underlying!!.positions.size > 1000)
        assertTrue(data.underlying.positions.any { it.symbol == "TSM" || it.name.contains("TAIWAN SEMICONDUCTOR") })
        assertNotNull(data.mer)
        assertTrue(data.direct.asOf.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        val stock = Security.of("GURU.TO", "GURU Energy", "TSX", "CAD")
        val p = Portfolio(id = "etf-test", name = "Disnat")
        fun holding(s: Security, v: String) = Holding(s.id, "10".dec(), "10".dec(), "10".dec(),
            ZERO, ZERO, v.dec(), ZERO, ZERO, ZERO, ZERO)
        val result = ca.monwallet.app.domain.Result(listOf(holding(fund, "5382"), holding(stock, "4918.76")),
            "10000".dec(), ZERO, "10300.76".dec(), ZERO, ZERO, null, null)
        val wallet = Wallet(portfolios = listOf(p), securities = listOf(fund, stock))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ReportsTestActivity.content = {
            WalletTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                    ReportAllocation(wallet, p.id, result) { _, _ -> EtfLoad(data) }
                }
            }
        }
        val device = UiDevice.getInstance(instrumentation)
        fun dismissLauncher() {
            if (device.hasObject(By.text("Quickstep isn't responding"))) {
                device.findObject(By.res("android:id/aerr_close"))?.click()
                device.waitForIdle()
            }
        }
        fun click(text: String) {
            dismissLauncher()
            val node = device.wait(Until.findObject(By.text(text)), 15_000)
            if (node == null) { dismissLauncher() }
            (node ?: device.wait(Until.findObject(By.text(text)), 10_000)
                ?: error("Missing $text")).click()
            device.waitForIdle()
        }
        fun capture(name: String) {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MonWallet") })!!
            context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        ActivityScenario.launch<ReportsTestActivity>(Intent(context, ReportsTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)).use {
            dismissLauncher()
            assertNotNull(device.wait(Until.findObject(By.text("● FNB / ETF")), 15_000))
            assertNotNull(device.findObject(By.text("● Actions")))
            capture("reports-etf-allocation.png")
            // Scroll down the allocation card to the fund action if necessary.
            repeat(3) {
                if (!device.hasObject(By.text("XEQT · Composition et pondérations  ›")))
                    device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                        device.displayWidth / 2, device.displayHeight / 3, 20)
            }
            click("XEQT · Composition et pondérations  ›")
            assertNotNull(device.wait(Until.findObject(By.text("Frais de gestion")), 15_000))
            capture("reports-etf-detail.png")
            click("Sous-jacents")
            assertNotNull(device.wait(Until.findObject(By.text("NVDA")), 10_000))
            capture("reports-etf-underlying.png")
            click("Secteurs")
            assertNotNull(device.wait(Until.findObject(By.text("Information Technology")), 10_000))
            click("Pays")
            assertNotNull(device.wait(Until.findObject(By.text("United States")), 10_000))
            click("Fermer")
        }
        ReportsTestActivity.content = null
    }
}
