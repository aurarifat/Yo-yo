package com.example.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

enum class ShizukuAvailability {
    CONNECTED,
    PERMISSION_GRANTED,
    PERMISSION_DENIED,
    RUNNING_NO_PERMISSION,
    INSTALLED_NOT_RUNNING,
    UNAVAILABLE
}

data class ShizukuConnectionState(
    val isInstalled: Boolean = false,
    val isBinderAlive: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val isPermissionDenied: Boolean = false,
    val shouldShowRationale: Boolean = false,
    val shizukuVersion: Int = -1,
    val shizukuUid: Int = -1,
    val availability: ShizukuAvailability = ShizukuAvailability.UNAVAILABLE,
    val statusHeadline: String = "NOT CONNECTED",
    val statusDetail: String = "Shizuku is not detected on this device."
) {
    val isConnected: Boolean
        get() = isBinderAlive && isPermissionGranted
}

/**
 * Manages the real Shizuku bridge lifecycle, binder listeners, permission requests,
 * and Wireless Debugging / ADB setup navigation.
 */
object ShizukuManager {

    const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
    const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

    private val _state = MutableStateFlow(ShizukuConnectionState())
    val state: StateFlow<ShizukuConnectionState> = _state.asStateFlow()

    private var appContext: Context? = null
    private var listenersRegistered = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        appContext?.let { recheck(it) }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        appContext?.let { recheck(it) }
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                appContext?.let { recheck(it, explicitDenial = !granted) }
            }
        }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (!listenersRegistered) {
            try {
                Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
                Shizuku.addBinderDeadListener(binderDeadListener)
                Shizuku.addRequestPermissionResultListener(permissionResultListener)
                listenersRegistered = true
            } catch (_: Throwable) {
            }
        }
        recheck(context)
    }

    fun cleanup() {
        if (listenersRegistered) {
            try {
                Shizuku.removeBinderReceivedListener(binderReceivedListener)
                Shizuku.removeBinderDeadListener(binderDeadListener)
                Shizuku.removeRequestPermissionResultListener(permissionResultListener)
            } catch (_: Throwable) {
            }
            listenersRegistered = false
        }
    }

    fun recheck(context: Context, explicitDenial: Boolean = false): ShizukuConnectionState {
        val installed = isShizukuInstalled(context)
        val binderAlive = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }

        if (!binderAlive) {
            val newState = ShizukuConnectionState(
                isInstalled = installed,
                isBinderAlive = false,
                isPermissionGranted = false,
                isPermissionDenied = false,
                availability = if (installed) {
                    ShizukuAvailability.INSTALLED_NOT_RUNNING
                } else {
                    ShizukuAvailability.UNAVAILABLE
                },
                statusHeadline = "NOT CONNECTED",
                statusDetail = if (installed) {
                    "Shizuku app is installed, but the Shizuku service is not running. Open Shizuku and start via Wireless Debugging or ADB."
                } else {
                    "Shizuku is not installed and binder is inactive. Install Shizuku or start ADB daemon."
                }
            )
            _state.value = newState
            return newState
        }

        val isPreV11 = try {
            Shizuku.isPreV11()
        } catch (_: Throwable) {
            false
        }

        val granted = if (isPreV11) {
            false
        } else {
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Throwable) {
                false
            }
        }

        val rationale = try {
            Shizuku.shouldShowRequestPermissionRationale()
        } catch (_: Throwable) {
            false
        }

        val version = try {
            Shizuku.getVersion()
        } catch (_: Throwable) {
            -1
        }

        val uid = try {
            Shizuku.getUid()
        } catch (_: Throwable) {
            -1
        }

        val denied = !granted && (explicitDenial || rationale)
        val availability = when {
            granted -> ShizukuAvailability.CONNECTED
            denied -> ShizukuAvailability.PERMISSION_DENIED
            else -> ShizukuAvailability.RUNNING_NO_PERMISSION
        }

        val privilegeLabel = when (uid) {
            0 -> "ROOT (uid 0)"
            2000 -> "ADB SHELL (uid 2000)"
            else -> "UID $uid"
        }

        val detail = when {
            isPreV11 -> "Shizuku version is pre-v11 and unsupported. Update the Shizuku app."
            granted -> "Shizuku v$version connected as $privilegeLabel. Elevated ADB commands are active."
            denied -> "Shizuku is running (v$version), but permission was denied. Open Shizuku or tap 'Request Permission'."
            else -> "Shizuku binder is running (v$version). Tap 'Request Permission' to authorize ADB bridge."
        }

        val newState = ShizukuConnectionState(
            isInstalled = installed || binderAlive,
            isBinderAlive = true,
            isPermissionGranted = granted,
            isPermissionDenied = denied,
            shouldShowRationale = rationale,
            shizukuVersion = version,
            shizukuUid = uid,
            availability = availability,
            statusHeadline = if (granted) "CONNECTED" else "NOT CONNECTED",
            statusDetail = detail
        )
        _state.value = newState
        return newState
    }

    fun requestPermission(): ShellExecutionResult {
        val current = _state.value
        if (!current.isBinderAlive) {
            return ShellExecutionResult(
                status = OperationStatus.PERMISSION_REQUIRED,
                command = "Shizuku.requestPermission",
                technicalReason = if (current.isInstalled) {
                    "Shizuku service is not running. Open Shizuku and start the service via Wireless Debugging or ADB first."
                } else {
                    "Shizuku is not installed on this device. Install Shizuku to use privileged ADB commands."
                }
            )
        }

        return try {
            if (Shizuku.isPreV11()) {
                return ShellExecutionResult(
                    status = OperationStatus.UNSUPPORTED,
                    command = "Shizuku.requestPermission",
                    technicalReason = "Shizuku pre-v11 is not supported."
                )
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                appContext?.let { recheck(it) }
                return ShellExecutionResult(
                    status = OperationStatus.SUCCESS,
                    command = "Shizuku.requestPermission",
                    exitCode = 0,
                    technicalReason = "Shizuku permission is already granted."
                )
            }
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            ShellExecutionResult(
                status = OperationStatus.PERMISSION_REQUIRED,
                command = "Shizuku.requestPermission",
                technicalReason = "Shizuku authorization dialog requested. Approve the prompt in Shizuku."
            )
        } catch (e: SecurityException) {
            ShellExecutionResult(
                status = OperationStatus.PERMISSION_REQUIRED,
                command = "Shizuku.requestPermission",
                technicalReason = "SecurityException requesting Shizuku permission: ${e.message}"
            )
        } catch (e: Throwable) {
            ShellExecutionResult(
                status = OperationStatus.FAILED,
                command = "Shizuku.requestPermission",
                technicalReason = "Could not request Shizuku permission: ${e.message}"
            )
        }
    }

    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    SHIZUKU_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    fun openShizukuApp(context: Context): ShellExecutionResult {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                ShellExecutionResult(
                    status = OperationStatus.SUCCESS,
                    command = "openShizukuApp",
                    exitCode = 0,
                    technicalReason = "Opened Shizuku application."
                )
            } else {
                ShellExecutionResult(
                    status = OperationStatus.UNSUPPORTED,
                    command = "openShizukuApp",
                    technicalReason = "Shizuku ($SHIZUKU_PACKAGE_NAME) is not installed on this device."
                )
            }
        } catch (e: Throwable) {
            ShellExecutionResult(
                status = OperationStatus.FAILED,
                command = "openShizukuApp",
                technicalReason = "Failed to launch Shizuku: ${e.message}"
            )
        }
    }

    fun openDeveloperSettings(context: Context): ShellExecutionResult {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ShellExecutionResult(
                status = OperationStatus.SUCCESS,
                command = "openDeveloperSettings",
                exitCode = 0,
                technicalReason = "Opened Android Developer Options for Wireless Debugging / ADB setup."
            )
        } catch (e: Throwable) {
            ShellExecutionResult(
                status = OperationStatus.FAILED,
                command = "openDeveloperSettings",
                technicalReason = "Could not open Developer Options: ${e.message}"
            )
        }
    }
}
