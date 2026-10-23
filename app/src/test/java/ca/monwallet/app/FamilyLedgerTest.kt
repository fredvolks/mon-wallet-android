package ca.monwallet.app

import ca.monwallet.app.domain.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class FamilyLedgerTest {
    private fun tx(id: String, type: TxType, q: String, p: String, member: String? = null, date: String = "2026-10-01", allow: Boolean? = null) =
        Transaction(id, "p", if (type in listOf(TxType.DEPOSIT, TxType.WITHDRAWAL)) null else "s",
            type, q.dec(), p.dec(), date = date, memberId = member, allowUnmatchedSale = allow)
    private fun eq(expected: String, actual: java.math.BigDecimal?) { assertNotNull(actual); assertEquals(0, expected.dec().compareTo(actual)) }

    @Test fun quantitiesCostsAndSaleProfitsStayWithTheirMember() {
        val all = listOf(tx("me", TxType.BUY, "10", "10"),
            tx("jacob", TxType.BUY, "3", "20", "jacob"),
            tx("sell", TxType.SELL, "2", "30", "jacob", "2026-10-02"))
        Engine.validate(all)
        eq("1", FamilyLedger.availableAt(all, "p", "s", "jacob", "2026-10-03"))
        eq("10", FamilyLedger.availableAt(all, "p", "s", null, "2026-10-03"))
        eq("20", TradeReports.sales(all).single().pnl)
        eq("20", Engine.calculate(all).realized)
        eq("11", Engine.calculate(all).holdings.single().quantity)
    }
    @Test fun excessSaleWarnsCanPersistAndReconcilesOnlyAfterEarlierPurchaseIsAdded() {
        val buy = tx("b", TxType.BUY, "3", "20", "jacob")
        val sale = tx("s", TxType.SELL, "5", "30", "jacob", "2026-10-02", true)
        val own = tx("own", TxType.BUY, "10", "5")
        val entries = listOf(buy, own, sale)
        Engine.validate(entries)
        assertEquals(listOf("s"), FamilyLedger.resolve(entries).pending.map { it.id })
        assertTrue(TradeReports.sales(entries).isEmpty())
        assertNull(Engine.calculate(entries).value)
        assertNull(Engine.calculate(entries).day)
        try { Engine.validate(entries.map { if (it.id == "s") it.copy(allowUnmatchedSale = null) else it }); fail() }
        catch (_: IllegalArgumentException) {}
        val fixed = entries + tx("missing", TxType.BUY, "2", "25", "jacob", "2026-09-30")
        Engine.validate(fixed)
        assertTrue(FamilyLedger.resolve(fixed).pending.isEmpty())
        eq("40", TradeReports.sales(fixed).single().pnl)
        assertFalse(FamilyLedger.resolve(entries + tx("late", TxType.BUY, "2", "25", "jacob", "2026-10-03")).pending.isEmpty())
    }
    @Test fun depositsAndSaleProceedsRemainInTheSelectedMembersCash() {
        val entries = listOf(tx("d", TxType.DEPOSIT, "0", "100", "jacob", "2026-09-30"),
            tx("b", TxType.BUY, "3", "20", "jacob"),
            tx("s", TxType.SELL, "2", "30", "jacob", "2026-10-02"))
        val wallet = Wallet(transactions = entries, settings = mapOf("family_member_jacob" to "Jacob"))
        eq("100", wallet.forMember("jacob").result().cash)
        eq("0", wallet.forMember(null).result().cash)
        assertEquals("Jacob", wallet.memberName("jacob"))
        eq("1", wallet.forMember("jacob").result().holdings.single().quantity)
    }
    @Test fun oldTransactionJsonKeepsExistingOwnerAndNewFieldsRoundTrip() {
        val gson = Gson()
        val original = tx("b", TxType.BUY, "1", "10")
        val old = gson.toJsonTree(original).asJsonObject.apply { remove("memberId"); remove("allowUnmatchedSale") }
        val decoded = gson.fromJson(old, Transaction::class.java)
        assertNull(decoded.memberId); Engine.validate(listOf(decoded))
        val family = original.copy(memberId = "jacob", allowUnmatchedSale = true)
        assertEquals(family, gson.fromJson(gson.toJson(family), Transaction::class.java))
    }
    @Test fun retainedComparisonUsesNetSaleAsItsPercentDenominator() {
        val value = RetainedTradeValue("552.99".dec(), "622.80".dec(), "625.26".dec())
        eq("-2.46", value.versusSale)
        assertTrue(value.versusSalePercent!! < ZERO)
        assertEquals("-0.39", value.versusSalePercent!!.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
        assertNull(RetainedTradeValue(ONE, ONE, ZERO).versusSalePercent)
    }
}
