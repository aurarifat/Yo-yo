package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.engine.PermissionItemId
import com.example.shizuku.ShizukuManager
import com.example.ui.BoosterViewModel
import com.example.ui.components.OperationResultBanner
import com.example.ui.components.OptimizationSequenceDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.GameLauncherScreen
import com.example.ui.screens.OverlaysScreen
import com.example.ui.screens.PermissionCenterScreen
import com.example.ui.screens.SystemTunerScreen
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoltGreen
import kotlin.math.roundToInt

enum class BoosterTab(val label: String, val icon: ImageVector, val testTag: String) {
    ENGINE("Engine", Icons.Default.Dashboard, "nav_tab_engine"),
    TUNER("Tuner", Icons.Default.Build, "nav_tab_tuner"),
    GAMES("Games", Icons.Default.SportsEsports, "nav_tab_games"),
    OVERLAYS("Overlays", Icons.Default.CenterFocusStrong, "nav_tab_overlays"),
    PERMISSIONS("Permissions", Icons.Default.Security, "nav_tab_permissions")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ShizukuManager.initialize(applicationContext)
        setContent {
            MyApplicationTheme {
                ApexBoosterApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            ShizukuManager.cleanup()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApexBoosterApp(viewModel: BoosterViewModel = viewModel()) {
    var currentTab by rememberSaveable { mutableStateOf(BoosterTab.ENGINE) }

    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val networkReport by viewModel.networkReport.collectAsStateWithLifecycle()
    val storageReport by viewModel.storageReport.collectAsStateWithLifecycle()
    val gameProfiles by viewModel.gameProfiles.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val modifiedSettings by viewModel.modifiedSettings.collectAsStateWithLifecycle()
    val commandLogs by viewModel.commandLogs.collectAsStateWithLifecycle()
    val stable90Report by viewModel.stable90Report.collectAsStateWithLifecycle()
    val lastOperationBanner by viewModel.lastOperationBanner.collectAsStateWithLifecycle()
    val optimizationDialog by viewModel.optimizationDialog.collectAsStateWithLifecycle()
    val crosshairConfig by viewModel.crosshairConfig.collectAsStateWithLifecycle()
    val monitorConfig by viewModel.monitorConfig.collectAsStateWithLifecycle()
    val touchLatencyMs by viewModel.touchLatencyMs.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResumedFromBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (currentTab != BoosterTab.ENGINE) {
        BackHandler {
            currentTab = BoosterTab.ENGINE
        }
    }

    OptimizationSequenceDialog(
        state = optimizationDialog,
        onDismiss = { viewModel.dismissOptimizationDialog() }
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SlateSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "APEXBOOST ADB ENGINE",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                letterSpacing = 0.6.sp
                            )
                            Text(
                                text = "${telemetry.manufacturer} ${telemetry.model} • ${telemetry.currentRefreshRateHz.roundToInt()} Hz",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        val shizukuColor = if (shizukuState.isConnected) VoltGreen else AmberWarn
                        Surface(
                            color = shizukuColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .border(1.dp, shizukuColor.copy(alpha = 0.5f), RoundedCornerShape(50))
                                .testTag("topbar_shizuku_status_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(shizukuColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (shizukuState.isConnected) "SHIZUKU: CONNECTED" else "SHIZUKU: DISCONNECTED",
                                    color = shizukuColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SlateSurface,
                tonalElevation = 8.dp
            ) {
                BoosterTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OperationResultBanner(
                result = lastOperationBanner,
                onDismiss = { viewModel.dismissBanner() }
            )

            val selectedGamePkg = gameProfiles.firstOrNull()?.packageName ?: "com.dts.freefiremax"

            when (currentTab) {
                BoosterTab.ENGINE -> {
                    DashboardScreen(
                        shizukuState = shizukuState,
                        telemetry = telemetry,
                        capabilities = capabilities,
                        stable90Report = stable90Report,
                        modifiedSettingsCount = modifiedSettings.size,
                        recentLogs = commandLogs,
                        onRecheckShizuku = { viewModel.recheckShizuku() },
                        onRequestShizukuPermission = { viewModel.requestShizukuPermission() },
                        onOpenShizuku = { viewModel.openShizukuApp() },
                        onOpenDeveloperOptions = { viewModel.openWirelessDebuggingGuide() },
                        onOptimizeSystem = { viewModel.startSystemOptimizationDialog(null) },
                        onActivate90FpsStable = { viewModel.activate90FpsStableProfile(selectedGamePkg) },
                        onResetAll = { clearProfiles -> viewModel.resetAllFunctions(clearProfiles) },
                        onRunShellDiagnostic = { cmd -> viewModel.runAllowlistedShellCommand(cmd) },
                        onRefreshTelemetry = { viewModel.refreshAllState() },
                        modifier = Modifier.weight(1f)
                    )
                }

                BoosterTab.TUNER -> {
                    SystemTunerScreen(
                        telemetry = telemetry,
                        capabilities = capabilities,
                        networkReport = networkReport,
                        storageReport = storageReport,
                        touchLatencyMs = touchLatencyMs,
                        selectedGamePackage = selectedGamePkg,
                        onToggleFixedPerformance = { viewModel.toggleFixedPerformance(it) },
                        onApplyRefreshRate = { viewModel.applyRefreshRate(it) },
                        onResetRefreshRate = { viewModel.resetRefreshRate() },
                        onRunNetworkCheck = { viewModel.runNetworkLatencyCheck() },
                        onApplyPrivateDns = { mode, host -> viewModel.applyPrivateDns(mode, host) },
                        onOpenAndroidDnsSettings = { viewModel.openAndroidDnsSettings() },
                        onRecordTouchLatency = { viewModel.recordMeasuredTouchLatency(it) },
                        onApplyTouchOptimization = { viewModel.applyTouchOptimization() },
                        onApplyAnimationScale = { viewModel.applyAnimationScale(it) },
                        onResetAnimationScale = { viewModel.resetAnimationScale() },
                        onToggleWindowBlurs = { viewModel.toggleDisableWindowBlurs(it) },
                        onToggleGamingFocusMode = { viewModel.toggleGamingFocusMode(it) },
                        onToggleHideDeveloperOptions = { viewModel.toggleHideDeveloperOptions(it) },
                        onApplyResolution = { prof, w, h -> viewModel.applyScreenResolution(prof, w, h) },
                        onResetResolution = { viewModel.resetScreenResolution() },
                        onApplyNativeGameMode = { pkg, mode -> viewModel.applyNativeGameMode(pkg, mode) },
                        onRefreshStorage = { viewModel.refreshStorageAnalysis() },
                        onConfirmCleanStorage = { viewModel.confirmAndExecuteStorageCleanup() },
                        modifier = Modifier.weight(1f)
                    )
                }

                BoosterTab.GAMES -> {
                    GameLauncherScreen(
                        profiles = gameProfiles,
                        installedApps = installedApps,
                        supportedRefreshRates = telemetry.supportedRefreshRatesHz,
                        onSaveProfile = { viewModel.saveGameProfile(it) },
                        onDeleteProfile = { viewModel.deleteGameProfile(it) },
                        onLaunchProfile = { viewModel.launchGameWithProfile(it) },
                        onRefreshInstalledApps = { viewModel.refreshInstalledApps() },
                        modifier = Modifier.weight(1f)
                    )
                }

                BoosterTab.OVERLAYS -> {
                    OverlaysScreen(
                        crosshairConfig = crosshairConfig,
                        monitorConfig = monitorConfig,
                        capabilities = capabilities,
                        onUpdateCrosshair = { viewModel.updateCrosshair(it) },
                        onUpdateMonitor = { viewModel.updateHardwareMonitor(it) },
                        onOpenOverlayPermission = { viewModel.openPermission(PermissionItemId.OVERLAY) },
                        modifier = Modifier.weight(1f)
                    )
                }

                BoosterTab.PERMISSIONS -> {
                    PermissionCenterScreen(
                        permissions = permissions,
                        onOpenSettings = { viewModel.openPermission(it) },
                        onRecheckAll = { viewModel.refreshAllState() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
