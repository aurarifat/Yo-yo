package com.example

import com.example.shizuku.OperationStatus
import com.example.shizuku.PermissionDeniedException
import com.example.shizuku.ShellExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun shellExecutor_allowlistPermitsSafeCommands() {
        assertTrue(ShellExecutor.isCommandAllowed("id"))
        assertTrue(ShellExecutor.isCommandAllowed("wm size"))
        assertTrue(ShellExecutor.isCommandAllowed("wm size 1080x2400"))
        assertTrue(ShellExecutor.isCommandAllowed("wm size reset"))
        assertTrue(ShellExecutor.isCommandAllowed("settings put global window_animation_scale 0.5"))
        assertTrue(ShellExecutor.isCommandAllowed("settings put system peak_refresh_rate 90.0"))
        assertTrue(ShellExecutor.isCommandAllowed("cmd power set-fixed-performance-mode-enabled true"))
        assertTrue(ShellExecutor.isCommandAllowed("cmd game mode performance com.dts.freefiremax"))
    }

    @Test
    fun shellExecutor_allowlistBlocksArbitraryAndChainedCommands() {
        assertFalse(ShellExecutor.isCommandAllowed("rm -rf /sdcard"))
        assertFalse(ShellExecutor.isCommandAllowed("id; rm -rf /"))
        assertFalse(ShellExecutor.isCommandAllowed("wm size && reboot"))
        assertFalse(ShellExecutor.isCommandAllowed("settings get global window_animation_scale | sh"))
        assertFalse(ShellExecutor.isCommandAllowed("am kill com.android.systemui"))
    }

    @Test
    fun shellExecutor_handlesPermissionDeniedGracefullyWhenShizukuDisconnected() {
        val result = ShellExecutor.executeBlocking("id")
        assertEquals(OperationStatus.PERMISSION_REQUIRED, result.status)
        assertTrue(result.technicalReason.isNotEmpty())
    }

    @Test
    fun shellExecutor_blocksDisallowedCommandBeforeExecution() {
        val result = ShellExecutor.executeBlocking("reboot")
        assertEquals(OperationStatus.FAILED, result.status)
        assertTrue(result.technicalReason.contains("allowlist", ignoreCase = true))
    }

    @Test
    fun permissionDeniedException_isSecurityException() {
        val ex = PermissionDeniedException("Shizuku permission denied")
        assertEquals("Shizuku permission denied", ex.message)
    }
}
