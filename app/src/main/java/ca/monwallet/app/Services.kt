package ca.monwallet.app

import android.app.Application
import android.content.Context
import androidx.work.*
import ca.monwallet.app.auth.AuthManager
import ca.monwallet.app.data.*
import ca.monwallet.app.database.Database
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.*
import ca.monwallet.app.news.NewsRepository
import ca.monwallet.app.notifications.*
import ca.monwallet.app.sync.SyncManager
import ca.monwallet.app.updates.Updater
import ca.monwallet.app.widgets.WalletWidget
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
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

/** Fetch far enough back to include an edited or backdated purchase. */
internal fun reportHistoryRange(first: LocalDate, today: LocalDate): String =
    when (ChronoUnit.DAYS.between(first, today).coerceAtLeast(0L)) {
        in 0..25 -> "1mo"
        in 26..80 -> "3mo"
        in 81..165 -> "6mo"
        in 166..350 -> "1y"
        in 351..700 -> "2y"
        in 701..1750 -> "5y"
        in 1751..3500 -> "10y"
        else -> "max"
    }

class Services(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val secure = SecureSettings(context)
    val repo = Repository(Database.create(context), scope)
    val auth = AuthManager(secure, repo)
    val sync = SyncManager(repo, auth)
    val market = Router(secure)
    val news = NewsRepository(repo, market, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    val discovery by lazy { Discovery(this) }
    val foregroundSecurities = MutableStateFlow<List<Security>>(emptyList())
    val updater = Updater(context, secure)
    val initialized = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val marketStatus = MutableStateFlow("Derniers cours en cache")
    val liveQuotes: LiveQuoteProvider = ForegroundQuoteProvider(market)
    private val quoteLocks = ConcurrentHashMap<String, Mutex>()
    private val recentQuotes = ConcurrentHashMap<String, Quote>()
    private val reportHistoryRefresh = ConcurrentHashMap<String, Long>()

    /** Fetch only real daily closes needed by Reports. Retain older cached closes on errors. */
    suspend fun refreshReportHistory(portfolioId: String?, force: Boolean = false) {
        val wallet = repo.current()
        val transactions = wallet.transactions.filter { portfolioId == null || it.portfolioId == portfolioId }
        val ids = transactions
            .mapNotNull { it.securityId }.toSet()
        val benchmarks = (Catalog.markets.filter { it.symbol in
            setOf("^GSPC", "^IXIC", "^GSPTSE", "CAD=X") } +
            Catalog.stocks.filter { it.symbol == "XEQT.TO" })
        val securities = (wallet.securities.filter { it.id in ids } + benchmarks).distinctBy { it.id }
        val today = LocalDate.now()
        val portfolioStart = transactions.minOfOrNull { LocalDate.parse(it.date) } ?: today
        val gate = Semaphore(3)
        supervisorScope {
            securities.map { security -> async {
                gate.withPermit {
                    val related = transactions.filter { it.securityId == security.id }
                    val firstNeeded = related.minOfOrNull { LocalDate.parse(it.date) }
                        ?: portfolioStart
                    val range = reportHistoryRange(firstNeeded, today)
                    val key = "${security.id}:$range:${related.hashCode()}"
                    val existing = wallet.prices.filter { it.securityId == security.id }
                    val earliestNeeded = firstNeeded
                    val missingTransactionClose = related.any { transaction ->
                        val day = LocalDate.parse(transaction.date)
                        day < today && day.dayOfWeek !in
                            setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) &&
                            existing.none { it.date == transaction.date }
                    }
                    val covered = existing.size >= 2 &&
                        existing.any { it.date <= earliestNeeded.plusDays(7).toString() } &&
                        existing.any { it.date >= today.minusDays(7).toString() } &&
                        !missingTransactionClose
                    val now = System.currentTimeMillis()
                    if (!force && (covered || now - (reportHistoryRefresh[key] ?: 0L) < 6 * 60 * 60_000L))
                        return@withPermit
                    try {
                        val fresh = market.history(security, range, "1d")
                        if (fresh.isNotEmpty()) {
                            // Repository merges closes with any longer history already cached.
                            repo.points(security.id, fresh)
                            reportHistoryRefresh[key] = now
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Failed requests remain retryable; never replace a real close.
                    }
                }
            } }.awaitAll()
        }
    }

    suspend fun refreshQuote(security: Security): Quote =
        quoteLocks.getOrPut(security.id) { Mutex() }.withLock {
            recentQuotes[security.id]?.takeIf {
                System.currentTimeMillis() - it.fetchedAt < 5_000
            } ?: liveQuotes.quote(security).cached.also { fresh ->
                repo.quote(fresh)
                recentQuotes[security.id] = fresh
            }
        }

    suspend fun refreshForeground(securities: List<Security>): Boolean {
        if (busy.value || securities.isEmpty()) return false
        val before = repo.state.value.quotes
        val gate = Semaphore(2)
        val updates = supervisorScope {
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
            }.awaitAll().filterNotNull()
        }
        val changed = updates.any { fresh -> before[fresh.securityId]?.let { old ->
            fresh.timestamp > old.timestamp || fresh.price != old.price ||
                fresh.preMarketTimestamp != old.preMarketTimestamp ||
                fresh.afterHoursTimestamp != old.afterHoursTimestamp
        } ?: true }
        marketStatus.value = if (updates.isEmpty())
            "Derniers cours en cache · fournisseur indisponible"
        else if (updates.any { (it.delay ?: 0) > 0 })
            "Cotation vérifiée · certains cours différés"
        else if (changed) "Nouvelle cotation reçue · délai non garanti"
        else "Dernière cotation inchangée · délai non garanti"
        return true
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
            try { news.refresh(w) } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* The quote refresh and cached news remain usable. */ }
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
