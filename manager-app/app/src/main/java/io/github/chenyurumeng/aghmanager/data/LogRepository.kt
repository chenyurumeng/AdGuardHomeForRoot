package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.LogSource

class LogRepository {
    suspend fun load(source: LogSource): ShellResult {
        val sh = '$'
        val command = when (source) {
            LogSource.BOX_CORE ->
                "bin=" + sh + "(awk -F'\"' '/^bin_name=/{print " + sh + "2; exit}' " +
                    StatusRepository.BOX_SETTINGS + " 2>/dev/null); " +
                    "[ -n \"" + sh + "bin\" ] || bin=mihomo; " +
                    "tail -n 260 /data/adb/box/run/" + sh + "bin.log 2>/dev/null"

            LogSource.BOX_SERVICE ->
                "tail -n 260 /data/adb/box/run/runs.log 2>/dev/null"

            LogSource.BOX_TOOL ->
                "tail -n 260 /data/adb/box/run/tool.log 2>/dev/null"

            LogSource.DOMESTIC ->
                "tail -n 260 /data/adb/agh/instances/domestic/agh.log 2>/dev/null"

            LogSource.FOREIGN ->
                "tail -n 260 /data/adb/agh/instances/foreign/agh.log 2>/dev/null"

            LogSource.AGH_MODULE ->
                "tail -n 260 /data/adb/agh/history.log 2>/dev/null"
        }

        return RootShell.exec(command, 30)
    }
}
