package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.HealthCheckItem
import io.github.chenyurumeng.aghmanager.model.HealthCheckStatus
import io.github.chenyurumeng.aghmanager.model.HealthRepairAction
import io.github.chenyurumeng.aghmanager.model.HealthSnapshot
import java.time.Instant

class HealthCenterRepository {
    companion object {
        private const val AGH_SETTINGS = "/data/adb/agh/settings.conf"
        private const val DOMESTIC_CONFIG = "/data/adb/agh/instances/domestic/AdGuardHome.yaml"
        private const val FOREIGN_CONFIG = "/data/adb/agh/instances/foreign/AdGuardHome.yaml"
        private const val DOMESTIC_PID = "/data/adb/agh/instances/domestic/agh.pid"
        private const val FOREIGN_PID = "/data/adb/agh/instances/foreign/agh.pid"
        private const val BOX_PID = "/data/adb/box/run/box.pid"
        private const val MIHOMO_BIN = "/data/adb/box/bin/mihomo"
        private const val MIHOMO_CONFIG = "/data/adb/box/mihomo/config.yaml"
    }

    suspend fun load(): Result<HealthSnapshot> {
        val result = RootShell.exec(buildCommand(), 60)
        if (!result.ok) {
            return Result.failure(
                IllegalStateException(
                    if (result.timedOut) "健康检查 Root 命令超时"
                    else "Root 不可用或健康检查执行失败"
                )
            )
        }
        return runCatching { parse(result.stdout) }
    }

    suspend fun repair(action: HealthRepairAction): Result<Unit> {
        val command = when (action) {
            HealthRepairAction.REAPPLY_APP_RULES ->
                "[ ! -f " + StatusRepository.BOX_STOP_GUARD + " ] || exit 40; " +
                    StatusRepository.BOX_SERVICE + " apply-apps"
            HealthRepairAction.RESTART_DOMESTIC ->
                StatusRepository.AGH_TOOL + " restart-domestic || { " +
                    StatusRepository.AGH_TOOL + " stop-domestic; " +
                    StatusRepository.AGH_TOOL + " start-domestic; }"
            HealthRepairAction.RESTART_FOREIGN ->
                StatusRepository.AGH_TOOL + " restart-foreign || { " +
                    StatusRepository.AGH_TOOL + " stop-foreign; " +
                    StatusRepository.AGH_TOOL + " start-foreign; }"
        }
        val result = RootShell.exec(command, 180)
        return if (result.ok) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(
                    when (result.exitCode) {
                        40 -> "Box 处于 user_stopped，拒绝重新应用规则"
                        else -> action.label + "失败（exit=" + result.exitCode + "）"
                    }
                )
            )
        }
    }

    fun formatReport(snapshot: HealthSnapshot): String = buildString {
        appendLine("Box & AGH Manager v0.5.0-rc18")
        appendLine("Health Center report")
        appendLine("Generated: " + snapshot.generatedAt)
        appendLine("Secrets: redacted by structured diagnostics")
        appendLine()
        snapshot.items.forEach { item ->
            append("[")
            append(item.status.label)
            append("] ")
            appendLine(item.title)
            appendLine("  " + item.detail)
        }
    }.trimEnd()

    private fun buildCommand(): String {
        val sh = '$'
        return listOf(
            "echo '===ROOT==='",
            "echo ROOT_UID=" + sh + "(id -u 2>/dev/null)",
            "echo '===MODULES==='",
            "[ -x " + StatusRepository.BOX_SERVICE + " ] && echo BOX_MODULE=true || echo BOX_MODULE=false",
            "[ -x " + StatusRepository.AGH_TOOL + " ] && echo AGH_MODULE=true || echo AGH_MODULE=false",
            "[ -f " + DOMESTIC_CONFIG + " ] && echo DOMESTIC_CONFIG=true || echo DOMESTIC_CONFIG=false",
            "[ -f " + FOREIGN_CONFIG + " ] && echo FOREIGN_CONFIG=true || echo FOREIGN_CONFIG=false",
            "[ -x " + MIHOMO_BIN + " ] && echo MIHOMO_BINARY=true || echo MIHOMO_BINARY=false",
            "[ -f " + MIHOMO_CONFIG + " ] && echo MIHOMO_CONFIG=true || echo MIHOMO_CONFIG=false",
            "echo '===BOX_SETTINGS==='",
            "grep -E '^(bin_name|proxy_mode|network_mode|dns_hijack_mode|domestic_dns_port|foreign_dns_port|foreign_dns_fallback_port|foreign_dns_fail_port|ipv6|mihomo_dns_forward)=' " +
                StatusRepository.BOX_SETTINGS + " 2>/dev/null | sed 's/^/BOX_SETTING:/' || true",
            "echo '===AGH_SETTINGS==='",
            "grep -E '^(domestic_dns_port|foreign_dns_port)=' " + AGH_SETTINGS +
                " 2>/dev/null | sed 's/^/AGH_SETTING:/' || true",
            "echo '===PROCESSES==='",
            "bp=" + sh + "(cat " + BOX_PID + " 2>/dev/null); " +
                "if [ -n \"" + sh + "bp\" ] && kill -0 \"" + sh + "bp\" 2>/dev/null; " +
                "then echo BOX_RUNNING=true; echo BOX_PID=" + sh + "bp; else echo BOX_RUNNING=false; fi",
            "[ -f " + StatusRepository.BOX_STOP_GUARD + " ] && echo BOX_USER_STOPPED=true || echo BOX_USER_STOPPED=false",
            "dp=" + sh + "(cat " + DOMESTIC_PID + " 2>/dev/null); " +
                "if [ -n \"" + sh + "dp\" ] && kill -0 \"" + sh + "dp\" 2>/dev/null; " +
                "then echo DOMESTIC_RUNNING=true; echo DOMESTIC_PID=" + sh + "dp; else echo DOMESTIC_RUNNING=false; fi",
            "fp=" + sh + "(cat " + FOREIGN_PID + " 2>/dev/null); " +
                "if [ -n \"" + sh + "fp\" ] && kill -0 \"" + sh + "fp\" 2>/dev/null; " +
                "then echo FOREIGN_RUNNING=true; echo FOREIGN_PID=" + sh + "fp; else echo FOREIGN_RUNNING=false; fi",
            "echo '===YAML_PORTS==='",
            "awk '/^dns:/{d=1;next} d && /^  port:/{print \"DOMESTIC_YAML_DNS_PORT=\" " + sh + "2; exit} /^[^ ]/{d=0}' " +
                DOMESTIC_CONFIG + " 2>/dev/null || true",
            "awk '/^dns:/{d=1;next} d && /^  port:/{print \"FOREIGN_YAML_DNS_PORT=\" " + sh + "2; exit} /^[^ ]/{d=0}' " +
                FOREIGN_CONFIG + " 2>/dev/null || true",
            "echo '===CONTROLLER==='",
            "controller=" + sh + "(awk '!/^[[:space:]]*#/ && /external-controller:[[:space:]]/ {print " +
                sh + "2; exit}' " + MIHOMO_CONFIG + " 2>/dev/null | tr -d '\"'); " +
                "echo CONTROLLER=" + sh + "controller",
            "echo '===IPV6==='",
            "cat /proc/sys/net/ipv6/conf/all/disable_ipv6 2>/dev/null | sed 's/^/KERNEL_IPV6_DISABLED=/' || true",
            "echo '===LISTENERS==='",
            "ss -lntuH 2>/dev/null || true",
            "echo '===NAT_DNS_HIJACK==='",
            "iptables -t nat -S NAT_DNS_HIJACK 2>/dev/null || true",
            "echo '===IPV6_GUARD==='",
            "ip6tables -t filter -S OUTPUT 2>/dev/null | grep BOX_DNS6_REJECT || true",
            "echo '===END==='",
            "true"
        ).joinToString("; ")
    }

    private fun parse(output: String): HealthSnapshot {
        val values = linkedMapOf<String, String>()
        val boxSettings = linkedMapOf<String, String>()
        val aghSettings = linkedMapOf<String, String>()
        val listeners = mutableListOf<String>()
        val natRules = mutableListOf<String>()
        val ipv6Rules = mutableListOf<String>()
        var section = ""

        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("===") && line.endsWith("===") -> section = line
                line.startsWith("BOX_SETTING:") -> parseSetting(
                    line.removePrefix("BOX_SETTING:"), boxSettings
                )
                line.startsWith("AGH_SETTING:") -> parseSetting(
                    line.removePrefix("AGH_SETTING:"), aghSettings
                )
                section == "===LISTENERS===" && line.isNotBlank() -> listeners += line
                section == "===NAT_DNS_HIJACK===" && line.isNotBlank() -> natRules += line
                section == "===IPV6_GUARD===" && line.isNotBlank() -> ipv6Rules += line
                line.contains('=') -> {
                    val index = line.indexOf('=')
                    values[line.substring(0, index)] = line.substring(index + 1).trim()
                }
            }
        }

        val rootOk = values["ROOT_UID"] == "0"
        val boxModule = values.bool("BOX_MODULE")
        val aghModule = values.bool("AGH_MODULE")
        val domesticConfig = values.bool("DOMESTIC_CONFIG")
        val foreignConfig = values.bool("FOREIGN_CONFIG")
        val mihomoBinary = values.bool("MIHOMO_BINARY")
        val mihomoConfig = values.bool("MIHOMO_CONFIG")
        val boxRunning = values.bool("BOX_RUNNING")
        val userStopped = values.bool("BOX_USER_STOPPED")
        val domesticRunning = values.bool("DOMESTIC_RUNNING")
        val foreignRunning = values.bool("FOREIGN_RUNNING")

        val core = clean(boxSettings["bin_name"]).ifBlank { "mihomo" }
        val dnsHijack = clean(boxSettings["dns_hijack_mode"]).ifBlank { "unknown" }
        val boxIpv6 = clean(boxSettings["ipv6"]).lowercase()
        val mihomoDnsForward = clean(boxSettings["mihomo_dns_forward"]).lowercase()
        val domesticBoxPort = clean(boxSettings["domestic_dns_port"]).toIntOrNull()
        val foreignBoxPort = clean(boxSettings["foreign_dns_port"]).toIntOrNull()
        val fallbackPort = clean(boxSettings["foreign_dns_fallback_port"]).toIntOrNull()
        val failPort = clean(boxSettings["foreign_dns_fail_port"]).toIntOrNull()
        val domesticAghPort = clean(aghSettings["domestic_dns_port"]).toIntOrNull()
        val foreignAghPort = clean(aghSettings["foreign_dns_port"]).toIntOrNull()
        val domesticYamlPort = values["DOMESTIC_YAML_DNS_PORT"]?.toIntOrNull()
        val foreignYamlPort = values["FOREIGN_YAML_DNS_PORT"]?.toIntOrNull()
        val controller = values["CONTROLLER"].orEmpty()
        val controllerPort = parsePort(controller)
        val kernelIpv6Disabled = values["KERNEL_IPV6_DISABLED"] == "1"

        val domesticListen = domesticBoxPort?.let { listens(listeners, it) } ?: false
        val foreignListen = foreignBoxPort?.let { listens(listeners, it) } ?: false
        val mihomoListen = listens(listeners, fallbackPort ?: 1053)
        val controllerListen = controllerPort?.let { listens(listeners, it) } ?: false
        val natPresent = natRules.isNotEmpty()
        val ipv6GuardPresent = ipv6Rules.isNotEmpty()

        val items = mutableListOf<HealthCheckItem>()
        items += HealthCheckItem(
            id = "root",
            title = "Root",
            status = if (rootOk) HealthCheckStatus.HEALTHY else HealthCheckStatus.ERROR,
            detail = if (rootOk) "su 命令以 uid 0 执行"
            else "未获得 uid 0，后续 Root 检查结果不可信"
        )

        items += when {
            !boxModule -> HealthCheckItem(
                "box", "Box", HealthCheckStatus.ERROR,
                "Box service 不存在或不可执行"
            )
            userStopped -> HealthCheckItem(
                "box", "Box", HealthCheckStatus.STOPPED,
                "user_stopped 已存在；保持主动停止状态"
            )
            boxRunning -> HealthCheckItem(
                "box", "Box", HealthCheckStatus.HEALTHY,
                "进程运行中 · core=" + core + " · dns_hijack_mode=" + dnsHijack,
                HealthRepairAction.REAPPLY_APP_RULES
            )
            else -> HealthCheckItem(
                "box", "Box", HealthCheckStatus.STOPPED,
                "Box module 存在，但进程未运行"
            )
        }

        items += when {
            core != "mihomo" -> HealthCheckItem(
                "mihomo", "Mihomo", HealthCheckStatus.DEGRADED,
                "当前 Box core=" + core + "；Mihomo 专用检查已跳过"
            )
            !mihomoBinary || !mihomoConfig -> HealthCheckItem(
                "mihomo", "Mihomo", HealthCheckStatus.ERROR,
                "Mihomo binary 或 config.yaml 缺失"
            )
            !boxRunning -> HealthCheckItem(
                "mihomo", "Mihomo", HealthCheckStatus.STOPPED,
                "Box/Mihomo 进程未运行"
            )
            isEnabled(mihomoDnsForward) && !mihomoListen -> HealthCheckItem(
                "mihomo", "Mihomo", HealthCheckStatus.MISMATCH,
                "mihomo_dns_forward 已启用，但 :" + (fallbackPort ?: 1053) + " 未监听",
                HealthRepairAction.REAPPLY_APP_RULES
            )
            else -> HealthCheckItem(
                "mihomo", "Mihomo", HealthCheckStatus.HEALTHY,
                if (isEnabled(mihomoDnsForward)) {
                    "进程运行，DNS :" + (fallbackPort ?: 1053) + " 正在监听"
                } else {
                    "进程运行；mihomo_dns_forward 未启用"
                }
            )
        }

        items += aghItem(
            id = "domestic",
            title = "Domestic AGH",
            moduleReady = aghModule,
            configReady = domesticConfig,
            running = domesticRunning,
            configuredPort = domesticBoxPort,
            yamlPort = domesticYamlPort,
            listening = domesticListen,
            repair = HealthRepairAction.RESTART_DOMESTIC
        )
        items += aghItem(
            id = "foreign",
            title = "Foreign AGH",
            moduleReady = aghModule,
            configReady = foreignConfig,
            running = foreignRunning,
            configuredPort = foreignBoxPort,
            yamlPort = foreignYamlPort,
            listening = foreignListen,
            repair = HealthRepairAction.RESTART_FOREIGN
        )

        items += when {
            userStopped || !boxRunning -> HealthCheckItem(
                "dns_chain", "DNS Chain", HealthCheckStatus.STOPPED,
                "Box 已停止；不要求 NAT_DNS_HIJACK 保持活动"
            )
            dnsHijack.equals("disable", true) -> HealthCheckItem(
                "dns_chain", "DNS Chain", HealthCheckStatus.HEALTHY,
                "dns_hijack_mode=disable，DNS 劫持按配置关闭"
            )
            !natPresent -> HealthCheckItem(
                "dns_chain", "DNS Chain", HealthCheckStatus.MISMATCH,
                "DNS 劫持已启用，但 NAT_DNS_HIJACK 链不存在",
                HealthRepairAction.REAPPLY_APP_RULES
            )
            else -> {
                val targets = buildList {
                    domesticBoxPort?.takeIf { natRules.any { rule -> rule.contains("--to-ports " + it) } }
                        ?.let { add("Domestic :" + it) }
                    foreignBoxPort?.takeIf { natRules.any { rule -> rule.contains("--to-ports " + it) } }
                        ?.let { add("Foreign :" + it) }
                    (fallbackPort ?: 1053).takeIf {
                        natRules.any { rule -> rule.contains("--to-ports " + it) }
                    }?.let { add("fallback :" + it) }
                    (failPort ?: 65534).takeIf {
                        natRules.any { rule -> rule.contains("--to-ports " + it) }
                    }?.let { add("fail-closed :" + it) }
                }
                HealthCheckItem(
                    "dns_chain", "DNS Chain", HealthCheckStatus.HEALTHY,
                    "NAT_DNS_HIJACK 已存在" +
                        if (targets.isEmpty()) "" else " · " + targets.joinToString()
                )
            }
        }

        items += HealthCheckItem(
            "iptables",
            "iptables",
            when {
                userStopped || !boxRunning -> HealthCheckStatus.STOPPED
                dnsHijack.equals("disable", true) -> HealthCheckStatus.HEALTHY
                natPresent -> HealthCheckStatus.HEALTHY
                else -> HealthCheckStatus.MISMATCH
            },
            "NAT_DNS_HIJACK=" + if (natPresent) "present" else "absent" +
                " · BOX_DNS6_REJECT=" + if (ipv6GuardPresent) "present" else "absent",
            if (!userStopped && boxRunning && !dnsHijack.equals("disable", true) && !natPresent) {
                HealthRepairAction.REAPPLY_APP_RULES
            } else null
        )

        items += HealthCheckItem(
            "ipv6",
            "IPv6",
            if (isEnabled(boxIpv6) && kernelIpv6Disabled) {
                HealthCheckStatus.MISMATCH
            } else {
                HealthCheckStatus.HEALTHY
            },
            "Box ipv6=" + boxIpv6.ifBlank { "unknown" } +
                " · kernel disable_ipv6=" + if (kernelIpv6Disabled) "1" else "0" +
                " · BOX_DNS6_REJECT=" + if (ipv6GuardPresent) "present" else "absent"
        )

        items += when {
            core != "mihomo" -> HealthCheckItem(
                "controller", "Controller", HealthCheckStatus.DEGRADED,
                "非 Mihomo core，Controller 专用检查跳过"
            )
            !boxRunning -> HealthCheckItem(
                "controller", "Controller", HealthCheckStatus.STOPPED,
                "Box/Mihomo 未运行"
            )
            controller.isBlank() -> HealthCheckItem(
                "controller", "Controller", HealthCheckStatus.DEGRADED,
                "config.yaml 未发现 external-controller"
            )
            controllerPort == null -> HealthCheckItem(
                "controller", "Controller", HealthCheckStatus.DEGRADED,
                "external-controller 地址无法解析监听端口"
            )
            controllerListen -> HealthCheckItem(
                "controller", "Controller", HealthCheckStatus.HEALTHY,
                "external-controller=" + redactController(controller) +
                    " · :" + controllerPort + " 正在监听"
            )
            else -> HealthCheckItem(
                "controller", "Controller", HealthCheckStatus.MISMATCH,
                "external-controller=" + redactController(controller) +
                    "，但 :" + controllerPort + " 未监听"
            )
        }

        val inconsistencies = mutableListOf<String>()
        comparePorts("Domestic Box/AGH settings", domesticBoxPort, domesticAghPort, inconsistencies)
        comparePorts("Foreign Box/AGH settings", foreignBoxPort, foreignAghPort, inconsistencies)
        comparePorts("Domestic Box/YAML", domesticBoxPort, domesticYamlPort, inconsistencies)
        comparePorts("Foreign Box/YAML", foreignBoxPort, foreignYamlPort, inconsistencies)
        if (fallbackPort != null && fallbackPort != 1053) {
            inconsistencies += "foreign fallback 应为 :1053，当前 :" + fallbackPort
        }
        if (failPort != null && failPort != 65534) {
            inconsistencies += "foreign fail-closed 应为 :65534，当前 :" + failPort
        }
        if (domesticRunning && domesticBoxPort != null && !domesticListen) {
            inconsistencies += "Domestic 运行但 :" + domesticBoxPort + " 未监听"
        }
        if (foreignRunning && foreignBoxPort != null && !foreignListen) {
            inconsistencies += "Foreign 运行但 :" + foreignBoxPort + " 未监听"
        }
        if (isEnabled(mihomoDnsForward) && boxRunning && core == "mihomo" && !mihomoListen) {
            inconsistencies += "Mihomo DNS forward 启用但 fallback 端口未监听"
        }
        if (!userStopped && boxRunning && !dnsHijack.equals("disable", true) && !natPresent) {
            inconsistencies += "DNS hijack 启用但 NAT_DNS_HIJACK 缺失"
        }

        items += HealthCheckItem(
            "consistency",
            "Configuration Consistency",
            if (inconsistencies.isEmpty()) HealthCheckStatus.HEALTHY
            else HealthCheckStatus.MISMATCH,
            if (inconsistencies.isEmpty()) {
                "Box、AGH settings、AGH YAML 与关键监听端口未发现不一致"
            } else {
                inconsistencies.joinToString("；")
            }
        )

        return HealthSnapshot(
            generatedAt = Instant.now().toString(),
            items = items
        )
    }

    private fun aghItem(
        id: String,
        title: String,
        moduleReady: Boolean,
        configReady: Boolean,
        running: Boolean,
        configuredPort: Int?,
        yamlPort: Int?,
        listening: Boolean,
        repair: HealthRepairAction
    ): HealthCheckItem = when {
        !moduleReady || !configReady -> HealthCheckItem(
            id, title, HealthCheckStatus.ERROR,
            "AGH tool 或实例配置文件缺失"
        )
        !running -> HealthCheckItem(
            id, title, HealthCheckStatus.STOPPED,
            "实例未运行", repair
        )
        configuredPort == null || yamlPort == null -> HealthCheckItem(
            id, title, HealthCheckStatus.MISMATCH,
            "无法同时读取 Box 端口和 AGH YAML 端口", repair
        )
        configuredPort != yamlPort -> HealthCheckItem(
            id, title, HealthCheckStatus.MISMATCH,
            "Box 配置 :" + configuredPort + "，AGH YAML :" + yamlPort, repair
        )
        !listening -> HealthCheckItem(
            id, title, HealthCheckStatus.MISMATCH,
            "进程运行，但配置端口 :" + configuredPort + " 未监听", repair
        )
        else -> HealthCheckItem(
            id, title, HealthCheckStatus.HEALTHY,
            "进程运行 · DNS :" + configuredPort + " 正在监听", repair
        )
    }

    private fun parseSetting(line: String, target: MutableMap<String, String>) {
        val index = line.indexOf('=')
        if (index <= 0) return
        target[line.substring(0, index)] = line.substring(index + 1).trim()
    }

    private fun clean(value: String?): String =
        value.orEmpty().trim().trim('"', '\'')

    private fun Map<String, String>.bool(key: String): Boolean =
        get(key).equals("true", true)

    private fun isEnabled(value: String): Boolean =
        value in setOf("true", "1", "yes", "on", "enable", "enabled")

    private fun listens(lines: List<String>, port: Int): Boolean {
        val regex = Regex("[:.]\\Q" + port + "\\E(?:\\s|$)")
        return lines.any { regex.containsMatchIn(it) }
    }

    private fun parsePort(controller: String): Int? {
        val normalized = controller.trim().trim('"').trimEnd('/')
        if (normalized.isBlank()) return null
        return normalized.substringAfterLast(':', "").toIntOrNull()
    }

    private fun redactController(controller: String): String {
        val normalized = controller.trim()
        return when {
            normalized.startsWith("0.0.0.0:") -> "127.0.0.1:" + normalized.substringAfter(':')
            normalized.startsWith("*:") -> "127.0.0.1:" + normalized.substringAfter(':')
            else -> normalized
        }
    }

    private fun comparePorts(
        label: String,
        left: Int?,
        right: Int?,
        issues: MutableList<String>
    ) {
        if (left != null && right != null && left != right) {
            issues += label + " 不一致（:" + left + " vs :" + right + "）"
        }
    }
}
