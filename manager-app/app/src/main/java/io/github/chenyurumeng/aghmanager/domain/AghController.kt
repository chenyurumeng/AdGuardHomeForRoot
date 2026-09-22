package io.github.chenyurumeng.aghmanager.domain

import io.github.chenyurumeng.aghmanager.data.RootShell
import io.github.chenyurumeng.aghmanager.data.ShellResult
import io.github.chenyurumeng.aghmanager.data.StatusRepository

class AghController(
    private val statusRepository: StatusRepository
) {
    suspend fun startAll(): ShellResult = run("start")
    suspend fun restartAll(): ShellResult = run("restart")
    suspend fun stopAll(): ShellResult = run("stop")

    suspend fun startDomestic(): ShellResult = run("start-domestic")
    suspend fun restartDomestic(): ShellResult = run("restart-domestic")
    suspend fun stopDomestic(): ShellResult = run("stop-domestic")

    suspend fun startForeign(): ShellResult = run("start-foreign")
    suspend fun restartForeign(): ShellResult = run("restart-foreign")
    suspend fun stopForeign(): ShellResult = run("stop-foreign")

    private suspend fun run(action: String): ShellResult {
        val tool = StatusRepository.AGH_TOOL
        val command = when (action) {
            "restart-domestic" ->
                tool + " restart-domestic || { " +
                    tool + " stop-domestic; " +
                    tool + " start-domestic; }"

            "restart-foreign" ->
                tool + " restart-foreign || { " +
                    tool + " stop-foreign; " +
                    tool + " start-foreign; }"

            else -> tool + " " + action
        }

        val result = RootShell.exec(command, 300)
        statusRepository.refresh()
        return result
    }
}
