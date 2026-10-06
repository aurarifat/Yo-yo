package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BoosterDatabase
import com.example.data.BoosterRepository
import com.example.data.CommandLogEntity
import com.example.data.GameProfileEntity
import com.example.data.SavedSettingEntity
import com.example.engine.BoosterFeatureId
import com.example.engine.FeatureCapabilityEngine
import com.example.engine.FeatureCapabilityReport
import com.example.engine.InstalledAppItem
import com.example.engine.NetworkAndStorageEngine
import com.example.engine.NetworkDiagnosticsReport
import com.example.engine.OptimizationProgressStep
import com.example.engine.PerformanceEngine
import com.example.engine.PermissionCenterItem
import com.example.engine.PermissionItemId
import com.example.engine.Stable90FpsReport
import com.example.engine.StorageAnalysisReport
import com.example.overlay.CrosshairConfig
import com.example.overlay.HardwareMonitorConfig
import com.example.overlay.OverlayStateController
import com.example.shizuku.OperationStatus
import com.example.shizuku.ShellExecutionResult
import com.example.shizuku.ShellExecutor
import com.example.shizuku.ShizukuConnectionState
import com.example.shizuku.ShizukuManager
import com.example.telemetry.DeviceTelemetryProvider
import com.example.telemetry.DeviceTelemetrySnapshot
import com.example.telemetry.ThermalLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ActiveOptimizationDialogState(
    val isVisible: Boolean = false,
    val title: String = "Optimizing System Performance",
    val targetGame: GameProfileEntity? = null,
    val isRunning: Boolean = false,
    val steps: List<OptimizationProgressStep> = emptyList(),
    val finalResult: ShellExecutionResult? = null
)

class BoosterViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val database = BoosterDatabase.getInstance(appContext)
    val repository = BoosterRepository(database.boosterDao())
    val telemetryProvider = DeviceTelemetryProvider(appContext)
    val performanceEngine = PerformanceEngine(appContext, repository, telemetryProvider)
    val networkAndStorageEngine = NetworkAndStorageEngine(appContext, repository)

    val shizukuState: StateFlow<ShizukuConnectionState> = ShizukuManager.state
    val telemetry: StateFlow<DeviceTelemetrySnapshot> = telemetryProvider.snapshot
    val crosshairConfig: StateFlow<CrosshairConfig> = OverlayStateController.crosshair
    val monitorConfig: StateFlow<HardwareMonitorConfig> = OverlayStateController.monitor

    val gameProfiles: StateFlow<List<GameProfileEntity>> = repository.allProfiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val modifiedSettings: StateFlow<List<SavedSettingEntity>> = repository.activeModifiedSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val commandLogs: StateFlow<List<CommandLogEntity>> = repository.recentLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _capabilities = MutableStateFlow<Map<BoosterFeatureId, FeatureCapabilityReport>>(emptyMap())
    val capabilities: StateFlow<Map<BoosterFeatureId, FeatureCapabilityReport>> = _capabilities.asStateFlow()

    private val _permissions = MutableStateFlow<List<PermissionCenterItem>>(emptyList())
    val permissions: StateFlow<List<PermissionCenterItem>> = _permissions.asStateFlow()

    private val _networkReport = MutableStateFlow(NetworkDiagnosticsReport())
    val networkReport: StateFlow<NetworkDiagnosticsReport> = _networkReport.asStateFlow()

    private val _storageReport = MutableStateFlow(StorageAnalysisReport())
    val storageReport: StateFlow<StorageAnalysisReport> = _storageReport.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    private val _stable90Report = MutableStateFlow<Stable90FpsReport?>(null)
    val stable90Report: StateFlow<Stable90FpsReport?> = _stable90Report.asStateFlow()

    private val _lastOperationBanner = MutableStateFlow<ShellExecutionResult?>(null)
    val lastOperationBanner: StateFlow<ShellExecutionResult?> = _lastOperationBanner.asStateFlow()

    private val _optimizationDialog = MutableStateFlow(ActiveOptimizationDialogState())
    val optimizationDialog: StateFlow<ActiveOptimizationDialogState> = _optimizationDialog.asStateFlow()

    private val _touchLatencyMs = MutableStateFlow<Float?>(null)
    val touchLatencyMs: StateFlow<Float?> = _touchLatencyMs.asStateFlow()

    private var activeLaunchedGameWithAutoRestore: GameProfileEntity? = null

    init {
        OverlayStateController.loadSavedConfig(appContext)
        ShizukuManager.initialize(appContext)
        telemetryProvider.startFrameMonitoring()

        viewModelScope.launch {
            seedDefaultGameProfileIfEmpty()
            refreshInstalledApps()
            refreshStorageAnalysis()
            _networkReport.value = networkAndStorageEngine.readNetworkSnapshot()
        }

        // Periodic hardware telemetry, thermal safety guard, and capability refresh loop
        viewModelScope.launch {
            while (isActive) {
                refreshAllState()
                delay(2500L)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        telemetryProvider.stopFrameMonitoring()
    }

    private suspend fun seedDefaultGameProfileIfEmpty() {
        val existing = repository.getProfile("com.dts.freefiremax")
        if (existing == null) {
            repository.upsertProfile(
                GameProfileEntity(
                    packageName = "com.dts.freefiremax",
                    gameName = "Free Fire MAX",
                    refreshRatePref = 90,
                    performanceProfile = "High",
                    resolutionPref = "Native",
                    crosshairOverlayEnabled = false,
                    hardwareMonitorEnabled = true,
                    touchOptimizationEnabled = true,
                    networkProfile = "Low Latency DNS",
                    thermalProfile = "Smart",
                    focusModeDndEnabled = false,
                    autoLaunch = true,
                    restoreAfterExit = true
                )
            )
        }
    }

    fun refreshAllState() {
        val snap = telemetryProvider.refresh()
        ShizukuManager.recheck(appContext)
        _permissions.value = FeatureCapabilityEngine.checkAllPermissions(appContext)
        _networkReport.value = networkAndStorageEngine.readNetworkSnapshot(_networkReport.value)

        val modKeys = modifiedSettings.value.map { it.settingKey }.toSet()

        // Smart Thermal Guard: if CRITICAL, automatically disable aggressive fixed performance
        if (snap.thermalLevel == ThermalLevel.CRITICAL && "fixed_performance_mode" in modKeys) {
            viewModelScope.launch {
                performanceEngine.setFixedPerformanceMode(false)
            }
        }

        _capabilities.value = FeatureCapabilityEngine.evaluateCapabilities(
            context = appContext,
            telemetry = snap,
            modifiedSettingsKeys = modKeys,
            crosshairEnabled = crosshairConfig.value.enabled,
            monitorEnabled = monitorConfig.value.enabled
        )
    }

    fun onAppResumedFromBackground() {
        refreshAllState()
        val pendingRestoreGame = activeLaunchedGameWithAutoRestore
        if (pendingRestoreGame != null && pendingRestoreGame.restoreAfterExit) {
            val stillInForeground = performanceEngine.isGameProcessActive(pendingRestoreGame.packageName)
            if (!stillInForeground) {
                activeLaunchedGameWithAutoRestore = null
                viewModelScope.launch {
                    val res = performanceEngine.restoreAllModifiedSettings(clearGameProfiles = false)
                    _lastOperationBanner.value = ShellExecutionResult(
                        status = OperationStatus.SUCCESS,
                        command = "AutomaticRestore(${pendingRestoreGame.gameName})",
                        exitCode = 0,
                        technicalReason = "Game session ended for ${pendingRestoreGame.gameName}: ${res.technicalReason}"
                    )
                    refreshAllState()
                }
            }
        }
    }

    fun dismissBanner() {
        _lastOperationBanner.value = null
    }

    // =========================================================================
    // SHIZUKU & SHELL EXECUTOR ACTIONS
    // =========================================================================
    fun recheckShizuku() {
        val state = ShizukuManager.recheck(appContext)
        refreshAllState()
        _lastOperationBanner.value = ShellExecutionResult(
            status = if (state.isConnected) OperationStatus.SUCCESS else OperationStatus.PERMISSION_REQUIRED,
            command = "Shizuku.recheck",
            exitCode = if (state.isConnected) 0 else -1,
            technicalReason = "${state.statusHeadline}: ${state.statusDetail}"
        )
    }

    fun requestShizukuPermission() {
        val res = ShizukuManager.requestPermission()
        _lastOperationBanner.value = res
        refreshAllState()
    }

    fun openShizukuApp() {
        val res = ShizukuManager.openShizukuApp(appContext)
        _lastOperationBanner.value = res
    }

    fun openWirelessDebuggingGuide() {
        val res = ShizukuManager.openDeveloperSettings(appContext)
        _lastOperationBanner.value = res
    }

    fun runAllowlistedShellCommand(command: String) {
        viewModelScope.launch {
            val res = ShellExecutor.execute(command)
            repository.logResult("Shizuku ShellExecutor", res)
            _lastOperationBanner.value = res
            refreshAllState()
        }
    }

    // =========================================================================
    // PERFORMANCE & TUNER ACTIONS
    // =========================================================================
    fun toggleFixedPerformance(enable: Boolean) {
        viewModelScope.launch {
            val res = performanceEngine.setFixedPerformanceMode(enable)
            _lastOperationBanner.value = res
            refreshAllState()
        }
    }

    fun startSystemOptimizationDialog(targetGame: GameProfileEntity? = null) {
        val title = if (targetGame != null) {
            "Optimizing ${targetGame.gameName}"
        } else {
            "Optimizing System Performance"
        }
        _optimizationDialog.value = ActiveOptimizationDialogState(
            isVisible = true,
            title = title,
            targetGame = targetGame,
            isRunning = true,
            steps = emptyList(),
            finalResult = null
        )

        viewModelScope.launch {
            val result = performanceEngine.runOptimizationSequence(
                targetGame = targetGame,
                selectedBackgroundPackages = null
            ) { updatedSteps ->
                _optimizationDialog.value = _optimizationDialog.value.copy(steps = updatedSteps)
            }

            if (targetGame != null && result.isSuccess && targetGame.restoreAfterExit) {
                activeLaunchedGameWithAutoRestore = targetGame
            }

            _optimizationDialog.value = _optimizationDialog.value.copy(
                isRunning = false,
                finalResult = result
            )
            _lastOperationBanner.value = result
            refreshAllState()
        }
    }

    fun dismissOptimizationDialog() {
        _optimizationDialog.value = ActiveOptimizationDialogState(isVisible = false)
    }

    fun applyRefreshRate(targetHz: Int) {
        viewModelScope.launch {
            val res = performanceEngine.applyRefreshRate(targetHz)
            _lastOperationBanner.value = res
            refreshAllState()
        }
    }

    fun resetRefreshRate() {
        viewModelScope.launch {
            val res = performanceEngine.resetRefreshRate()
            _lastOperationBanner.value = res
            refreshAllState()
        }
    }

    fun activate90FpsStableProfile(packageName: String? = null) {
        viewModelScope.launch {
            val report = performanceEngine.apply90FpsStableProfile(packageName)
            _stable90Report.value = report
            _lastOperationBanner.value = report.operationResult
            refreshAllState()
        }
    }

    fun runNetworkLatencyCheck() {
        viewModelScope.launch {
            val (report, res) = networkAndStorageEngine.runLatencyDiagnostics()
            _networkReport.value = report
            _lastOperationBanner.value = res
            refreshAllState()
        }
    }

    fun applyPrivateDns(mode: String, hostname: String) {
        viewModelScope.launch {
            val res = networkAndStorageEngine.configurePrivateDns(mode, hostname)
            _networkReport.value = networkAndStorageEngine.readNetworkSnapshot(_networkReport.value)
            _lastOperationBanner.value = res
            refreshAllState()
        }
    }

    fun openAndroidDnsSettings() {
        _lastOperationBanner.value = networkAndStorageEngine.openAndroidNetworkSettings()
    }

    fun recordMeasuredTouchLatency(deltaMs: Float) {
        _touchLatencyMs.value = deltaMs
    }

    fun applyTouchOptimization() {
        viewModelScope.launch {
            val res = performanceEngine.optimizeTouchResponse(_touchLatencyMs.value)
            _lastOperationBanner.value = res
            refreshAllState()
        }
    }

    fun applyAnimationScale(scale: Float) {
        viewModelScope.launch {
            val res = performanceEngine.applyAnimationScale(scale)
            _lastOperationBanner.value = res
            refreshAllState()
        }
    }

    fun resetAnimationScale() {
        viewModelScope.launch {
            val res = performanceEngine.resetAnimationScale()
            _lastOperationBanner.value = res
            refreshAllState()
        }
    }

    fun toggleDisableWindowBlurs(disable: Boolean) {
        viewModelScope.launch {
            val res = performanceEngine.setDisableWindowBlurs(disable)
            _lastOperationBanner.value = res
            refreshAllState()
        }
    }

    fun toggleGamingFocusMode(enable: Boolean) {
        viewModelScope.launch {
            val res = performanceEngine.setGamingFocusMode(enable)
            _lastOperationBanner.value = res
            refreshAllState()
        }
    }

    fun toggleHideDeveloperOptions(hide: Boolean) {
        viewModelScope.launch {
            val res = performanceEngine.setHideDeveloperOptions(hide)
            _lastOperationBanner.value = res
            refreshAllState()
        }
    }

    fun applyScreenResolution(profile: String, customW: Int = 0, customH: Int = 0) {
        viewModelScope.launch {
            val res = performanceEngine.applyScreenResolution(profile, customW, customH)
            _lastOperationBanner.value = res
            refreshAllState()
        }
    }

    fun resetScreenResolution() {
        viewModelScope.launch {
            val res = performanceEngine.resetScreenResolution()
            _lastOperationBanner.value = res
            refreshAllState()
        }
    }

    fun applyNativeGameMode(packageName: String, modeLabel: String) {
        viewModelScope.launch {
            val res = performanceEngine.applyNativeGameMode(packageName, modeLabel)
            _lastOperationBanner.value = res
            refreshAllState()
        }
    }

    fun refreshStorageAnalysis() {
        viewModelScope.launch {
            _storageReport.value = networkAndStorageEngine.analyzeStorage()
        }
    }

    fun confirmAndExecuteStorageCleanup() {
        viewModelScope.launch {
            val res = networkAndStorageEngine.executeSafeStorageCleanup()
            _storageReport.value = networkAndStorageEngine.analyzeStorage()
            _lastOperationBanner.value = res
            refreshAllState()
        }
    }

    // =========================================================================
    // OVERLAYS
    // =========================================================================
    fun updateCrosshair(transform: (CrosshairConfig) -> CrosshairConfig) {
        val res = OverlayStateController.updateCrosshairConfig(appContext, transform)
        _lastOperationBanner.value = res
        refreshAllState()
    }

    fun updateHardwareMonitor(transform: (HardwareMonitorConfig) -> HardwareMonitorConfig) {
        val res = OverlayStateController.updateMonitorConfig(appContext, transform)
        _lastOperationBanner.value = res
        refreshAllState()
    }

    // =========================================================================
    // GAME PROFILES & LAUNCHER
    // =========================================================================
    fun refreshInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = performanceEngine.loadLaunchableApps()
        }
    }

    fun saveGameProfile(profile: GameProfileEntity) {
        viewModelScope.launch {
            repository.upsertProfile(profile)
            _lastOperationBanner.value = ShellExecutionResult(
                status = OperationStatus.SUCCESS,
                command = "SaveGameProfile(${profile.packageName})",
                exitCode = 0,
                technicalReason = "Saved per-game profile for '${profile.gameName}' (${profile.refreshRatePref}Hz, ${profile.performanceProfile})."
            )
        }
    }

    fun deleteGameProfile(packageName: String) {
        viewModelScope.launch {
            repository.deleteProfile(packageName)
        }
    }

    fun launchGameWithProfile(profile: GameProfileEntity) {
        viewModelScope.launch {
            repository.upsertProfile(profile.copy(lastLaunchedTimestamp = System.currentTimeMillis()))
            startSystemOptimizationDialog(targetGame = profile)
        }
    }

    // =========================================================================
    // PERMISSION CENTER & RESET ALL
    // =========================================================================
    fun openPermission(id: PermissionItemId) {
        FeatureCapabilityEngine.openPermissionSettings(appContext, id)
    }

    fun resetAllFunctions(clearProfiles: Boolean) {
        viewModelScope.launch {
            val res = performanceEngine.restoreAllModifiedSettings(clearGameProfiles = clearProfiles)
            if (clearProfiles) {
                seedDefaultGameProfileIfEmpty()
            }
            _stable90Report.value = null
            _lastOperationBanner.value = res
            refreshAllState()
        }
    }
}
