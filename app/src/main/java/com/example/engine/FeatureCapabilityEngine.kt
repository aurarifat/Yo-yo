package com.example.engine

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.shizuku.ShizukuManager
import com.example.telemetry.DeviceTelemetrySnapshot

enum class FeatureState(val label: String) {
    SUPPORTED("SUPPORTED"),
    PERMISSION_REQUIRED("PERMISSION_REQUIRED"),
    UNSUPPORTED("UNSUPPORTED"),
    ERROR("ERROR"),
    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE")
}

enum class BoosterFeatureId(val title: String) {
    FIXED_PERFORMANCE("Enabled Fixed Performance"),
    OPTIMIZE_SYSTEM("Optimize System Performance"),
    REFRESH_RATE("Adjust Refresh Rate"),
    STABLE_90_FPS("90 FPS Stable Profile"),
    NETWORK_PERFORMANCE("Improve Network Performance"),
    TOUCH_RESPONSE("Improve Touch Latency & Response"),
    UI_ANIMATIONS("Speed Up UI Animations"),
    WINDOW_BLUR("Disable Window Blurs"),
    GAMING_FOCUS_MODE("Gaming Focus Mode (DND)"),
    HIDE_DEV_OPTIONS("Hide Developer Options"),
    SCREEN_RESOLUTION("Change Screen Resolution"),
    CUSTOM_DNS("Custom Private DNS"),
    CROSSHAIR_OVERLAY("Enable Crosshair Overlay"),
    HARDWARE_MONITOR("Real-Time Hardware Monitor"),
    NATIVE_GAME_MODE("Native Game Mode API"),
    THERMAL_MANAGEMENT("Smart Thermal Performance"),
    STORAGE_OPTIMIZER("Storage Optimizer")
}

data class FeatureCapabilityReport(
    val featureId: BoosterFeatureId,
    val state: FeatureState,
    val currentSummary: String,
    val technicalDetail: String
)

enum class PermissionItemId {
    SHIZUKU,
    WIRELESS_DEBUGGING,
    OVERLAY,
    WRITE_SETTINGS,
    NOTIFICATION_POLICY,
    POST_NOTIFICATIONS,
    USAGE_ACCESS,
    BATTERY_OPTIMIZATION
}

data class PermissionCenterItem(
    val id: PermissionItemId,
    val title: String,
    val isGranted: Boolean,
    val statusText: String,
    val whyRequired: String,
    val actionLabel: String = "OPEN SETTINGS"
)

object FeatureCapabilityEngine {

    fun checkAllPermissions(context: Context): List<PermissionCenterItem> {
        val shizukuState = ShizukuManager.recheck(context)
        val devOptionsEnabled = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        } catch (_: Throwable) {
            false
        }
        val adbEnabled = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (_: Throwable) {
            false
        }
        val overlayGranted = Settings.canDrawOverlays(context)
        val writeSettingsGranted = Settings.System.canWrite(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dndGranted = nm.isNotificationPolicyAccessGranted

        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val usageAccessGranted = hasUsageStatsPermission(context)
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryUnrestricted = pm.isIgnoringBatteryOptimizations(context.packageName)

        return listOf(
            PermissionCenterItem(
                id = PermissionItemId.SHIZUKU,
                title = "Shizuku Privileged Bridge",
                isGranted = shizukuState.isConnected,
                statusText = if (shizukuState.isConnected) {
                    "CONNECTED (UID ${shizukuState.shizukuUid})"
                } else {
                    shizukuState.statusHeadline
                },
                whyRequired = "Required for privileged ADB shell operations: Fixed Performance Mode, UI Animation Scales, Screen Resolution (wm size), Private DNS, and Native Game Mode.",
                actionLabel = if (shizukuState.isBinderAlive && !shizukuState.isPermissionGranted) {
                    "REQUEST PERMISSION"
                } else {
                    "OPEN SHIZUKU"
                }
            ),
            PermissionCenterItem(
                id = PermissionItemId.WIRELESS_DEBUGGING,
                title = "Wireless Debugging / ADB",
                isGranted = devOptionsEnabled && adbEnabled,
                statusText = when {
                    devOptionsEnabled && adbEnabled -> "ENABLED"
                    devOptionsEnabled -> "DEV OPTIONS ON (ADB OFF)"
                    else -> "DISABLED"
                },
                whyRequired = "Required to start the local Shizuku daemon directly on-device without a PC."
            ),
            PermissionCenterItem(
                id = PermissionItemId.OVERLAY,
                title = "Display Over Other Apps (Overlay)",
                isGranted = overlayGranted,
                statusText = if (overlayGranted) "GRANTED" else "PERMISSION REQUIRED",
                whyRequired = "Required to draw the real-time Crosshair Overlay and floating Hardware Monitor HUD over games."
            ),
            PermissionCenterItem(
                id = PermissionItemId.WRITE_SETTINGS,
                title = "Modify System Settings",
                isGranted = writeSettingsGranted,
                statusText = if (writeSettingsGranted) "GRANTED" else "PERMISSION REQUIRED",
                whyRequired = "Required to adjust display refresh rate preferences (peak_refresh_rate / min_refresh_rate) when permitted by the OEM."
            ),
            PermissionCenterItem(
                id = PermissionItemId.NOTIFICATION_POLICY,
                title = "Do Not Disturb / Notification Policy",
                isGranted = dndGranted,
                statusText = if (dndGranted) "GRANTED" else "PERMISSION REQUIRED",
                whyRequired = "Required for Gaming Focus Mode to suppress heads-up interruptions during gameplay and restore them after exit."
            ),
            PermissionCenterItem(
                id = PermissionItemId.POST_NOTIFICATIONS,
                title = "Foreground Service Notifications",
                isGranted = notifGranted,
                statusText = if (notifGranted) "GRANTED" else "PERMISSION REQUIRED",
                whyRequired = "Required on Android 13+ to display the persistent status notification for the real-time Hardware Monitor service."
            ),
            PermissionCenterItem(
                id = PermissionItemId.USAGE_ACCESS,
                title = "Usage Access (App & Game Detection)",
                isGranted = usageAccessGranted,
                statusText = if (usageAccessGranted) "GRANTED" else "PERMISSION REQUIRED",
                whyRequired = "Required to detect active foreground/background app state and automatically restore settings when a game exits."
            ),
            PermissionCenterItem(
                id = PermissionItemId.BATTERY_OPTIMIZATION,
                title = "Unrestricted Battery / Foreground Stability",
                isGranted = batteryUnrestricted,
                statusText = if (batteryUnrestricted) "UNRESTRICTED" else "OPTIMIZED (RESTRICTED)",
                whyRequired = "Prevents Android from killing the floating Hardware Monitor overlay and automatic restore watcher while a heavy game runs."
            )
        )
    }

    fun evaluateCapabilities(
        context: Context,
        telemetry: DeviceTelemetrySnapshot,
        modifiedSettingsKeys: Set<String>,
        crosshairEnabled: Boolean,
        monitorEnabled: Boolean
    ): Map<BoosterFeatureId, FeatureCapabilityReport> {
        val shizukuConnected = ShizukuManager.state.value.isConnected
        val canWriteSettings = Settings.System.canWrite(context)
        val canOverlay = Settings.canDrawOverlays(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val canDnd = nm.isNotificationPolicyAccessGranted

        val results = mutableMapOf<BoosterFeatureId, FeatureCapabilityReport>()

        // 1. Fixed Performance
        val fixedPerfActive = "fixed_performance_mode" in modifiedSettingsKeys
        results[BoosterFeatureId.FIXED_PERFORMANCE] = FeatureCapabilityReport(
            featureId = BoosterFeatureId.FIXED_PERFORMANCE,
            state = when {
                fixedPerfActive -> FeatureState.ACTIVE
                shizukuConnected -> FeatureState.SUPPORTED
                else -> FeatureState.PERMISSION_REQUIRED
            },
            currentSummary = if (fixedPerfActive) "Active via cmd power" else "Inactive",
            technicalDetail = if (shizukuConnected) {
                "Ready to invoke 'cmd power set-fixed-performance-mode-enabled'."
            } else {
                "Requires Shizuku ADB permission to execute 'cmd power set-fixed-performance-mode-enabled'."
            }
        )

        // 2. Optimize System Performance
        results[BoosterFeatureId.OPTIMIZE_SYSTEM] = FeatureCapabilityReport(
            featureId = BoosterFeatureId.OPTIMIZE_SYSTEM,
            state = FeatureState.SUPPORTED,
            currentSummary = "RAM Used: ${telemetry.ramUsagePercent}%",
            technicalDetail = if (shizukuConnected) {
                "Full optimization available (ActivityManager + Shizuku pm trim-caches)."
            } else {
                "Standard Android background process cleanup available; connect Shizuku for deeper cache trim."
            }
        )

        // 3. Refresh Rate Control
        val refreshModified = "peak_refresh_rate" in modifiedSettingsKeys
        val multiHzSupported = telemetry.supportedRefreshRatesHz.size > 1
        results[BoosterFeatureId.REFRESH_RATE] = FeatureCapabilityReport(
            featureId = BoosterFeatureId.REFRESH_RATE,
            state = when {
                refreshModified -> FeatureState.ACTIVE
                !multiHzSupported -> FeatureState.UNSUPPORTED
                canWriteSettings || shizukuConnected -> FeatureState.SUPPORTED
                else -> FeatureState.PERMISSION_REQUIRED
            },
            currentSummary = "Current: ${telemetry.currentRefreshRateHz.toInt()} Hz (Supported: ${telemetry.supportedRefreshRatesHz.joinToString("/")} Hz)",
            technicalDetail = when {
                !multiHzSupported -> "Display hardware only exposes a single refresh rate (${telemetry.supportedRefreshRatesHz.firstOrNull() ?: 60} Hz)."
                canWriteSettings || shizukuConnected -> "Display supports ${telemetry.supportedRefreshRatesHz.joinToString(", ")} Hz."
                else -> "Requires 'Modify System Settings' or Shizuku permission to update peak_refresh_rate."
            }
        )

        // 4. 90 FPS Stability Profile
        val has90Hz = telemetry.supportedRefreshRatesHz.any { it >= 90 }
        val stable90Active = "stable_90_fps_profile" in modifiedSettingsKeys
        results[BoosterFeatureId.STABLE_90_FPS] = FeatureCapabilityReport(
            featureId = BoosterFeatureId.STABLE_90_FPS,
            state = when {
                stable90Active -> FeatureState.ACTIVE
                !has90Hz -> FeatureState.UNSUPPORTED
                canWriteSettings || shizukuConnected -> FeatureState.SUPPORTED
                else -> FeatureState.PERMISSION_REQUIRED
            },
            currentSummary = if (has90Hz) "90Hz+ Hardware Detected" else "60Hz Display Limit",
            technicalDetail = if (has90Hz) {
                "Hardware supports ≥90Hz. Monitors Choreographer frame time & thermal headroom."
            } else {
                "Display panel only supports ${telemetry.supportedRefreshRatesHz.joinToString("/")} Hz; 90 Hz mode is not supported on this device."
            }
        )

        // 5. Network Performance
        results[BoosterFeatureId.NETWORK_PERFORMANCE] = FeatureCapabilityReport(
            featureId = BoosterFeatureId.NETWORK_PERFORMANCE,
            state = FeatureState.SUPPORTED,
            currentSummary = "Live Ping & Link Diagnostics",
            technicalDetail = "Real-time socket RTT latency, jitter, DNS server verification, and link bandwidth analysis."
        )

        // 6. Touch Response
        val touchActive = "touch_optimization" in modifiedSettingsKeys
        results[BoosterFeatureId.TOUCH_RESPONSE] = FeatureCapabilityReport(
            featureId = BoosterFeatureId.TOUCH_RESPONSE,
            state = if (touchActive) FeatureState.ACTIVE else FeatureState.SUPPORTED,
            currentSummary = "Hardware touch sampling cannot be changed by this app.",
            technicalDetail = "Optimizes system animation/gesture overhead and measures real touch event latency."
        )

        // 7. UI Animations
        val animModified = "window_animation_scale" in modifiedSettingsKeys
        val animScale = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1.0f)
        } catch (_: Throwable) {
            1.0f
        }
        results[BoosterFeatureId.UI_ANIMATIONS] = FeatureCapabilityReport(
            featureId = BoosterFeatureId.UI_ANIMATIONS,
            state = when {
                animModified -> FeatureState.ACTIVE
                shizukuConnected -> FeatureState.SUPPORTED
                else -> FeatureState.PERMISSION_REQUIRED
            },
            currentSummary = "Current Scale: ${animScale}x",
            technicalDetail = if (shizukuConnected) {
                "Can read and write window, transition, and animator duration scales via Shizuku."
            } else {
                "Requires Shizuku permission to modify Settings.Global animation scales."
            }
        )

        // 8. Window Blur
        val blurSupportedApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val blurModified = "disable_window_blurs" in modifiedSettingsKeys
        val blurCurrent = try {
            Settings.Global.getInt(context.contentResolver, "disable_window_blurs", 0)
        } catch (_: Throwable) {
            0
        }
        results[BoosterFeatureId.WINDOW_BLUR] = FeatureCapabilityReport(
            featureId = BoosterFeatureId.WINDOW_BLUR,
            state = when {
                !blurSupportedApi -> FeatureState.UNSUPPORTED
                blurModified || blurCurrent == 1 -> FeatureState.ACTIVE
                shizukuConnected -> FeatureState.SUPPORTED
                else -> FeatureState.PERMISSION_REQUIRED
            },
            currentSummary = if (!blurSupportedApi) {
                "Window blur control is not supported on this device."
            } else if (blurCurrent == 1) {
                "Window Blurs Disabled (1)"
            } else {
                "Window Blurs Enabled (0)"
            },
            technicalDetail = if (!blurSupportedApi) {
                "Window blur control requires Android 12 (API 31) or higher."
            } else if (shizukuConnected) {
                "Controls Settings.Global 'disable_window_blurs' via Shizuku."
            } else {
                "Requires Shizuku permission to write Settings.Global 'disable_window_blurs'."
            }
        )

        // 9. Gaming Focus Mode
        val dndModified = "dnd_interruption_filter" in modifiedSettingsKeys
        results[BoosterFeatureId.GAMING_FOCUS_MODE] = FeatureCapabilityReport(
            featureId = BoosterFeatureId.GAMING_FOCUS_MODE,
            state = when {
                dndModified -> FeatureState.ACTIVE
                canDnd -> FeatureState.SUPPORTED
                else -> FeatureState.PERMISSION_REQUIRED
            },
            currentSummary = if (dndModified) "DND Priority Focus Active" else "Inactive",
            technicalDetail = if (canDnd) {
                "Ready to suppress heads-up interruptions via NotificationManager policy."
            } else {
                "Requires Notification Policy (Do Not Disturb) access permission from user."
            }
        )

        // 10. Hide Developer Options
        val devEnabled = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        } catch (_: Throwable) {
            false
        }
        results[BoosterFeatureId.HIDE_DEV_OPTIONS] = FeatureCapabilityReport(
            featureId = BoosterFeatureId.HIDE_DEV_OPTIONS,
            state = if (shizukuConnected) FeatureState.SUPPORTED else FeatureState.PERMISSION_REQUIRED,
            currentSummary = if (devEnabled) "Developer Options: Visible (1)" else "Developer Options: Hidden (0)",
            technicalDetail = if (shizukuConnected) {
                "Uses Shizuku to toggle Settings.Global.DEVELOPMENT_SETTINGS_ENABLED (Note: turning off may stop Wireless Debugging)."
            } else {
                "Requires Shizuku permission to modify Settings.Global.DEVELOPMENT_SETTINGS_ENABLED."
            }
        )

        // 11. Screen Resolution
        val resModified = "wm_size" in modifiedSettingsKeys
        results[BoosterFeatureId.SCREEN_RESOLUTION] = FeatureCapabilityReport(
            featureId = BoosterFeatureId.SCREEN_RESOLUTION,
            state = when {
                resModified -> FeatureState.ACTIVE
                shizukuConnected -> FeatureState.SUPPORTED
                else -> FeatureState.PERMISSION_REQUIRED
            },
            currentSummary = telemetry.displayResolution,
            technicalDetail = if (shizukuConnected) {
                "Uses 'wm size' via Shizuku with automatic original resolution backup and verification."
            } else {
                "Requires Shizuku ADB permission to execute 'wm size'."
            }
        )

        // 12. Custom DNS
        val dnsMode = try {
            Settings.Global.getString(context.contentResolver, "private_dns_mode") ?: "opportunistic"
        } catch (_: Throwable) {
            "unknown"
        }
        results[BoosterFeatureId.CUSTOM_DNS] = FeatureCapabilityReport(
            featureId = BoosterFeatureId.CUSTOM_DNS,
            state = if (shizukuConnected) FeatureState.SUPPORTED else FeatureState.PERMISSION_REQUIRED,
            currentSummary = "Mode: $dnsMode",
            technicalDetail = if (shizukuConnected) {
                "Direct Private DNS configuration available via Shizuku, or open system Network settings."
            } else {
                "Direct modification requires Shizuku; otherwise opens Android Network & DNS settings."
            }
        )

        // 13. Crosshair Overlay
        results[BoosterFeatureId.CROSSHAIR_OVERLAY] = FeatureCapabilityReport(
            featureId = BoosterFeatureId.CROSSHAIR_OVERLAY,
            state = when {
                crosshairEnabled -> FeatureState.ACTIVE
                canOverlay -> FeatureState.SUPPORTED
                else -> FeatureState.PERMISSION_REQUIRED
            },
            currentSummary = if (crosshairEnabled) "Active on Screen" else "Disabled",
            technicalDetail = if (canOverlay) {
                "SYSTEM_ALERT_WINDOW permission granted."
            } else {
                "Requires 'Display over other apps' (SYSTEM_ALERT_WINDOW) permission."
            }
        )

        // 14. Real-Time Hardware Monitor
        results[BoosterFeatureId.HARDWARE_MONITOR] = FeatureCapabilityReport(
            featureId = BoosterFeatureId.HARDWARE_MONITOR,
            state = when {
                monitorEnabled -> FeatureState.ACTIVE
                canOverlay -> FeatureState.SUPPORTED
                else -> FeatureState.PERMISSION_REQUIRED
            },
            currentSummary = if (monitorEnabled) "HUD Floating Active" else "Disabled",
            technicalDetail = if (canOverlay) {
                "Displays real RAM, Battery, Temp, CPU, Hz & Choreographer FPS."
            } else {
                "Requires 'Display over other apps' (SYSTEM_ALERT_WINDOW) permission."
            }
        )

        // 15. Native Game Mode API
        val gameModeApiSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        results[BoosterFeatureId.NATIVE_GAME_MODE] = FeatureCapabilityReport(
            featureId = BoosterFeatureId.NATIVE_GAME_MODE,
            state = when {
                !gameModeApiSupported -> FeatureState.UNSUPPORTED
                shizukuConnected -> FeatureState.SUPPORTED
                else -> FeatureState.PERMISSION_REQUIRED
            },
            currentSummary = if (gameModeApiSupported) "Android 12+ GameManager API" else "Game Mode API unavailable.",
            technicalDetail = if (!gameModeApiSupported) {
                "Game Mode API unavailable on Android API ${Build.VERSION.SDK_INT} (requires API 31+)."
            } else if (shizukuConnected) {
                "Can query and switch native Game Mode (Standard / Performance / Battery) via 'cmd game mode'."
            } else {
                "Can read GameManager state; requires Shizuku to change mode for third-party game packages."
            }
        )

        // 16. Thermal Management
        results[BoosterFeatureId.THERMAL_MANAGEMENT] = FeatureCapabilityReport(
            featureId = BoosterFeatureId.THERMAL_MANAGEMENT,
            state = FeatureState.ACTIVE,
            currentSummary = "State: ${telemetry.thermalLevel.label}",
            technicalDetail = telemetry.thermalLevel.policyDescription
        )

        // 17. Storage Optimizer
        results[BoosterFeatureId.STORAGE_OPTIMIZER] = FeatureCapabilityReport(
            featureId = BoosterFeatureId.STORAGE_OPTIMIZER,
            state = FeatureState.SUPPORTED,
            currentSummary = "${telemetry.storageUsagePercent}% Used",
            technicalDetail = "Analyzes partition usage and cleans safe temporary cache with user confirmation."
        )

        return results
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) {
            false
        }
    }

    fun openPermissionSettings(context: Context, id: PermissionItemId) {
        try {
            when (id) {
                PermissionItemId.SHIZUKU -> {
                    val state = ShizukuManager.state.value
                    if (state.isBinderAlive && !state.isPermissionGranted) {
                        ShizukuManager.requestPermission()
                    } else if (state.isInstalled) {
                        ShizukuManager.openShizukuApp(context)
                    } else {
                        ShizukuManager.openDeveloperSettings(context)
                    }
                }
                PermissionItemId.WIRELESS_DEBUGGING -> {
                    ShizukuManager.openDeveloperSettings(context)
                }
                PermissionItemId.OVERLAY -> {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                }
                PermissionItemId.WRITE_SETTINGS -> {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                }
                PermissionItemId.NOTIFICATION_POLICY -> {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                PermissionItemId.POST_NOTIFICATIONS -> {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                PermissionItemId.USAGE_ACCESS -> {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                PermissionItemId.BATTERY_OPTIMIZATION -> {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                }
            }
        } catch (_: Throwable) {
            try {
                val fallback = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(fallback)
            } catch (_: Throwable) {
            }
        }
    }
}
