package com.example.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.telemetry.DeviceTelemetryProvider
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Real Foreground Service that manages the floating Crosshair Overlay and
 * Real-Time Hardware Monitor Overlay using Android's [WindowManager].
 */
class HardwareOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var telemetryProvider: DeviceTelemetryProvider
    private val mainHandler = Handler(Looper.getMainLooper())

    private var crosshairView: CrosshairCanvasView? = null
    private var crosshairParams: WindowManager.LayoutParams? = null

    private var monitorContainer: LinearLayout? = null
    private var monitorStatsTextView: TextView? = null
    private var monitorHeaderTextView: TextView? = null
    private var monitorParams: WindowManager.LayoutParams? = null

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!Settings.canDrawOverlays(this@HardwareOverlayService)) {
                OverlayStateController.disableAllOverlays(this@HardwareOverlayService)
                stopSelf()
                return
            }
            syncViewsWithState()
            mainHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        telemetryProvider = DeviceTelemetryProvider(applicationContext)
        telemetryProvider.startFrameMonitoring()
        startForegroundSafely()
        mainHandler.post(updateRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely()
        syncViewsWithState()
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(updateRunnable)
        telemetryProvider.stopFrameMonitoring()
        removeCrosshairView()
        removeMonitorView()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundSafely() {
        val channelId = "apex_hardware_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                channelId,
                "Hardware Telemetry & Crosshair Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays real-time hardware stats and crosshair overlay during gaming."
            }
            nm.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("ApexBoost Overlay Active")
            .setContentText("Monitoring real-time hardware telemetry & overlay")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    2001,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(2001, notification)
            }
        } catch (_: Throwable) {
        }
    }

    private fun syncViewsWithState() {
        if (!Settings.canDrawOverlays(this)) {
            removeCrosshairView()
            removeMonitorView()
            return
        }

        val chConfig = OverlayStateController.crosshair.value
        val monConfig = OverlayStateController.monitor.value

        if (!chConfig.enabled && !monConfig.enabled) {
            removeCrosshairView()
            removeMonitorView()
            stopSelf()
            return
        }

        if (chConfig.enabled) {
            ensureOrUpdateCrosshairView(chConfig)
        } else {
            removeCrosshairView()
        }

        if (monConfig.enabled) {
            val snap = telemetryProvider.refresh()
            ensureOrUpdateMonitorView(monConfig, snap)
        } else {
            removeMonitorView()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureOrUpdateCrosshairView(config: CrosshairConfig) {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val sizePx = dpToPx(config.sizeDp.coerceIn(12, 80))
        val flags = if (config.draggable) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        if (crosshairView == null) {
            val view = CrosshairCanvasView(this)
            val params = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                overlayType,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                x = config.offsetX
                y = config.offsetY
            }

            view.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var touchX = 0f
                private var touchY = 0f

                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    if (!OverlayStateController.crosshair.value.draggable) return false
                    val currentParams = crosshairParams ?: return false
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = currentParams.x
                            initialY = currentParams.y
                            touchX = event.rawX
                            touchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - touchX).roundToInt()
                            val dy = (event.rawY - touchY).roundToInt()
                            currentParams.x = initialX + dx
                            currentParams.y = initialY + dy
                            try {
                                windowManager.updateViewLayout(view, currentParams)
                            } catch (_: Throwable) {
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            OverlayStateController.updateCrosshairConfig(this@HardwareOverlayService) {
                                it.copy(offsetX = currentParams.x, offsetY = currentParams.y)
                            }
                            return true
                        }
                    }
                    return false
                }
            })

            try {
                windowManager.addView(view, params)
                crosshairView = view
                crosshairParams = params
            } catch (_: Throwable) {
            }
        } else {
            val view = crosshairView ?: return
            val params = crosshairParams ?: return
            params.width = sizePx
            params.height = sizePx
            params.flags = flags
            params.x = config.offsetX
            params.y = config.offsetY
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Throwable) {
            }
        }

        crosshairView?.updateConfig(config)
    }

    private fun removeCrosshairView() {
        crosshairView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {
            }
        }
        crosshairView = null
        crosshairParams = null
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun ensureOrUpdateMonitorView(
        config: HardwareMonitorConfig,
        snap: com.example.telemetry.DeviceTelemetryProvider.Companion? = null
    ) {
        val telemetry = telemetryProvider.snapshot.value
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        if (monitorContainer == null) {
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val pad = dpToPx(10)
                setPadding(pad, dpToPx(6), pad, dpToPx(8))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(12).toFloat()
                    setColor(Color.argb((config.opacity * 235).roundToInt().coerceIn(80, 245), 12, 18, 28))
                    setStroke(dpToPx(1), Color.argb(180, 0, 229, 255))
                }
            }

            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val titleTv = TextView(this).apply {
                text = "APEX HUD  [–]"
                setTextColor(Color.parseColor("#00E5FF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setOnClickListener {
                    OverlayStateController.updateMonitorConfig(this@HardwareOverlayService) {
                        it.copy(minimized = !it.minimized)
                    }
                }
            }

            val closeTv = TextView(this).apply {
                text = "  ✕"
                setTextColor(Color.parseColor("#FF6B6B"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setOnClickListener {
                    OverlayStateController.updateMonitorConfig(this@HardwareOverlayService) {
                        it.copy(enabled = false)
                    }
                }
            }

            headerRow.addView(titleTv)
            headerRow.addView(closeTv)

            val statsTv = TextView(this).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.MONOSPACE
            }

            container.addView(headerRow)
            container.addView(statsTv)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = config.posX
                y = config.posY
            }

            container.setOnTouchListener(object : View.OnTouchListener {
                private var startX = 0
                private var startY = 0
                private var touchX = 0f
                private var touchY = 0f

                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    val p = monitorParams ?: return false
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = p.x
                            startY = p.y
                            touchX = event.rawX
                            touchY = event.rawY
                            return false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - touchX).roundToInt()
                            val dy = (event.rawY - touchY).roundToInt()
                            if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) {
                                p.x = (startX + dx).coerceAtLeast(0)
                                p.y = (startY + dy).coerceAtLeast(0)
                                try {
                                    windowManager.updateViewLayout(container, p)
                                } catch (_: Throwable) {
                                }
                                return true
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            OverlayStateController.updateMonitorConfig(this@HardwareOverlayService) {
                                it.copy(posX = p.x, posY = p.y)
                            }
                        }
                    }
                    return false
                }
            })

            try {
                windowManager.addView(container, params)
                monitorContainer = container
                monitorHeaderTextView = titleTv
                monitorStatsTextView = statsTv
                monitorParams = params
            } catch (_: Throwable) {
            }
        }

        val container = monitorContainer ?: return
        val statsTv = monitorStatsTextView ?: return
        val headerTv = monitorHeaderTextView ?: return

        (container.background as? GradientDrawable)?.setColor(
            Color.argb((config.opacity * 235).roundToInt().coerceIn(80, 245), 12, 18, 28)
        )

        if (config.minimized) {
            headerTv.text = "APEX HUD ${telemetry.currentRefreshRateHz.roundToInt()}Hz [Expand]"
            statsTv.visibility = View.GONE
        } else {
            headerTv.text = "APEX HUD  [Minimize]"
            statsTv.visibility = View.VISIBLE
            val lines = mutableListOf<String>()
            if (config.showFps) {
                val fpsText = telemetry.measuredFps?.let {
                    String.format(Locale.US, "%.1f FPS (%.1f ms)", it, telemetry.measuredFrameTimeMs ?: 0f)
                } ?: "Sampling..."
                lines.add("FPS : $fpsText")
            }
            if (config.showRefreshRate) {
                lines.add("DISP: ${telemetry.currentRefreshRateHz.roundToInt()} Hz")
            }
            if (config.showRam) {
                lines.add("RAM : ${telemetry.ramUsagePercent}% (${DeviceTelemetryProvider.formatBytes(telemetry.ramAvailableBytes)} free)")
            }
            if (config.showTemperature) {
                val tempStr = telemetry.batteryTempCelsius?.let {
                    String.format(Locale.US, "%.1f°C (%s)", it, telemetry.thermalLevel.label)
                } ?: telemetry.thermalLevel.label
                lines.add("TEMP: $tempStr")
            }
            if (config.showBattery) {
                lines.add("BAT : ${telemetry.batteryPercentage}% (${telemetry.chargingState})")
            }
            if (config.showCpu) {
                lines.add("CPU : ${telemetry.cpuFrequenciesSummary.take(28)}")
            }
            statsTv.text = lines.joinToString("\n")
        }
    }

    private fun removeMonitorView() {
        monitorContainer?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Throwable) {
            }
        }
        monitorContainer = null
        monitorStatsTextView = null
        monitorHeaderTextView = null
        monitorParams = null
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).roundToInt()
    }

    private class CrosshairCanvasView(context: Context) : View(context) {
        private var config: CrosshairConfig = CrosshairConfig()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }

        fun updateConfig(newConfig: CrosshairConfig) {
            config = newConfig
            alpha = newConfig.opacity.coerceIn(0.15f, 1.0f)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = (width.coerceAtMost(height) / 2f) * 0.82f

            paint.color = config.colorArgb
            paint.strokeWidth = (width * 0.07f).coerceAtLeast(3f)

            when (config.style) {
                CrosshairStyle.CROSS -> {
                    paint.style = Paint.Style.STROKE
                    val gap = radius * 0.25f
                    canvas.drawLine(cx - radius, cy, cx - gap, cy, paint)
                    canvas.drawLine(cx + gap, cy, cx + radius, cy, paint)
                    canvas.drawLine(cx, cy - radius, cx, cy - gap, paint)
                    canvas.drawLine(cx, cy + gap, cx, cy + radius, paint)
                }
                CrosshairStyle.DOT -> {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy, (radius * 0.28f).coerceAtLeast(4f), paint)
                }
                CrosshairStyle.CIRCLE_CROSS -> {
                    paint.style = Paint.Style.STROKE
                    canvas.drawCircle(cx, cy, radius * 0.65f, paint)
                    canvas.drawLine(cx - radius, cy, cx - radius * 0.35f, cy, paint)
                    canvas.drawLine(cx + radius * 0.35f, cy, cx + radius, cy, paint)
                    canvas.drawLine(cx, cy - radius, cx, cy - radius * 0.35f, paint)
                    canvas.drawLine(cx, cy + radius * 0.35f, cx, cy + radius, paint)
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy, 3f, paint)
                }
                CrosshairStyle.CHEVRON -> {
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(cx - radius * 0.7f, cy + radius * 0.45f, cx, cy - radius * 0.3f, paint)
                    canvas.drawLine(cx, cy - radius * 0.3f, cx + radius * 0.7f, cy + radius * 0.45f, paint)
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy + radius * 0.55f, 3.5f, paint)
                }
            }
        }
    }
}
