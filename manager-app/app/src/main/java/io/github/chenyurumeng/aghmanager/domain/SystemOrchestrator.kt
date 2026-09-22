package io.github.chenyurumeng.aghmanager.domain

import io.github.chenyurumeng.aghmanager.data.RootShell
import io.github.chenyurumeng.aghmanager.data.ShellResult
import io.github.chenyurumeng.aghmanager.data.StatusRepository

class SystemOrchestrator(
    private val statusRepository: StatusRepository
) {
    suspend fun restartNetwork(): ShellResult {
        val command =
            "[ -x " + StatusRepository.AGH_TOOL + " ] || exit 31; " +
                "[ -x " + StatusRepository.BOX_SERVICE + " ] || exit 32; " +
                StatusRepository.BOX_SERVICE + " stop || exit \$?; " +
                StatusRepository.AGH_TOOL + " restart || exit \$?; " +
                StatusRepository.BOX_SERVICE + " start"

        val result = RootShell.exec(command, 420)
        statusRepository.refresh()
        return result
    }
}
