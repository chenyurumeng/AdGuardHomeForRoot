package io.github.chenyurumeng.aghmanager.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val timedOut: Boolean = false
) {
    val ok: Boolean
        get() = exitCode == 0 && !timedOut
}

object RootShell {
    suspend fun exec(command: String, timeoutSeconds: Long = 30): ShellResult =
        withContext(Dispatchers.IO) {
            var process: Process? = null
            try {
                val runningProcess = ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start()
                process = runningProcess

                coroutineScope {
                    val reader = async(Dispatchers.IO) {
                        runningProcess.inputStream
                            .bufferedReader(StandardCharsets.UTF_8)
                            .use { it.readText() }
                    }

                    val finished = runningProcess.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                    if (!finished) {
                        runningProcess.destroyForcibly()
                        val partial = withTimeoutOrNull(2_000L) { reader.await() }.orEmpty()
                        ShellResult(
                            exitCode = 124,
                            stdout = if (partial.isBlank()) {
                                "Command timed out after " + timeoutSeconds + "s"
                            } else {
                                partial.trim()
                            },
                            timedOut = true
                        )
                    } else {
                        ShellResult(
                            exitCode = runningProcess.exitValue(),
                            stdout = reader.await().trim()
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                process?.destroyForcibly()
                throw cancelled
            } catch (e: Exception) {
                ShellResult(
                    exitCode = 127,
                    stdout = e.javaClass.simpleName + ": " + (e.message ?: "")
                )
            } finally {
                process?.destroy()
            }
        }
}
