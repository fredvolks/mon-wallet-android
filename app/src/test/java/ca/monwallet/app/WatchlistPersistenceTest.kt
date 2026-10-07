package ca.monwallet.app

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.monwallet.app.data.Repository
import ca.monwallet.app.database.Database
import ca.monwallet.app.domain.Security
import ca.monwallet.app.domain.Watchlist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class WatchlistPersistenceTest {
    @Test fun manualOrderAndSelectedListSurviveQuoteChangesAndRepositoryRestart() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),
            Database::class.java).allowMainThreadQueries().build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = Repository(db, scope)
            val watchlist = Watchlist(name = "Long terme")
            repo.put("watchlist", watchlist.id, watchlist)
            repo.setting("watchlist:selected", watchlist.id)
            val tickers = listOf("AAPL", "MSFT", "NVDA")
            tickers.forEach { ticker ->
                repo.watch(Security.of(ticker, ticker, "NASDAQ", "USD"), watchlist.id)
            }
            val initial = repo.current()
            val middle = initial.items.first { item ->
                initial.security(item.securityId)?.ticker == "MSFT"
            }
            repo.moveWatchItem(watchlist.id, middle.id, -1)
            // The cache writes that happen during quote refresh never edit orderIndex.
            val restarted = Repository(db, scope)
            val wallet = restarted.current()
            assertEquals(watchlist.id, wallet.settings["watchlist:selected"])
            assertEquals(listOf("MSFT", "AAPL", "NVDA"), wallet.items
                .filter { it.watchlistId == watchlist.id }.sortedBy { it.order }
                .map { wallet.security(it.securityId)?.ticker })
            assertEquals(listOf(0, 1, 2), wallet.items.sortedBy { it.order }.map { it.order })
        } finally { scope.cancel(); db.close() }
    }
}
