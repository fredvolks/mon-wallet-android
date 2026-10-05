package ca.monwallet.app.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "wallet_records",
    primaryKeys = ["owner", "id"],
    indices = [Index("owner"), Index("kind")],
)
data class Record(
    val id: String,
    val owner: String = "guest",
    val kind: String,
    val payload: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val serverVersion: Long = 0,
    val dirty: Boolean = true,
    val conflict: String? = null,
)

@Entity(tableName = "market_cache")
data class Cache(
    @PrimaryKey val id: String,
    val kind: String,
    val payload: String,
    val fetchedAt: Long,
)

@androidx.room.Dao
interface Dao {
    @Query("SELECT * FROM wallet_records WHERE owner=:owner")
    fun observe(owner: String): Flow<List<Record>>

    @Query("SELECT * FROM wallet_records WHERE owner=:owner")
    suspend fun records(owner: String): List<Record>

    @Query("SELECT * FROM wallet_records WHERE owner=:owner AND id=:id")
    suspend fun get(id: String, owner: String): Record?

    @Upsert suspend fun put(record: Record)

    @Upsert suspend fun putAll(records: List<Record>)

    @Query("DELETE FROM wallet_records WHERE owner=:owner") suspend fun clear(owner: String)

    @Query("SELECT * FROM market_cache") fun observeCache(): Flow<List<Cache>>

    @Query("SELECT * FROM market_cache") suspend fun cache(): List<Cache>

    @Upsert suspend fun cache(row: Cache)
}

@androidx.room.Database(entities = [Record::class, Cache::class], version = 2, exportSchema = true)
abstract class Database : RoomDatabase() {
    abstract fun dao(): Dao

    companion object {
        val migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE wallet_records ADD COLUMN conflict TEXT DEFAULT NULL")
                }
            }

        fun create(context: Context) =
            Room.databaseBuilder(context, Database::class.java, "monwallet.db")
                .addMigrations(migration)
                .build()
    }
}
