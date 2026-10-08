package ca.monwallet.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.*
import ca.monwallet.app.widgets.WidgetSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TestMonWallet::class)
class WidgetAndFinancialRoutingTest {
    @Test fun symbolsRouteToExactExchange() {
        listOf("DOL.TO", "GURU.TO", "BLDP.TO", "XEQT.TO").forEach {
            val type = if (it == "XEQT.TO") "ETF" else "STOCK"
            assertEquals("CANADA", FinancialSymbolResolver.resolve(
                Security.of(it, it, "TSX", "CAD", type))?.market)
        }
        listOf("AAPL" to "NASDAQ", "MSFT" to "NASDAQ",
            "NVDA" to "NASDAQ", "TSM" to "NYSE", "MCD" to "NYSE").forEach { (s, e) ->
            assertEquals("US", FinancialSymbolResolver.resolve(Security.of(s, s, e, "USD"))?.market)
        }
        assertNull(FinancialSymbolResolver.resolve(Security.of("BLDP.TO", "Ballard", "NASDAQ", "USD")))
        assertEquals("US", FinancialSymbolResolver.resolve(
            Security.of("BLDP", "Ballard", "NASDAQ", "USD").copy(country = "Canada"))?.market)
    }

    @Test fun canadaNeverCallsUsSources() = runBlocking {
        val called = mutableListOf<String>()
        fun fake(name: String) = object : FundamentalsProvider {
            override val name = name
            override suspend fun load(security: Security): Fundamentals? {
                called.add(name)
                return Fundamentals(mapOf("P/E" to "20"), source = name)
            }
        }
        val s = Security.of("DOL.TO", "Dollarama", "TSX", "CAD")
        assertEquals("Canada", FinancialSourceRouter(fake("Canada"), fake("Finviz"),
            fake("SEC"), fake("Nasdaq")).load(s).source)
        assertEquals(listOf("Canada"), called)
        try {
            FinancialSourceRouter(null, null, fake("SEC"), fake("Nasdaq")).load(s)
            fail("No licensed Canadian source")
        } catch (_: NoFinancialCoverage) { }
    }

    @Test fun settingsBelongToTheirWidgetId() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        WidgetSettings.save(c, 101, WidgetSettings(portfolio = "disnat", custom = true,
            titleIds = listOf("xeqt", "tsm"), hideAmounts = true), "guest")
        WidgetSettings.save(c, 102, WidgetSettings(style = "Résumé"), "guest")
        assertEquals(listOf("xeqt", "tsm"), WidgetSettings.load(c, 101).titleIds)
        assertTrue(WidgetSettings.load(c, 101).hideAmounts)
        WidgetSettings.delete(c, 101)
        assertTrue(WidgetSettings.load(c, 102).totalPercent)
        assertEquals("Résumé", WidgetSettings.load(c, 102).style)
    }
}

