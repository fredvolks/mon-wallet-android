package ca.monwallet.app

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.monwallet.app.data.*
import ca.monwallet.app.database.Database
import ca.monwallet.app.domain.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RepositoryTest {
    private lateinit var database: Database
    private lateinit var repo: Repository
    private lateinit var scope: CoroutineScope

    @Before fun setup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), Database::class.java)
            .allowMainThreadQueries().build()
        repo = Repository(database, scope)
    }

    @After fun cleanup() { scope.cancel(); database.close() }

    private suspend fun portfolio(): Portfolio = Portfolio(name="CELI DISNAT").also {
        repo.put("portfolio", it.id, it)
    }

    @Test fun freshGuestStartsEmpty() = runBlocking {
        assertTrue(repo.current().portfolios.isEmpty())
        assertTrue(repo.current().watchlists.isEmpty())
    }

    @Test fun guestMigrationPreservesIdsAndDecimalValues() = runBlocking {
        val p = portfolio()
        val security = Catalog.all.first { it.symbol == "XEQT.TO" }
        val tx = Transaction(portfolioId=p.id,securityId=security.id,quantity="120".dec(),price="45.60".dec(),date="2026-10-01")
        repo.transaction(tx, security)
        repo.migrateGuest("user-A")
        assertEquals("user-A", repo.owner.value)
        assertTrue(repo.dao.records("guest").isEmpty())
        assertEquals(tx.id, repo.current().transactions.single().id)
        assertEquals(0,repo.current().result().invested.compareTo("5472".dec()))
        repo.switch("user-B")
        assertTrue(repo.current().transactions.isEmpty())
        repo.switch("user-A")
        assertEquals(1, repo.current().transactions.size)
    }

    @Test fun csvReimportIsIdempotentAndMissingFxRejected() = runBlocking {
        val csv="date,ticker,quantity,price,currency,fees,portfolio,type\n2026-10-01,XEQT.TO,120,45.60,CAD,0,CELI DISNAT,BUY"
        Csv.import(Csv.preview(csv,repo),repo)
        Csv.import(Csv.preview(csv,repo),repo)
        assertEquals(1,repo.current().transactions.size)
        assertTrue(runCatching { Csv.preview(csv.replace("XEQT.TO","TSM").replace("CAD","USD"),repo) }.isFailure)
    }

    @Test fun invalidSaleRollsBackAtomically() = runBlocking {
        val p=portfolio()
        val security=Catalog.all.first { it.symbol=="XEQT.TO" }
        val sale=Transaction(portfolioId=p.id,securityId=security.id,type=TxType.SELL,quantity="10".dec(),price="47".dec())
        assertTrue(runCatching { repo.transaction(sale,security) }.isFailure)
        assertTrue(repo.current().transactions.isEmpty())
    }

    @Test fun jsonBackupRestoresWatchlistsAndTombstones() = runBlocking {
        val list=Watchlist(name="Principale").also { repo.put("watchlist",it.id,it) }
        val security=Catalog.all.first()
        repo.watch(security,list.id)
        val item=repo.current().items.single()
        repo.remove(item.id)
        val backup=repo.exportJson()
        repo.switch("restored")
        repo.restore(backup)
        assertEquals(1,repo.current().watchlists.size)
        assertTrue(repo.current().items.isEmpty())
        assertNotNull(repo.dao.get(item.id,"restored")!!.deletedAt)
    }

    @Test fun roomMigrationKeepsVersionOneRecords() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>()
        val name="migration-test.db"
        context.deleteDatabase(name)
        val path=context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path,null).use { db ->
            db.execSQL("CREATE TABLE wallet_records (id TEXT NOT NULL, owner TEXT NOT NULL, kind TEXT NOT NULL, payload TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER, serverVersion INTEGER NOT NULL, dirty INTEGER NOT NULL, PRIMARY KEY(owner,id))")
            db.execSQL("CREATE INDEX index_wallet_records_owner ON wallet_records(owner)")
            db.execSQL("CREATE INDEX index_wallet_records_kind ON wallet_records(kind)")
            db.execSQL("CREATE TABLE market_cache (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, payload TEXT NOT NULL, fetchedAt INTEGER NOT NULL)")
            db.execSQL("INSERT INTO wallet_records VALUES ('p','guest','portfolio','{\"id\":\"p\",\"name\":\"CELI\",\"type\":\"CELI\",\"currency\":\"CAD\"}',1,1,NULL,0,1)")
            db.version=1
        }
        val migrated=Room.databaseBuilder(context,Database::class.java,name)
            .addMigrations(Database.migration).allowMainThreadQueries().build()
        try {
            assertEquals("p",migrated.dao().records("guest").single().id)
            assertNull(migrated.dao().records("guest").single().conflict)
        } finally { migrated.close();context.deleteDatabase(name) }
    }

    @Test fun emptyCustomPortfolioSurvivesMigration() = runBlocking {
        val p=Portfolio(name="Épargne personnelle",type="PERSONNEL")
        repo.put("portfolio",p.id,p)
        repo.migrateGuest("new-user")
        assertEquals(p.id,repo.current().portfolios.single().id)
    }
}
