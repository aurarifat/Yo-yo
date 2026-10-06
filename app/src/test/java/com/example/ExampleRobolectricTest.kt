package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.BoosterDatabase
import com.example.data.BoosterRepository
import com.example.data.GameProfileEntity
import com.example.engine.BoosterFeatureId
import com.example.engine.FeatureCapabilityEngine
import com.example.engine.NetworkAndStorageEngine
import com.example.engine.PerformanceEngine
import com.example.shizuku.OperationStatus
import com.example.shizuku.ShizukuManager
import com.example.telemetry.DeviceTelemetryProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name and verify telemetry and shizuku bridge`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ApexBoost Game Engine", appName)

        val shizukuState = ShizukuManager.recheck(context)
        assertFalse(shizukuState.isConnected)
        assertEquals("NOT CONNECTED", shizukuState.statusHeadline)

        val telemetryProvider = DeviceTelemetryProvider(context)
        val snap = telemetryProvider.refresh()
        assertTrue(snap.ramTotalBytes > 0L)
        assertTrue(snap.storageTotalBytes > 0L)
        assertTrue(snap.supportedRefreshRatesHz.isNotEmpty())

        val permissions = FeatureCapabilityEngine.checkAllPermissions(context)
        assertTrue(permissions.isNotEmpty())
        val capabilities = FeatureCapabilityEngine.evaluateCapabilities(
            context = context,
            telemetry = snap,
            modifiedSettingsKeys = emptySet(),
            crosshairEnabled = false,
            monitorEnabled = false
        )
        assertTrue(capabilities.containsKey(BoosterFeatureId.FIXED_PERFORMANCE))
        assertTrue(capabilities.containsKey(BoosterFeatureId.STABLE_90_FPS))
        assertTrue(capabilities.containsKey(BoosterFeatureId.TOUCH_RESPONSE))
    }

    @Test
    fun `verify performance engine, storage analysis, and game profile persistence`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, BoosterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = BoosterRepository(db.boosterDao())
        val telemetryProvider = DeviceTelemetryProvider(context)
        val engine = PerformanceEngine(context, repo, telemetryProvider)
        val netStorage = NetworkAndStorageEngine(context, repo)

        // Verify game profile save & load
        val profile = GameProfileEntity(
            packageName = "com.dts.freefiremax",
            gameName = "Free Fire MAX",
            refreshRatePref = 90,
            performanceProfile = "High"
        )
        repo.upsertProfile(profile)
        val loaded = repo.getProfile("com.dts.freefiremax")
        assertNotNull(loaded)
        assertEquals("Free Fire MAX", loaded?.gameName)

        // Verify Fixed Performance reports PERMISSION_REQUIRED when Shizuku is disconnected
        val fixedRes = engine.setFixedPerformanceMode(true)
        assertEquals(OperationStatus.PERMISSION_REQUIRED, fixedRes.status)

        // Verify Touch Response honestly reports hardware limitation
        val touchRes = engine.optimizeTouchResponse(8.4f)
        assertEquals(OperationStatus.SUCCESS, touchRes.status)
        assertTrue(touchRes.technicalReason.contains("Hardware touch sampling cannot be changed by this app."))

        // Verify Storage Optimizer analysis and cleanup
        val storageReport = netStorage.analyzeStorage()
        assertTrue(storageReport.totalBytes > 0L)
        assertTrue(storageReport.cleanableItems.isNotEmpty())

        // Verify Reset All Functions restores modified settings and logs verification
        repo.recordSettingChange("touch_optimization", "default", "optimized")
        val resetRes = engine.restoreAllModifiedSettings(clearGameProfiles = false)
        assertEquals(OperationStatus.SUCCESS, resetRes.status)
        assertTrue(repo.getModifiedSettingsSnapshot().isEmpty())

        db.close()
    }
}
