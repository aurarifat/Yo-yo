package com.example.shizuku

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Exception thrown when Shizuku or Android privileged shell permission is denied.
 */
class PermissionDeniedException(
    message: String,
    cause: Throwable? = null
) : SecurityException(message, cause)

/**
 * Standardized status for every command and Android API operation.
 */
enum class OperationStatus(val badge: String, val label: String) {
    SUCCESS("✓ Success", "Success"),
    UNSUPPORTED("⚠ Unsupported", "Not supported on this device"),
    PERMISSION_REQUIRED("🔒 Permission required", "Permission required"),
    FAILED("✕ Failed", "Failed")
}

/**
 * Result returned by [ShellExecutor] and system operations.
 */
data class ShellExecutionResult(
    val status: OperationStatus,
    val command: String,
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = -1,
    val technicalReason: String = "",
    val timestampMs: Long = System.currentTimeMillis()
) {
    val isSuccess: Boolean
        get() = status == OperationStatus.SUCCESS && exitCode == 0
}

/**
 * Secure Singleton that executes allowlisted shell commands via [Shizuku.newProcess].
 *
 * Security guarantees:
 * 1. Never executes arbitrary or chained shell input (blocks `;`, `&&`, `||`, `|`, `` ` ``, `$()`, `>`, `<`).
 * 2. Enforces a strict allowlist of safe, verified Android diagnostic and performance commands.
 * 3. Wraps all Shizuku binder and process invocations in try-catch blocks handling [PermissionDeniedException]
 *    and [SecurityException].
 */
object ShellExecutor {

    private val FORBIDDEN_METACHARACTERS = listOf(
        ";", "&&", "||", "|", "`", "$(", ">", "<", "\n", "\r", "&"
    )

    /**
     * Explicit allowlist of permitted command prefixes and exact patterns.
     */
    val ALLOWED_COMMAND_PREFIXES: List<String> = listOf(
        "settings get system ",
        "settings get global ",
        "settings get secure ",
        "settings put system peak_refresh_rate ",
        "settings put system min_refresh_rate ",
        "settings put system user_refresh_rate ",
        "settings delete system peak_refresh_rate",
        "settings delete system min_refresh_rate",
        "settings put global window_animation_scale ",
        "settings put global transition_animation_scale ",
        "settings put global animator_duration_scale ",
        "settings put global disable_window_blurs ",
        "settings put global development_settings_enabled ",
        "settings put global private_dns_mode ",
        "settings put global private_dns_specifier ",
        "cmd power set-fixed-performance-mode-enabled ",
        "cmd power set-adaptive-power-saver-enabled ",
        "cmd power set-mode ",
        "cmd game mode ",
        "cmd game set ",
        "cmd game reset ",
        "cmd game list ",
        "cmd thermalservice ",
        "wm size",
        "wm density",
        "dumpsys display",
        "dumpsys SurfaceFlinger",
        "dumpsys gfxinfo ",
        "dumpsys thermalservice",
        "dumpsys battery",
        "dumpsys meminfo",
        "dumpsys cpuinfo",
        "dumpsys power",
        "dumpsys game_manager",
        "pm trim-caches ",
        "am kill ",
        "am kill-all",
        "ping -c ",
        "getprop ",
        "cat /sys/devices/system/cpu/",
        "cat /sys/class/thermal/",
        "cat /proc/meminfo",
        "cat /proc/stat",
        "cat /proc/version",
        "cat /proc/cpuinfo",
        "id"
    )

    /**
     * Critical Android system packages that must NEVER be force-stopped or killed.
     */
    private val PROTECTED_SYSTEM_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.shell",
        "com.google.android.gms",
        "com.google.android.gsf",
        "moe.shizuku.privileged.api",
        "com.example"
    )

    /**
     * Validates whether a command string is safe and explicitly permitted by the allowlist.
     */
    fun isCommandAllowed(rawCommand: String): Boolean {
        val cmd = rawCommand.trim()
        if (cmd.isEmpty()) return false

        // Reject any shell chaining, redirection, or subshell injection
        if (FORBIDDEN_METACHARACTERS.any { cmd.contains(it) }) {
            return false
        }

        // Prevent killing protected packages
        if (cmd.startsWith("am kill ")) {
            val targetPkg = cmd.removePrefix("am kill ").trim()
            if (targetPkg in PROTECTED_SYSTEM_PACKAGES || targetPkg.startsWith("com.android.system")) {
                return false
            }
        }

        // Verify prefix against the allowlist
        return ALLOWED_COMMAND_PREFIXES.any { allowedPrefix ->
            if (allowedPrefix.endsWith(" ")) {
                cmd.startsWith(allowedPrefix) && cmd.length > allowedPrefix.length
            } else {
                cmd == allowedPrefix || cmd.startsWith("$allowedPrefix ")
            }
        }
    }

    /**
     * Verifies Shizuku binder availability and permission status, throwing [PermissionDeniedException]
     * if Shizuku permission is not granted.
     */
    @Throws(PermissionDeniedException::class)
    fun ensureShizukuPermission() {
        val binderAlive = try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
        if (!binderAlive) {
            throw PermissionDeniedException(
                "Shizuku service is not running or binder is disconnected. Start Shizuku via Wireless Debugging or ADB first."
            )
        }

        val permissionState = try {
            Shizuku.checkSelfPermission()
        } catch (e: SecurityException) {
            throw PermissionDeniedException("SecurityException while checking Shizuku permission: ${e.message}", e)
        } catch (e: Throwable) {
            throw PermissionDeniedException("Unable to verify Shizuku permission: ${e.message}", e)
        }

        if (permissionState != PackageManager.PERMISSION_GRANTED) {
            throw PermissionDeniedException(
                "Shizuku permission is denied. Tap 'Request Permission' and approve access in the Shizuku prompt."
            )
        }
    }

    /**
     * Executes an allowlisted shell command using [Shizuku.newProcess] on a background thread.
     * All calls are wrapped in try-catch blocks handling [PermissionDeniedException].
     */
    suspend fun execute(
        command: String,
        timeoutSeconds: Long = 8L
    ): ShellExecutionResult = withContext(Dispatchers.IO) {
        executeBlocking(command, timeoutSeconds)
    }

    /**
     * Synchronous execution of an allowlisted command via [Shizuku.newProcess] wrapped in try-catch blocks.
     */
    fun executeBlocking(
        command: String,
        timeoutSeconds: Long = 8L
    ): ShellExecutionResult {
        val trimmed = command.trim()

        if (!isCommandAllowed(trimmed)) {
            return ShellExecutionResult(
                status = OperationStatus.FAILED,
                command = trimmed,
                exitCode = -1,
                technicalReason = "Blocked by security allowlist: Command '$trimmed' is not permitted or contains forbidden shell metacharacters."
            )
        }

        var remoteProcess: ShizukuRemoteProcess? = null
        return try {
            ensureShizukuPermission()

            // Execute command via Shizuku.newProcess()
            remoteProcess = invokeShizukuNewProcess(
                cmd = arrayOf("sh", "-c", trimmed),
                env = null,
                dir = null
            )

            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val stdoutThread = Thread {
                try {
                    BufferedReader(InputStreamReader(remoteProcess.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (stdoutBuilder.isNotEmpty()) stdoutBuilder.append('\n')
                            stdoutBuilder.append(line)
                        }
                    }
                } catch (_: Throwable) {
                }
            }

            val stderrThread = Thread {
                try {
                    BufferedReader(InputStreamReader(remoteProcess.errorStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (stderrBuilder.isNotEmpty()) stderrBuilder.append('\n')
                            stderrBuilder.append(line)
                        }
                    }
                } catch (_: Throwable) {
                }
            }

            stdoutThread.start()
            stderrThread.start()

            var exited = false
            var processExitCode = -1
            val waitThread = Thread {
                try {
                    processExitCode = remoteProcess.waitFor()
                    exited = true
                } catch (_: Throwable) {
                }
            }
            waitThread.start()
            waitThread.join(timeoutSeconds * 1000L)

            if (!exited) {
                remoteProcess.destroy()
                return ShellExecutionResult(
                    status = OperationStatus.FAILED,
                    command = trimmed,
                    stdout = stdoutBuilder.toString(),
                    stderr = stderrBuilder.toString(),
                    exitCode = -1,
                    technicalReason = "Command timed out after ${timeoutSeconds}s."
                )
            }

            stdoutThread.join(1000)
            stderrThread.join(1000)

            val exitCode = processExitCode
            val stdout = stdoutBuilder.toString().trim()
            val stderr = stderrBuilder.toString().trim()

            when {
                exitCode == 0 && !stderr.contains("SecurityException", ignoreCase = true) -> {
                    ShellExecutionResult(
                        status = OperationStatus.SUCCESS,
                        command = trimmed,
                        stdout = stdout,
                        stderr = stderr,
                        exitCode = 0,
                        technicalReason = if (stdout.isNotEmpty()) "Command succeeded: $stdout" else "Executed via Shizuku privileged shell (exit code 0)."
                    )
                }
                stderr.contains("Permission denial", ignoreCase = true) ||
                    stderr.contains("SecurityException", ignoreCase = true) -> {
                    ShellExecutionResult(
                        status = OperationStatus.PERMISSION_REQUIRED,
                        command = trimmed,
                        stdout = stdout,
                        stderr = stderr,
                        exitCode = exitCode,
                        technicalReason = stderr.ifEmpty { "Android system rejected command due to insufficient shell privileges." }
                    )
                }
                stderr.contains("not supported", ignoreCase = true) ||
                    stderr.contains("unknown command", ignoreCase = true) ||
                    stderr.contains("Can't find service", ignoreCase = true) -> {
                    ShellExecutionResult(
                        status = OperationStatus.UNSUPPORTED,
                        command = trimmed,
                        stdout = stdout,
                        stderr = stderr,
                        exitCode = exitCode,
                        technicalReason = stderr.ifEmpty { "Command or service is not supported on this Android version/device." }
                    )
                }
                else -> {
                    ShellExecutionResult(
                        status = OperationStatus.FAILED,
                        command = trimmed,
                        stdout = stdout,
                        stderr = stderr,
                        exitCode = exitCode,
                        technicalReason = stderr.ifEmpty { stdout.ifEmpty { "Command exited with non-zero code $exitCode." } }
                    )
                }
            }
        } catch (e: PermissionDeniedException) {
            ShellExecutionResult(
                status = OperationStatus.PERMISSION_REQUIRED,
                command = trimmed,
                exitCode = -1,
                technicalReason = e.message ?: "Shizuku permission denied."
            )
        } catch (e: SecurityException) {
            val wrapped = PermissionDeniedException(
                "Permission denied while calling Shizuku.newProcess(): ${e.message}",
                e
            )
            ShellExecutionResult(
                status = OperationStatus.PERMISSION_REQUIRED,
                command = trimmed,
                exitCode = -1,
                technicalReason = wrapped.message ?: "Permission denied."
            )
        } catch (e: IllegalStateException) {
            ShellExecutionResult(
                status = OperationStatus.PERMISSION_REQUIRED,
                command = trimmed,
                exitCode = -1,
                technicalReason = "Shizuku binder not ready: ${e.message ?: "Connect Shizuku first."}"
            )
        } catch (e: Throwable) {
            ShellExecutionResult(
                status = OperationStatus.FAILED,
                command = trimmed,
                exitCode = -1,
                technicalReason = "Shell execution error (${e.javaClass.simpleName}): ${e.message ?: "Unknown error"}"
            )
        } finally {
            try {
                remoteProcess?.destroy()
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Invokes `Shizuku.newProcess(cmd, env, dir)` directly and handles visibility across Shizuku API versions.
     */
    @Throws(PermissionDeniedException::class)
    private fun invokeShizukuNewProcess(
        cmd: Array<String>,
        env: Array<String>?,
        dir: String?
    ): ShizukuRemoteProcess {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            method.invoke(null, cmd, env, dir) as ShizukuRemoteProcess
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val target = e.targetException ?: e
            if (target is SecurityException) {
                throw PermissionDeniedException(
                    "Permission denied by Shizuku.newProcess(): ${target.message}",
                    target
                )
            }
            throw target
        } catch (e: SecurityException) {
            throw PermissionDeniedException(
                "SecurityException accessing Shizuku.newProcess(): ${e.message}",
                e
            )
        }
    }
}
