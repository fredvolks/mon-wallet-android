package ca.monwallet.app.marketdata

import ca.monwallet.app.domain.Security
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import com.google.gson.JsonParser
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object GlobalXFunds {
    suspend fun fetch(s: Security): EtfDetails {
        require(s.type.uppercase() == "ETF" && s.currency == "CAD" &&
            s.exchange.uppercase() in setOf("TSX", "TOR", "TORONTO"))
        val ticker = s.symbol.uppercase().removeSuffix(".TO")
        require(ticker.matches(Regex("[A-Z][A-Z0-9.]{0,12}")))
        val url = "https://www.globalx.ca/product/${ticker.lowercase()}"
        return parse(s, Http.raw(Request.Builder().url(url)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("User-Agent", "Mozilla/5.0").build()), url)
    }
    internal fun parse(s: Security, html: String, url: String): EtfDetails {
        val doc = Jsoup.parse(html)
        val ticker = s.symbol.uppercase().removeSuffix(".TO")
        check(doc.title().startsWith("$ticker ")) { "Identité Global X non confirmée." }
        val holdings = doc.selectFirst("#holdings") ?: error("Composition Global X absente.")
        fun day(element: Element): String {
            val text = Regex("As at ([A-Za-z]+ \\d{1,2}, \\d{4})").find(element.text())?.groupValues?.get(1)
                ?: error("Date Global X absente.")
            return LocalDate.parse(text, DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US)).toString()
        }
        val heading = holdings.select("h2").firstOrNull { it.text().trim() == "Top Holdings" }
            ?: error("Principales positions Global X absentes.")
        val block = heading.parent()?.parent() ?: error("Format Global X non reconnu.")
        val positions = block.select("div").mapNotNull { row ->
            val p = row.children().filter { it.tagName() == "p" }
            if (p.size != 2) return@mapNotNull null
            val weight = p[1].text().trim().removeSuffix("%").toBigDecimalOrNull() ?: return@mapNotNull null
            FundPosition(p[0].text(), p[0].text(), weight, null, null, null)
        }
        check(positions.isNotEmpty()) { "Positions Global X vides." }
        fun exposure(id: String): FundExposure? {
            val chart = holdings.selectFirst(id) ?: return null
            val section = chart.parents().firstOrNull { it.select("h2").isNotEmpty() } ?: return null
            val values = JsonParser.parseString(chart.attr("data-chart")).asJsonObject.entrySet()
                .associate { it.key to it.value.asBigDecimal }
            return FundExposure(day(section), values)
        }
        fun field(label: String) = doc.select("p").firstOrNull { it.ownText().trim() == label }
            ?.nextElementSibling()?.text()?.takeIf { it.isNotBlank() }?.replace("As at", "au")
        return EtfDetails(doc.select("h1").text().ifBlank { s.name }, "Global X Canada", url,
            FundComposition(day(heading.parent()!!), positions), null,
            field("Management Fee"), field("Management Expense Ratio"), field("Distribution Frequency"), field("Total Net Assets"),
            compositionNote = "Principales positions publiées par Global X, liste partielle. Les répartitions par secteurs et pays sont celles publiées séparément par l’émetteur; les pondérations ne sont pas renormalisées.",
            sectorExposure = exposure("#sector-allocation-chart"), countryExposure = exposure("#geographic-exposure-chart"))
    }
}
