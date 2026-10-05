package ca.monwallet.app

import android.app.Application
import android.content.Context
import androidx.work.*
import ca.monwallet.app.auth.AuthManager
import ca.monwallet.app.data.*
import ca.monwallet.app.database.Database
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.*
import ca.monwallet.app.notifications.*
import ca.monwallet.app.sync.SyncManager
import ca.monwallet.app.updates.Updater
import ca.monwallet.app.widgets.WalletWidget
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

open class MonWallet : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleController.wrap(base))
    }

    lateinit var services: Services

    override fun onCreate() {
        super.onCreate()
        services = Services(this)
        services.scope.launch {
            services.auth.initialize()
            services.initialized.value = true
        }
        Notifications.channels(this)
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "wallet-refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .build(),
            )
    }
}

class Services(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val secure = SecureSettings(context)
    val repo = Repository(Database.create(context), scope)
    val auth = AuthManager(secure, repo)
    val sync = SyncManager(repo, auth)
    val market = Router(secure)
    val discovery by lazy { Discovery(this) }
    val foregroundSecurities = MutableStateFlow<List<Security>>(emptyList())
    val updater = Updater(context, secure)
    val initialized = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val marketStatus = MutableStateFlow("Derniers cours en cache")
    val liveQuotes: LiveQuoteProvider = ForegroundQuoteProvider(market)
    private val quoteLocks = ConcurrentHashMap<String, Mutex>()
    private val recentQuotes = ConcurrentHashMap<String, Quote>()

    suspend fun refreshQuote(security: Security): Quote =
        quoteLocks.getOrPut(security.id) { Mutex() }.withLock {
            recentQuotes[security.id]?.takeIf {
                System.currentTimeMillis() - it.fetchedAt < 5_000
            } ?: liveQuotes.quote(security).cached.also { fresh ->
                repo.quote(fresh)
                recentQuotes[security.id] = fresh
            }
        }

    suspend fun refreshForeground(securities: List<Security>) {
        if (busy.value || securities.isEmpty()) return
        val gate = Semaphore(2)
        supervisorScope {
            securities.distinctBy { it.id }.take(20).map { security ->
                async {
                    gate.withPermit {
                        try {
                            val live = refreshQuote(security)
                            // The existing quote cache remains the single source observed by
                            // portfolio, watchlist, detail and widgets.
                            live
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll()
        }
        marketStatus.value = "Cours actualisés · délai du fournisseur non garanti"
    }

    suspend fun refresh(extra: List<Security> = emptyList(), history: Boolean = false,
        notifyWidgets: Boolean = true) {
        if (busy.value) return
        busy.value = true
        try {
            val w = repo.current()
            val ids =
                (w.transactions.mapNotNull { it.securityId } +
                        w.items.map { it.securityId } +
                        w.alerts.map { it.securityId })
                    .toSet()
            val securities =
                (w.securities.filter { it.id in ids } + Catalog.markets + extra).distinctBy {
                    it.id
                }
            val errors = java.util.concurrent.atomic.AtomicInteger(0)
            val semaphore = Semaphore(3)
            supervisorScope {
                securities
                    .map { s ->
                        async {
                            semaphore.withPermit {
                                try {
                                    refreshQuote(s)
                                    if (history || w.prices.none { it.securityId == s.id })
                                        repo.points(s.id, market.history(s))
                                    if (s.id in ids || s.symbol == "CAD=X")
                                        runCatching {
                                            repo.points(s.id, market.history(s, "1d", "5m"), true)
                                        }
                                } catch (e: Exception) {
                                    errors.incrementAndGet()
                                }
                            }
                        }
                    }
                    .awaitAll()
            }
            marketStatus.value =
                if (errors.get() == 0) "Cours actualisés · délai non garanti"
                else "${errors.get()} cours indisponible(s) · cache conservé"
            runCatching { Notifications.evaluate(this) }
            if (notifyWidgets) WalletWidget.updateAll(context)
            if (auth.user.value != null) runCatching { sync.sync() }
        } finally {
            busy.value = false
        }
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val s = (applicationContext as MonWallet).services
        while (!s.initialized.value) delay(50)
        return try {
            val widgetId = inputData.getInt("widgetId", -1)
            s.refresh(notifyWidgets = widgetId < 0)
            if (widgetId >= 0)
                WalletWidget.render(applicationContext, intArrayOf(widgetId))
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
