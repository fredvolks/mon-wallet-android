package ca.monwallet.app

import ca.monwallet.app.marketdata.YahooNewsMatcher
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CanadianNewsMatchTest {
    @Test fun issuerNameFallbackRequiresExactMarketSymbol() {
        assertTrue(YahooNewsMatcher.identifies(JSONObject(
            """{"relatedTickers":["DOL.TO","WMT"]}"""), "DOL.TO"))
        assertFalse(YahooNewsMatcher.identifies(JSONObject(
            """{"relatedTickers":["GURU","PHOS"]}"""), "GURU.TO"))
        assertFalse(YahooNewsMatcher.identifies(JSONObject("""{"title":"DOL news"}"""),
            "DOL.TO"))
        assertFalse(YahooNewsMatcher.identifies(JSONObject(
            """{"relatedTickers":["PHOS.CN"]}"""), "PHOS.TO"))
    }
}
