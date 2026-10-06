package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.BoosterFeatureId
import com.example.engine.FeatureCapabilityReport
import com.example.engine.PermissionItemId
import com.example.overlay.CrosshairConfig
import com.example.overlay.CrosshairStyle
import com.example.overlay.HardwareMonitorConfig
import com.example.ui.components.FeatureStateChip
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverlaysScreen(
    crosshairConfig: CrosshairConfig,
    monitorConfig: HardwareMonitorConfig,
    capabilities: Map<BoosterFeatureId, FeatureCapabilityReport>,
    onUpdateCrosshair: ((CrosshairConfig) -> CrosshairConfig) -> Unit,
    onUpdateMonitor: ((HardwareMonitorConfig) -> HardwareMonitorConfig) -> Unit,
    onOpenOverlayPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val crosshairCap = capabilities[BoosterFeatureId.CROSSHAIR_OVERLAY]
    val monitorCap = capabilities[BoosterFeatureId.HARDWARE_MONITOR]

    val colorChoices = listOf(
        0xFF00E5FF.toInt() to "Cyan",
        0xFFFF3B30.toInt() to "Laser Red",
        0xFF00E676.toInt() to "Volt Green",
        0xFFFFD600.toInt() to "Amber",
        0xFFFFFFFF.toInt() to "White"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 15. CROSSHAIR OVERLAY CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OutlineSubtle, RoundedCornerShape(16.dp))
                    .testTag("crosshair_overlay_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Crosshair Overlay", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "Real SYSTEM_ALERT_WINDOW overlay • Auto-hides when disabled",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        crosshairCap?.let { FeatureStateChip(state = it.state) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = crosshairConfig.enabled,
                            onCheckedChange = { enabled ->
                                onUpdateCrosshair { it.copy(enabled = enabled) }
                            },
                            modifier = Modifier.testTag("crosshair_enable_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Reticle Preview Box
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .background(SlateCardElevated, RoundedCornerShape(12.dp))
                            .border(1.dp, OutlineSubtle, RoundedCornerShape(12.dp))
                    ) {
                        val previewColor = Color(crosshairConfig.colorArgb).copy(alpha = crosshairConfig.opacity)
                        Canvas(modifier = Modifier.size(crosshairConfig.sizeDp.dp)) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val r = (size.minDimension / 2f) * 0.82f
                            val stroke = (size.minDimension * 0.08f).coerceAtLeast(3f)

                            when (crosshairConfig.style) {
                                CrosshairStyle.CROSS -> {
                                    val gap = r * 0.25f
                                    drawLine(previewColor, Offset(cx - r, cy), Offset(cx - gap, cy), stroke)
                                    drawLine(previewColor, Offset(cx + gap, cy), Offset(cx + r, cy), stroke)
                                    drawLine(previewColor, Offset(cx, cy - r), Offset(cx, cy - gap), stroke)
                                    drawLine(previewColor, Offset(cx, cy + gap), Offset(cx, cy + r), stroke)
                                }
                                CrosshairStyle.DOT -> {
                                    drawCircle(previewColor, radius = (r * 0.3f).coerceAtLeast(4f), center = Offset(cx, cy))
                                }
                                CrosshairStyle.CIRCLE_CROSS -> {
                                    drawCircle(previewColor, radius = r * 0.65f, center = Offset(cx, cy), style = Stroke(stroke))
                                    drawLine(previewColor, Offset(cx - r, cy), Offset(cx - r * 0.35f, cy), stroke)
                                    drawLine(previewColor, Offset(cx + r * 0.35f, cy), Offset(cx + r, cy), stroke)
                                    drawLine(previewColor, Offset(cx, cy - r), Offset(cx, cy - r * 0.35f), stroke)
                                    drawLine(previewColor, Offset(cx, cy + r * 0.35f), Offset(cx, cy + r), stroke)
                                    drawCircle(previewColor, radius = 3f, center = Offset(cx, cy))
                                }
                                CrosshairStyle.CHEVRON -> {
                                    drawLine(previewColor, Offset(cx - r * 0.7f, cy + r * 0.45f), Offset(cx, cy - r * 0.3f), stroke)
                                    drawLine(previewColor, Offset(cx, cy - r * 0.3f), Offset(cx + r * 0.7f, cy + r * 0.45f), stroke)
                                    drawCircle(previewColor, radius = 3.5f, center = Offset(cx, cy + r * 0.55f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Reticle Style", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CrosshairStyle.entries.forEach { style ->
                            FilterChip(
                                selected = crosshairConfig.style == style,
                                onClick = { onUpdateCrosshair { it.copy(style = style) } },
                                label = { Text(style.label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Reticle Color", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        colorChoices.forEach { (argb, label) ->
                            val selected = crosshairConfig.colorArgb == argb
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(argb), CircleShape)
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) Color.White else OutlineSubtle,
                                        shape = CircleShape
                                    )
                                    .clickable { onUpdateCrosshair { it.copy(colorArgb = argb) } }
                                    .testTag("crosshair_color_$label")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Size: ${crosshairConfig.sizeDp} dp  •  Opacity: ${(crosshairConfig.opacity * 100).roundToInt()}%",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Slider(
                        value = crosshairConfig.sizeDp.toFloat(),
                        onValueChange = { v -> onUpdateCrosshair { it.copy(sizeDp = v.roundToInt()) } },
                        valueRange = 14f..64f
                    )
                    Slider(
                        value = crosshairConfig.opacity,
                        onValueChange = { v -> onUpdateCrosshair { it.copy(opacity = v) } },
                        valueRange = 0.2f..1.0f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onUpdateCrosshair { it.copy(draggable = !it.draggable) } },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("crosshair_draggable_button")
                        ) {
                            Text(
                                text = if (crosshairConfig.draggable) "Position: Unlocked (Draggable)" else "Position: Locked",
                                fontSize = 11.sp
                            )
                        }
                        OutlinedButton(
                            onClick = { onUpdateCrosshair { it.copy(offsetX = 0, offsetY = 0) } },
                            modifier = Modifier.testTag("crosshair_reset_center_button")
                        ) {
                            Text("Center Reset", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenOverlayPermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_overlay_permission_button")
                    ) {
                        Text("Open SYSTEM_ALERT_WINDOW Permission Settings", fontSize = 11.sp)
                    }
                }
            }
        }

        // 16. REAL-TIME HARDWARE MONITOR OVERLAY CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OutlineSubtle, RoundedCornerShape(16.dp))
                    .testTag("hardware_monitor_overlay_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Real-Time Hardware Monitor", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "Floating draggable HUD with real RAM, Battery, Temp, CPU, Hz & FPS",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                        monitorCap?.let { FeatureStateChip(state = it.state) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = monitorConfig.enabled,
                            onCheckedChange = { enabled ->
                                onUpdateMonitor { it.copy(enabled = enabled) }
                            },
                            modifier = Modifier.testTag("hardware_monitor_enable_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "HUD Opacity: ${(monitorConfig.opacity * 100).roundToInt()}%  •  Saved Position: (${monitorConfig.posX}, ${monitorConfig.posY})",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Slider(
                        value = monitorConfig.opacity,
                        onValueChange = { v -> onUpdateMonitor { it.copy(opacity = v) } },
                        valueRange = 0.4f..1.0f
                    )

                    Text("Visible Telemetry Metrics", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = monitorConfig.showFps,
                            onClick = { onUpdateMonitor { it.copy(showFps = !it.showFps) } },
                            label = { Text("FPS & Frame Time", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = monitorConfig.showRefreshRate,
                            onClick = { onUpdateMonitor { it.copy(showRefreshRate = !it.showRefreshRate) } },
                            label = { Text("Refresh Rate (Hz)", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = monitorConfig.showRam,
                            onClick = { onUpdateMonitor { it.copy(showRam = !it.showRam) } },
                            label = { Text("RAM Usage", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = monitorConfig.showTemperature,
                            onClick = { onUpdateMonitor { it.copy(showTemperature = !it.showTemperature) } },
                            label = { Text("Temperature", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = monitorConfig.showBattery,
                            onClick = { onUpdateMonitor { it.copy(showBattery = !it.showBattery) } },
                            label = { Text("Battery", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = monitorConfig.showCpu,
                            onClick = { onUpdateMonitor { it.copy(showCpu = !it.showCpu) } },
                            label = { Text("CPU Freq", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onUpdateMonitor { it.copy(minimized = !it.minimized) } },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("monitor_toggle_minimize_button")
                        ) {
                            Text(
                                text = if (monitorConfig.minimized) "Expand HUD" else "Minimize HUD",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        OutlinedButton(
                            onClick = { onUpdateMonitor { it.copy(posX = 24, posY = 140) } },
                            modifier = Modifier.testTag("monitor_reset_pos_button")
                        ) {
                            Text("Reset Pos", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
