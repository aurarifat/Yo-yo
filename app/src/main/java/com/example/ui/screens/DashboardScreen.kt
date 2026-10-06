package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CommandLogEntity
import com.example.engine.BoosterFeatureId
import com.example.engine.FeatureCapabilityReport
import com.example.engine.Stable90FpsReport
import com.example.shizuku.ShizukuConnectionState
import com.example.telemetry.DeviceTelemetryProvider
import com.example.telemetry.DeviceTelemetrySnapshot
import com.example.telemetry.ThermalLevel
import com.example.ui.components.FeatureStateChip
import com.example.ui.components.ResetAllConfirmDialog
import com.example.ui.components.WirelessDebuggingGuideDialog
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoltGreen
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    shizukuState: ShizukuConnectionState,
    telemetry: DeviceTelemetrySnapshot,
    capabilities: Map<BoosterFeatureId, FeatureCapabilityReport>,
    stable90Report: Stable90FpsReport?,
    modifiedSettingsCount: Int,
    recentLogs: List<CommandLogEntity>,
    onRecheckShizuku: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onOptimizeSystem: () -> Unit,
    onActivate90FpsStable: () -> Unit,
    onResetAll: (Boolean) -> Unit,
    onRunShellDiagnostic: (String) -> Unit,
    onRefreshTelemetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSetupGuide by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    if (showSetupGuide) {
        WirelessDebuggingGuideDialog(
            onDismiss = { showSetupGuide = false },
            onOpenDeveloperOptions = onOpenDeveloperOptions,
            onOpenShizuku = onOpenShizuku
        )
    }

    if (showResetDialog) {
        ResetAllConfirmDialog(
            modifiedSettingsCount = modifiedSettingsCount,
            onDismiss = { showResetDialog = false },
            onConfirmReset = onResetAll
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. SHIZUKU / ADB ENGINE CARD
        item {
            ShizukuEngineCard(
                shizukuState = shizukuState,
                onOpenSetupGuide = { showSetupGuide = true },
                onRequestPermission = onRequestShizukuPermission,
                onRecheck = onRecheckShizuku,
                onOpenShizuku = onOpenShizuku
            )
        }

        // 2. SYSTEM OPTIMIZATION & 90 FPS STABILITY PROFILE HERO
        item {
            QuickOptimizationHeroCard(
                telemetry = telemetry,
                stable90Report = stable90Report,
                stable90Cap = capabilities[BoosterFeatureId.STABLE_90_FPS],
                modifiedSettingsCount = modifiedSettingsCount,
                onOptimizeSystem = onOptimizeSystem,
                onActivate90FpsStable = onActivate90FpsStable,
                onShowResetAll = { showResetDialog = true }
            )
        }

        // 3. SMART THERMAL MANAGEMENT CARD
        item {
            SmartThermalCard(telemetry = telemetry)
        }

        // 4. REAL-TIME DEVICE INFORMATION CARD
        item {
            DeviceInformationCard(
                telemetry = telemetry,
                onRefresh = onRefreshTelemetry
            )
        }

        // 5. SECURE SHIZUKU SHELLEXECUTOR ALLOWLIST CONSOLE & LOGS
        item {
            SecureShellDiagnosticCard(
                recentLogs = recentLogs,
                onRunCommand = onRunShellDiagnostic
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShizukuEngineCard(
    shizukuState: ShizukuConnectionState,
    onOpenSetupGuide: () -> Unit,
    onRequestPermission: () -> Unit,
    onRecheck: () -> Unit,
    onOpenShizuku: () -> Unit
) {
    val statusColor = if (shizukuState.isConnected) VoltGreen else AmberWarn

    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .testTag("shizuku_engine_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Shizuku ADB Engine",
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SHIZUKU / ADB ENGINE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.8.sp
                    )
                }

                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.border(1.dp, statusColor.copy(alpha = 0.6f), RoundedCornerShape(50))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = shizukuState.statusHeadline,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("shizuku_status_headline")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = shizukuState.statusDetail,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Diagnostic flags row
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DiagnosticFlagPill("Installed", shizukuState.isInstalled)
                DiagnosticFlagPill("Binder Running", shizukuState.isBinderAlive)
                DiagnosticFlagPill("Permission Granted", shizukuState.isPermissionGranted)
                if (shizukuState.isPermissionDenied) {
                    DiagnosticFlagPill("Permission Denied", true, isWarning = true)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("shizuku_request_permission_button")
                ) {
                    Text(
                        text = "Request Permission",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onRecheck,
                    modifier = Modifier
                        .weight(0.75f)
                        .testTag("shizuku_recheck_button")
                ) {
                    Text("Recheck", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenSetupGuide,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("shizuku_setup_guide_button")
                ) {
                    Text("Connect / Setup Guide", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onOpenShizuku,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("shizuku_open_app_button")
                ) {
                    Text("Open Shizuku", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticFlagPill(
    label: String,
    active: Boolean,
    isWarning: Boolean = false
) {
    val color = when {
        isWarning -> CoralError
        active -> VoltGreen
        else -> TextSecondary
    }
    Surface(
        color = SlateCardElevated,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = "$label: ${if (active) "YES" else "NO"}",
            color = color,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun QuickOptimizationHeroCard(
    telemetry: DeviceTelemetrySnapshot,
    stable90Report: Stable90FpsReport?,
    stable90Cap: FeatureCapabilityReport?,
    modifiedSettingsCount: Int,
    onOptimizeSystem: () -> Unit,
    onActivate90FpsStable: () -> Unit,
    onShowResetAll: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OutlineSubtle, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            CyberCyan.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Performance Engine",
                        tint = CyberCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "PERFORMANCE & 90 FPS ENGINE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Real background cleanup, display mode verification & frame cadence",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
                stable90Cap?.let { FeatureStateChip(state = it.state) }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live 90 FPS Stability Readout Box
            Surface(
                color = SlateCardElevated,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stable90Report?.targetHeadline ?: "90 FPS TARGET",
                            color = CyberCyan,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = stable90Report?.displayHzText
                                ?: "${telemetry.currentRefreshRateHz.roundToInt()} Hz DISPLAY",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        val liveFpsText = telemetry.measuredFps?.let {
                            String.format(Locale.US, "%.0f–%.0f FPS", (it - 1f).coerceAtLeast(1f), it)
                        } ?: (stable90Report?.fpsStatusText ?: "FPS: unavailable")

                        val liveFrameTime = telemetry.measuredFrameTimeMs?.let {
                            String.format(Locale.US, "%.1f ms frame time", it)
                        } ?: (stable90Report?.frameTimeText ?: "Sampling cadence")

                        Text(
                            text = liveFpsText,
                            color = VoltGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = liveFrameTime,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOptimizeSystem,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("optimize_system_performance_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Optimize System",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = onActivate90FpsStable,
                    colors = ButtonDefaults.buttonColors(containerColor = SlateCardElevated),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, CyberCyan.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .testTag("stable_90_fps_button")
                ) {
                    Text(
                        text = "90 FPS Stable",
                        color = CyberCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onShowResetAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_all_functions_button")
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = CoralError,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Reset All Functions ($modifiedSettingsCount active change(s))",
                    color = CoralError,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SmartThermalCard(telemetry: DeviceTelemetrySnapshot) {
    val thermalColor = when (telemetry.thermalLevel) {
        ThermalLevel.COOL, ThermalLevel.NORMAL -> VoltGreen
        ThermalLevel.WARM -> AmberWarn
        ThermalLevel.HOT, ThermalLevel.CRITICAL -> CoralError
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, thermalColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .testTag("smart_thermal_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = "Thermal Status",
                        tint = thermalColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "SMART THERMAL PERFORMANCE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Hardware safety guard • Never spoofs or bypasses thermal limits",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    color = thermalColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = telemetry.thermalLevel.label,
                        color = thermalColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val tempDisplay = telemetry.batteryTempCelsius?.let {
                String.format(Locale.US, "%.1f °C", it)
            } ?: "Sensor N/A"

            Text(
                text = "Temperature: $tempDisplay • OS Thermal Status: ${telemetry.systemThermalStatusRaw}",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = telemetry.thermalLevel.policyDescription,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun DeviceInformationCard(
    telemetry: DeviceTelemetrySnapshot,
    onRefresh: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OutlineSubtle, RoundedCornerShape(16.dp))
            .testTag("device_information_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Device Info",
                        tint = CyberCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "REAL-TIME DEVICE INFORMATION",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Auto-refreshing live hardware telemetry",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_telemetry_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh device telemetry",
                        tint = CyberCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // RAM Progress Bar
            Text(
                text = "RAM: ${DeviceTelemetryProvider.formatBytes(telemetry.ramUsedBytes)} used / ${DeviceTelemetryProvider.formatBytes(telemetry.ramTotalBytes)} total (${DeviceTelemetryProvider.formatBytes(telemetry.ramAvailableBytes)} available • ${telemetry.ramUsagePercent}%)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { telemetry.ramUsagePercent / 100f },
                color = if (telemetry.ramUsagePercent > 85) AmberWarn else CyberCyan,
                trackColor = SlateCardElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Storage Progress Bar
            Text(
                text = "Storage: ${DeviceTelemetryProvider.formatBytes(telemetry.storageAvailableBytes)} free of ${DeviceTelemetryProvider.formatBytes(telemetry.storageTotalBytes)} (${telemetry.storageUsagePercent}% used)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { telemetry.storageUsagePercent / 100f },
                color = VoltGreen,
                trackColor = SlateCardElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = OutlineSubtle)
            Spacer(modifier = Modifier.height(10.dp))

            TelemetryRow("Manufacturer / Model", "${telemetry.manufacturer} ${telemetry.model}")
            TelemetryRow("Android Version / API", "Android ${telemetry.androidVersion} (API ${telemetry.apiLevel})")
            TelemetryRow("Battery & Charging", "${telemetry.batteryPercentage}% • ${telemetry.chargingState} (${telemetry.batteryVoltageMv} mV)")
            TelemetryRow("Device Temperature", telemetry.batteryTempCelsius?.let { String.format(Locale.US, "%.1f °C", it) } ?: "Unavailable on sensor")
            TelemetryRow("Display Resolution", telemetry.displayResolution)
            TelemetryRow("Current Refresh Rate", "${String.format(Locale.US, "%.1f", telemetry.currentRefreshRateHz)} Hz")
            TelemetryRow("Supported Refresh Rates", "${telemetry.supportedRefreshRatesHz.joinToString(" Hz, ")} Hz")
            TelemetryRow("CPU SoC / Cores", "${telemetry.cpuHardwareName} (${telemetry.cpuCores} cores, ${telemetry.cpuArchitecture})")
            TelemetryRow("CPU Frequencies", telemetry.cpuFrequenciesSummary)
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.55f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SecureShellDiagnosticCard(
    recentLogs: List<CommandLogEntity>,
    onRunCommand: (String) -> Unit
) {
    val allowlistedProbes = listOf(
        "id" to "Verify Shell UID",
        "wm size" to "Check Display Size",
        "dumpsys display" to "Display Modes",
        "settings get global window_animation_scale" to "Read Animation Scale",
        "dumpsys thermalservice" to "Thermal Service",
        "cat /proc/version" to "Kernel Version"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OutlineSubtle, RoundedCornerShape(16.dp))
            .testTag("shell_executor_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "ShellExecutor",
                    tint = CyberCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "SHIZUKU SHELLEXECUTOR (ALLOWLISTED)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Executes safe allowlisted commands via Shizuku.newProcess() with PermissionDeniedException protection",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                allowlistedProbes.forEach { (cmd, label) ->
                    AssistChip(
                        onClick = { onRunCommand(cmd) },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier.testTag("shell_probe_${cmd.substringBefore(' ')}")
                    )
                }
            }

            if (recentLogs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = OutlineSubtle)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "RECENT VERIFICATION & COMMAND LOGS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                recentLogs.take(5).forEach { log ->
                    Surface(
                        color = SlateCardElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${log.statusBadge} • ${log.featureName}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        log.statusBadge.contains("✓") -> VoltGreen
                                        log.statusBadge.contains("✕") -> CoralError
                                        else -> AmberWarn
                                    }
                                )
                                Text(
                                    text = log.commandOrApi.take(28),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text = log.technicalReason,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
