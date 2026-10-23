package ca.monwallet.app.marketdata

import ca.monwallet.app.domain.Security
import java.text.Normalizer
import java.util.Locale

object SecuritySearch {
    private val aliases = mapOf(
        "NA.TO" to listOf("BNC", "Banque Nationale", "Banque Nationale du Canada", "National Bank", "National Bank of Canada", "NBC"),
        "RY.TO" to listOf("RBC", "Banque Royale", "Royal Bank"),
        "TD.TO" to listOf("Toronto Dominion", "Banque TD"),
        "BNS.TO" to listOf("Scotiabank", "Banque Scotia"),
        "BMO.TO" to listOf("Banque de Montreal", "Bank of Montreal"),
        "CM.TO" to listOf("CIBC", "Banque Canadienne Imperiale"),
        "DOL.TO" to listOf("Dollarama"),
        "ATD.TO" to listOf("Couche Tard", "Alimentation Couche Tard"),
        "GOOGL" to listOf("Google"), "META" to listOf("Facebook"),
    )
    fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").uppercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
    fun identity(s: Security): String {
        val market = when (s.exchange.uppercase()) { "TOR", "TORONTO", "TSX" -> "TSX"; "NYQ" -> "NYSE";
            "NMS", "NASDAQGS", "NASDAQGM", "NASDAQCM", "NCM", "NGM" -> "NASDAQ"; else -> s.exchange.uppercase() }
        return "${s.symbol.uppercase()}|$market|${s.currency}"
    }
    fun remoteQueries(query: String): List<String> {
        val q = normalized(query).replace(Regex("^(TSX|NASDAQ|NYSE)[: ]+"), "").replace(Regex(" (TSX|NASDAQ|NYSE)$"), "")
        val alias = aliases.entries.firstOrNull { (_, values) -> values.any { normalized(it) == q } }?.key
        val exactCanadian = if (q.matches(Regex("[A-Z]{1,5}"))) "$q.TO" else null
        return listOfNotNull(alias ?: exactCanadian, query.trim()).distinct().take(2)
    }
    fun rank(query: String, candidates: List<Security>): List<Security> {
        val raw = normalized(query)
        val market = Regex("^(TSX|NASDAQ|NYSE)[: ]+| (TSX|NASDAQ|NYSE)$").find(raw)?.let {
            it.groupValues.drop(1).firstOrNull(String::isNotBlank) }
        val q = raw.replace(Regex("^(TSX|NASDAQ|NYSE)[: ]+"), "").replace(Regex(" (TSX|NASDAQ|NYSE)$"), "")
        fun oneTypo(a: String, b: String): Boolean {
            if (a.length < 5 || kotlin.math.abs(a.length - b.length) > 1) return false
            var i = 0; var j = 0; var edits = 0
            while (i < a.length && j < b.length) {
                if (a[i] == b[j]) { i++; j++; continue }
                if (++edits > 1) return false
                if (a.length <= b.length) j++
                if (a.length >= b.length) i++
            }
            return edits + (a.length - i) + (b.length - j) <= 1
        }
        fun score(s: Security): Int {
            if (market != null && identity(s).split('|')[1] != market) return 0
            if (q.isBlank()) return 1
            val symbol = normalized(s.symbol)
            val ticker = symbol.removeSuffix(".TO").removeSuffix(".NE").removeSuffix(".CN").removeSuffix(".V")
            val names = (listOf(s.name) + aliases[symbol].orEmpty()).map(::normalized)
            val bonus = if (s.currency == "CAD" && s.exchange.uppercase() in setOf("TSX", "TOR", "TORONTO")) 50 else 0
            val base = when {
                symbol == q -> 12000
                ticker == q -> 11000
                aliases[symbol].orEmpty().any { normalized(it) == q } -> 10500
                names.any { it == q } -> 10000
                symbol.startsWith(q) -> 8000
                names.any { name -> name.split(' ', '-', '\'').any { it.startsWith(q) } } -> 6000
                q.contains(' ') && names.any { name ->
                    val words = name.split(Regex("[^A-Z0-9]+"))
                    q.split(Regex("[^A-Z0-9]+" )).filter(String::isNotBlank).all { token -> words.any { it.startsWith(token) } }
                } -> 5000
                q.length >= 3 && names.any { it.contains(q) } -> 4000
                names.any { name -> name.split(' ', '-', '\'').any { oneTypo(q, it) } } -> 2000
                normalized(s.exchange) == q -> 1000
                else -> 0
            }
            return if (base > 0) base + bonus else 0
        }
        return candidates.distinctBy(::identity).map { it to score(it) }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Security, Int>> { it.second }.thenBy { it.first.symbol })
            .map { it.first }
    }
}
