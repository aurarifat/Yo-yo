package com.example.overlay

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.example.shizuku.OperationStatus
import com.example.shizuku.ShellExecutionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CrosshairStyle(val label: String) {
    CROSS("Precision Cross"),
    DOT("Center Dot"),
    CIRCLE_CROSS("Circle Reticle"),
    CHEVRON("Tactical Chevron")
}

data class CrosshairConfig(
    val enabled: Boolean = false,
    val style: CrosshairStyle = CrosshairStyle.CIRCLE_CROSS,
    val sizeDp: Int = 28,
    val opacity: Float = 0.9f,
    val colorArgb: Int = 0xFF00E5FF.toInt(),
    val draggable: Boolean = false,
    val offsetX: Int = 0,
    val offsetY: Int = 0
)

data class HardwareMonitorConfig(
    val enabled: Boolean = false,
    val minimized: Boolean = false,
    val opacity: Float = 0.88f,
    val showRam: Boolean = true,
    val showBattery: Boolean = true,
    val showTemperature: Boolean = true,
    val showCpu: Boolean = true,
    val showRefreshRate: Boolean = true,
    val showFps: Boolean = true,
    val posX: Int = 24,
    val posY: Int = 140
)

object OverlayStateController {

    private const val PREFS_NAME = "apex_overlay_prefs"

    private val _crosshair = MutableStateFlow(CrosshairConfig())
    val crosshair: StateFlow<CrosshairConfig> = _crosshair.asStateFlow()

    private val _monitor = MutableStateFlow(HardwareMonitorConfig())
    val monitor: StateFlow<HardwareMonitorConfig> = _monitor.asStateFlow()

    fun loadSavedConfig(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val styleName = prefs.getString("ch_style", CrosshairStyle.CIRCLE_CROSS.name) ?: CrosshairStyle.CIRCLE_CROSS.name
        val style = CrosshairStyle.entries.firstOrNull { it.name == styleName } ?: CrosshairStyle.CIRCLE_CROSS

        _crosshair.value = CrosshairConfig(
            enabled = false,
            style = style,
            sizeDp = prefs.getInt("ch_size", 28),
            opacity = prefs.getFloat("ch_opacity", 0.9f),
            colorArgb = prefs.getInt("ch_color", 0xFF00E5FF.toInt()),
            draggable = prefs.getBoolean("ch_draggable", false),
            offsetX = prefs.getInt("ch_x", 0),
            offsetY = prefs.getInt("ch_y", 0)
        )

        _monitor.value = HardwareMonitorConfig(
            enabled = false,
            minimized = prefs.getBoolean("mon_min", false),
            opacity = prefs.getFloat("mon_opacity", 0.88f),
            showRam = prefs.getBoolean("mon_ram", true),
            showBattery = prefs.getBoolean("mon_bat", true),
            showTemperature = prefs.getBoolean("mon_temp", true),
            showCpu = prefs.getBoolean("mon_cpu", true),
            showRefreshRate = prefs.getBoolean("mon_hz", true),
            showFps = prefs.getBoolean("mon_fps", true),
            posX = prefs.getInt("mon_x", 24),
            posY = prefs.getInt("mon_y", 140)
        )
    }

    fun updateCrosshairConfig(context: Context, transform: (CrosshairConfig) -> CrosshairConfig): ShellExecutionResult {
        val current = _crosshair.value
        val updated = transform(current)
        if (updated.enabled && !Settings.canDrawOverlays(context)) {
            return ShellExecutionResult(
                status = OperationStatus.PERMISSION_REQUIRED,
                command = "SYSTEM_ALERT_WINDOW (Crosshair Overlay)",
                technicalReason = "Overlay permission (Display over other apps) is required before enabling Crosshair Overlay."
            )
        }
        _crosshair.value = updated
        savePrefs(context)
        syncOverlayService(context)
        return ShellExecutionResult(
            status = OperationStatus.SUCCESS,
            command = "CrosshairOverlay(${if (updated.enabled) "ENABLED" else "DISABLED"})",
            exitCode = 0,
            technicalReason = if (updated.enabled) {
                "Crosshair overlay active (${updated.style.label}, ${updated.sizeDp}dp)."
            } else {
                "Crosshair overlay hidden."
            }
        )
    }

    fun updateMonitorConfig(context: Context, transform: (HardwareMonitorConfig) -> HardwareMonitorConfig): ShellExecutionResult {
        val current = _monitor.value
        val updated = transform(current)
        if (updated.enabled && !Settings.canDrawOverlays(context)) {
            return ShellExecutionResult(
                status = OperationStatus.PERMISSION_REQUIRED,
                command = "SYSTEM_ALERT_WINDOW (Hardware Monitor)",
                technicalReason = "Overlay permission (Display over other apps) is required before starting Hardware Monitor."
            )
        }
        _monitor.value = updated
        savePrefs(context)
        syncOverlayService(context)
        return ShellExecutionResult(
            status = OperationStatus.SUCCESS,
            command = "HardwareMonitorOverlay(${if (updated.enabled) "ENABLED" else "DISABLED"})",
            exitCode = 0,
            technicalReason = if (updated.enabled) {
                "Real-time Hardware Monitor overlay started."
            } else {
                "Real-time Hardware Monitor overlay stopped."
            }
        )
    }

    fun disableAllOverlays(context: Context) {
        _crosshair.value = _crosshair.value.copy(enabled = false)
        _monitor.value = _monitor.value.copy(enabled = false)
        savePrefs(context)
        try {
            context.stopService(Intent(context, HardwareOverlayService::class.java))
        } catch (_: Throwable) {
        }
    }

    private fun savePrefs(context: Context) {
        val ch = _crosshair.value
        val mon = _monitor.value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString("ch_style", ch.style.name)
            putInt("ch_size", ch.sizeDp)
            putFloat("ch_opacity", ch.opacity)
            putInt("ch_color", ch.colorArgb)
            putBoolean("ch_draggable", ch.draggable)
            putInt("ch_x", ch.offsetX)
            putInt("ch_y", ch.offsetY)

            putBoolean("mon_min", mon.minimized)
            putFloat("mon_opacity", mon.opacity)
            putBoolean("mon_ram", mon.showRam)
            putBoolean("mon_bat", mon.showBattery)
            putBoolean("mon_temp", mon.showTemperature)
            putBoolean("mon_cpu", mon.showCpu)
            putBoolean("mon_hz", mon.showRefreshRate)
            putBoolean("mon_fps", mon.showFps)
            putInt("mon_x", mon.posX)
            putInt("mon_y", mon.posY)
            apply()
        }
    }

    private fun syncOverlayService(context: Context) {
        val chEnabled = _crosshair.value.enabled
        val monEnabled = _monitor.value.enabled
        val intent = Intent(context, HardwareOverlayService::class.java)
        try {
            if ((chEnabled || monEnabled) && Settings.canDrawOverlays(context)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.stopService(intent)
            }
        } catch (_: Throwable) {
        }
    }
}
