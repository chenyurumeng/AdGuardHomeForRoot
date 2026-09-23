package io.github.chenyurumeng.aghmanager.domain

import io.github.chenyurumeng.aghmanager.data.RootShell
import io.github.chenyurumeng.aghmanager.data.ShellResult
import io.github.chenyurumeng.aghmanager.data.StatusRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class SystemOrchestrator(
    private val statusRepository: StatusRepository
) {
    suspend fun restartNetwork(): ShellResult {
        val preflight = RootShell.exec(
            "[ -x " + StatusRepository.AGH_TOOL + " ] || exit 31; " +
                "[ -x " + StatusRepository.BOX_SERVICE + " ] || exit 32",
            15
        )
        if (!preflight.ok) {
            statusRepository.refresh()
            return preflight
        }

        val stopResult = RootShell.exec(StatusRepository.BOX_SERVICE + " stop", 150)
        if (!stopResult.ok) {
            statusRepository.refresh()
            return stopResult
        }

        var aghResult = ShellResult(125, "AGH restart did not complete")
        var boxStartResult = ShellResult(125, "Box recovery start did not complete")
        try {
            aghResult = RootShell.exec(StatusRepository.AGH_TOOL + " restart", 300)
        } finally {
            withContext(NonCancellable) {
                boxStartResult = RootShell.exec(StatusRepository.BOX_SERVICE + " start", 150)
                statusRepository.refresh()
            }
        }

        return combineRestartResults(aghResult, boxStartResult)
    }

    internal fun combineRestartResults(
        aghResult: ShellResult,
        boxStartResult: ShellResult
    ): ShellResult {
        if (!aghResult.ok) {
            val recovery = if (boxStartResult.ok) {
                "Box recovery start succeeded"
            } else {
                "Box recovery start failed (exit=" + boxStartResult.exitCode + ")"
            }
            return aghResult.copy(
                stdout = listOf(aghResult.stdout, recovery)
                    .filter(String::isNotBlank)
                    .joinToString("\n")
            )
        }
        return boxStartResult
    }
}
