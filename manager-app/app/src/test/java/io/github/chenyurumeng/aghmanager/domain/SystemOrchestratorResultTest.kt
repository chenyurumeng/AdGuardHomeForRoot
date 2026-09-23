package io.github.chenyurumeng.aghmanager.domain

import io.github.chenyurumeng.aghmanager.data.ShellResult
import io.github.chenyurumeng.aghmanager.data.StatusRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemOrchestratorResultTest {
    private val orchestrator = SystemOrchestrator(StatusRepository())

    @Test
    fun aghFailureRemainsFailureAfterSuccessfulBoxRecovery() {
        val result = orchestrator.combineRestartResults(
            ShellResult(7, "agh failed"),
            ShellResult(0, "")
        )
        assertFalse(result.ok)
        assertEquals(7, result.exitCode)
        assertTrue(result.stdout.contains("Box recovery start succeeded"))
    }

    @Test
    fun boxRecoveryFailureIsReportedWithAghFailure() {
        val result = orchestrator.combineRestartResults(
            ShellResult(9, "agh failed"),
            ShellResult(11, "box failed")
        )
        assertFalse(result.ok)
        assertEquals(9, result.exitCode)
        assertTrue(result.stdout.contains("Box recovery start failed"))
    }

    @Test
    fun boxStartFailureWinsWhenAghRestartSucceeded() {
        val result = orchestrator.combineRestartResults(
            ShellResult(0, ""),
            ShellResult(13, "box start failed")
        )
        assertFalse(result.ok)
        assertEquals(13, result.exitCode)
    }
}
