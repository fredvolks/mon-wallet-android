package ca.monwallet.app

import ca.monwallet.app.domain.Security
import ca.monwallet.app.domain.OfficialDomains
import ca.monwallet.app.marketdata.NasdaqAnalystProvider
import ca.monwallet.app.marketdata.ProviderSymbolResolver
import ca.monwallet.app.marketdata.SecFacts
import ca.monwallet.app.marketdata.ResearchSection
import ca.monwallet.app.marketdata.SeekingAlphaResearch
import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TestMonWallet::class)
class FinancialProvidersTest {
    @Test fun seekingAlphaLinksOnlyVerifiedCanadianListings() {
        assertEquals("https://seekingalpha.com/symbol/DOL%3ACA/income-statement",
            SeekingAlphaResearch.url(Security.of("DOL.TO", "Dollarama", "TSX", "CAD"), ResearchSection.FINANCIALS))
        assertEquals("https://seekingalpha.com/symbol/GURU%3ACA/ratings/sell-side-ratings",
            SeekingAlphaResearch.url(Security.of("GURU.TO", "GURU Organic", "Toronto", "CAD"), ResearchSection.ANALYSTS))
        assertEquals("https://seekingalpha.com/symbol/BLDP%3ACA/income-statement",
            SeekingAlphaResearch.url(Security.of("BLDP.TO", "Ballard", "TOR", "CAD"), ResearchSection.FINANCIALS))
        assertEquals("https://seekingalpha.com/symbol/XEQT%3ACA",
            SeekingAlphaResearch.url(Security.of("XEQT.TO", "iShares", "TSX", "CAD", "ETF"), ResearchSection.FINANCIALS))
        assertNull(SeekingAlphaResearch.url(Security.of("PHOS.CN", "First Phosphate", "CSE", "CAD"), ResearchSection.FINANCIALS))
        assertNull(SeekingAlphaResearch.url(Security.of("BLDP", "Ballard", "NASDAQ", "USD"), ResearchSection.FINANCIALS))
        assertNull(SeekingAlphaResearch.url(Security.of("DOL", "Unrelated", "NYSE", "USD"), ResearchSection.FINANCIALS))
    }

    @Test fun resolverNeverConfusesCanadianListingsWithAmericanNamesakes() {
        assertEquals("AAPL", ProviderSymbolResolver.usEquity(Security.of("AAPL", "Apple", "NASDAQ", "USD")))
        assertNull(ProviderSymbolResolver.usEquity(Security.of("GURU.TO", "GURU Organic Energy", "TSX", "CAD")))
        assertNull(ProviderSymbolResolver.usEquity(Security.of("PHOS.CN", "First Phosphate", "CSE", "CAD")))
        assertNull(ProviderSymbolResolver.usEquity(Security.of("XEQT.TO", "iShares XEQT", "TSX", "CAD", "ETF")))
        assertNull(ProviderSymbolResolver.usEquity(Security.of("BLDP.TO", "Ballard", "TSX", "CAD")))
        assertEquals("BLDP", ProviderSymbolResolver.usEquity(Security.of("BLDP", "Ballard", "NASDAQ", "USD")))
        assertEquals("apple.com", OfficialDomains.forIdentity("AAPL", "NasdaqGS", "USD", "STOCK"))
        assertEquals("guruenergy.com", OfficialDomains.forIdentity("GURU.TO", "Toronto", "CAD", "STOCK"))
        assertEquals("firstphosphate.com", OfficialDomains.forIdentity("PHOS.CN", "CNQ", "CAD", "STOCK"))
        assertEquals("ishares.com", OfficialDomains.forIdentity("XEQT.TO", "TOR", "CAD", "ETF"))
    }

    private fun fact(vararg entries: JSONObject, unit: String = "USD") = JSONObject()
        .put("units", JSONObject().put(unit, JSONArray(entries.toList())))

    private fun period(end: String, value: Number, start: String? = null, filed: String = "2025-11-01", form: String = "10-K") =
        JSONObject().put("end", end).put("val", value).put("filed", filed).put("form", form)
            .apply { if (start != null) put("start", start) }

    @Test fun secParserUsesFiledAnnualPeriodsAndNeverFillsMissingFieldsWithZero() {
        val first = "2023-10-01"; val second = "2024-09-29"
        val gaap = JSONObject()
            .put("RevenueFromContractWithCustomerExcludingAssessedTax", fact(
                period("2024-09-28", 80, first),
                period("2025-09-27", 100, second),
                period("2025-09-27", 999, "2025-06-28", form = "10-Q"),
            ))
            .put("NetIncomeLoss", fact(period("2025-09-27", 20, second)))
            .put("EarningsPerShareDiluted", fact(period("2025-09-27", 2.5, second), unit = "USD/shares"))
            .put("CashAndCashEquivalentsAtCarryingValue", fact(period("2025-09-27", 40)))
            .put("NetCashProvidedByUsedInOperatingActivities", fact(period("2025-09-27", 30, second)))
            .put("PaymentsToAcquirePropertyPlantAndEquipment", fact(period("2025-09-27", 5, second)))
        val root = JSONObject().put("facts", JSONObject().put("us-gaap", gaap))
        val finance = SecFacts.parse(root)!!
        assertEquals("100", finance.metrics["Revenus"])
        assertEquals("20", finance.metrics["Bénéfice net"])
        assertEquals("2.5", finance.metrics["BPA"])
        assertEquals("25", finance.metrics["Free cash flow"])
        assertEquals("20.00", finance.metrics["Marge nette %"])
        assertEquals("25.00", finance.metrics["Croissance revenus %"])
        assertFalse(finance.metrics.containsKey("Dette nette"))
        assertEquals(2, finance.annual["Revenus"]?.size)
    }

    @Test fun analystParserKeepsActualCountsAndDistinguishesAbsentCoverage() {
        val data = JSONObject().put("consensusOverview", JSONObject()
            .put("buy", 15).put("hold", 9).put("sell", 4)
            .put("priceTarget", 334.9).put("highPriceTarget", 400).put("lowPriceTarget", 245))
            .put("historicalConsensus", JSONArray().put(JSONObject().put("z", JSONObject().put("date", "10/01/2026"))))
        val analyst = NasdaqAnalystProvider().parse(JSONObject().put("data", data))!!
        assertEquals(28, listOfNotNull(analyst.buy, analyst.hold, analyst.sell).sum())
        assertEquals(BigDecimal("334.9"), analyst.target)
        assertEquals("2026-10-01", analyst.date)
        assertNull(NasdaqAnalystProvider().parse(JSONObject().put("data", JSONObject.NULL)))
    }

    @Test fun ifrsIssuerKeepsReportedUsdAndDoesNotGuessMissingCapex() {
        val ifrs = JSONObject()
            .put("Revenue", fact(period("2025-12-31", 500, "2025-01-01")))
            .put("ProfitLoss", fact(period("2025-12-31", 120, "2025-01-01")))
            .put("CashAndCashEquivalents", fact(period("2025-12-31", 60)))
            .put("CashFlowsFromUsedInOperatingActivities", fact(period("2025-12-31", 130, "2025-01-01")))
        val result = SecFacts.parse(JSONObject().put("facts", JSONObject().put("ifrs-full", ifrs)))!!
        assertEquals("500", result.metrics["Revenus"])
        assertEquals("60", result.metrics["Cash"])
        assertFalse(result.metrics.containsKey("Free cash flow"))
        assertTrue(result.source.contains("IFRS en USD"))
    }
}
