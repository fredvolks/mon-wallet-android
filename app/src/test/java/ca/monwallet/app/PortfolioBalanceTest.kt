package ca.monwallet.app

import ca.monwallet.app.domain.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class PortfolioBalanceTest {
    private fun d(s: String) = BigDecimal(s)
    private val stock = Security("s", "MSFT", "Microsoft", "NASDAQ", "USD", sector = "Technology", country = "US")
    private val etf = Security("e", "XEQT.TO", "XEQT", "TSX", "CAD", "ETF")
    private val securities = listOf(stock, etf).associateBy { it.id }
    private fun holding(id: String, value: String) = Holding(id, ONE, ONE, ONE, ZERO, ZERO, d(value), ZERO, ZERO, ZERO, ZERO)
    private fun result() = Result(listOf(holding("s", "300"), holding("e", "500")), d("1000"), d("200"), d("1000"), ZERO, ZERO, null, null)
    private fun equal(expected: String, actual: BigDecimal?) = assertEquals(0, d(expected).compareTo(actual!!))
    @Test fun unknownFundDoesNotInflateKnownTechWeight() {
        val report = PortfolioBalance.analyze(result(), securities, emptyMap())
        equal("1000", report.total); equal("500", report.unknownSector)
        equal("30", report.sectors.first { it.label == "Technologie" }.weight)
        equal("100", report.sectors.fold(ZERO) { a, b -> a + b.weight })
    }
    @Test fun fundLookThroughIsCountedOnceAndMissingRemainderStaysUnknown() {
        val fund = ExposureData(mapOf("Information Technology" to d("20"), "Financials" to d("70")), emptyMap(), "issuer", "2026-10-22")
        val report = PortfolioBalance.analyze(result(), securities, mapOf("e" to fund))
        equal("40", report.sectors.first { it.label == "Technologie" }.weight)
        equal("50", report.unknownSector); equal("1000", report.sectors.fold(ZERO) { a, b -> a + b.value })
    }
    @Test fun externalAndCashPurchasesUseDifferentDenominators() {
        val report = PortfolioBalance.analyze(result(), securities, emptyMap())
        val external = PortfolioBalance.simulate(report, d("200"), "Santé", true)
        val cash = PortfolioBalance.simulate(report, d("200"), "Santé", false)
        equal("25", external.first { it.label == "Technologie" }.weight)
        equal("30", cash.first { it.label == "Technologie" }.weight)
        assertFalse(cash.any { it.label == "Liquidités" })
    }
    @Test fun missingQuoteSuppressesPercentagesAndSimulation() {
        val report = PortfolioBalance.analyze(result().copy(holdings = listOf(holding("s", "300").copy(value = null), holding("e", "500"))), securities, emptyMap())
        assertNull(report.total)
        assertTrue(runCatching { PortfolioBalance.simulate(report, ONE, "Santé", true) }.isFailure)
    }
    @Test fun excessiveWeightsAreRejectedInsteadOfNormalized() {
        val report = PortfolioBalance.analyze(result(), securities, mapOf("e" to ExposureData(mapOf("Technology" to d("101")), emptyMap(), "issuer", "2026-10-22")))
        equal("500", report.unknownSector)
    }
    @Test fun memberPositionsAreFilteredBeforeAnalysis() {
        val w = Wallet(transactions = listOf(Transaction(portfolioId = "p", securityId = "s", price = ONE, quantity = ONE),
            Transaction(portfolioId = "p", securityId = "s", price = ONE, quantity = d("3"), memberId = "jacob")))
        equal("1", w.forMember(null).transactions.single().quantity)
        equal("3", w.forMember("jacob").transactions.single().quantity)
    }
    @Test fun insufficientCashCannotBecomeAnImplicitLoan() {
        val report = PortfolioBalance.analyze(result(), securities, emptyMap())
        assertTrue(runCatching { PortfolioBalance.simulate(report, d("201"), "Santé", false) }.isFailure)
    }
}
