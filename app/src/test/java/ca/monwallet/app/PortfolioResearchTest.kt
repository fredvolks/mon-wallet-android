package ca.monwallet.app

import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.*
import org.junit.Assert.*
import org.junit.Test

class PortfolioResearchTest {
    private fun q(id: String, value: String, currency: String = "CAD") =
        Quote(id, value.dec(), value.dec(), currency, 1, "2026-10-23", "fixture")
    private fun holding(id: String, value: String) = Holding(id, ONE, value.dec(), value.dec(), ZERO, ZERO, value.dec(), ZERO, ZERO, ZERO, value.dec())
    private fun result(holdings: List<Holding>, cash: String = "0") = Result(holdings, holdings.fold(ZERO) { n, h -> n + h.costCad }, cash.dec(),
        holdings.fold(cash.dec()) { n, h -> n + (h.value ?: ZERO) }, ZERO, ZERO, ZERO, ONE)
    private fun fund(weights: Map<String, String>) = EtfDetails("Fund", "Issuer", "https://example.com",
        FundComposition("2026-09-30", emptyList()), null, null, null, null, null,
        sectorExposure = FundExposure("2026-09-30", weights.mapValues { it.value.dec() }))
    private fun eq(expected: String, actual: java.math.BigDecimal?) { assertNotNull(actual); assertEquals(0, expected.dec().compareTo(actual)) }
    @Test fun partialFundUsesWholeWalletDenominatorWithoutDoubleCounting() {
        val etf = Security("fund", "VFV.TO", "Fund", "TSX", "CAD", "ETF")
        val stock = Security("stock", "MSFT", "Microsoft", "NASDAQ", "USD", sector = "Technology")
        val exposure = PortfolioResearch.build(result(listOf(holding("fund", "1000"), holding("stock", "100"))),
            listOf(etf, stock), mapOf("fund" to fund(mapOf("Information Technology" to "50", "Financials" to "20"))))
        eq("1100", exposure.total)
        eq("600", exposure.sectors.single { it.label == "Technologie" }.value)
        eq("200", exposure.sectors.single { it.label == "Services financiers" }.value)
        eq("300", exposure.unknownSector)
        eq("1100", exposure.sectors.fold(ZERO) { n, e -> n + e.value })
    }
    @Test fun newMoneyAndExistingCashProduceDifferentSectorWeightsAndPreserveTotal() {
        val stock = Security("s", "MSFT", "Microsoft", "NASDAQ", "CAD", sector = "Technology")
        val e = PortfolioResearch.build(result(listOf(holding("s", "500")), "500"), listOf(stock))
        val contributed = PortfolioResearch.simulate(e, stock, q("s", "100"), "500".dec(), false, null)!!
        eq("1500", contributed.after.total); eq("5", contributed.quantity); eq("500", contributed.after.cash)
        val existing = PortfolioResearch.simulate(e, stock, q("s", "100"), "500".dec(), true, null)!!
        eq("1000", existing.after.total); eq("0", existing.after.cash)
        eq("100", existing.after.sectors.single { it.label == "Technologie" }.percent)
        assertNull(PortfolioResearch.simulate(e, stock, q("s", "100"), "501".dec(), true, null))
    }
    @Test fun usdNeedsVerifiedFxAndWholeShareRemainderStaysCash() {
        val stock = Security("s", "MSFT", "Microsoft", "NASDAQ", "USD", sector = "Technology")
        val e = PortfolioResearch.build(result(emptyList()), listOf(stock))
        val impact = PortfolioResearch.simulate(e, stock, q("s", "100", "USD"), "500".dec(), false, "1.4".dec())!!
        eq("3", impact.quantity); eq("420", impact.spent); eq("80", impact.after.cash); eq("500", impact.after.total)
        assertNull(PortfolioResearch.simulate(e, stock, q("s", "100", "USD"), "500".dec(), false, null))
    }
    @Test fun missingPricesDoNotBecomeACompleteAllocationAndInvalidFundWeightsAreNotNormalized() {
        val etf = Security("f", "VFV.TO", "Fund", "TSX", "CAD", "ETF")
        val e = PortfolioResearch.build(result(listOf(holding("f", "1000"))).copy(value = null), listOf(etf))
        assertNull(e.total)
        assertTrue(PortfolioResearch.fundWeights(fund(mapOf("Technology" to "110")), true).isEmpty())
        val noFund = PortfolioResearch.build(result(listOf(holding("f", "1000"))), listOf(etf))
        eq("1000", noFund.unknownSector)
        eq("100", noFund.sectors.single().percent)
    }
}
