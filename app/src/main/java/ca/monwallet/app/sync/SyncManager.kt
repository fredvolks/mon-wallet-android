package ca.monwallet.app.sync

import android.os.Build
import androidx.room.withTransaction
import ca.monwallet.app.auth.AuthManager
import ca.monwallet.app.data.Repository
import ca.monwallet.app.database.Record
import ca.monwallet.app.marketdata.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class SyncManager(private val repo: Repository, private val auth: AuthManager) {
    val status = MutableStateFlow("Mode invité · données locales")
    private val mutex = Mutex()

    suspend fun rpc(name: String, body: JSONObject = JSONObject()) =
        auth.request("/rest/v1/rpc/$name", body, auth.token())

    private fun read(j: JSONObject, owner: String) =
        Record(
            j.getString("id"),
            owner,
            j.getString("kind"),
            j.getJSONObject("payload").toString(),
            j.getLong("created_at"),
            j.getLong("updated_at"),
            j.optLong("deleted_at").takeIf { !j.isNull("deleted_at") },
            j.getLong("version"),
            false,
        )

    suspend fun sync() =
        mutex.withLock {
            val user = auth.user.value
            if (user == null) {
                status.value = "Mode invité · données locales"
                return@withLock
            }
            check(repo.owner.value == user)
            status.value = "Synchronisation…"
            try {
                rpc(
                    "register_device",
                    JSONObject().put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}"),
                )
                val queue = repo.dao.records(user).filter { it.dirty && it.conflict == null }
                for (r in queue) {
                    check(auth.user.value == user && repo.owner.value == user)
                    val result =
                        rpc(
                            "push_record",
                            JSONObject()
                                .put("record_id", r.id)
                                .put("record_kind", r.kind)
                                .put("record_payload", JSONObject(r.payload))
                                .put("record_created_at", r.createdAt)
                                .put("record_deleted_at", r.deletedAt ?: JSONObject.NULL)
                                .put("expected_version", r.serverVersion),
                        )
                    val remote = read(result.getJSONObject("record"), user)
                    repo.db.withTransaction {
                        val current = repo.dao.get(r.id, user) ?: return@withTransaction
                        if (result.getBoolean("accepted")) {
                            repo.dao.put(
                                if (
                                    current.updatedAt == r.updatedAt &&
                                        current.payload == r.payload &&
                                        current.deletedAt == r.deletedAt
                                )
                                    remote
                                else current.copy(serverVersion = remote.serverVersion)
                            )
                        } else
                            repo.dao.put(
                                current.copy(conflict = result.getJSONObject("record").toString())
                            )
                    }
                }
                var after = ""
                do {
                    check(auth.user.value == user && repo.owner.value == user)
                    val page = rpc("pull_records", JSONObject().put("after_id", after))
                    val rows = page.getJSONArray("records").objects()
                    repo.db.withTransaction {
                        rows.forEach { j ->
                            val remote = read(j, user)
                            val local = repo.dao.get(remote.id, user)
                            if (local == null || !local.dirty) repo.dao.put(remote)
                            else if (
                                local.serverVersion != remote.serverVersion &&
                                    local.conflict == null
                            )
                                repo.dao.put(local.copy(conflict = j.toString()))
                        }
                    }
                    if (rows.isNotEmpty()) after = rows.last().getString("id")
                } while (rows.size == 200)
                val remaining = repo.dao.records(user)
                val conflicts = remaining.count { it.conflict != null }
                status.value =
                    if (conflicts > 0) "$conflicts conflit(s) à résoudre"
                    else if (remaining.any { it.dirty }) "Synchronisation en attente"
                    else "✓ Synchronisé"
            } catch (e: Exception) {
                status.value = "Hors ligne · synchronisation en attente"
                throw e
            }
        }

    suspend fun conflicts() = repo.dao.records(repo.owner.value).filter { it.conflict != null }

    suspend fun resolve(id: String, keepLocal: Boolean) {
        repo.db.withTransaction {
            val local = repo.dao.get(id, repo.owner.value) ?: return@withTransaction
            val remote =
                read(JSONObject(local.conflict ?: return@withTransaction), repo.owner.value)
            repo.dao.put(
                if (keepLocal)
                    local.copy(
                        serverVersion = remote.serverVersion,
                        conflict = null,
                        dirty = true,
                        updatedAt = System.currentTimeMillis(),
                    )
                else remote
            )
        }
        sync()
    }

    suspend fun devices() = rpc("list_devices").getJSONArray("devices").objects()

    suspend fun revoke(id: String) {
        rpc("revoke_device", JSONObject().put("target_session", id))
        sync()
    }
}
