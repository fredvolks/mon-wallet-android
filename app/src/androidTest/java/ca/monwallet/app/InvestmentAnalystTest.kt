package ca.monwallet.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import android.content.ClipboardManager
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
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InvestmentAnalystTest {
    @Test fun datedResearchShowsUnknownCoverageAndCopyExcludesMemberIdentity() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("portfolio_analyst", 0).edit().putString("sector_cap", "35").commit()
        val services = Services(context)
        val previous = services.repo.owner.value
        val owner = "analyst-ui-" + uuid()
        val p = Portfolio(name = "Compte test analyste")
        val stock = Security("analyst-stock-" + uuid(), "DEMO.TO", "Société de test", "TSX", "CAD", sector = "Technology", country = "Canada")
        val fund = Security("analyst-fund-" + uuid(), "FIXT.TO", "FNB de test", "TSX", "CAD", "ETF")
        val now = System.currentTimeMillis()
        val date = MarketClock.date(now).toString()
        try {
            services.repo.switch(owner)
            services.repo.put("portfolio", p.id, p)
            services.repo.setting("family_member_jacob", "Jacob")
            services.repo.transaction(Transaction(portfolioId = p.id, type = TxType.DEPOSIT, price = "1000".dec(), date = "2026-10-01"), null)
            services.repo.transaction(Transaction(portfolioId = p.id, securityId = stock.id, quantity = "3".dec(), price = "100".dec(), date = "2026-10-01"), stock)
            services.repo.transaction(Transaction(portfolioId = p.id, securityId = fund.id, quantity = "10".dec(), price = "50".dec(), date = "2026-10-01"), fund)
            services.repo.quote(Quote(stock.id, "100".dec(), "99".dec(), "CAD", now, date, "Fixture Android"))
            services.repo.quote(Quote(fund.id, "50".dec(), "49".dec(), "CAD", now, date, "Fixture Android"))
            withTimeout(10000) { services.repo.state.first { it.transactions.size == 3 && it.quotes[fund.id] != null } }
            val wallet = services.repo.current()
            val detail = EtfDetails("FNB de test", "Émetteur test", "https://example.com",
                FundComposition("2026-09-30", emptyList()), null, null, null, null, null,
                sectorExposure = FundExposure("2026-09-30", mapOf("Technology" to "50".dec(), "Financials" to "20".dec())))
            val exposure = PortfolioResearch.build(wallet.result(), wallet.securities, mapOf(fund.id to detail))
            val stockResearch = ResearchStock(stock, wallet.quotes[stock.id],
                Fundamentals(mapOf("P/E" to "20", "Dette / capitaux propres" to "1", "Capitalisation (M)" to "2000"), source = "Fixture Android", asOf = date),
                emptyList(), emptyList(), emptyList(), listOf("Historique de tendance incomplet"))
            val research = InvestmentResearch(services)
            val pack = ResearchPack(now, "TITLE", exposure, mapOf(fund.id to detail), listOf(stockResearch),
                emptyList(), listOf(ResearchSource("fixture", "Source de test", "https://example.com", date)),
                "500".dec(), "Canada", "3 à 5 ans", "Modéré", 35, emptyList(),
                ledgerKey = research.ledgerKey(wallet, null, false, null),
                positions = listOf(ResearchPosition(stock.symbol, stock.name, "3".dec(), "300".dec(), "CAD", stock.sector, now)))
            research.save("self:all:TITLE", pack, null)
            ReportsTestActivity.content = { WalletTheme { InvestmentAnalystDialog(services, stock, {}) } }
            val intent = Intent(context, ReportsTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ActivityScenario.launch<ReportsTestActivity>(intent).use { scenario ->
                val device = UiDevice.getInstance(instrumentation)
                fun find(text: String, up: Boolean = false): androidx.test.uiautomator.UiObject2 {
                    if (device.hasObject(By.text("Quickstep isn't responding"))) device.findObject(By.res("android:id/aerr_close"))?.click()
                    repeat(35) {
                        device.findObject(By.textContains(text))?.let { return it }
                        device.swipe(device.displayWidth / 2, device.displayHeight * (if (up) 35 else 78) / 100,
                            device.displayWidth / 2, device.displayHeight * (if (up) 78 else 35) / 100, 20)
                    }
                    error("Missing analyst UI: " + text)
                }
                assertTrue(device.wait(Until.hasObject(By.text("Analyste IA")), 10000))
                find("Équilibre du portefeuille")
                assertNotNull(device.findObject(By.textContains("Valeur totale")))
                capture(context, "reports-analyst-balance.png")
                find("Société de test")
                capture(context, "reports-analyst-title.png")
                find("Copier le dossier pour ChatGPT").click()
                var copied = ""
                scenario.onActivity { activity ->
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    copied = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                }
                assertTrue(copied.contains("Société de test"))
                assertTrue(copied.contains("unknownSector"))
                assertFalse(copied.contains("Jacob"))
                assertFalse(copied.contains("memberId"))
                find("Pour qui?", true).click()
                assertTrue(device.wait(Until.hasObject(By.text("Jacob")), 3000))
                device.findObject(By.text("Jacob")).click()
                instrumentation.waitForIdleSync()
                assertFalse(device.hasObject(By.textContains("Dossier du")))
                capture(context, "reports-analyst-scope.png")
            }
        } finally {
            ReportsTestActivity.content = {}
            services.repo.dao.clear(owner)
            services.repo.switch(previous)
            services.scope.cancel()
            services.repo.db.close()
        }
    }
    @Test fun freeResearchCollectsRealVfvExposureAndDatedSources() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val services = Services(context)
        val previous = services.repo.owner.value
        try {
            services.repo.switch("analyst-network-" + uuid())
            val security = Security.of("VFV.TO", "Vanguard S&P 500 Index ETF", "TSX", "CAD", "ETF")
            val wallet = Wallet(portfolios = listOf(Portfolio(id = "research", name = "Fixture")),
                securities = listOf(security),
                transactions = listOf(Transaction(portfolioId = "research", securityId = security.id,
                    quantity = ONE, price = ONE, date = "2026-10-01")))
            val pack = withTimeout(150_000) { InvestmentResearch(services).collect(wallet, "BALANCE", null, true,
                "research", null, "500".dec(), "Canada", "3 à 5 ans", "Modéré", 35, {}) }
            assertNotNull(pack.exposure.total)
            assertTrue(pack.exposure.total!! > ZERO)
            assertTrue("Actual VFV sector coverage must be available", (pack.exposure.sectorCoverage ?: ZERO) > ZERO)
            assertTrue(pack.exposure.sectorCoverage!! <= "100".dec())
            assertEquals("VFV.TO", pack.positions.single().symbol)
            assertTrue(pack.sources.any { it.url.startsWith("https://www.vanguard.ca/") })
            assertTrue(pack.funds[security.id]?.underlying?.positions?.isNotEmpty() == true)
            android.util.Log.d("InvestmentResearchTest", "VFV total=" + pack.exposure.total + " coverage=" + pack.exposure.sectorCoverage)
        } finally {
            services.repo.switch(previous)
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
