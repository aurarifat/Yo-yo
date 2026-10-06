package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.engine.FeatureState
import com.example.shizuku.OperationStatus
import com.example.shizuku.ShellExecutionResult
import com.example.ui.ActiveOptimizationDialogState
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CoralError
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoltGreen

@Composable
fun FeatureStateChip(state: FeatureState, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (state) {
        FeatureState.ACTIVE -> VoltGreen.copy(alpha = 0.18f) to VoltGreen
        FeatureState.SUPPORTED -> CyberCyan.copy(alpha = 0.16f) to CyberCyan
        FeatureState.PERMISSION_REQUIRED -> AmberWarn.copy(alpha = 0.18f) to AmberWarn
        FeatureState.UNSUPPORTED -> Color(0xFF64748B).copy(alpha = 0.25f) to Color(0xFFCBD5E1)
        FeatureState.ERROR -> CoralError.copy(alpha = 0.2f) to CoralError
        FeatureState.INACTIVE -> Color(0xFF475569).copy(alpha = 0.25f) to TextSecondary
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.border(1.dp, textColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
    ) {
        Text(
            text = state.label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun OperationResultBanner(
    result: ShellExecutionResult?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = result != null, modifier = modifier) {
        if (result != null) {
            val accentColor = when (result.status) {
                OperationStatus.SUCCESS -> VoltGreen
                OperationStatus.UNSUPPORTED -> AmberWarn
                OperationStatus.PERMISSION_REQUIRED -> AmberWarn
                OperationStatus.FAILED -> CoralError
            }
            val icon = when (result.status) {
                OperationStatus.SUCCESS -> Icons.Default.CheckCircle
                OperationStatus.UNSUPPORTED -> Icons.Default.Warning
                OperationStatus.PERMISSION_REQUIRED -> Icons.Default.Lock
                OperationStatus.FAILED -> Icons.Default.Error
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCardElevated),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .border(1.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .testTag("operation_result_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = result.status.label,
                        tint = accentColor,
                        modifier = Modifier
                            .size(22.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = result.status.badge,
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = result.command,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = result.technicalReason,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("dismiss_banner_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss notification",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OptimizationSequenceDialog(
    state: ActiveOptimizationDialogState,
    onDismiss: () -> Unit
) {
    if (!state.isVisible) return

    Dialog(onDismissRequest = { if (!state.isRunning) onDismiss() }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .testTag("optimization_progress_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.isRunning) {
                        CircularProgressIndicator(
                            color = CyberCyan,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = VoltGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.isRunning) "Executing real system operations..." else "Verification complete",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val completedCount = state.steps.count { it.isCompleted }
                val totalSteps = state.steps.size.coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { completedCount.toFloat() / totalSteps.toFloat() },
                    color = CyberCyan,
                    trackColor = SlateCardElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                state.steps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(10.dp)
                                .background(
                                    color = when {
                                        !step.isCompleted -> CyberCyan.copy(alpha = 0.4f)
                                        step.status == OperationStatus.SUCCESS -> VoltGreen
                                        step.status == OperationStatus.PERMISSION_REQUIRED ||
                                            step.status == OperationStatus.UNSUPPORTED -> AmberWarn
                                        else -> CoralError
                                    },
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${index + 1}. ${step.stepTitle}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                step.status?.let { st ->
                                    Text(
                                        text = st.badge,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (st) {
                                            OperationStatus.SUCCESS -> VoltGreen
                                            OperationStatus.FAILED -> CoralError
                                            else -> AmberWarn
                                        }
                                    )
                                }
                            }
                            if (step.detail.isNotEmpty()) {
                                Text(
                                    text = step.detail,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                state.finalResult?.let { finalRes ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = SlateCardElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = finalRes.status.badge,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (finalRes.isSuccess) VoltGreen else AmberWarn
                            )
                            Text(
                                text = finalRes.technicalReason,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    enabled = !state.isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("close_optimization_dialog_button")
                ) {
                    Text(
                        text = if (state.isRunning) "Processing..." else "Done",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun WirelessDebuggingGuideDialog(
    onDismiss: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onOpenShizuku: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateCard,
        title = {
            Text(
                text = "Shizuku & Wireless Debugging Setup",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "To execute privileged ADB performance commands (Fixed Performance Mode, UI Animation Scales, wm size Resolution, Game Mode API):",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Text("1. Install and open the official Shizuku app (moe.shizuku.privileged.api).", fontSize = 12.sp)
                Text("2. Enable Developer Options & Wireless Debugging in Android Settings.", fontSize = 12.sp)
                Text("3. In Shizuku, tap 'Pairing' -> 'Pair device with pairing code' in Wireless Debugging.", fontSize = 12.sp)
                Text("4. Tap 'Start' in Shizuku, then return here and tap 'Request Permission'.", fontSize = 12.sp)
                Text("5. Alternatively, from a PC with USB Debugging enabled, run:\nadb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = CyberCyan)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onOpenDeveloperOptions()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                modifier = Modifier.testTag("guide_open_dev_options_button")
            ) {
                Text("Developer Options", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                OutlinedButton(
                    onClick = {
                        onOpenShizuku()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("guide_open_shizuku_button")
                ) {
                    Text("Open Shizuku")
                }
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
fun ResetAllConfirmDialog(
    modifiedSettingsCount: Int,
    onDismiss: () -> Unit,
    onConfirmReset: (Boolean) -> Unit
) {
    var alsoClearProfiles by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateCard,
        title = {
            Text("Reset All Functions?", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "This will restore all system settings modified by the booster ($modifiedSettingsCount active change(s)) back to their saved original values, disable overlays, and verify the restored configuration.",
                    fontSize = 13.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Checkbox(
                        checked = alsoClearProfiles,
                        onCheckedChange = { alsoClearProfiles = it },
                        modifier = Modifier.testTag("reset_profiles_checkbox")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Also reset custom per-game profiles to defaults",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmReset(alsoClearProfiles)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CoralError),
                modifier = Modifier.testTag("confirm_reset_all_button")
            ) {
                Text("Restore & Verify", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
