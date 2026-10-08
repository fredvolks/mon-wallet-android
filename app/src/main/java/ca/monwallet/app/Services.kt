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
internal fun reportHistoryRefreshDue(force: Boolean, lastSuccessfulRefresh: Long?, nowMillis: Long): Boolean =
    force || lastSuccessfulRefresh == null ||
        nowMillis - lastSuccessfulRefresh >= 6 * 60 * 60_000L

internal fun reportHistoryHasCoverage(points: List<Point>, first: LocalDate, today: LocalDate): Boolean {
    val dates = points.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        .filter { it >= first && it <= today }.distinct().sorted()
    if (dates.isEmpty()) return false
    val firstClose = dates.first()
    val latestClose = dates.last()
    if (firstClose > first.plusDays(7) || latestClose < today.minusDays(7)) return false
    val weekdays = generateSequence(firstClose) { date ->
        if (date < latestClose) date.plusDays(1) else null
    }.count { it <= latestClose && it.dayOfWeek.value <= 5 }
    // On the purchase date itself, one dated quote is enough to value the new position.
    val minimum = if (first == today) 1 else maxOf(2, kotlin.math.ceil(weekdays * 0.60).toInt())
    return dates.size >= minimum
}

internal fun reportHistoryFallbackRange(range: String): String? = when (range) {
    "1mo" -> "3mo"
    "3mo" -> "6mo"
    "6mo" -> "1y"
    "1y" -> "2y"
    "2y" -> "5y"
    "5y" -> "10y"
    "10y" -> "max"
    else -> null
}

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
    private val reportHistoryAttempt = ConcurrentHashMap<String, Long>()
    val reportHistoryStatus = MutableStateFlow("")

    /** Fetch real daily closes for Reports. Cached endpoints alone cannot prove that
     * every session between the first trade and the latest close is present. Retain cached
     * closes on errors and retry at most every six hours unless forced. */
    suspend fun refreshReportHistory(portfolioId: String?, force: Boolean = false) {
        val wallet = repo.current()
        val transactions = wallet.transactions.filter { portfolioId == null || it.portfolioId == portfolioId }
        val ids = transactions.mapNotNull { it.securityId }.toSet()
        val usdIds = transactions.mapNotNull { tx ->
            tx.securityId?.takeIf { wallet.security(it)?.currency == "USD" }
        }.toSet()
        val needsFxHistory = usdIds.isNotEmpty()
        val benchmarks = (Catalog.markets.filter { it.symbol in
            setOf("^GSPC", "^IXIC", "^GSPTSE", "CAD=X") } +
            Catalog.stocks.filter { it.symbol == "XEQT.TO" })
        val securities = (wallet.securities.filter { it.id in ids } + benchmarks).distinctBy { it.id }
        val today = LocalDate.now()
        val portfolioStart = transactions.minOfOrNull { LocalDate.parse(it.date) } ?: today
        val gate = Semaphore(2)
        reportHistoryStatus.value = "Chargement des clôtures historiques…"
        data class FetchResult(val security: Security, val points: Int, val covered: Boolean,
            val error: String?)
        val fetched = supervisorScope {
            securities.map { security -> async {
                gate.withPermit {
                    val related = transactions.filter { it.securityId == security.id }
                    val isFx = security.symbol == "CAD=X" && needsFxHistory
                    val relevantTransactions = if (isFx) transactions.filter { it.securityId in usdIds } else related
                    val firstNeeded = relevantTransactions.minOfOrNull { LocalDate.parse(it.date) } ?: portfolioStart
                    val requiredForReports = security.id in ids || isFx
                    val range = reportHistoryRange(firstNeeded, today)
                    val key = "${security.id}:$range:${relevantTransactions.hashCode()}"
                    val now = System.currentTimeMillis()
                    val cached = wallet.prices.filter { it.securityId == security.id }
                    val cachedCovered = !requiredForReports ||
                        reportHistoryHasCoverage(cached, firstNeeded, today)
                    if (!reportHistoryRefreshDue(force, reportHistoryRefresh[key], now) ||
                        (!force && now - (reportHistoryAttempt[key] ?: 0L) < 90_000L))
                        return@withPermit FetchResult(security, cached.size, cachedCovered, null)
                    reportHistoryAttempt[key] = now
                    var points = cached
                    var failure: String? = null
                    try {
                        try {
                            val fresh = market.history(security, range, "1d")
                            points = (points + fresh).distinctBy { it.date }.sortedBy { it.date }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            failure = error.message?.take(90) ?: error::class.java.simpleName
                        }
                        var covered = !requiredForReports || reportHistoryHasCoverage(points, firstNeeded, today)
                        if (!covered) {
                            val fallback = reportHistoryFallbackRange(range)
                            if (fallback != null) {
                                try {
                                    val retry = market.history(security, fallback, "1d")
                                    points = (points + retry).distinctBy { it.date }.sortedBy { it.date }
                                    covered = reportHistoryHasCoverage(points, firstNeeded, today)
                                    if (covered) failure = null
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    failure = error.message?.take(90) ?: error::class.java.simpleName
                                }
                            }
                        }
                        if (points.isNotEmpty()) repo.points(security.id, points)
                        if (covered && points.isNotEmpty()) reportHistoryRefresh[key] = now
                        return@withPermit FetchResult(security, points.size, covered,
                            if (covered) null else failure ?: "historique partiel")
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        FetchResult(security, points.size, false,
                            error.message?.take(90) ?: error::class.java.simpleName)
                    }
                }
            } }.awaitAll()
        }
        val required = fetched.filter { it.security.id in ids ||
            (needsFxHistory && it.security.symbol == "CAD=X") }
        val good = required.count { it.covered && it.points > 0 }
        val totalPoints = required.sumOf { it.points }
        val incomplete = required.filterNot { it.covered && it.points > 0 }
            .map { (if (it.security.symbol == "CAD=X") "USD/CAD" else it.security.symbol) +
                (it.error?.let { e -> " ($e)" } ?: "") }
        reportHistoryStatus.value = if (required.isEmpty()) {
            "Aucun titre à recalculer pour ce portefeuille."
        } else if (incomplete.isEmpty()) {
            "Clôtures historiques reçues : $good/${required.size} titres · $totalPoints clôtures."
        } else {
            "Historique incomplet : $good/${required.size} titres · $totalPoints clôtures. À vérifier : ${incomplete.joinToString()}"
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
