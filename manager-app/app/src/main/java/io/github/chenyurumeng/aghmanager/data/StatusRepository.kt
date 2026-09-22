package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.AghInstanceState
import io.github.chenyurumeng.aghmanager.model.BoxState
import io.github.chenyurumeng.aghmanager.model.DnsState
import io.github.chenyurumeng.aghmanager.model.HealthState
import io.github.chenyurumeng.aghmanager.model.SystemState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class StatusRepository {
    companion object {
        const val AGH_TOOL = "/data/adb/agh/scripts/tool.sh"
        const val BOX_SERVICE = "/data/adb/box/scripts/box.service"
        const val BOX_SETTINGS = "/data/adb/box/settings.ini"
        const val BOX_STOP_GUARD = "/data/adb/box/run/state/user_stopped"
    }

    private val refreshMutex = Mutex()
    private val _state = MutableStateFlow(SystemState())
    val state: StateFlow<SystemState> = _state.asStateFlow()

    suspend fun refresh() {
        refreshMutex.withLock {
            _state.value = querySnapshot()
        }
    }

    private suspend fun querySnapshot(): SystemState {
        val sh = '$'
        val command = listOf(
            "echo '===MODULES==='",
            "[ -x " + AGH_TOOL + " ] && echo 'AGH_MODULE=ready' || echo 'AGH_MODULE=missing'",
            "[ -x " + BOX_SERVICE + " ] && echo 'BOX_MODULE=ready' || echo 'BOX_MODULE=missing'",
            "echo '===AGH_STATUS==='",
            "if [ -x " + AGH_TOOL + " ]; then " + AGH_TOOL + " status 2>&1; fi",
            "echo '===BOX_SETTINGS==='",
            "if [ -f " + BOX_SETTINGS + " ]; then grep -E '^(bin_name|proxy_mode|network_mode|dns_hijack_mode|ipv6|domestic_dns_port|foreign_dns_port|foreign_dns_fallback_port|foreign_dns_fail_port)=' " + BOX_SETTINGS + " 2>/dev/null || true; fi",
            "echo '===BOX_PROCESS==='",
            """bin=${sh}(sed -n 's/^bin_name="\([^"]*\)".*/\1/p' ${BOX_SETTINGS} 2>/dev/null | head -n1)""",
            """[ -n "${sh}bin" ] || bin=mihomo""",
            """echo BOX_BIN=${sh}bin""",
            """pid=${sh}(cat /data/adb/box/run/box.pid 2>/dev/null)""",
            """if [ -n "${sh}pid" ] && kill -0 "${sh}pid" 2>/dev/null; then echo 'BOX_STATUS=up'; echo BOX_PID=${sh}pid; else echo 'BOX_STATUS=down'; fi""",
            "[ -f " + BOX_STOP_GUARD + " ] && echo 'BOX_USER_STOPPED=true' || echo 'BOX_USER_STOPPED=false'",
            """if [ "${sh}bin" = mihomo ] && [ -x /data/adb/box/bin/mihomo ]; then v=${sh}(/data/adb/box/bin/mihomo -v 2>/dev/null | head -n1); echo "BOX_VERSION=${sh}v"; fi""",
            """controller=${sh}(awk '!/^[[:space:]]*#/ && /external-controller:[[:space:]]/ {print ${sh}2; exit}' /data/adb/box/mihomo/config.yaml 2>/dev/null | tr -d '"')""",
            """[ -n "${sh}controller" ] && echo BOX_CONTROLLER=${sh}controller""",
            "echo '===PORTS==='",
            """for p in 5591 5592 1053 9090; do if ss -lntu 2>/dev/null | grep -qE '[:.]'${sh}p'([[:space:]]|${sh})'; then echo PORT_${sh}p=up; else echo PORT_${sh}p=down; fi; done""",
            "echo '===DNS_RULES==='",
            "iptables -t nat -S NAT_DNS_HIJACK 2>/dev/null || true",
            "echo '===END==='",
            "true"
        ).joinToString("; ")

        return parse(RootShell.exec(command, 45))
    }

    private fun parse(result: ShellResult): SystemState {
        if (!result.ok) {
            return SystemState(
                checking = false,
                rootAvailable = false,
                health = HealthState.ERROR,
                healthDetail = if (result.timedOut) {
                    "Root 状态查询超时"
                } else {
                    "Root 不可用或状态查询失败"
                }
            )
        }

        var aghReady = false
        var boxReady = false
        var domesticUp = false
        var foreignUp = false
        var boxUp = false
        var userStopped = false
        var port5591 = false
        var port5592 = false
        var port1053 = false
        var port9090 = false
        var route5591 = false
        var route5592 = false
        var route1053 = false
        var route65534 = false
        var domesticPid = ""
        var foreignPid = ""
        var boxPid = ""
        var boxBin = ""
        var boxVersion = ""
        var boxController = ""
        var proxyMode = ""
        var networkMode = ""
        var dnsHijackMode = ""
        var ipv6 = ""
        var inDnsRules = false

        result.stdout.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line == "===DNS_RULES===" -> inDnsRules = true
                line == "===END===" -> inDnsRules = false
                line == "AGH_MODULE=ready" -> aghReady = true
                line == "BOX_MODULE=ready" -> boxReady = true
                line.startsWith("domestic:") -> {
                    domesticUp = line.contains(" up ")
                    domesticPid = valueAfter(line, "pid=")
                }
                line.startsWith("foreign:") -> {
                    foreignUp = line.contains(" up ")
                    foreignPid = valueAfter(line, "pid=")
                }
                line == "BOX_STATUS=up" -> boxUp = true
                line == "BOX_USER_STOPPED=true" -> userStopped = true
                line.startsWith("BOX_PID=") -> boxPid = afterEquals(line)
                line.startsWith("BOX_BIN=") -> boxBin = afterEquals(line)
                line.startsWith("BOX_VERSION=") -> boxVersion = afterEquals(line)
                line.startsWith("BOX_CONTROLLER=") -> boxController = afterEquals(line)
                line.startsWith("proxy_mode=") -> proxyMode = cleanSetting(afterEquals(line))
                line.startsWith("network_mode=") -> networkMode = cleanSetting(afterEquals(line))
                line.startsWith("dns_hijack_mode=") -> dnsHijackMode = cleanSetting(afterEquals(line))
                line.startsWith("ipv6=") -> ipv6 = cleanSetting(afterEquals(line))
                line == "PORT_5591=up" -> port5591 = true
                line == "PORT_5592=up" -> port5592 = true
                line == "PORT_1053=up" -> port1053 = true
                line == "PORT_9090=up" -> port9090 = true
                inDnsRules && line.contains("--to-ports 5591") -> route5591 = true
                inDnsRules && line.contains("--to-ports 5592") -> route5592 = true
                inDnsRules && line.contains("--to-ports 1053") -> route1053 = true
                inDnsRules && line.contains("--to-ports 65534") -> route65534 = true
            }
        }

        val health = when {
            !aghReady || !boxReady -> HealthState.ERROR
            userStopped || !boxUp -> HealthState.STOPPED
            route65534 || (!foreignUp && !port1053) -> HealthState.FAIL_CLOSED
            boxUp && domesticUp && foreignUp && port5591 && port5592 && port1053 ->
                HealthState.HEALTHY
            else -> HealthState.DEGRADED
        }

        val detail = when (health) {
            HealthState.HEALTHY -> "Box、双 AGH 与 Mihomo DNS 均正常"
            HealthState.DEGRADED -> "部分组件不可用，当前由回退策略维持服务"
            HealthState.FAIL_CLOSED -> "Foreign 与 Mihomo DNS 不可用，白名单 DNS 已保护性阻断"
            HealthState.STOPPED -> if (userStopped) {
                "Box 已被主动停止，DNS hooks 不应被 watchdog 重建"
            } else {
                "Box 当前未运行"
            }
            HealthState.ERROR -> "Root、Box 或 AGH 模块状态异常"
            HealthState.CHECKING -> "正在检查 Root 与网络服务状态"
        }

        return SystemState(
            checking = false,
            rootAvailable = true,
            boxModuleReady = boxReady,
            aghModuleReady = aghReady,
            box = BoxState(
                running = boxUp,
                coreName = boxBin,
                pid = boxPid,
                version = boxVersion,
                proxyMode = proxyMode,
                networkMode = networkMode,
                dnsHijackMode = dnsHijackMode,
                ipv6 = ipv6,
                controller = boxController,
                userStopped = userStopped
            ),
            domestic = AghInstanceState("Domestic", 5591, 3000, domesticUp, domesticPid),
            foreign = AghInstanceState("Foreign", 5592, 3001, foreignUp, foreignPid),
            dns = DnsState(
                port5591 = port5591,
                port5592 = port5592,
                port1053 = port1053,
                port9090 = port9090,
                route5591 = route5591,
                route5592 = route5592,
                route1053 = route1053,
                route65534 = route65534
            ),
            health = health,
            healthDetail = detail
        )
    }

    private fun cleanSetting(value: String): String = value.replace("\"", "").trim()

    private fun afterEquals(line: String): String {
        val index = line.indexOf('=')
        return if (index < 0) "" else line.substring(index + 1).trim()
    }

    private fun valueAfter(line: String, key: String): String {
        val index = line.indexOf(key)
        if (index < 0) return ""
        val rest = line.substring(index + key.length)
        val end = rest.indexOf(' ')
        return if (end >= 0) rest.substring(0, end) else rest
    }
}
