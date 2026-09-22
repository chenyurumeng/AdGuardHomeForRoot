package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.AghInstance
import io.github.chenyurumeng.aghmanager.model.AghStructuralConfig
import java.nio.charset.StandardCharsets
import java.util.Base64

class AghConfigRepository {
    companion object {
        private const val AGH_BIN = "/data/adb/agh/bin/AdGuardHome"
        private const val AGH_SETTINGS = "/data/adb/agh/settings.conf"
        private const val BOX_SETTINGS = "/data/adb/box/settings.ini"
        private const val BOX_SERVICE = "/data/adb/box/scripts/box.service"
        private const val AGH_TOOL = "/data/adb/agh/scripts/tool.sh"
        private const val STATE_DIR = "/data/adb/agh/run/manager"
        private val USERNAME_REGEX = Regex("[A-Za-z0-9._-]{1,64}")
    }

    suspend fun load(instance: AghInstance): Result<AghStructuralConfig> {
        val result = RootShell.exec("cat " + instance.configPath + " 2>/dev/null", 20)
        if (!result.ok || result.stdout.isBlank()) {
            return Result.failure(IllegalStateException("无法读取 " + instance.label + " 配置"))
        }
        return runCatching { parse(result.stdout, instance) }
    }

    fun validate(config: AghStructuralConfig): String? {
        if (config.webHost.isBlank()) return "Web 监听地址不能为空"
        if (config.webHost.any { it == '\n' || it == '\r' }) return "Web 监听地址格式无效"
        if (config.webPort !in 1..65535) return "Web 端口必须在 1–65535"
        if (config.dnsPort !in 1..65535) return "DNS 端口必须在 1–65535"
        if (config.webPort == config.dnsPort) return "Web 端口和 DNS 端口不能相同"
        if (config.dnsBindHosts.isEmpty()) return "至少保留一个 DNS 监听地址"
        if (config.dnsBindHosts.any { it.isBlank() || it.contains('\n') || it.contains('\r') }) {
            return "DNS 监听地址包含非法值"
        }
        if (!USERNAME_REGEX.matches(config.username)) {
            return "用户名仅允许 1–64 位字母、数字、点、下划线和短横线"
        }
        return null
    }

    suspend fun apply(
        instance: AghInstance,
        current: AghStructuralConfig,
        pending: AghStructuralConfig,
        passwordHash: String?
    ): Result<Unit> {
        validate(pending)?.let {
            return Result.failure(IllegalArgumentException(it))
        }

        val configRead = RootShell.exec("cat " + instance.configPath + " 2>/dev/null", 20)
        if (!configRead.ok || configRead.stdout.isBlank()) {
            return Result.failure(IllegalStateException("读取 AGH 配置失败"))
        }

        val newConfig = runCatching {
            patchConfig(configRead.stdout, pending, passwordHash)
        }.getOrElse { return Result.failure(it) }

        val portChanged = current.dnsPort != pending.dnsPort
        var newAghSettings: String? = null
        var newBoxSettings: String? = null

        if (portChanged) {
            val aghSettingsRead = RootShell.exec("cat " + AGH_SETTINGS + " 2>/dev/null", 15)
            val boxSettingsRead = RootShell.exec("cat " + BOX_SETTINGS + " 2>/dev/null", 15)
            if (!aghSettingsRead.ok || !boxSettingsRead.ok) {
                return Result.failure(
                    IllegalStateException("DNS 端口联动配置读取失败")
                )
            }
            val key = if (instance == AghInstance.DOMESTIC) {
                "domestic_dns_port"
            } else {
                "foreign_dns_port"
            }
            newAghSettings = patchShellSetting(aghSettingsRead.stdout, key, pending.dnsPort.toString())
            newBoxSettings = patchShellSetting(boxSettingsRead.stdout, key, pending.dnsPort.toString())
        }

        if (pending.dnsPort != current.dnsPort) {
            val conflict = RootShell.exec(
                "if ss -lntu 2>/dev/null | grep -qE '[:.]"
                    + pending.dnsPort + "([[:space:]]|$)'; then echo busy; fi",
                10
            )
            if (conflict.stdout.contains("busy")) {
                return Result.failure(
                    IllegalStateException("DNS 端口 " + pending.dnsPort + " 已被占用")
                )
            }
        }

        return applyTransaction(
            instance = instance,
            config = newConfig,
            aghSettings = newAghSettings,
            boxSettings = newBoxSettings,
            expectedDnsPort = pending.dnsPort,
            portChanged = portChanged
        )
    }

    private suspend fun applyTransaction(
        instance: AghInstance,
        config: String,
        aghSettings: String?,
        boxSettings: String?,
        expectedDnsPort: Int,
        portChanged: Boolean
    ): Result<Unit> {
        val config64 = encode(config)
        val agh64 = aghSettings?.let(::encode)
        val box64 = boxSettings?.let(::encode)
        val key = instance.key
        val dir = "/data/adb/agh/instances/" + key
        val startAction = "start-" + key
        val stopAction = "stop-" + key
        val pidFile = dir + "/agh.pid"
        val sh = '$'

        val command = buildString {
            append("mkdir -p ").append(STATE_DIR).append(" || exit 60; ")
            append("cfg=").append(instance.configPath).append("; ")
            append("cfg_bak=").append(STATE_DIR).append("/").append(key).append(".yaml.bak; ")
            append("cfg_tmp=").append(STATE_DIR).append("/").append(key).append(".yaml.tmp; ")
            append("cp -p \"").append(sh).append("cfg\" \"").append(sh).append("cfg_bak\" || exit 61; ")
            append("was_running=0; p=").append(sh).append("(cat ").append(pidFile)
                .append(" 2>/dev/null); [ -n \"").append(sh).append("p\" ] && kill -0 \"")
                .append(sh).append("p\" 2>/dev/null && was_running=1; ")
            append("box_running=0; bp=").append(sh).append("(cat /data/adb/box/run/box.pid 2>/dev/null); ")
            append("[ -n \"").append(sh).append("bp\" ] && kill -0 \"").append(sh)
                .append("bp\" 2>/dev/null && box_running=1; ")

            if (portChanged) {
                append("cp -p ").append(AGH_SETTINGS).append(" ").append(STATE_DIR)
                    .append("/agh-settings.bak || exit 62; ")
                append("cp -p ").append(BOX_SETTINGS).append(" ").append(STATE_DIR)
                    .append("/box-settings.bak || exit 63; ")
                append("[ \"").append(sh).append("box_running\" = 1 ] && ")
                    .append(BOX_SERVICE).append(" stop >/dev/null 2>&1 || true; ")
            }

            append("[ \"").append(sh).append("was_running\" = 1 ] && ")
                .append(AGH_TOOL).append(" ").append(stopAction)
                .append(" >/dev/null 2>&1 || true; ")
            append("cp -p \"").append(sh).append("cfg\" \"").append(sh).append("cfg_tmp\" || exit 64; ")
            append("printf '%s' '").append(config64).append("' | base64 -d > \"")
                .append(sh).append("cfg_tmp\" || exit 65; ")

            if (agh64 != null && box64 != null) {
                append("printf '%s' '").append(agh64).append("' | base64 -d > ")
                    .append(STATE_DIR).append("/agh-settings.tmp || exit 66; ")
                append("printf '%s' '").append(box64).append("' | base64 -d > ")
                    .append(STATE_DIR).append("/box-settings.tmp || exit 67; ")
            }

            append(AGH_BIN).append(" --check-config --config \"").append(sh)
                .append("cfg_tmp\" --work-dir ").append(dir)
                .append(" >/dev/null 2>&1 || { ")
                .append("[ \"").append(sh).append("was_running\" = 1 ] && ")
                .append(AGH_TOOL).append(" ").append(startAction).append(" >/dev/null 2>&1 || true; ")
                .append("[ \"").append(sh).append("box_running\" = 1 ] && ")
                .append(BOX_SERVICE).append(" start >/dev/null 2>&1 || true; exit 68; }; ")

            append("mv \"").append(sh).append("cfg_tmp\" \"").append(sh).append("cfg\" || exit 69; ")
            if (agh64 != null && box64 != null) {
                append("cp -p ").append(AGH_SETTINGS).append(" ").append(STATE_DIR)
                    .append("/agh-settings.mode; ")
                append("cat ").append(STATE_DIR).append("/agh-settings.tmp > ").append(AGH_SETTINGS)
                    .append(" || exit 70; ")
                append("cat ").append(STATE_DIR).append("/box-settings.tmp > ").append(BOX_SETTINGS)
                    .append(" || exit 71; ")
            }

            append("ok=1; ")
            append("if [ \"").append(sh).append("was_running\" = 1 ]; then ")
                .append(AGH_TOOL).append(" ").append(startAction)
                .append(" >/dev/null 2>&1 || ok=0; fi; ")
            if (portChanged) {
                append("if [ \"").append(sh).append("box_running\" = 1 ]; then ")
                    .append(BOX_SERVICE).append(" start >/dev/null 2>&1 || ok=0; fi; ")
            }
            append("if [ \"").append(sh).append("was_running\" = 1 ]; then ")
                .append("sleep 1; ss -lntu 2>/dev/null | grep -qE '[:.]")
                .append(expectedDnsPort).append("([[:space:]]|$)' || ok=0; fi; ")

            append("if [ \"").append(sh).append("ok\" = 1 ]; then ")
                .append("rm -f \"").append(sh).append("cfg_bak\" ")
                .append(STATE_DIR).append("/*.tmp; exit 0; fi; ")

            append("[ \"").append(sh).append("was_running\" = 1 ] && ")
                .append(AGH_TOOL).append(" ").append(stopAction)
                .append(" >/dev/null 2>&1 || true; ")
            append("cp -p \"").append(sh).append("cfg_bak\" \"").append(sh).append("cfg\"; ")
            if (portChanged) {
                append("cp -p ").append(STATE_DIR).append("/agh-settings.bak ").append(AGH_SETTINGS).append("; ")
                append("cp -p ").append(STATE_DIR).append("/box-settings.bak ").append(BOX_SETTINGS).append("; ")
            }
            append("[ \"").append(sh).append("was_running\" = 1 ] && ")
                .append(AGH_TOOL).append(" ").append(startAction).append(" >/dev/null 2>&1 || true; ")
            if (portChanged) {
                append("[ \"").append(sh).append("box_running\" = 1 ] && ")
                    .append(BOX_SERVICE).append(" start >/dev/null 2>&1 || true; ")
            }
            append("exit 72")
        }

        val result = RootShell.exec(command, 180)
        return if (result.ok) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(
                    when (result.exitCode) {
                        68 -> "AGH 配置校验失败，原配置未修改"
                        72 -> "新配置启动验证失败，已尝试恢复原配置"
                        else -> "AGH 结构配置应用失败（exit=" + result.exitCode + "）"
                    }
                )
            )
        }
    }

    private fun parse(content: String, instance: AghInstance): AghStructuralConfig {
        val lines = content.lines()
        var webAddress = "127.0.0.1:" + instance.defaultWebPort
        var dnsPort = instance.defaultDnsPort
        val bindHosts = mutableListOf<String>()
        var username = "root"
        var hasUser = false

        var section = ""
        var inBindHosts = false
        for (raw in lines) {
            val indent = raw.takeWhile { it == ' ' }.length
            val line = raw.trim()
            if (indent == 0 && line.endsWith(":")) {
                section = line.removeSuffix(":")
                inBindHosts = false
                continue
            }

            when (section) {
                "http" -> if (indent == 2 && line.startsWith("address:")) {
                    webAddress = unquote(line.substringAfter(':').trim())
                }
                "dns" -> {
                    if (indent == 2 && line == "bind_hosts:") {
                        inBindHosts = true
                        continue
                    }
                    if (indent == 2 && line.startsWith("port:")) {
                        dnsPort = line.substringAfter(':').trim().toIntOrNull()
                            ?: instance.defaultDnsPort
                        inBindHosts = false
                    } else if (inBindHosts && indent == 4 && line.startsWith("- ")) {
                        bindHosts += unquote(line.removePrefix("- ").trim())
                    } else if (inBindHosts && indent <= 2 && line.isNotBlank()) {
                        inBindHosts = false
                    }
                }
                "users" -> if (!hasUser && indent == 2 && line.startsWith("- name:")) {
                    username = unquote(line.substringAfter(':').trim())
                    hasUser = true
                }
            }
        }

        val (webHost, webPort) = splitAddress(webAddress, instance.defaultWebPort)
        return AghStructuralConfig(
            webHost = webHost,
            webPort = webPort,
            dnsBindHosts = bindHosts.ifEmpty { listOf("127.0.0.1", "::1") },
            dnsPort = dnsPort,
            username = username,
            hasUser = hasUser
        )
    }

    private fun patchConfig(
        content: String,
        config: AghStructuralConfig,
        passwordHash: String?
    ): String {
        val lines = content.lines().toMutableList()
        replaceSectionScalar(lines, "http", "address", yamlQuote(joinAddress(config.webHost, config.webPort)))
        replaceSectionScalar(lines, "dns", "port", config.dnsPort.toString(), quote = false)
        replaceSectionList(lines, "dns", "bind_hosts", config.dnsBindHosts)

        val users = sectionRange(lines, "users")
            ?: throw IllegalStateException("AGH 配置缺少 users 段")
        var nameIndex = -1
        var passwordIndex = -1
        for (i in users.first until users.second) {
            val trimmed = lines[i].trim()
            if (nameIndex < 0 && lines[i].startsWith("  - name:")) nameIndex = i
            if (nameIndex >= 0 && lines[i].startsWith("    password:")) {
                passwordIndex = i
                break
            }
        }
        if (nameIndex < 0 || passwordIndex < 0) {
            throw IllegalStateException("AGH users 配置结构无法识别")
        }
        lines[nameIndex] = "  - name: " + yamlQuote(config.username)
        if (passwordHash != null) {
            lines[passwordIndex] = "    password: " + passwordHash
        }
        return lines.joinToString("\n")
    }

    private fun replaceSectionScalar(
        lines: MutableList<String>,
        section: String,
        key: String,
        value: String,
        quote: Boolean = true
    ) {
        val range = sectionRange(lines, section)
            ?: throw IllegalStateException("AGH 配置缺少 $section 段")
        val prefix = "  $key:"
        val index = (range.first until range.second)
            .firstOrNull { lines[it].startsWith(prefix) }
            ?: throw IllegalStateException("AGH 配置缺少 $section.$key")
        lines[index] = prefix + " " + if (quote) value else value
    }

    private fun replaceSectionList(
        lines: MutableList<String>,
        section: String,
        key: String,
        values: List<String>
    ) {
        val range = sectionRange(lines, section)
            ?: throw IllegalStateException("AGH 配置缺少 $section 段")
        val header = "  $key:"
        val index = (range.first until range.second)
            .firstOrNull { lines[it] == header }
            ?: throw IllegalStateException("AGH 配置缺少 $section.$key")

        var end = index + 1
        while (end < lines.size && lines[end].startsWith("    - ")) end++
        for (i in end - 1 downTo index + 1) lines.removeAt(i)
        lines.addAll(index + 1, values.map { "    - " + yamlQuote(it) })
    }

    private fun sectionRange(lines: List<String>, section: String): Pair<Int, Int>? {
        val start = lines.indexOfFirst { it == "$section:" }
        if (start < 0) return null
        var end = lines.size
        for (i in start + 1 until lines.size) {
            if (lines[i].isNotBlank() && !lines[i].startsWith(" ") && !lines[i].startsWith("#")) {
                end = i
                break
            }
        }
        return (start + 1) to end
    }

    private fun patchShellSetting(content: String, key: String, value: String): String {
        val regex = Regex("(?m)^" + Regex.escape(key) + "=.*$")
        if (!regex.containsMatchIn(content)) {
            throw IllegalStateException("配置缺少 $key")
        }
        return content.replace(regex, key + "=\"" + value + "\"")
    }

    private fun splitAddress(value: String, defaultPort: Int): Pair<String, Int> {
        val v = value.trim()
        if (v.startsWith("[")) {
            val close = v.indexOf(']')
            if (close > 0) {
                val host = v.substring(1, close)
                val port = v.substring(close + 1).removePrefix(":").toIntOrNull() ?: defaultPort
                return host to port
            }
        }
        val colon = v.lastIndexOf(':')
        if (colon > 0 && v.indexOf(':') == colon) {
            return v.substring(0, colon) to (v.substring(colon + 1).toIntOrNull() ?: defaultPort)
        }
        return v to defaultPort
    }

    private fun joinAddress(host: String, port: Int): String {
        val normalized = host.trim()
        return if (normalized.contains(':') && !normalized.startsWith("[")) {
            "[$normalized]:$port"
        } else {
            "$normalized:$port"
        }
    }

    private fun yamlQuote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun unquote(value: String): String {
        if (value.length >= 2) {
            val first = value.first()
            val last = value.last()
            if ((first == '\"' && last == '\"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length - 1)
            }
        }
        return value
    }

    private fun encode(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
}
