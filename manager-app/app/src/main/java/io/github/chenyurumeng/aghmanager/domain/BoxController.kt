package io.github.chenyurumeng.aghmanager.domain

import io.github.chenyurumeng.aghmanager.data.RootShell
import io.github.chenyurumeng.aghmanager.data.ShellResult
import io.github.chenyurumeng.aghmanager.data.StatusRepository

class BoxController(
    private val statusRepository: StatusRepository
) {
    suspend fun start(): ShellResult = run("start")

    suspend fun restart(): ShellResult = run("restart")

    suspend fun stop(): ShellResult = run("stop")

    private suspend fun run(action: String): ShellResult {
        val result = RootShell.exec(StatusRepository.BOX_SERVICE + " " + action, 150)
        statusRepository.refresh()
        return result
    }
}
