package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "game_profiles")
data class GameProfileEntity(
    @PrimaryKey val packageName: String,
    val gameName: String,
    val refreshRatePref: Int = 90,
    val performanceProfile: String = "High",
    val resolutionPref: String = "Native",
    val customWidth: Int = 0,
    val customHeight: Int = 0,
    val crosshairOverlayEnabled: Boolean = false,
    val hardwareMonitorEnabled: Boolean = true,
    val touchOptimizationEnabled: Boolean = true,
    val networkProfile: String = "Default",
    val thermalProfile: String = "Smart",
    val focusModeDndEnabled: Boolean = false,
    val autoLaunch: Boolean = true,
    val restoreAfterExit: Boolean = true,
    val lastLaunchedTimestamp: Long = 0L
)

@Entity(tableName = "saved_system_settings")
data class SavedSettingEntity(
    @PrimaryKey val settingKey: String,
    val originalValue: String,
    val appliedValue: String,
    val modifiedByBooster: Boolean = true,
    val timestampMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "command_logs")
data class CommandLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val featureName: String,
    val commandOrApi: String,
    val statusBadge: String,
    val statusName: String,
    val technicalReason: String,
    val timestampMs: Long = System.currentTimeMillis()
)

@Dao
interface BoosterDao {
    @Query("SELECT * FROM game_profiles ORDER BY lastLaunchedTimestamp DESC, gameName ASC")
    fun getAllProfiles(): Flow<List<GameProfileEntity>>

    @Query("SELECT * FROM game_profiles WHERE packageName = :packageName LIMIT 1")
    suspend fun getProfileByPackage(packageName: String): GameProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: GameProfileEntity)

    @Query("DELETE FROM game_profiles WHERE packageName = :packageName")
    suspend fun deleteProfile(packageName: String)

    @Query("DELETE FROM game_profiles")
    suspend fun clearAllProfiles()

    @Query("SELECT * FROM saved_system_settings WHERE modifiedByBooster = 1")
    fun getActiveModifiedSettings(): Flow<List<SavedSettingEntity>>

    @Query("SELECT * FROM saved_system_settings WHERE modifiedByBooster = 1")
    suspend fun getActiveModifiedSettingsSnapshot(): List<SavedSettingEntity>

    @Query("SELECT * FROM saved_system_settings WHERE settingKey = :key LIMIT 1")
    suspend fun getSavedSetting(key: String): SavedSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOriginalSetting(setting: SavedSettingEntity)

    @Query("DELETE FROM saved_system_settings WHERE settingKey = :key")
    suspend fun removeSavedSetting(key: String)

    @Query("DELETE FROM saved_system_settings")
    suspend fun clearAllSavedSettings()

    @Query("SELECT * FROM command_logs ORDER BY timestampMs DESC LIMIT 60")
    fun getRecentLogs(): Flow<List<CommandLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: CommandLogEntity)

    @Query("DELETE FROM command_logs")
    suspend fun clearLogs()
}

@Database(
    entities = [
        GameProfileEntity::class,
        SavedSettingEntity::class,
        CommandLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BoosterDatabase : RoomDatabase() {
    abstract fun boosterDao(): BoosterDao

    companion object {
        @Volatile
        private var INSTANCE: BoosterDatabase? = null

        fun getInstance(context: Context): BoosterDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BoosterDatabase::class.java,
                    "apex_booster.db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
