package ca.monwallet.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import ca.monwallet.app.domain.*
import ca.monwallet.app.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FamilyInvestmentsTest {
    @Test fun memberSaleAllSharesAndConfirmedExcessPersistWithoutInventedProfit() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val services = Services(context)
        val owner = "family-ui-" + uuid()
        val previousOwner = services.repo.owner.value
        val p = Portfolio(name = "Compte famille test")
        val security = Security("family-ui-" + uuid(), "XEQT.TO", "iShares Core Equity ETF", "TSX", "CAD", "ETF")
        try {
            services.repo.switch(owner)
            services.repo.put("portfolio", p.id, p)
            services.repo.setting("family_member_jacob", "Jacob")
            val personal = Transaction(portfolioId = p.id, securityId = security.id, quantity = "10".dec(),
                price = "45".dec(), date = "2026-10-01")
            val jacob = personal.copy(id = uuid(), quantity = "3".dec(), memberId = "jacob")
            services.repo.transaction(personal, security)
            services.repo.transaction(jacob, security)
            services.repo.quote(Quote(security.id, "47".dec(), "46".dec(), "CAD", System.currentTimeMillis(),
                MarketClock.date(System.currentTimeMillis()).toString(), "Fixture Android"))
            withTimeout(10_000) { services.repo.state.first { it.transactions.size == 2 && it.memberName("jacob") == "Jacob" } }
            ReportsTestActivity.content = { WalletTheme { FamilyDialog(services, p.id, {}) } }
            val intent = Intent(context, ReportsTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ActivityScenario.launch<ReportsTestActivity>(intent).use { scenario ->
                val device = UiDevice.getInstance(instrumentation)
                fun clearLauncher() {
                    if (device.hasObject(By.text("Quickstep isn't responding")))
                        device.findObject(By.res("android:id/aerr_close"))?.click()
                }
                fun find(text: String): androidx.test.uiautomator.UiObject2 {
                    clearLauncher()
                    repeat(15) {
                        device.findObject(By.textContains(text))?.let { return it }
                        device.swipe(device.displayWidth / 2, device.displayHeight * 78 / 100,
                            device.displayWidth / 2, device.displayHeight * 36 / 100, 25)
                    }
                    error("Missing family UI: " + text)
                }
                assertTrue(device.wait(Until.hasObject(By.text("Jacob")), 10_000))
                device.findObject(By.text("Jacob")).click()
                instrumentation.waitForIdleSync()
                capture(context, "reports-family.png")
                find("Vendre ce titre").click()
                assertTrue(device.wait(Until.hasObject(By.textContains("Tout vendre")), 10_000))
                capture(context, "reports-family-sale.png")
                device.findObject(By.textContains("Tout vendre")).click()
                instrumentation.waitForIdleSync()
                val fields = device.findObjects(By.clazz("android.widget.EditText"))
                assertTrue(fields.size >= 2)
                assertTrue(fields[0].text.contains("3"))
                fields[0].text = "5"
                fields[1].text = "48"
                scenario.onActivity { activity -> activity.window.insetsController?.hide(android.view.WindowInsets.Type.ime()) }
                find("Vendre des parts").click()
                assertTrue(device.wait(Until.hasObject(By.text("Enregistrer quand même")), 10_000))
                capture(context, "reports-family-warning.png")
                device.findObject(By.text("Enregistrer quand même")).click()
                withTimeout(10_000) { services.repo.state.first { w -> w.transactions.any { t -> t.type == TxType.SELL } } }
                val saved = services.repo.current()
                val sale = saved.transactions.single { t -> t.type == TxType.SELL }
                assertEquals("jacob", sale.memberId)
                assertEquals(true, sale.allowUnmatchedSale)
                assertEquals(1, FamilyLedger.resolve(saved.transactions).pending.size)
                assertNull(saved.result(p.id).value)
                assertTrue(TradeReports.sales(saved.transactions).isEmpty())
                services.repo.transaction(jacob.copy(id = uuid(), quantity = "2".dec(),
                    date = "2026-09-30", fxDate = "2026-09-30"), security)
                assertTrue(FamilyLedger.resolve(services.repo.current().transactions).pending.isEmpty())
                assertEquals(0, FamilyLedger.availableAt(services.repo.current().transactions, p.id,
                    security.id, "jacob", "2026-10-23").compareTo(ZERO))
                assertEquals(0, FamilyLedger.availableAt(services.repo.current().transactions, p.id,
                    security.id, null, "2026-10-23").compareTo("10".dec()))
            }
        } finally {
            ReportsTestActivity.content = {}
            services.repo.dao.clear(owner)
            services.repo.switch(previousOwner)
            services.scope.cancel()
            services.repo.db.close()
        }
    }
    private fun capture(context: Context, name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MonWallet")
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        requireNotNull(context.contentResolver.openOutputStream(uri)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
