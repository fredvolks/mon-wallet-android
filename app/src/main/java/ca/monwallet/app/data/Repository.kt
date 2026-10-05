package ca.monwallet.app.data

import androidx.room.withTransaction
import ca.monwallet.app.database.*
import ca.monwallet.app.domain.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Repository(val db: Database, val scope: CoroutineScope) {
    val gson = Gson()
    val dao = db.dao()
    val owner = MutableStateFlow("guest")
    private val mutex = Mutex()
    @OptIn(ExperimentalCoroutinesApi::class)
    val state =
        owner
            .flatMapLatest { user ->
                combine(dao.observe(user), dao.observeCache()) { r, c -> decode(r, c) }
            }
            .stateIn(scope, SharingStarted.Eagerly, Wallet())

    fun decode(records: List<Record>, cache: List<Cache>): Wallet {
        val alive = records.filter { it.deletedAt == null }
        fun <T> rows(kind: String, c: Class<T>) =
            alive.filter { it.kind == kind }.map { gson.fromJson(it.payload, c) }
        val quotes =
            cache.filter { it.kind == "quote" }.map { gson.fromJson(it.payload, Quote::class.java) }
        val points =
            cache
                .filter { it.kind == "history" }
                .flatMap { gson.fromJson(it.payload, Array<Point>::class.java).toList() }
        return Wallet(
            rows("portfolio", Portfolio::class.java),
            (Catalog.all + rows("security", Security::class.java))
                .associateBy { it.id }
                .values
                .toList(),
            rows("transaction", Transaction::class.java),
            rows("watchlist", Watchlist::class.java).sortedBy { it.order },
            rows("watch_item", WatchItem::class.java),
            rows("alert", PriceAlert::class.java),
            rows("notification_preference", NotificationPreference::class.java),
            rows("event", AlertEvent::class.java),
            rows("setting", Setting::class.java).associate { it.id to it.value },
            quotes.associateBy { it.securityId },
            points,
            records.count { it.dirty },
        )
    }

    suspend fun current() = decode(dao.records(owner.value), dao.cache())

    private suspend fun raw(kind: String, id: String, value: Any) {
        val old = dao.get(id, owner.value)
        val now = System.currentTimeMillis()
        dao.put(
            Record(
                id,
                owner.value,
                kind,
                gson.toJson(value),
                old?.createdAt ?: now,
                now,
                serverVersion = old?.serverVersion ?: 0,
                conflict = old?.conflict,
            )
        )
    }

    suspend fun put(kind: String, id: String, value: Any) =
        mutex.withLock { db.withTransaction { raw(kind, id, value) } }

    suspend fun transaction(t: Transaction, security: Security?) =
        mutex.withLock {
            db.withTransaction {
                val s = current()
                require(s.portfolios.any { it.id == t.portfolioId }) { "Portefeuille introuvable." }
                require(t.date <= java.time.LocalDate.now().toString()) { "Date future interdite." }
                require(t.currency in listOf("CAD", "USD")) {
                    "Devises CAD et USD prises en charge."
                }
                if (t.securityId != null)
                    require(security != null && security.currency == t.currency) {
                        "Vérifie le marché et la devise."
                    }
                Engine.validate(s.transactions.filter { it.id != t.id } + t)
                security?.let { raw("security", it.id, it) }
                raw("transaction", t.id, t)
            }
        }

    suspend fun remove(id: String) =
        mutex.withLock {
            db.withTransaction {
                val old = dao.get(id, owner.value) ?: return@withTransaction
                val s = current()
                if (old.kind == "transaction")
                    Engine.validate(s.transactions.filter { it.id != id })
                if (old.kind == "portfolio")
                    require(s.transactions.none { it.portfolioId == id }) {
                        "Le portefeuille contient des transactions."
                    }
                if (old.kind == "watchlist")
                    s.items
                        .filter { it.watchlistId == id }
                        .forEach { item ->
                            dao.get(item.id, owner.value)?.let {
                                dao.put(
                                    it.copy(deletedAt = System.currentTimeMillis(), dirty = true)
                                )
                            }
                        }
                dao.put(
                    old.copy(
                        deletedAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        dirty = true,
                    )
                )
            }
        }

    suspend fun setting(key: String, value: String) = put("setting", key, Setting(key, value))

    suspend fun watch(security: Security, list: String) {
        if (current().items.any { it.watchlistId == list && it.securityId == security.id }) return
        put("security", security.id, security)
        val i =
            WatchItem(
                watchlistId = list,
                securityId = security.id,
                order = current().items.count { it.watchlistId == list },
            )
        put("watch_item", i.id, i)
    }

    suspend fun quote(q: Quote) =
        dao.cache(Cache(q.securityId, "quote", gson.toJson(q), q.fetchedAt))

    suspend fun points(id: String, points: List<Point>, intraday: Boolean = false) =
        dao.cache(
            Cache(
                "${if(intraday)"intraday"else"history"}:$id",
                if (intraday) "intraday" else "history",
                gson.toJson(points),
                System.currentTimeMillis(),
            )
        )

    suspend fun switch(user: String) = mutex.withLock { owner.value = user }

    suspend fun migrateGuest(user: String) =
        mutex.withLock {
            db.withTransaction {
                val guest = dao.records("guest")
                // Guest import is explicitly selected at sign-in. Preserve even empty objects.
                guest.forEach { r ->
                    if (dao.get(r.id, user) == null)
                        dao.put(r.copy(owner = user, serverVersion = 0, dirty = true))
                }
                dao.clear("guest")
                owner.value = user
            }
        }

    data class Backup(val formatVersion: Int, val records: List<Record>)

    suspend fun exportJson() = gson.toJson(Backup(1, dao.records(owner.value)))

    suspend fun restore(raw: String) =
        mutex.withLock {
            db.withTransaction {
                val backup = gson.fromJson(raw, Backup::class.java)
                require(backup.formatVersion == 1) { "Sauvegarde incompatible." }
                val merged = dao.records(owner.value).associateBy { it.id }.toMutableMap()
                val allowed =
                    setOf(
                        "portfolio",
                        "security",
                        "transaction",
                        "watchlist",
                        "watch_item",
                        "alert",
                        "notification_preference",
                        "event",
                        "setting",
                    )
                backup.records.forEach { r ->
                    require(r.kind in allowed)
                    merged[r.id] =
                        r.copy(
                            owner = owner.value,
                            serverVersion = merged[r.id]?.serverVersion ?: 0,
                            dirty = true,
                            conflict = null,
                            updatedAt = System.currentTimeMillis(),
                        )
                }
                val s = decode(merged.values.toList(), emptyList())
                require(
                    s.transactions.all { t ->
                        s.portfolios.any { it.id == t.portfolioId } &&
                            (t.securityId == null || s.securities.any { it.id == t.securityId })
                    }
                )
                Engine.validate(s.transactions)
                dao.putAll(merged.values.toList())
                backup.records.size
            }
        }
}
