package ca.monwallet.app

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PremiumScreenerTest {
    @Test fun realFundCoverageDividendFiltersAndGeneralSearch() = runBlocking {
        val semaphore = Semaphore(3)
        val funds = listOf("VFV", "VEQT", "VGRO", "VDY", "ZSP", "ZEB", "HXS", "QQC", "XEI", "XUU")
        val results = coroutineScope { funds.map { ticker -> async {
            semaphore.withPermit {
                val fund = Security.of("$ticker.TO", ticker, "TSX", "CAD", "ETF")
                ticker to (try { EtfData.fetch(fund) } catch (e: Exception) { throw IllegalStateException("Fund $ticker: ${e.message}", e) }).also { data ->
                    android.util.Log.i("FundCoverage", "$ticker ${data.issuer} ${data.direct.asOf} ${data.direct.positions.size}")
                    assertTrue("$ticker positions", data.direct.positions.isNotEmpty())
                    if (ticker == "HXS" && data.direct.asOf.isBlank()) assertTrue(data.compositionNote!!.contains("Date de composition non fournie"))
                    else assertTrue("$ticker date", data.direct.asOf.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
                }
            }
        } }.awaitAll().toMap() }
        val vfv = results.getValue("VFV")
        assertEquals("Vanguard Canada", vfv.issuer)
        assertTrue(vfv.direct.positions.any { it.symbol == "VOO" })
        assertTrue(vfv.underlying!!.positions.any { it.symbol == "NVDA" })
        assertNotNull("VFV management fee", vfv.managementFee)
        assertEquals("Vanguard Canada", results.getValue("VEQT").issuer)
        val us = EtfData.fetch(Security.of("QQQ", "Invesco QQQ", "NASDAQ", "USD", "ETF"))
        assertTrue(us.direct.positions.isNotEmpty())
        val bank = Security.of("NA.TO", "Banque Nationale du Canada", "TSX", "CAD")
        val dividend = DividendData.fetch(bank)
        assertTrue(dividend.annualAmount > ZERO)
        assertTrue(dividend.yield!! > ZERO)
        assertEquals("QUARTERLY", dividend.frequency)
        val quote = Yahoo().quote(bank)
        val row = DiscoveryRow(bank, quote.price, null, null, emptyList(), null, emptyMap(), null, null,
            "Financial Services", "", dividend.yield, emptyMap(), dividend = dividend)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val services = Services(context)
        assertTrue("Remote search outside local catalog", services.market.search("NWC.TO").any { it.symbol == "NWC.TO" })
        ReportsTestActivity.content = {
            WalletTheme {
                Surface(color = Night) {
                    var mode by remember { mutableIntStateOf(0) }
                    var filters by remember { mutableStateOf(dividendPreset().copy(dividendMin = null)) }
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(10.dp)) {
                        Row {
                            listOf("Scan", "Critères", "VFV", "Recherche").forEachIndexed { i, text ->
                                TextButton(onClick = { mode = i }) { Text(text) }
                            }
                        }
                        when (mode) {
                            0 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                                ScreenerHero(1, false, filters)
                                ScreenerStockCard(row, quote, PerformancePeriod.M3) {}
                            }
                            1 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                                Text("Minimum actif : ${filters.dividendMin}")
                                DividendFilterPanel(filters) { filters = it }
                            }
                            2 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                                EtfDetailsCard(Security.of("VFV.TO", "Vanguard S&P 500 Index ETF", "TSX", "CAD", "ETF"),
                                    load = { _, _ -> EtfLoad(vfv) })
                            }
                            3 -> SearchDialog(services, Wallet(), { mode = 0 }) {}
                        }
                    }
                }
            }
        }
        val device = UiDevice.getInstance(instrumentation)
        fun dismissLauncher() {
            if (device.hasObject(By.text("Quickstep isn't responding"))) {
                device.findObject(By.res("android:id/aerr_close"))?.click(); device.waitForIdle()
            }
        }
        fun shown(text: String) {
            dismissLauncher()
            assertNotNull(text, device.wait(Until.findObject(By.clazz("android.widget.TextView").text(text)), 15_000))
        }
        fun click(text: String) {
            dismissLauncher()
            (device.wait(Until.findObject(By.text(text)), 15_000) ?: error("Missing $text")).click()
            device.waitForIdle()
        }
        fun capture(name: String) {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "reports-$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MonWallet")
            })!!
            context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        ActivityScenario.launch<ReportsTestActivity>(Intent(context, ReportsTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)).use {
            shown("NA"); capture("screener-card")
            click("Critères"); shown("Revenu et dividendes")
            click("≥ 5 %"); shown("Minimum actif : 5")
            capture("screener-filters")
            click("VFV"); shown("VOO"); capture("vfv-detail")
            click("Sous-jacents"); shown("NVDA"); capture("vfv-underlying")
            click("Recherche")
            for ((query, expected) in listOf("NA" to "NA", "MSFT" to "MSFT", "VFV.TO" to "VFV",
                "Banque de Montreal" to "BMO", "Microsft" to "MSFT", "NVDA" to "NVDA")) {
                val edit = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000)!!
                edit.text = query
                shown(expected)
                if (query == "NA") capture("search-na")
                if (query == "NVDA") capture("search-general")
            }
        }
        services.scope.cancel()
        ReportsTestActivity.content = null
    }
}
