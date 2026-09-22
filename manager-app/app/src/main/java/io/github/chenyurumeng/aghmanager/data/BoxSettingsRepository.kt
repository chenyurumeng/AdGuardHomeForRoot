package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.ApplyStrategy
import io.github.chenyurumeng.aghmanager.model.BoxConfig
import io.github.chenyurumeng.aghmanager.model.DnsHijackMode
import io.github.chenyurumeng.aghmanager.model.NetworkMode
import io.github.chenyurumeng.aghmanager.model.RoutingMode

class BoxSettingsRepository {
    companion object {
        private const val SETTINGS = "/data/adb/box/settings.ini"
        private const val SERVICE = "/data/adb/box/scripts/box.service"
        private const val STATE_DIR = "/data/adb/box/run/state"
    }

    suspend fun load(): Result<BoxConfig> {
        val command = "grep -E '^(proxy_mode|network_mode|dns_hijack_mode|ipv6|proxy_tcp|proxy_udp|dns_hijack_tcp|dns_hijack_udp|quic|mihomo_dns_forward)=' " +
            SETTINGS + " 2>/dev/null"
        val result = RootShell.exec(command, 30)
        if (!result.ok) {
            return Result.failure(IllegalStateException("读取 Box settings.ini 失败"))
        }

        val values = linkedMapOf<String, String>()
        result.stdout.lineSequence().forEach { raw ->
            val line = raw.trim()
            val index = line.indexOf('=')
            if (index > 0) {
                values[line.substring(0, index)] =
                    line.substring(index + 1).trim().trim('"', '\'')
            }
        }

        return Result.success(
            BoxConfig(
                proxyMode = RoutingMode.fromRaw(values["proxy_mode"]),
                networkMode = NetworkMode.fromRaw(values["network_mode"]),
                dnsHijackMode = DnsHijackMode.fromRaw(values["dns_hijack_mode"]),
                ipv6 = parseBoolean(values["ipv6"], true),
                proxyTcp = parseBoolean(values["proxy_tcp"], true),
                proxyUdp = parseBoolean(values["proxy_udp"], true),
                dnsHijackTcp = parseBoolean(values["dns_hijack_tcp"], true),
                dnsHijackUdp = parseBoolean(values["dns_hijack_udp"], true),
                quic = parseEnable(values["quic"], true),
                mihomoDnsForward = parseEnable(values["mihomo_dns_forward"], true)
            )
        )
    }

    suspend fun apply(
        current: BoxConfig,
        pending: BoxConfig,
        boxRunning: Boolean
    ): ShellResult {
        val changed = current.changedKeys(pending)
        if (changed.isEmpty()) {
            return ShellResult(0, "", false)
        }

        val strategy = resolveStrategy(changed, boxRunning)
        val replacements = linkedMapOf<String, String>()
        if ("proxy_mode" in changed) replacements["proxy_mode"] = pending.proxyMode.raw
        if ("network_mode" in changed) replacements["network_mode"] = pending.networkMode.raw
        if ("dns_hijack_mode" in changed) replacements["dns_hijack_mode"] = pending.dnsHijackMode.raw
        if ("ipv6" in changed) replacements["ipv6"] = pending.ipv6.toString()
        if ("proxy_tcp" in changed) replacements["proxy_tcp"] = pending.proxyTcp.toString()
        if ("proxy_udp" in changed) replacements["proxy_udp"] = pending.proxyUdp.toString()
        if ("dns_hijack_tcp" in changed) replacements["dns_hijack_tcp"] = pending.dnsHijackTcp.toString()
        if ("dns_hijack_udp" in changed) replacements["dns_hijack_udp"] = pending.dnsHijackUdp.toString()
        if ("quic" in changed) replacements["quic"] = if (pending.quic) "enable" else "disable"
        if ("mihomo_dns_forward" in changed) {
            replacements["mihomo_dns_forward"] = if (pending.mihomoDnsForward) "enable" else "disable"
        }

        val edits = replacements.entries.joinToString("; ") { (key, value) ->
            "grep -q '^" + key + "=' \"\$tmp\" && sed -i 's#^" + key +
                "=.*#" + key + "=\\\"" + value + "\\\"#' \"\$tmp\""
        }

        val applyAction = when (strategy) {
            ApplyStrategy.SAVE_ONLY -> "true"
            ApplyStrategy.APPLY_APPS -> SERVICE + " apply-apps"
            ApplyStrategy.BOX_RESTART -> SERVICE + " restart"
        }

        val restoreAction = when (strategy) {
            ApplyStrategy.SAVE_ONLY -> "true"
            ApplyStrategy.APPLY_APPS -> SERVICE + " apply-apps >/dev/null 2>&1 || true"
            ApplyStrategy.BOX_RESTART -> SERVICE + " restart >/dev/null 2>&1 || true"
        }

        val sh = '$'
        val command = buildString {
            append("settings=")
            append(SETTINGS)
            append("; state_dir=")
            append(STATE_DIR)
            append("; mkdir -p \"")
            append(sh)
            append("state_dir\"; ")
            append("backup=\"")
            append(sh)
            append("state_dir/settings.ini.manager.bak\"; ")
            append("tmp=\"")
            append(sh)
            append("state_dir/settings.ini.manager.")
            append(sh)
            append(sh)
            append(".tmp\"; ")
            append("cp -p \"")
            append(sh)
            append("settings\" \"")
            append(sh)
            append("backup\" || exit 31; ")
            append("cp -p \"")
            append(sh)
            append("settings\" \"")
            append(sh)
            append("tmp\" || exit 32; ")
            append(edits)
            append("; sh -n \"")
            append(sh)
            append("tmp\" || { rm -f \"")
            append(sh)
            append("tmp\"; exit 33; }; ")
            append("mv \"")
            append(sh)
            append("tmp\" \"")
            append(sh)
            append("settings\" || exit 34; ")
            append("if ")
            append(applyAction)
            append("; then rm -f \"")
            append(sh)
            append("backup\"; exit 0; ")
            append("else rc=")
            append(sh)
            append("?; cp -p \"")
            append(sh)
            append("backup\" \"")
            append(sh)
            append("settings\"; ")
            append(restoreAction)
            append("; rm -f \"")
            append(sh)
            append("backup\"; exit ")
            append(sh)
            append("rc; fi")
        }

        return RootShell.exec(command, if (strategy == ApplyStrategy.BOX_RESTART) 180 else 120)
    }

    fun resolveStrategy(changed: Set<String>, boxRunning: Boolean): ApplyStrategy {
        if (!boxRunning) return ApplyStrategy.SAVE_ONLY
        return if (changed == setOf("proxy_mode")) {
            ApplyStrategy.APPLY_APPS
        } else {
            ApplyStrategy.BOX_RESTART
        }
    }

    private fun parseBoolean(value: String?, default: Boolean): Boolean =
        when (value?.lowercase()) {
            "true", "1", "yes", "on", "enable", "enabled" -> true
            "false", "0", "no", "off", "disable", "disabled" -> false
            else -> default
        }

    private fun parseEnable(value: String?, default: Boolean): Boolean =
        parseBoolean(value, default)
}
