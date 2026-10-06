package com.example.telemetry

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.view.Choreographer
import android.view.Display
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

enum class ThermalLevel(val label: String, val policyDescription: String) {
    COOL("COOL", "Normal / High performance profiles permitted."),
    NORMAL("NORMAL", "Normal performance profile active."),
    WARM("WARM", "Reducing unnecessary background work to stabilize thermals."),
    HOT("HOT", "Warning: Device is warm/hot. Aggressive optimizations automatically reduced."),
    CRITICAL("CRITICAL", "Safety Priority: Aggressive performance operations stopped to protect hardware.")
}

data class DisplayModeSpec(
    val modeId: Int,
    val width: Int,
    val height: Int,
    val refreshRateHz: Float
) {
    val roundedHz: Int
        get() = refreshRateHz.roundToInt()
}

data class DeviceTelemetrySnapshot(
    val manufacturer: String = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
    val model: String = Build.MODEL,
    val deviceBoard: String = "${Build.BRAND} / ${Build.HARDWARE}",
    val androidVersion: String = Build.VERSION.RELEASE,
    val apiLevel: Int = Build.VERSION.SDK_INT,
    val ramTotalBytes: Long = 0L,
    val ramAvailableBytes: Long = 0L,
    val ramUsedBytes: Long = 0L,
    val ramUsagePercent: Int = 0,
    val isLowMemory: Boolean = false,
    val storageTotalBytes: Long = 0L,
    val storageAvailableBytes: Long = 0L,
    val storageUsedBytes: Long = 0L,
    val storageUsagePercent: Int = 0,
    val batteryPercentage: Int = 0,
    val chargingState: String = "Unknown",
    val isCharging: Boolean = false,
    val batteryTempCelsius: Float? = null,
    val batteryVoltageMv: Int = 0,
    val displayResolution: String = "Unknown",
    val displayWidthPx: Int = 0,
    val displayHeightPx: Int = 0,
    val displayDensityDpi: Int = 0,
    val currentRefreshRateHz: Float = 60f,
    val supportedRefreshRatesHz: List<Int> = listOf(60),
    val supportedDisplayModes: List<DisplayModeSpec> = emptyList(),
    val cpuArchitecture: String = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown",
    val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    val cpuHardwareName: String = "Android SoC",
    val cpuFrequenciesSummary: String = "Reading...",
    val thermalLevel: ThermalLevel = ThermalLevel.NORMAL,
    val systemThermalStatusRaw: String = "Normal",
    val measuredFps: Float? = null,
    val measuredFrameTimeMs: Float? = null,
    val fpsMeasurementSource: String = "Choreographer Display Cadence",
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Reads real Android hardware and system telemetry using standard Android SDK APIs
 * and readable `/sys` or `/proc` nodes where permitted by the OS.
 */
class DeviceTelemetryProvider(private val context: Context) {

    private val _snapshot = MutableStateFlow(DeviceTelemetrySnapshot())
    val snapshot: StateFlow<DeviceTelemetrySnapshot> = _snapshot.asStateFlow()

    private var frameMonitorActive = false
    private var lastFrameTimeNs = 0L
    private val frameIntervalsNs = ArrayDeque<Long>(32)

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!frameMonitorActive) return
            if (lastFrameTimeNs != 0L) {
                val delta = frameTimeNanos - lastFrameTimeNs
                if (delta in 1_000_000L..250_000_000L) {
                    if (frameIntervalsNs.size >= 30) {
                        frameIntervalsNs.removeFirst()
                    }
                    frameIntervalsNs.addLast(delta)
                }
            }
            lastFrameTimeNs = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun startFrameMonitoring() {
        if (!frameMonitorActive) {
            frameMonitorActive = true
            lastFrameTimeNs = 0L
            frameIntervalsNs.clear()
            try {
                Choreographer.getInstance().postFrameCallback(frameCallback)
            } catch (_: Throwable) {
                frameMonitorActive = false
            }
        }
    }

    fun stopFrameMonitoring() {
        frameMonitorActive = false
        try {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        } catch (_: Throwable) {
        }
    }

    fun refresh(): DeviceTelemetrySnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val ramTotal = memInfo.totalMem.coerceAtLeast(1L)
        val ramAvail = memInfo.availMem.coerceAtLeast(0L)
        val ramUsed = (ramTotal - ramAvail).coerceAtLeast(0L)
        val ramPercent = ((ramUsed.toDouble() / ramTotal.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)

        // Storage metrics from real data partition
        val dataDir = Environment.getDataDirectory()
        val statFs = StatFs(dataDir.path)
        val storageTotal = statFs.totalBytes.coerceAtLeast(1L)
        val storageAvail = statFs.availableBytes.coerceAtLeast(0L)
        val storageUsed = (storageTotal - storageAvail).coerceAtLeast(0L)
        val storagePercent = ((storageUsed.toDouble() / storageTotal.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)

        // Battery metrics from real sticky broadcast
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) ((level * 100f) / scale).roundToInt() else 0

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val chargingLabel = when {
            status == BatteryManager.BATTERY_STATUS_FULL -> "Full"
            status == BatteryManager.BATTERY_STATUS_CHARGING && plugged == BatteryManager.BATTERY_PLUGGED_AC -> "Charging (AC)"
            status == BatteryManager.BATTERY_STATUS_CHARGING && plugged == BatteryManager.BATTERY_PLUGGED_USB -> "Charging (USB)"
            status == BatteryManager.BATTERY_STATUS_CHARGING && plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Charging (Wireless)"
            status == BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            status == BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            status == BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Discharging"
        }

        val rawTempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val tempCelsius = if (rawTempTenths != Int.MIN_VALUE && rawTempTenths > -200) {
            rawTempTenths / 10f
        } else {
            readThermalZoneTempCelsius()
        }
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        // Display metrics & supported refresh rates
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val defaultDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = context.resources.displayMetrics

        var widthPx = metrics.widthPixels
        var heightPx = metrics.heightPixels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            widthPx = bounds.width()
            heightPx = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            defaultDisplay?.getRealMetrics(metrics)
            widthPx = metrics.widthPixels
            heightPx = metrics.heightPixels
        }

        val currentHz = defaultDisplay?.refreshRate ?: 60f
        val modes = defaultDisplay?.supportedModes?.map {
            DisplayModeSpec(
                modeId = it.modeId,
                width = it.physicalWidth,
                height = it.physicalHeight,
                refreshRateHz = it.refreshRate
            )
        } ?: listOf(DisplayModeSpec(1, widthPx, heightPx, currentHz))

        val supportedHzList = modes
            .map { it.roundedHz }
            .distinct()
            .sorted()
            .ifEmpty { listOf(currentHz.roundToInt()) }

        // CPU Info
        val cpuModel = readCpuHardwareModel()
        val cpuFreqSummary = readCpuFrequencies(Runtime.getRuntime().availableProcessors())

        // Thermal State
        val (thermalLevel, rawThermalDesc) = evaluateThermalState(tempCelsius)

        // Frame Timing / FPS
        val (fps, frameMs) = computeMeasuredFrameStats()

        val newSnapshot = DeviceTelemetrySnapshot(
            ramTotalBytes = ramTotal,
            ramAvailableBytes = ramAvail,
            ramUsedBytes = ramUsed,
            ramUsagePercent = ramPercent,
            isLowMemory = memInfo.lowMemory,
            storageTotalBytes = storageTotal,
            storageAvailableBytes = storageAvail,
            storageUsedBytes = storageUsed,
            storageUsagePercent = storagePercent,
            batteryPercentage = batteryPct,
            chargingState = chargingLabel,
            isCharging = isCharging,
            batteryTempCelsius = tempCelsius,
            batteryVoltageMv = voltageMv,
            displayResolution = "${widthPx} × ${heightPx} (${metrics.densityDpi} dpi)",
            displayWidthPx = widthPx,
            displayHeightPx = heightPx,
            displayDensityDpi = metrics.densityDpi,
            currentRefreshRateHz = currentHz,
            supportedRefreshRatesHz = supportedHzList,
            supportedDisplayModes = modes,
            cpuHardwareName = cpuModel,
            cpuFrequenciesSummary = cpuFreqSummary,
            thermalLevel = thermalLevel,
            systemThermalStatusRaw = rawThermalDesc,
            measuredFps = fps,
            measuredFrameTimeMs = frameMs,
            timestampMs = System.currentTimeMillis()
        )
        _snapshot.value = newSnapshot
        return newSnapshot
    }

    private fun computeMeasuredFrameStats(): Pair<Float?, Float?> {
        if (!frameMonitorActive || frameIntervalsNs.size < 4) {
            return null to null
        }
        val copy = frameIntervalsNs.toList()
        val avgNs = copy.average()
        if (avgNs <= 0.0) return null to null
        val frameMs = (avgNs / 1_000_000.0).toFloat()
        val fps = (1_000_000_000.0 / avgNs).toFloat()
        return fps to frameMs
    }

    private fun evaluateThermalState(batteryTempC: Float?): Pair<ThermalLevel, String> {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        var apiStatusLabel = "Not reported by OS"
        var apiLevelThermal = ThermalLevel.NORMAL

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val status = pm.currentThermalStatus
            when (status) {
                PowerManager.THERMAL_STATUS_NONE -> {
                    apiStatusLabel = "THERMAL_STATUS_NONE"
                    apiLevelThermal = ThermalLevel.COOL
                }
                PowerManager.THERMAL_STATUS_LIGHT -> {
                    apiStatusLabel = "THERMAL_STATUS_LIGHT"
                    apiLevelThermal = ThermalLevel.NORMAL
                }
                PowerManager.THERMAL_STATUS_MODERATE -> {
                    apiStatusLabel = "THERMAL_STATUS_MODERATE"
                    apiLevelThermal = ThermalLevel.WARM
                }
                PowerManager.THERMAL_STATUS_SEVERE -> {
                    apiStatusLabel = "THERMAL_STATUS_SEVERE"
                    apiLevelThermal = ThermalLevel.HOT
                }
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> {
                    apiStatusLabel = "THERMAL_STATUS_CRITICAL ($status)"
                    apiLevelThermal = ThermalLevel.CRITICAL
                }
            }
        }

        val tempBased = when {
            batteryTempC == null -> ThermalLevel.NORMAL
            batteryTempC >= 45.0f -> ThermalLevel.CRITICAL
            batteryTempC >= 41.5f -> ThermalLevel.HOT
            batteryTempC >= 38.0f -> ThermalLevel.WARM
            batteryTempC <= 32.5f -> ThermalLevel.COOL
            else -> ThermalLevel.NORMAL
        }

        val finalLevel = if (tempBased.ordinal > apiLevelThermal.ordinal) tempBased else apiLevelThermal
        return finalLevel to apiStatusLabel
    }

    private fun readCpuHardwareModel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val soc = "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim()
            if (soc.isNotEmpty() && !soc.contains("unknown", ignoreCase = true)) {
                return soc
            }
        }
        return try {
            val cpuInfoFile = File("/proc/cpuinfo")
            if (cpuInfoFile.canRead()) {
                cpuInfoFile.useLines { lines ->
                    lines.firstOrNull {
                        it.startsWith("Hardware", ignoreCase = true) ||
                            it.startsWith("model name", ignoreCase = true)
                    }?.substringAfter(":")?.trim()
                } ?: Build.HARDWARE
            } else {
                Build.HARDWARE
            }
        } catch (_: Throwable) {
            Build.HARDWARE
        }
    }

    private fun readCpuFrequencies(coreCount: Int): String {
        val freqsMhz = mutableListOf<String>()
        for (i in 0 until coreCount.coerceAtMost(8)) {
            val curFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            val maxFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            try {
                if (curFile.canRead()) {
                    val khz = curFile.readText().trim().toLongOrNull()
                    if (khz != null && khz > 0) {
                        freqsMhz.add("C$i: ${khz / 1000}MHz")
                        continue
                    }
                }
                if (maxFile.canRead()) {
                    val maxKhz = maxFile.readText().trim().toLongOrNull()
                    if (maxKhz != null && maxKhz > 0) {
                        freqsMhz.add("C$i max: ${maxKhz / 1000}MHz")
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return if (freqsMhz.isNotEmpty()) {
            freqsMhz.joinToString(" • ")
        } else {
            "$coreCount cores (${Build.SUPPORTED_ABIS.firstOrNull() ?: "ARM"}) • Cur freq restricted by SELinux"
        }
    }

    private fun readThermalZoneTempCelsius(): Float? {
        return try {
            val file = File("/sys/class/thermal/thermal_zone0/temp")
            if (file.canRead()) {
                val raw = file.readText().trim().toFloatOrNull() ?: return null
                if (raw > 1000f) raw / 1000f else raw
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 MB"
            val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            return if (gb >= 1.0) {
                String.format(Locale.US, "%.2f GB", gb)
            } else {
                val mb = bytes.toDouble() / (1024.0 * 1024.0)
                String.format(Locale.US, "%.0f MB", mb)
            }
        }
    }
}
