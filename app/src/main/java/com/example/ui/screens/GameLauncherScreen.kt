package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GameProfileEntity
import com.example.engine.InstalledAppItem
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.OutlineSubtle
import com.example.ui.theme.SlateCard
import com.example.ui.theme.SlateCardElevated
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoltGreen

@Composable
fun GameLauncherScreen(
    profiles: List<GameProfileEntity>,
    installedApps: List<InstalledAppItem>,
    supportedRefreshRates: List<Int>,
    onSaveProfile: (GameProfileEntity) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onLaunchProfile: (GameProfileEntity) -> Unit,
    onRefreshInstalledApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingProfile by remember { mutableStateOf<GameProfileEntity?>(null) }

    editingProfile?.let { prof ->
        EditGameProfileDialog(
            initialProfile = prof,
            supportedRefreshRates = supportedRefreshRates,
            onDismiss = { editingProfile = null },
            onSave = { updated ->
                onSaveProfile(updated)
                editingProfile = null
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

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PER-GAME PROFILES & LAUNCHER",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Applies profile settings, verifies changes & auto-restores after exit",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                IconButton(
                    onClick = onRefreshInstalledApps,
                    modifier = Modifier.testTag("refresh_installed_apps_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh installed apps",
                        tint = CyberCyan
                    )
                }
            }
        }

        items(profiles, key = { it.packageName }) { profile ->
            val matchedApp = installedApps.firstOrNull { it.packageName == profile.packageName }
            GameProfileCard(
                profile = profile,
                appIcon = matchedApp?.iconBitmap,
                isInstalled = matchedApp != null,
                onEdit = { editingProfile = profile },
                onDelete = { onDeleteProfile(profile.packageName) },
                onLaunch = { onLaunchProfile(profile) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "INSTALLED LAUNCHABLE APPS (${installedApps.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = CyberCyan
            )
            Text(
                text = "Select any installed game or application to create a custom booster profile or launch directly.",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }

        items(installedApps, key = { "installed_${it.packageName}" }) { app ->
            val existingProfile = profiles.firstOrNull { it.packageName == app.packageName }
            InstalledAppRowCard(
                app = app,
                hasProfile = existingProfile != null,
                onCreateOrEditProfile = {
                    val target = existingProfile ?: GameProfileEntity(
                        packageName = app.packageName,
                        gameName = app.appName,
                        refreshRatePref = supportedRefreshRates.maxOrNull() ?: 60,
                        performanceProfile = "High",
                        resolutionPref = "Native",
                        hardwareMonitorEnabled = true,
                        restoreAfterExit = true
                    )
                    editingProfile = target
                },
                onQuickLaunch = {
                    val target = existingProfile ?: GameProfileEntity(
                        packageName = app.packageName,
                        gameName = app.appName,
                        refreshRatePref = supportedRefreshRates.maxOrNull() ?: 60,
                        performanceProfile = "High"
                    )
                    onLaunchProfile(target)
                }
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameProfileCard(
    profile: GameProfileEntity,
    appIcon: ImageBitmap?,
    isInstalled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLaunch: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .testTag("game_profile_card_${profile.packageName}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = profile.gameName,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .background(CyberCyan.copy(alpha = 0.16f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = profile.gameName,
                                tint = CyberCyan
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = profile.gameName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = profile.packageName,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_profile_${profile.packageName}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit profile",
                            tint = CyberCyan
                        )
                    }
                    if (profile.packageName != "com.dts.freefiremax") {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.testTag("delete_profile_${profile.packageName}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete profile",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProfileSpecTag("Performance: ${profile.performanceProfile}")
                ProfileSpecTag("Refresh: ${profile.refreshRatePref} Hz")
                ProfileSpecTag("Resolution: ${profile.resolutionPref}")
                ProfileSpecTag("Thermal: ${profile.thermalProfile}")
                ProfileSpecTag("Overlay HUD: ${if (profile.hardwareMonitorEnabled) "Enabled" else "Off"}")
                ProfileSpecTag("Auto-Restore: ${if (profile.restoreAfterExit) "ON" else "OFF"}")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onLaunch,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("launch_game_${profile.packageName}")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isInstalled) {
                        "Launch Game (${profile.gameName})"
                    } else {
                        "Optimize & Launch ${profile.gameName}"
                    },
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ProfileSpecTag(text: String) {
    Surface(
        color = SlateCardElevated,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun InstalledAppRowCard(
    app: InstalledAppItem,
    hasProfile: Boolean,
    onCreateOrEditProfile: () -> Unit,
    onQuickLaunch: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OutlineSubtle, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (app.iconBitmap != null) {
                    Image(
                        bitmap = app.iconBitmap,
                        contentDescription = app.appName,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .background(CyberCyan.copy(alpha = 0.16f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = app.appName,
                            tint = CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = app.appName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = app.packageName,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onCreateOrEditProfile,
                    modifier = Modifier.testTag("configure_app_${app.packageName}")
                ) {
                    Icon(
                        imageVector = if (hasProfile) Icons.Default.Edit else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (hasProfile) "Edit" else "Profile", fontSize = 11.sp)
                }
                Button(
                    onClick = onQuickLaunch,
                    colors = ButtonDefaults.buttonColors(containerColor = VoltGreen),
                    modifier = Modifier.testTag("quick_launch_${app.packageName}")
                ) {
                    Text("Launch", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditGameProfileDialog(
    initialProfile: GameProfileEntity,
    supportedRefreshRates: List<Int>,
    onDismiss: () -> Unit,
    onSave: (GameProfileEntity) -> Unit
) {
    var perfProfile by remember { mutableStateOf(initialProfile.performanceProfile) }
    var refreshHz by remember { mutableIntStateOf(initialProfile.refreshRatePref) }
    var resolutionPref by remember { mutableStateOf(initialProfile.resolutionPref) }
    var crosshairOn by remember { mutableStateOf(initialProfile.crosshairOverlayEnabled) }
    var monitorOn by remember { mutableStateOf(initialProfile.hardwareMonitorEnabled) }
    var focusDndOn by remember { mutableStateOf(initialProfile.focusModeDndEnabled) }
    var autoLaunch by remember { mutableStateOf(initialProfile.autoLaunch) }
    var restoreOnExit by remember { mutableStateOf(initialProfile.restoreAfterExit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateCard,
        title = {
            Text("Configure Profile: ${initialProfile.gameName}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Performance Mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CyberCyan)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Balanced", "High", "90 FPS Stable").forEach { mode ->
                        FilterChip(
                            selected = perfProfile == mode,
                            onClick = { perfProfile = mode },
                            label = { Text(mode, fontSize = 11.sp) }
                        )
                    }
                }

                Text("Target Refresh Rate", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CyberCyan)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (supportedRefreshRates + listOf(60, 90)).distinct().sorted().forEach { hz ->
                        FilterChip(
                            selected = refreshHz == hz,
                            onClick = { refreshHz = hz },
                            label = { Text("$hz Hz", fontSize = 11.sp) }
                        )
                    }
                }

                Text("Resolution Profile", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CyberCyan)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Native", "Performance").forEach { res ->
                        FilterChip(
                            selected = resolutionPref == res,
                            onClick = { resolutionPref = res },
                            label = { Text(res, fontSize = 11.sp) }
                        )
                    }
                }

                ProfileToggleRow("Enable Hardware Monitor Overlay", monitorOn) { monitorOn = it }
                ProfileToggleRow("Enable Crosshair Overlay", crosshairOn) { crosshairOn = it }
                ProfileToggleRow("Enable Gaming Focus Mode (DND)", focusDndOn) { focusDndOn = it }
                ProfileToggleRow("Auto-Launch Game After Optimization", autoLaunch) { autoLaunch = it }
                ProfileToggleRow("Automatically Restore Settings on Exit", restoreOnExit) { restoreOnExit = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialProfile.copy(
                            performanceProfile = perfProfile,
                            refreshRatePref = refreshHz,
                            resolutionPref = resolutionPref,
                            crosshairOverlayEnabled = crosshairOn,
                            hardwareMonitorEnabled = monitorOn,
                            focusModeDndEnabled = focusDndOn,
                            autoLaunch = autoLaunch,
                            restoreAfterExit = restoreOnExit
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                modifier = Modifier.testTag("save_game_profile_button")
            ) {
                Text("Save Profile", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ProfileToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 12.sp)
    }
}
