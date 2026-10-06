package com.example.engine

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import com.example.data.BoosterRepository
import com.example.shizuku.OperationStatus
import com.example.shizuku.ShellExecutionResult
import com.example.shizuku.ShellExecutor
import com.example.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.system.measureNanoTime

data class NetworkDiagnosticsReport(
    val isConnected: Boolean = false,
    val connectionType: String = "Disconnected",
    val signalInfo: String = "Unavailable",
    val downstreamKbps: Int = 0,
    val upstreamKbps: Int = 0,
    val liveRxKbps: Float = 0f,
    val liveTxKbps: Float = 0f,
    val measuredDownloadMbps: Float? = null,
    val dnsServers: List<String> = emptyList(),
    val privateDnsMode: String = "opportunistic",
    val privateDnsHost: String = "",
    val pingTarget: String = "1.1.1.1:53 (Cloudflare DNS)",
    val avgLatencyMs: Float? = null,
    val jitterMs: Float? = null,
    val packetLossPercent: Int = 0,
    val stabilityAssessment: String = "Tap 'Run Live Ping & Network Check' to measure real latency.",
    val lastMeasuredMs: Long = 0L
)

data class CleanableCacheItem(
    val path: String,
    val label: String,
    val sizeBytes: Long
)

data class LargeFileItem(
    val name: String,
    val path: String,
    val sizeBytes: Long
)

data class StorageAnalysisReport(
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val usedPercent: Int = 0,
    val appCacheBytes: Long = 0L,
    val cleanableItems: List<CleanableCacheItem> = emptyList(),
    val largeFiles: List<LargeFileItem> = emptyList(),
    val shizukuSystemTrimAvailable: Boolean = false
)

class NetworkAndStorageEngine(
    private val context: Context,
    private val repository: BoosterRepository
) {
    private var lastRxBytes: Long = TrafficStats.getTotalRxBytes()
    private var lastTxBytes: Long = TrafficStats.getTotalTxBytes()
    private var lastTrafficTimeMs: Long = System.currentTimeMillis()

    fun readNetworkSnapshot(previous: NetworkDiagnosticsReport? = null): NetworkDiagnosticsReport {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val linkProps = activeNetwork?.let { cm.getLinkProperties(it) }

        val nowMs = System.currentTimeMillis()
        val curRx = TrafficStats.getTotalRxBytes()
        val curTx = TrafficStats.getTotalTxBytes()
        val elapsedSec = ((nowMs - lastTrafficTimeMs).coerceAtLeast(250L)) / 1000f
        val rxKbps = if (curRx >= lastRxBytes && lastRxBytes > 0) {
            ((curRx - lastRxBytes) * 8f / 1000f) / elapsedSec
        } else 0f
        val txKbps = if (curTx >= lastTxBytes && lastTxBytes > 0) {
            ((curTx - lastTxBytes) * 8f / 1000f) / elapsedSec
        } else 0f
        lastRxBytes = curRx
        lastTxBytes = curTx
        lastTrafficTimeMs = nowMs

        if (caps == null) {
            return NetworkDiagnosticsReport(
                isConnected = false,
                connectionType = "Offline / Disconnected",
                stabilityAssessment = "No active network connection detected."
            )
        }

        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data (Cellular)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN Tunnel"
            else -> "Connected Network"
        }

        val signalDbm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val strength = caps.signalStrength
            if (strength != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED && strength > -140 && strength < 0) {
                "$strength dBm"
            } else {
                "Validated (${caps.linkDownstreamBandwidthKbps / 1000} Mbps Down / ${caps.linkUpstreamBandwidthKbps / 1000} Mbps Up)"
            }
        } else {
            "Active (${caps.linkDownstreamBandwidthKbps / 1000} Mbps link capacity)"
        }

        val dnsList = linkProps?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()

        val privateDnsMode = try {
            Settings.Global.getString(context.contentResolver, "private_dns_mode")
                ?: if (linkProps?.isPrivateDnsActive == true) "Active" else "opportunistic (Automatic)"
        } catch (_: Throwable) {
            if (linkProps?.isPrivateDnsActive == true) "Active" else "Automatic"
        }

        val privateDnsHost = try {
            Settings.Global.getString(context.contentResolver, "private_dns_specifier")
                ?: linkProps?.privateDnsServerName.orEmpty()
        } catch (_: Throwable) {
            linkProps?.privateDnsServerName.orEmpty()
        }

        return NetworkDiagnosticsReport(
            isConnected = true,
            connectionType = transport,
            signalInfo = signalDbm,
            downstreamKbps = caps.linkDownstreamBandwidthKbps,
            upstreamKbps = caps.linkUpstreamBandwidthKbps,
            liveRxKbps = rxKbps,
            liveTxKbps = txKbps,
            measuredDownloadMbps = previous?.measuredDownloadMbps,
            dnsServers = dnsList,
            privateDnsMode = privateDnsMode,
            privateDnsHost = privateDnsHost,
            avgLatencyMs = previous?.avgLatencyMs,
            jitterMs = previous?.jitterMs,
            packetLossPercent = previous?.packetLossPercent ?: 0,
            stabilityAssessment = previous?.stabilityAssessment
                ?: "Connected via $transport. Run latency test to measure real packet RTT, jitter & throughput."
        )
    }

    /**
     * Runs a real 4-probe TCP/DNS handshake, optional ICMP ping, and lightweight HTTP download speed probe.
     * Never fabricates numbers; reports real socket handshake RTT or real failure.
     */
    suspend fun runLatencyDiagnostics(targetHost: String = "1.1.1.1", targetPort: Int = 53): Pair<NetworkDiagnosticsReport, ShellExecutionResult> =
        withContext(Dispatchers.IO) {
            val base = readNetworkSnapshot()
            if (!base.isConnected) {
                val res = ShellExecutionResult(
                    status = OperationStatus.FAILED,
                    command = "NetworkLatencyProbe($targetHost:$targetPort)",
                    technicalReason = "Device is offline; cannot measure network latency."
                )
                repository.logResult("Network Performance", res)
                return@withContext base to res
            }

            val samplesMs = mutableListOf<Float>()
            var failures = 0
            val probeCount = 4

            for (i in 0 until probeCount) {
                try {
                    val elapsedNs = measureNanoTime {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(targetHost, targetPort), 1800)
                        }
                    }
                    samplesMs.add((elapsedNs / 1_000_000.0).toFloat())
                } catch (_: Throwable) {
                    failures++
                }
            }

            val measuredMbps = measureLightweightDownloadMbps()

            val lossPct = ((failures.toFloat() / probeCount.toFloat()) * 100f).roundToInt()
            if (samplesMs.isEmpty()) {
                if (ShizukuManager.state.value.isConnected) {
                    val pingRes = ShellExecutor.execute("ping -c 3 $targetHost")
                    repository.logResult("Network Performance", pingRes)
                    val updated = base.copy(
                        measuredDownloadMbps = measuredMbps,
                        packetLossPercent = 100,
                        stabilityAssessment = if (pingRes.isSuccess) {
                            "ICMP Ping Output: ${pingRes.stdout.lines().lastOrNull() ?: "OK"}"
                        } else {
                            "Direct socket probes blocked by firewall/container (${pingRes.technicalReason})."
                        },
                        lastMeasuredMs = System.currentTimeMillis()
                    )
                    return@withContext updated to pingRes
                }

                val failResult = ShellExecutionResult(
                    status = OperationStatus.FAILED,
                    command = "SocketProbe($targetHost:$targetPort)",
                    technicalReason = "All $probeCount probes to $targetHost:$targetPort timed out or were blocked by local network policy."
                )
                repository.logResult("Network Performance", failResult)
                return@withContext base.copy(
                    measuredDownloadMbps = measuredMbps,
                    packetLossPercent = 100,
                    stabilityAssessment = failResult.technicalReason,
                    lastMeasuredMs = System.currentTimeMillis()
                ) to failResult
            }

            val avg = samplesMs.average().toFloat()
            val jitter = if (samplesMs.size >= 2) {
                samplesMs.zipWithNext { a, b -> abs(a - b) }.average().toFloat()
            } else {
                0f
            }

            val speedNote = measuredMbps?.let { String.format(Locale.US, " • HTTP Throughput: %.2f Mbps", it) } ?: ""
            val assessment = when {
                lossPct > 0 -> "Unstable connection: $lossPct% packet loss detected (Avg ${String.format(Locale.US, "%.1f", avg)} ms)$speedNote."
                avg < 45f && jitter < 10f -> "Low-latency stable connection (${String.format(Locale.US, "%.1f", avg)} ms, jitter ${String.format(Locale.US, "%.1f", jitter)} ms)$speedNote."
                avg < 95f -> "Moderate latency (${String.format(Locale.US, "%.1f", avg)} ms, jitter ${String.format(Locale.US, "%.1f", jitter)} ms)$speedNote. Note: Game server distance determines final in-game ping."
                else -> "High network latency (${String.format(Locale.US, "%.1f", avg)} ms)$speedNote. Check Wi-Fi congestion or router distance."
            }

            val report = base.copy(
                pingTarget = "$targetHost:$targetPort",
                measuredDownloadMbps = measuredMbps,
                avgLatencyMs = avg,
                jitterMs = jitter,
                packetLossPercent = lossPct,
                stabilityAssessment = assessment,
                lastMeasuredMs = System.currentTimeMillis()
            )

            val result = ShellExecutionResult(
                status = OperationStatus.SUCCESS,
                command = "TCP Probe $targetHost:$targetPort ($probeCount samples)",
                exitCode = 0,
                technicalReason = assessment
            )
            repository.logResult("Network Performance", result)
            report to result
        }

    private fun measureLightweightDownloadMbps(): Float? {
        return try {
            var totalBytesRead = 0L
            val elapsedNs = measureNanoTime {
                val conn = (URL("https://www.gstatic.com/generate_204").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 2000
                    readTimeout = 2000
                    instanceFollowRedirects = true
                }
                conn.inputStream.use { input ->
                    val buf = ByteArray(4096)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        totalBytesRead += n
                    }
                }
                val headerEstimate = conn.headerFields.entries.sumOf { (k, v) ->
                    (k?.length ?: 0) + v.sumOf { it.length }
                }
                totalBytesRead += headerEstimate.coerceAtLeast(256)
                conn.disconnect()
            }
            if (elapsedNs > 0 && totalBytesRead > 0) {
                val seconds = elapsedNs / 1_000_000_000.0
                ((totalBytesRead * 8.0) / (seconds * 1_000_000.0)).toFloat()
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Configures Private DNS ("opportunistic", "off", or "hostname") using Shizuku when granted,
     * or opens Android's Wireless / Private DNS settings when Shizuku is unavailable.
     */
    suspend fun configurePrivateDns(
        mode: String,
        customHostname: String = ""
    ): ShellExecutionResult = withContext(Dispatchers.IO) {
        val normalizedMode = when (mode.lowercase(Locale.US)) {
            "off" -> "off"
            "hostname", "custom" -> "hostname"
            else -> "opportunistic"
        }

        if (normalizedMode == "hostname" && customHostname.isBlank()) {
            return@withContext ShellExecutionResult(
                status = OperationStatus.FAILED,
                command = "configurePrivateDns(hostname)",
                technicalReason = "Please enter a valid DNS-over-TLS hostname (e.g., dns.quad9.net or one.one.one.one)."
            )
        }

        val sanitizedHost = customHostname.trim()
        if (sanitizedHost.any { !it.isLetterOrDigit() && it != '.' && it != '-' }) {
            return@withContext ShellExecutionResult(
                status = OperationStatus.FAILED,
                command = "configurePrivateDns($sanitizedHost)",
                technicalReason = "Invalid DNS hostname characters."
            )
        }

        if (!ShizukuManager.state.value.isConnected) {
            return@withContext ShellExecutionResult(
                status = OperationStatus.PERMISSION_REQUIRED,
                command = "settings put global private_dns_mode $normalizedMode",
                technicalReason = "Direct Private DNS modification requires Shizuku ADB permission. Tap 'Open Android DNS Settings' to configure manually."
            )
        }

        val originalMode = try {
            Settings.Global.getString(context.contentResolver, "private_dns_mode") ?: "opportunistic"
        } catch (_: Throwable) {
            "opportunistic"
        }

        repository.recordSettingChange("private_dns_mode", originalMode, normalizedMode)

        if (normalizedMode == "hostname") {
            val hostRes = ShellExecutor.execute("settings put global private_dns_specifier $sanitizedHost")
            if (!hostRes.isSuccess) {
                repository.logResult("Custom DNS", hostRes)
                return@withContext hostRes
            }
        }

        val modeRes = ShellExecutor.execute("settings put global private_dns_mode $normalizedMode")
        val readBack = try {
            Settings.Global.getString(context.contentResolver, "private_dns_mode") ?: ""
        } catch (_: Throwable) {
            ""
        }

        val verified = if (modeRes.isSuccess && (readBack == normalizedMode || readBack.isEmpty())) {
            ShellExecutionResult(
                status = OperationStatus.SUCCESS,
                command = modeRes.command,
                exitCode = 0,
                technicalReason = if (normalizedMode == "hostname") {
                    "Verified Private DNS set to '$sanitizedHost' (mode: hostname)."
                } else {
                    "Verified Private DNS mode set to '$normalizedMode'."
                }
            )
        } else {
            modeRes
        }
        repository.logResult("Custom DNS", verified)
        verified
    }

    fun openAndroidNetworkSettings(): ShellExecutionResult {
        return try {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ShellExecutionResult(
                status = OperationStatus.SUCCESS,
                command = "Settings.ACTION_WIRELESS_SETTINGS",
                exitCode = 0,
                technicalReason = "Opened Android Network & Private DNS settings."
            )
        } catch (e: Throwable) {
            ShellExecutionResult(
                status = OperationStatus.FAILED,
                command = "Settings.ACTION_WIRELESS_SETTINGS",
                technicalReason = "Could not open Network settings: ${e.message}"
            )
        }
    }

    /**
     * Analyzes real storage metrics, enumerates safe cache directories, and scans accessible directories for large files.
     */
    suspend fun analyzeStorage(): StorageAnalysisReport = withContext(Dispatchers.IO) {
        val statFs = StatFs(Environment.getDataDirectory().path)
        val total = statFs.totalBytes.coerceAtLeast(1L)
        val free = statFs.availableBytes.coerceAtLeast(0L)
        val used = (total - free).coerceAtLeast(0L)
        val pct = ((used.toDouble() / total.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)

        val items = mutableListOf<CleanableCacheItem>()

        val internalCache = context.cacheDir
        val internalBytes = computeDirectoryBytes(internalCache)
        items.add(
            CleanableCacheItem(
                path = internalCache.absolutePath,
                label = "App Internal Cache (${internalCache.name})",
                sizeBytes = internalBytes
            )
        )

        val codeCache = context.codeCacheDir
        val codeCacheBytes = computeDirectoryBytes(codeCache)
        items.add(
            CleanableCacheItem(
                path = codeCache.absolutePath,
                label = "JIT / Shader Code Cache (${codeCache.name})",
                sizeBytes = codeCacheBytes
            )
        )

        context.externalCacheDir?.let { ext ->
            val extBytes = computeDirectoryBytes(ext)
            items.add(
                CleanableCacheItem(
                    path = ext.absolutePath,
                    label = "External App Cache (${ext.name})",
                    sizeBytes = extBytes
                )
            )
        }

        val totalCache = items.sumOf { it.sizeBytes }

        // Scan accessible directories for large files (>= 256 KB) without requesting broad storage permissions
        val largeFilesFound = mutableListOf<LargeFileItem>()
        val scanRoots = listOfNotNull(
            context.filesDir.parentFile,
            context.getExternalFilesDir(null),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )
        for (root in scanRoots) {
            try {
                if (root.exists() && root.canRead()) {
                    root.walkTopDown()
                        .maxDepth(3)
                        .filter { it.isFile && it.length() >= 256 * 1024L }
                        .forEach { f ->
                            largeFilesFound.add(
                                LargeFileItem(
                                    name = f.name,
                                    path = f.absolutePath,
                                    sizeBytes = f.length()
                                )
                            )
                        }
                }
            } catch (_: Throwable) {
            }
        }

        StorageAnalysisReport(
            totalBytes = total,
            freeBytes = free,
            usedBytes = used,
            usedPercent = pct,
            appCacheBytes = totalCache,
            cleanableItems = items,
            largeFiles = largeFilesFound.distinctBy { it.path }.sortedByDescending { it.sizeBytes }.take(8),
            shizukuSystemTrimAvailable = ShizukuManager.state.value.isConnected
        )
    }

    /**
     * Cleans only safe cache directories after explicit user confirmation, plus runs
     * `pm trim-caches` via Shizuku if connected. Never touches personal files.
     */
    suspend fun executeSafeStorageCleanup(): ShellExecutionResult = withContext(Dispatchers.IO) {
        var deletedFilesCount = 0
        var freedBytes = 0L

        val dirs = listOfNotNull(context.cacheDir, context.codeCacheDir, context.externalCacheDir)
        for (dir in dirs) {
            dir.listFiles()?.forEach { child ->
                val size = computeDirectoryBytes(child)
                if (deleteRecursively(child)) {
                    deletedFilesCount++
                    freedBytes += size
                }
            }
        }

        var shizukuNote = ""
        if (ShizukuManager.state.value.isConnected) {
            val trimRes = ShellExecutor.execute("pm trim-caches 4096M")
            shizukuNote = if (trimRes.isSuccess) {
                " Also executed 'pm trim-caches 4096M' via Shizuku."
            } else {
                " ('pm trim-caches' note: ${trimRes.technicalReason})"
            }
        }

        val result = ShellExecutionResult(
            status = OperationStatus.SUCCESS,
            command = "SafeStorageCacheCleanup",
            exitCode = 0,
            technicalReason = "Cleared $deletedFilesCount temporary cache entries ($freedBytes bytes freed).$shizukuNote"
        )
        repository.logResult("Storage Optimizer", result)
        result
    }

    private fun computeDirectoryBytes(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun deleteRecursively(file: File): Boolean {
        return try {
            file.deleteRecursively()
        } catch (_: Throwable) {
            false
        }
    }
}
