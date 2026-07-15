package com.fliptofocus.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// Entities
// ---------------------------------------------------------------------------

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val isEnabled: Boolean,
    val addedAt: Long
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimestamp: Long,
    val endTimestamp: Long?,
    val challengeDurationMillis: Long,
    val triggeringPackage: String,
    val status: String
)

@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey val id: Int = 1,
    // --- Legacy columns retained from the pre-lock schema so the v3 -> v4 migration is purely
    //     additive (no table recreation). They are unused by the parental lock. ---
    val challengeType: String = "PIN",
    @ColumnInfo(defaultValue = "MEDIUM") val difficulty: String = "MEDIUM",
    val challengeDurationMinutes: Int = 5,
    val requireFaceDown: Boolean = false,
    val motionTolerance: Float = 1.0f,
    val shakeCount: Int = 30,
    val mathProblemCount: Int = 3,
    // Master switch (kept as a column; now means "locking enabled").
    val isBlockingEnabled: Boolean = true,
    // --- Lock credentials + settings, added in schema v4. Nullable so the migration needs no
    //     column defaults (existing rows simply get NULL). ---
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val comboHash: String? = null,
    val comboSalt: String? = null,
    val recoveryHash: String? = null,
    val recoverySalt: String? = null,
    val relockGraceSeconds: Int? = null
)

// ---------------------------------------------------------------------------
// DAOs
// ---------------------------------------------------------------------------

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps ORDER BY appLabel")
    fun observeAll(): Flow<List<BlockedAppEntity>>

    @Query("SELECT packageName FROM blocked_apps WHERE isEnabled = 1")
    fun observeEnabledPackages(): Flow<List<String>>

    @Query("SELECT * FROM blocked_apps WHERE isEnabled = 1")
    suspend fun getEnabled(): List<BlockedAppEntity>

    @Upsert
    suspend fun upsert(app: BlockedAppEntity)

    @Query("DELETE FROM blocked_apps WHERE packageName = :pkg")
    suspend fun deleteByPackage(pkg: String)

    @Query("UPDATE blocked_apps SET isEnabled = :enabled WHERE packageName = :pkg")
    suspend fun setEnabled(pkg: String, enabled: Boolean)

    @Query("UPDATE blocked_apps SET isEnabled = :enabled")
    suspend fun setAllEnabled(enabled: Boolean)

    @Query("SELECT COUNT(*) FROM blocked_apps")
    suspend fun count(): Int
}

@Dao
interface FocusSessionDao {
    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Update
    suspend fun update(session: FocusSessionEntity)

    @Query("SELECT * FROM focus_sessions ORDER BY startTimestamp DESC")
    fun observeAll(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE id = :id")
    suspend fun getById(id: Long): FocusSessionEntity?

    @Query("DELETE FROM focus_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM focus_sessions")
    suspend fun clearAll()
}

@Dao
interface AppConfigDao {
    @Query("SELECT * FROM app_config WHERE id = 1")
    fun observe(): Flow<AppConfigEntity?>

    @Query("SELECT * FROM app_config WHERE id = 1")
    suspend fun get(): AppConfigEntity?

    @Upsert
    suspend fun upsert(config: AppConfigEntity)
}

// ---------------------------------------------------------------------------
// Database
// ---------------------------------------------------------------------------

@Database(
    entities = [BlockedAppEntity::class, FocusSessionEntity::class, AppConfigEntity::class],
    version = 4,
    exportSchema = false
)
abstract class FlipToFocusDatabase : RoomDatabase() {
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun appConfigDao(): AppConfigDao
}
