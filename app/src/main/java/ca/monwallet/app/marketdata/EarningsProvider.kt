package ca.monwallet.app.marketdata

import ca.monwallet.app.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import okhttp3.Request
import org.json.JSONObject

/** Public Nasdaq endpoints already used for US research; no symbol-only Canadian mapping. */
object NasdaqEarnings {
    private val dateFormat = DateTimeFormatter.ofPattern("M/d/uuuu")
    internal fun parseUpcoming(data: JSONObject): Earnings? {
        val text = data.optString("reportText")
        val rawDate = Regex("""\b[0-9]{1,2}/[0-9]{1,2}/[0-9]{4}\b""").find(text)?.value ?: return null
        val date = runCatching { LocalDate.parse(rawDate, dateFormat).toString() }.getOrNull() ?: return null
        val eps = Regex("""consensus EPS forecast for the quarter is [$](-?[0-9.,]+)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull()
        return Earnings(date, eps, null, null, null, "Nasdaq · Zacks (date prévue)")
    }
    internal fun parseResults(data: JSONObject): List<Earnings> =
        data.optJSONObject("earningsSurpriseTable")?.optJSONArray("rows")?.objects()?.mapNotNull { row ->
            val date = runCatching { LocalDate.parse(row.optString("dateReported"), dateFormat).toString() }
                .getOrNull() ?: return@mapNotNull null
            Earnings(date, row.number("consensusForecast"), row.number("eps"), null, null, "Nasdaq")
        }.orEmpty()

    private suspend fun get(path: String): JSONObject = Http.json(Request.Builder()
        .url("https://api.nasdaq.com/api/$path").header("User-Agent", "Mozilla/5.0 MonWallet")
        .header("Accept", "application/json").header("Origin", "https://www.nasdaq.com").build())
        .optJSONObject("data") ?: JSONObject()

    suspend fun load(security: Security): List<Earnings> {
        val symbol = ProviderSymbolResolver.usEquity(security) ?: return emptyList()
        val rows = mutableListOf<Earnings>()
        try { parseUpcoming(get("analyst/$symbol/earnings-date"))?.let(rows::add) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { }
        try { rows += parseResults(get("company/$symbol/earnings-surprise")) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { }
        return rows
    }
}
