package ca.monwallet.app.marketdata

import ca.monwallet.app.domain.*
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.*
import org.jsoup.Jsoup
import java.time.LocalDate

/** Public queries used by Vanguard Canada's product site; no account or API key. */
object VanguardFunds {
    private const val root = "https://www.vanguard.ca"
    @Volatile private var catalog: Pair<Long, Map<String, String>>? = null
    private suspend fun gql(query: String, variables: JSONObject): JSONObject {
        val payload = JSONObject().put("query", query).put("variables", variables)
        val result = Http.json(Request.Builder().url("$root/gpx/graphql")
            .header("X-Consumer-ID", "ca0").header("User-Agent", "Mozilla/5.0 MonWallet")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build())
        check(!result.has("errors")) { "Données Vanguard indisponibles." }
        return result.getJSONObject("data")
    }
    private suspend fun ids(): Map<String, String> {
        catalog?.takeIf { System.currentTimeMillis() - it.first < 86400_000 }?.let { return it.second }
        val html = Http.raw(Request.Builder().url("$root/en/product/etf/equity/9563/vanguard-sp-500-index-etf").build())
        val state = Jsoup.parse(html).selectFirst("script#serverApp-state")?.data().orEmpty()
        val portIds = Regex("\"portIds\"\\s*:\\s*\"([^\"]+)\"").find(state)?.groupValues?.get(1)
            ?.split(',')?.filter { it.matches(Regex("\\d{4}")) } ?: error("Catalogue Vanguard indisponible.")
        val data = gql("""query(${'$'}ids:[String!]) { funds(portIds:${'$'}ids) { profile {
            portId fundCurrency listings { fundCurrency stockExchangeMarketIdentifierCode identifiers { altIdCode altIdValue } }
        } } }""", JSONObject().put("ids", JSONArray(portIds)))
        val rows = data.getJSONArray("funds").objects().flatMap { fund ->
            val profile = fund.getJSONObject("profile")
            profile.optJSONArray("listings")?.objects().orEmpty().flatMap { listing ->
                listing.optJSONArray("identifiers")?.objects().orEmpty().mapNotNull { id ->
                    if (id.text("altIdCode") in setOf("TICKER", "RIC", "BBGI", "BLMB")) {
                        val raw = id.text("altIdValue")?.substringBefore(' ')?.removeSuffix(".TO") ?: return@mapNotNull null
                        raw to profile.getString("portId")
                    } else null
                }
            }
        }.toMap()
        check(rows.isNotEmpty()) { "Catalogue Vanguard vide." }
        catalog = System.currentTimeMillis() to rows
        return rows
    }
    suspend fun fetch(s: Security): EtfDetails {
        require(s.type.uppercase() == "ETF" && s.currency == "CAD" &&
            s.exchange.uppercase() in setOf("TSX", "TOR", "TORONTO"))
        val id = if (s.symbol.uppercase().removeSuffix(".TO") == "VFV") "9563"
            else ids()[s.symbol.uppercase().removeSuffix(".TO")] ?: error("FNB absent du catalogue Vanguard.")
        suspend fun table(kind: String): Pair<JSONObject, FundComposition> {
            val rows = mutableListOf<FundPosition>()
            var key: String? = null
            var name: JSONObject? = null
            val dates = mutableSetOf<String>()
            do {
                val query = """query(${'$'}ids:[String!],${'$'}key:String) {
                    funds(portIds:${'$'}ids) { profile { fundFullName fundCurrency } }
                    borHoldings(portIds:${'$'}ids) { $kind(limit:1500,lastItemKey:${'$'}key) {
                        items { issuerName securityLongDescription ticker marketValuePercentage effectiveDate gicsSectorDescription bloombergIsoCountry securityType }
                        totalHoldings lastItemKey
                    } }
                }"""
                val data = gql(query, JSONObject().put("ids", JSONArray(listOf(id))).put("key", key ?: JSONObject.NULL))
                name = data.getJSONArray("funds").getJSONObject(0).getJSONObject("profile")
                val page = data.getJSONArray("borHoldings").getJSONObject(0).getJSONObject(kind)
                page.getJSONArray("items").objects().forEach { row ->
                    val date = row.text("effectiveDate")?.take(10)?.let { LocalDate.parse(it).toString() }
                        ?: error("Date de composition absente.")
                    dates += date
                    val weight = row.number("marketValuePercentage") ?: error("Pondération absente.")
                    val label = row.text("securityLongDescription") ?: row.text("issuerName") ?: "Position sans nom"
                    val country = row.text("bloombergIsoCountry")?.let { code ->
                        java.util.Locale.Builder().setRegion(code).build().getDisplayCountry(java.util.Locale.FRENCH) }
                    rows += FundPosition(row.text("ticker") ?: label, label, weight,
                        row.text("gicsSectorDescription"), country, row.text("securityType"))
                }
                key = page.text("lastItemKey")
                check(rows.size < 50_000) { "Composition trop volumineuse." }
            } while (key != null)
            check(rows.isNotEmpty() && dates.size == 1) { "Composition Vanguard incomplète ou dates incohérentes." }
            return name!! to FundComposition(dates.single(), rows)
        }
        val (profile, direct) = table("holdings")
        val underlying = try { table("delayeredHoldings").second }
            catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
        val page = if (id == "9563") "$root/en/product/etf/equity/9563/vanguard-sp-500-index-etf"
            else "$root/en/product"
        val text = try { Jsoup.parse(Http.raw(Request.Builder().url(page).build())).text() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { "" }
        fun metric(label: String) = Regex(Regex.escape(label) + "\\s+([0-9.]+%)").find(text)?.groupValues?.get(1)
        return EtfDetails(profile.getString("fundFullName"), "Vanguard Canada", page, direct, underlying,
            metric("Management Fee"), metric("Management Expense Ratio (MER)"), null, null)
    }
}
