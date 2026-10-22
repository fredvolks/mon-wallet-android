package ca.monwallet.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.monwallet.app.domain.Security
import ca.monwallet.app.marketdata.FinvizSnapshotProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinancialSnapshotNetworkTest {
    @Test fun tsmAndSpcxReturnRealFinancialSnapshotOnAndroid() = runBlocking {
        for ((symbol, exchange) in listOf("TSM" to "NYSE", "SPCX" to "NASDAQ")) {
            val snapshot = FinvizSnapshotProvider().load(Security.of(symbol, symbol, exchange, "USD"))
            assertNotNull("No public snapshot for $symbol", snapshot)
            val metrics = snapshot!!.metrics
            assertNotNull("Debt/equity missing for $symbol", metrics["Dette / capitaux propres"])
            assertTrue(metrics["Capitalisation (M)"]!!.toBigDecimal().signum() > 0)
            if (symbol == "TSM") assertNotNull("TSM P/E missing", metrics["P/E"])
            Log.i("MonWalletFinancialTest", "$symbol: ${snapshot.source}, ${snapshot.ratiosAsOf}, $metrics")
        }
        Unit
    }
}
