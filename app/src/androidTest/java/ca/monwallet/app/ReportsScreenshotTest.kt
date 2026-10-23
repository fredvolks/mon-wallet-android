package ca.monwallet.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import ca.monwallet.app.domain.*
import ca.monwallet.app.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/** Pure UI fixture, never written into the user's repository/database. */
@RunWith(AndroidJUnit4::class)
class ReportsScreenshotTest {
    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun reportsShowDailyWalletClosedTradeTransactionsAndExportPeriods() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        Locale.setDefault(Locale.CANADA_FRENCH)
        val dol = Security.of("DOL.TO", "Dollarama", "TSX", "CAD")
        val xeqt = Security.of("XEQT.TO", "iShares Core Equity ETF", "TSX", "CAD", "ETF")
        val p = Portfolio(id = "report-preview", name = "Disnat")
        fun tx(id: String, security: Security, type: TxType, quantity: String, price: String, date: String) =
            Transaction(id = id, portfolioId = p.id, securityId = security.id, type = type,
                quantity = quantity.dec(), price = price.dec(), date = date)
        val transactions = listOf(tx("buy-dol", dol, TxType.BUY, "10", "100", "2026-10-08"),
            tx("buy-xeqt", xeqt, TxType.BUY, "100", "80", "2026-10-08"),
            tx("sell-dol", dol, TxType.SELL, "10", "113", "2026-10-22"))
        val dates = generateSequence(LocalDate.parse("2026-10-08")) { it.plusDays(1) }
            .takeWhile { it <= LocalDate.parse("2026-10-22") }.filter { it.dayOfWeek.value <= 5 }.toList()
        val prices = dates.flatMapIndexed { i, day ->
            val ratio = java.math.BigDecimal(i).divide(java.math.BigDecimal(dates.size - 1), MC)
            listOf(Point(dol.id, day.toString(), "100".dec() + "13".dec() * ratio, 0),
                Point(xeqt.id, day.toString(), if (day.toString() == "2026-10-22") "82.1836".dec()
                    else "80".dec() + "2".dec() * ratio, 0))
        }
        val wallet = Wallet(portfolios = listOf(p), securities = listOf(dol, xeqt),
            transactions = transactions, prices = prices)
        val history = ReportEngine.history(wallet, p.id)
        val now = LocalDateTime.parse("2026-10-23T08:06:00").atZone(MarketClock.zone).toInstant().toEpochMilli()
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                WalletTheme {
                    Scaffold(topBar = { TopAppBar(title = { Text("Rapports") },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Night)) },
                        bottomBar = {
                            Surface(color = Night) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly) {
                                    listOf("Portefeuille", "Marchés", "Watchlist", "Découvrir", "Rapports", "Profil")
                                        .forEach { Text(it, fontSize = 9.sp, color = if (it == "Rapports") Green else Muted) }
                                }
                            }
                        }) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            ReportsDashboard(wallet, history, p.id, now = now)
                        }
                    }
                }
            } }
            val device = UiDevice.getInstance(instrumentation)
            fun shown(text: String) = assertTrue("Missing UI: $text",
                device.wait(Until.hasObject(By.textContains(text)), 15_000))
            shown("Mon wallet par jour")
            shown("348,36") // French-Canada grouping is a non-breaking space.
            capture(context, "reports-summary.png")
            device.findObject(By.text("Calendrier")).click()
            shown("octobre 2026")
            val dayReturn = device.wait(Until.findObject(By.text("0,55%")), 5_000)
            assertNotNull("Calendar return must be visible", dayReturn)
            assertTrue("Calendar percentage clipped vertically",
                dayReturn!!.visibleBounds.height() >=
                    (10 * context.resources.displayMetrics.scaledDensity).toInt())
            capture(context, "reports-calendar.png")
            device.findObject(By.text("22")).click()
            shown("Transactions du jour")
            shown("DOL")
            capture(context, "reports-day-detail.png")
            device.findObject(By.text("Fermer")).click()
            device.findObject(By.text("Trades clôturés")).click()
            shown("+13,00 %")
            shown("8 oct. 2026")
            shown("22 oct. 2026")
            capture(context, "reports-closed-trades.png")
            device.findObject(By.text("Transactions")).click()
            shown("Vente")
            shown("DOL")
            capture(context, "reports-transactions.png")
            device.findObject(By.text("CSV ↓")).click()
            shown("Exporter mes rapports")
            shown("Wallet jour par jour")
            capture(context, "reports-export.png")
            device.findObject(By.textContains("→")).click()
            shown("Choisir la période")
            device.findObject(By.text("Mois")).click()
            shown("Mois : octobre")
            capture(context, "reports-export-month.png")
            device.findObject(By.text("Année")).click()
            shown("Année : 2026")
            device.findObject(By.text("Appliquer la période")).click()
            shown("31 déc. 2026")
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
        requireNotNull(context.contentResolver.openOutputStream(uri)).use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
