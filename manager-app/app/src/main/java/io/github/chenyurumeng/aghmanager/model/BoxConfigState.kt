package io.github.chenyurumeng.aghmanager.model

enum class NetworkMode(val raw: String, val label: String) {
    TUN("tun", "TUN"),
    TPROXY("tproxy", "TPROXY"),
    REDIRECT("redirect", "Redirect"),
    MIXED("mixed", "Mixed"),
    ENHANCE("enhance", "Enhance");

    companion object {
        fun fromRaw(value: String?): NetworkMode =
            entries.firstOrNull { it.raw.equals(value, true) } ?: TUN
    }
}

enum class DnsHijackMode(val raw: String, val label: String) {
    DISABLE("disable", "Disable"),
    TPROXY("tproxy", "TPROXY"),
    REDIRECT("redirect", "Redirect"),
    REDIRECT_APPS("redirect-apps", "Redirect Apps"),
    SPLIT_APPS("split-apps", "Split Apps");

    companion object {
        fun fromRaw(value: String?): DnsHijackMode =
            entries.firstOrNull { it.raw.equals(value, true) } ?: TPROXY
    }
}

data class BoxConfig(
    val proxyMode: RoutingMode = RoutingMode.CORE,
    val networkMode: NetworkMode = NetworkMode.TUN,
    val dnsHijackMode: DnsHijackMode = DnsHijackMode.TPROXY,
    val ipv6: Boolean = true,
    val proxyTcp: Boolean = true,
    val proxyUdp: Boolean = true,
    val dnsHijackTcp: Boolean = true,
    val dnsHijackUdp: Boolean = true,
    val quic: Boolean = true,
    val mihomoDnsForward: Boolean = true
) {
    fun changedKeys(other: BoxConfig): Set<String> = buildSet {
        if (proxyMode != other.proxyMode) add("proxy_mode")
        if (networkMode != other.networkMode) add("network_mode")
        if (dnsHijackMode != other.dnsHijackMode) add("dns_hijack_mode")
        if (ipv6 != other.ipv6) add("ipv6")
        if (proxyTcp != other.proxyTcp) add("proxy_tcp")
        if (proxyUdp != other.proxyUdp) add("proxy_udp")
        if (dnsHijackTcp != other.dnsHijackTcp) add("dns_hijack_tcp")
        if (dnsHijackUdp != other.dnsHijackUdp) add("dns_hijack_udp")
        if (quic != other.quic) add("quic")
        if (mihomoDnsForward != other.mihomoDnsForward) add("mihomo_dns_forward")
    }
}

enum class ApplyStrategy {
    SAVE_ONLY,
    APPLY_APPS,
    BOX_RESTART
}

data class BoxConfigUiState(
    val current: BoxConfig = BoxConfig(),
    val pending: BoxConfig = BoxConfig(),
    val loaded: Boolean = false,
    val applying: Boolean = false,
    val error: String = ""
) {
    val dirtyKeys: Set<String>
        get() = current.changedKeys(pending)

    val dirty: Boolean
        get() = dirtyKeys.isNotEmpty()
}
