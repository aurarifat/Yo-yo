package com.example.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.BoosterFeatureId
import com.example.engine.FeatureCapabilityReport
import com.example.engine.NetworkDiagnosticsReport
import com.example.engine.StorageAnalysisReport
import com.example.telemetry.DeviceTelemetryProvider
import com.example.telemetry.DeviceTelemetrySnapshot
import com.example.ui.components.FeatureStateChip
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemTunerScreen(
    telemetry: DeviceTelemetrySnapshot,
    capabilities: Map<BoosterFeatureId, FeatureCapabilityReport>,
    networkReport: NetworkDiagnosticsReport,
    storageReport: StorageAnalysisReport,
    touchLatencyMs: Float?,
    selectedGamePackage: String,
    onToggleFixedPerformance: (Boolean) -> Unit,
    onApplyRefreshRate: (Int) -> Unit,
    onResetRefreshRate: () -> Unit,
    onRunNetworkCheck: () -> Unit,
    onApplyPrivateDns: (String, String) -> Unit,
    onOpenAndroidDnsSettings: () -> Unit,
    onRecordTouchLatency: (Float) -> Unit,
    onApplyTouchOptimization: () -> Unit,
    onApplyAnimationScale: (Float) -> Unit,
    onResetAnimationScale: () -> Unit,
    onToggleWindowBlurs: (Boolean) -> Unit,
    onToggleGamingFocusMode: (Boolean) -> Unit,
    onToggleHideDeveloperOptions: (Boolean) -> Unit,
    onApplyResolution: (String, Int, Int) -> Unit,
    onResetResolution: () -> Unit,
    onApplyNativeGameMode: (String, String) -> Unit,
    onRefreshStorage: () -> Unit,
    onConfirmCleanStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFocusConfirmDialog by remember { mutableStateOf(false) }
    var showStorageConfirmDialog by remember { mutableStateOf(false) }
    var customDnsHostname by remember { mutableStateOf(networkReport.privateDnsHost.ifEmpty { "one.one.one.one" }) }

    val defaultCustomW = ((telemetry.displayWidthPx.coerceAtLeast(720) * 0.8f).roundToInt() / 2) * 2
    val defaultCustomH = ((telemetry.displayHeightPx.coerceAtLeast(1280) * 0.8f).roundToInt() / 2) * 2
    var customResW by remember(telemetry.displayWidthPx) { mutableStateOf(defaultCustomW.toString()) }
    var customResH by remember(telemetry.displayHeightPx) { mutableStateOf(defaultCustomH.toString()) }

    if (showFocusConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showFocusConfirmDialog = false },
            containerColor = SlateCard,
            title = { Text("Enable Gaming Focus Mode?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Gaming Focus Mode will enable Priority Do Not Disturb to suppress heads-up notifications during gameplay. Your original notification interruption filter is saved and automatically restored when you exit the game or disable Focus Mode.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleGamingFocusMode(true)
                        showFocusConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier.testTag("confirm_focus_mode_button")
                ) {
                    Text("Confirm & Enable", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFocusConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showStorageConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showStorageConfirmDialog = false },
            containerColor = SlateCard,
            title = { Text("Confirm Safe Cache Cleanup", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "The following temporary cache directories will be cleaned (no personal files are touched):",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    storageReport.cleanableItems.forEach { item ->
                        Text(
                            text = "• ${item.label}: ${DeviceTelemetryProvider.formatBytes(item.sizeBytes)}\n  (${item.path})",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirmCleanStorage()
                        showStorageConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier.testTag("confirm_storage_clean_button")
                ) {
                    Text("Clean Safe Cache", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStorageConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. FIXED PERFORMANCE MODE
        item {
            TunerSectionCard(
                title = "Enabled Fixed Performance",
                report = capabilities[BoosterFeatureId.FIXED_PERFORMANCE]
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onToggleFixedPerformance(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("enable_fixed_performance_button")
                    ) {
                        Text("Enable Fixed Performance", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onToggleFixedPerformance(false) },
                        modifier = Modifier.testTag("reset_fixed_performance_button")
                    ) {
                        Text("RESET", fontSize = 12.sp)
                    }
                }
            }
        }

        // 2. REFRESH RATE CONTROL
        item {
            TunerSectionCard(
                title = "Adjust Refresh Rate",
                report = capabilities[BoosterFeatureId.REFRESH_RATE]
            ) {
                Text(
                    text = "Active Display Mode: ${telemetry.currentRefreshRateHz.roundToInt()} Hz • Supported Modes: ${telemetry.supportedRefreshRatesHz.joinToString(" Hz, ")} Hz",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CyberCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Note: Selecting 90 Hz configures Android display refresh preference. Games that cap their own internal frame rate still control in-game FPS.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modesToShow = (telemetry.supportedRefreshRatesHz + listOf(60, 90, 120)).distinct().sorted()
                    modesToShow.forEach { hz ->
                        val supported = hz in telemetry.supportedRefreshRatesHz
                        FilterChip(
                            selected = telemetry.currentRefreshRateHz.roundToInt() == hz,
                            onClick = { onApplyRefreshRate(hz) },
                            label = {
                                Text(
                                    text = if (supported) "$hz Hz" else "$hz Hz (Unsupported)",
                                    fontSize = 12.sp
                                )
                            },
                            modifier = Modifier.testTag("refresh_rate_${hz}_chip")
                        )
                    }
                    OutlinedButton(
                        onClick = onResetRefreshRate,
                        modifier = Modifier.testTag("reset_refresh_rate_button")
                    ) {
                        Text("RESET", fontSize = 12.sp)
                    }
                }
            }
        }

        // 3. UI ANIMATION SCALES
        item {
            TunerSectionCard(
                title = "Speed Up UI Animations",
                report = capabilities[BoosterFeatureId.UI_ANIMATIONS]
            ) {
                Text(
                    text = "Configures window_animation_scale, transition_animation_scale & animator_duration_scale and verifies read-back.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1.0f to "1x", 0.5f to "0.5x", 0.0f to "0x").forEach { (scale, label) ->
                        OutlinedButton(
                            onClick = { onApplyAnimationScale(scale) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("anim_scale_${label}_button")
                        ) {
                            Text(label, fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = onResetAnimationScale,
                        modifier = Modifier.testTag("reset_anim_scale_button")
                    ) {
                        Text("RESET")
                    }
                }
            }
        }

        // 4. SCREEN RESOLUTION
        item {
            TunerSectionCard(
                title = "Change Screen Resolution",
                report = capabilities[BoosterFeatureId.SCREEN_RESOLUTION]
            ) {
                Text(
                    text = "Saves native resolution (${telemetry.displayWidthPx}×${telemetry.displayHeightPx}) before applying 'wm size' and verifies actual resolution.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onApplyResolution("Native", 0, 0) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("resolution_native_button")
                    ) {
                        Text("Native", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { onApplyResolution("Performance", 0, 0) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier
                            .weight(1.25f)
                            .testTag("resolution_performance_button")
                    ) {
                        Text("Performance (85%)", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onResetResolution,
                        modifier = Modifier.testTag("reset_resolution_button")
                    ) {
                        Text("RESET", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Custom Supported Configuration (Safe 50%–100% Display Bounds)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customResW,
                        onValueChange = { customResW = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Width px", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_res_width_input")
                    )
                    OutlinedTextField(
                        value = customResH,
                        onValueChange = { customResH = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Height px", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_res_height_input")
                    )
                    OutlinedButton(
                        onClick = {
                            val w = customResW.toIntOrNull() ?: defaultCustomW
                            val h = customResH.toIntOrNull() ?: defaultCustomH
                            onApplyResolution("Custom", w, h)
                        },
                        modifier = Modifier.testTag("apply_custom_resolution_button")
                    ) {
                        Text("Apply Custom", fontSize = 11.sp)
                    }
                }
            }
        }

        // 5. TOUCH RESPONSE & DIAGNOSTIC PAD
        item {
            TunerSectionCard(
                title = "Improve Touch Latency & Response",
                report = capabilities[BoosterFeatureId.TOUCH_RESPONSE]
            ) {
                Surface(
                    color = AmberWarn.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Hardware touch sampling cannot be changed by this app.",
                        color = AmberWarn,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(10.dp)
                            .testTag("touch_sampling_limitation_text")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .background(SlateCardElevated, RoundedCornerShape(10.dp))
                        .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val dispatchDelta = (SystemClock.uptimeMillis() - down.uptimeMillis)
                                    .coerceAtLeast(1L)
                                    .toFloat()
                                onRecordTouchLatency(dispatchDelta)
                            }
                        }
                        .testTag("touch_diagnostic_pad")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = touchLatencyMs?.let {
                                String.format(Locale.US, "Measured Touch Dispatch Latency: %.1f ms", it)
                            } ?: "TAP HERE TO MEASURE REAL TOUCH DISPATCH LATENCY",
                            color = CyberCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Display Refresh Interval: ${String.format(Locale.US, "%.1f", 1000f / telemetry.currentRefreshRateHz.coerceAtLeast(1f))} ms",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onApplyTouchOptimization,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("apply_touch_optimization_button")
                ) {
                    Text("Apply Supported Touch & Transition Optimizations", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 6. NETWORK PERFORMANCE & PRIVATE DNS
        item {
            TunerSectionCard(
                title = "Improve Network Performance & Custom DNS",
                report = capabilities[BoosterFeatureId.NETWORK_PERFORMANCE]
            ) {
                Text(
                    text = "Connection: ${networkReport.connectionType} • Signal: ${networkReport.signalInfo}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Live Traffic: ↓ ${String.format(Locale.US, "%.1f", networkReport.liveRxKbps)} kbps / ↑ ${String.format(Locale.US, "%.1f", networkReport.liveTxKbps)} kbps",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CyberCyan
                )
                Text(
                    text = "Active DNS: ${networkReport.dnsServers.joinToString(", ").ifEmpty { "System Default" }} • Private DNS: ${networkReport.privateDnsMode}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = SlateCardElevated,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        val rttStr = networkReport.avgLatencyMs?.let {
                            String.format(Locale.US, "%.1f ms (Jitter: %.1f ms, Loss: %d%%)", it, networkReport.jitterMs ?: 0f, networkReport.packetLossPercent)
                        } ?: "Not tested yet"
                        Text(
                            text = "Latency / Ping ($rttStr)",
                            color = CyberCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        networkReport.measuredDownloadMbps?.let { dl ->
                            Text(
                                text = String.format(Locale.US, "Measured HTTP Download Probe: %.2f Mbps", dl),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = networkReport.stabilityAssessment,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onRunNetworkCheck,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("run_network_ping_button")
                ) {
                    Text("Run Live Ping & Throughput Diagnostics", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = OutlineSubtle)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Custom Private DNS Configuration",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = customDnsHostname,
                    onValueChange = { customDnsHostname = it },
                    label = { Text("DNS-over-TLS Hostname (e.g. one.one.one.one)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_dns_hostname_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { onApplyPrivateDns("automatic", "") },
                        modifier = Modifier.testTag("dns_auto_button")
                    ) {
                        Text("Automatic", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { onApplyPrivateDns("off", "") },
                        modifier = Modifier.testTag("dns_off_button")
                    ) {
                        Text("Off", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { onApplyPrivateDns("hostname", customDnsHostname) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier.testTag("dns_apply_hostname_button")
                    ) {
                        Text("Apply Hostname", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onOpenAndroidDnsSettings,
                        modifier = Modifier.testTag("dns_open_system_settings_button")
                    ) {
                        Text("Open Android DNS Settings", fontSize = 11.sp)
                    }
                }
            }
        }

        // 7. DISABLE WINDOW BLURS
        item {
            TunerSectionCard(
                title = "Disable Window Blurs",
                report = capabilities[BoosterFeatureId.WINDOW_BLUR]
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onToggleWindowBlurs(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("disable_window_blurs_button")
                    ) {
                        Text("Disable Window Blurs", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onToggleWindowBlurs(false) },
                        modifier = Modifier.testTag("restore_window_blurs_button")
                    ) {
                        Text("RESET", fontSize = 12.sp)
                    }
                }
            }
        }

        // 8. GAMING FOCUS MODE (DND)
        item {
            TunerSectionCard(
                title = "Gaming Focus Mode",
                report = capabilities[BoosterFeatureId.GAMING_FOCUS_MODE]
            ) {
                Text(
                    text = "Suppresses heads-up notifications via NotificationManager policy after user confirmation and restores previous state on exit.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showFocusConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("enable_gaming_focus_button")
                    ) {
                        Text("Enable Focus Mode", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { onToggleGamingFocusMode(false) },
                        modifier = Modifier.testTag("restore_gaming_focus_button")
                    ) {
                        Text("Restore State", fontSize = 12.sp)
                    }
                }
            }
        }

        // 9. HIDE DEVELOPER OPTIONS
        item {
            TunerSectionCard(
                title = "Hide Developer Options",
                report = capabilities[BoosterFeatureId.HIDE_DEV_OPTIONS]
            ) {
                Text(
                    text = "Modifies Settings.Global.DEVELOPMENT_SETTINGS_ENABLED only when permitted by Shizuku/OS. Anti-cheat or banking compatibility is never guaranteed.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onToggleHideDeveloperOptions(true) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("hide_dev_options_button")
                    ) {
                        Text("Hide Dev Options", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onToggleHideDeveloperOptions(false) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("restore_dev_options_button")
                    ) {
                        Text("Restore Visible", fontSize = 12.sp)
                    }
                }
            }
        }

        // 10. NATIVE GAME MODE API
        item {
            TunerSectionCard(
                title = "Native Game Mode API",
                report = capabilities[BoosterFeatureId.NATIVE_GAME_MODE]
            ) {
                Text(
                    text = "Target Package: $selectedGamePackage",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CyberCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Standard", "Performance", "Battery").forEach { mode ->
                        OutlinedButton(
                            onClick = { onApplyNativeGameMode(selectedGamePackage, mode) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("native_game_mode_${mode.lowercase()}_button")
                        ) {
                            Text(mode, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // 11. STORAGE OPTIMIZER
        item {
            TunerSectionCard(
                title = "Storage Optimizer",
                report = capabilities[BoosterFeatureId.STORAGE_OPTIMIZER]
            ) {
                Text(
                    text = "Total: ${DeviceTelemetryProvider.formatBytes(storageReport.totalBytes)} • Free: ${DeviceTelemetryProvider.formatBytes(storageReport.freeBytes)} • Used: ${DeviceTelemetryProvider.formatBytes(storageReport.usedBytes)} (${storageReport.usedPercent}%)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Safe Cache Breakdown:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan
                )
                storageReport.cleanableItems.forEach { item ->
                    Text(
                        text = "• ${item.label}: ${DeviceTelemetryProvider.formatBytes(item.sizeBytes)}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }

                if (storageReport.largeFiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Detected Large Files (Read-Only Analysis • Never Auto-Deleted):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberWarn
                    )
                    storageReport.largeFiles.forEach { lf ->
                        Text(
                            text = "• ${lf.name} (${DeviceTelemetryProvider.formatBytes(lf.sizeBytes)})",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRefreshStorage,
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("analyze_storage_button")
                    ) {
                        Text("Re-Scan", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { showStorageConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("clean_safe_cache_button")
                    ) {
                        Text("Review & Clean Cache", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun TunerSectionCard(
    title: String,
    report: FeatureCapabilityReport?,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OutlineSubtle, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                report?.let { FeatureStateChip(state = it.state) }
            }

            report?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${it.currentSummary} — ${it.technicalDetail}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
