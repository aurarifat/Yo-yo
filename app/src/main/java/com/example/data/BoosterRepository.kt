package com.example.data

import com.example.shizuku.OperationStatus
import com.example.shizuku.ShellExecutionResult
import kotlinx.coroutines.flow.Flow

class BoosterRepository(private val dao: BoosterDao) {

    val allProfiles: Flow<List<GameProfileEntity>> = dao.getAllProfiles()
    val activeModifiedSettings: Flow<List<SavedSettingEntity>> = dao.getActiveModifiedSettings()
    val recentLogs: Flow<List<CommandLogEntity>> = dao.getRecentLogs()

    suspend fun getProfile(packageName: String): GameProfileEntity? =
        dao.getProfileByPackage(packageName)

    suspend fun upsertProfile(profile: GameProfileEntity) =
        dao.upsertProfile(profile)

    suspend fun deleteProfile(packageName: String) =
        dao.deleteProfile(packageName)

    suspend fun clearAllProfiles() =
        dao.clearAllProfiles()

    /**
     * Stores the original system setting value before modifying it.
     * If an original value is already recorded for [key], we preserve the earliest originalValue
     * and only update [appliedValue].
     */
    suspend fun recordSettingChange(key: String, originalValue: String, appliedValue: String) {
        val existing = dao.getSavedSetting(key)
        val preservedOriginal = existing?.originalValue ?: originalValue
        dao.saveOriginalSetting(
            SavedSettingEntity(
                settingKey = key,
                originalValue = preservedOriginal,
                appliedValue = appliedValue,
                modifiedByBooster = true,
                timestampMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun getSavedSetting(key: String): SavedSettingEntity? =
        dao.getSavedSetting(key)

    suspend fun getModifiedSettingsSnapshot(): List<SavedSettingEntity> =
        dao.getActiveModifiedSettingsSnapshot()

    suspend fun markSettingRestored(key: String) =
        dao.removeSavedSetting(key)

    suspend fun clearAllSavedSettings() =
        dao.clearAllSavedSettings()

    suspend fun logResult(featureName: String, result: ShellExecutionResult) {
        dao.insertLog(
            CommandLogEntity(
                featureName = featureName,
                commandOrApi = result.command,
                statusBadge = result.status.badge,
                statusName = result.status.name,
                technicalReason = result.technicalReason,
                timestampMs = result.timestampMs
            )
        )
    }

    suspend fun logOperation(
        featureName: String,
        commandOrApi: String,
        status: OperationStatus,
        technicalReason: String
    ) {
        dao.insertLog(
            CommandLogEntity(
                featureName = featureName,
                commandOrApi = commandOrApi,
                statusBadge = status.badge,
                statusName = status.name,
                technicalReason = technicalReason
            )
        )
    }

    suspend fun clearLogs() = dao.clearLogs()
}
