package ca.monwallet.app

import ca.monwallet.app.data.Catalog
import ca.monwallet.app.domain.Security
import ca.monwallet.app.marketdata.*
import org.junit.Assert.*
import org.junit.Test

class StockSearchTest {
    private val rows = Catalog.all + SectorUniverse.stocks
    @Test fun nationalBankAliasesResolveWithoutNetwork() {
        listOf("NA", "na.to", "BNC", "Banque Nationale", "National Bank", "TSX:NA", "NA TSX").forEach { q ->
            val first = SecuritySearch.rank(q, rows).first()
            assertEquals(q, "NA.TO", first.symbol)
            assertEquals("CAD", first.currency)
        }
    }
    @Test fun genericExactTickersNamesAccentsAndTyposRankBeforeSubstringMatches() {
        listOf("MSFT" to "MSFT", "Microsoft" to "MSFT", "Microsft" to "MSFT",
            "NVDA" to "NVDA", "VFV" to "VFV.TO", "vfv.to" to "VFV.TO", "GURU" to "GURU.TO",
            "Banque de Montreal" to "BMO.TO", "Banque Montréal" to "BMO.TO", "Google" to "GOOGL").forEach { (q, expected) ->
            assertEquals(q, expected, SecuritySearch.rank(q, rows).first().symbol)
        }
        assertTrue(SecuritySearch.rank("no-such-company-zzzz", rows).isEmpty())
        val shareClass = Security.of("BRK.B", "Berkshire Class B", "NYSE", "USD")
        assertEquals("BRK.B", SecuritySearch.rank("BRK.B", rows + shareClass).first().ticker)
        val match = SecuritySearch.rank("TSX:VFV", rows)
        assertTrue(match.all { it.exchange.uppercase() in setOf("TSX", "TOR", "TORONTO") })
    }
    @Test fun duplicatesAreCanonicalButSeparateListingsAndCurrenciesSurvive() {
        val cad = Security.of("RY.TO", "Royal Bank", "TSX", "CAD")
        val duplicate = cad.copy(id = "remote", exchange = "Toronto")
        val usd = Security.of("RY", "Royal Bank ADR", "NYSE", "USD")
        val ranked = SecuritySearch.rank("RY", listOf(cad, duplicate, usd))
        assertEquals(2, ranked.size)
        assertEquals(setOf("CAD", "USD"), ranked.map { it.currency }.toSet())
        assertEquals(listOf("NA.TO", "BNC"), SecuritySearch.remoteQueries("BNC"))
    }
}
