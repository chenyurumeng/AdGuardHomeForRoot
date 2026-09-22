package io.github.chenyurumeng.aghmanager.model

enum class HealthState {
    CHECKING,
    HEALTHY,
    DEGRADED,
    FAIL_CLOSED,
    STOPPED,
    ERROR
}

data class BoxState(
    val running: Boolean = false,
    val coreName: String = "",
    val pid: String = "",
    val version: String = "",
    val proxyMode: String = "",
    val networkMode: String = "",
    val dnsHijackMode: String = "",
    val ipv6: String = "",
    val controller: String = "",
    val userStopped: Boolean = false
)

data class AghInstanceState(
    val name: String,
    val dnsPort: Int,
    val webPort: Int,
    val running: Boolean = false,
    val pid: String = ""
)

data class DnsState(
    val port5591: Boolean = false,
    val port5592: Boolean = false,
    val port1053: Boolean = false,
    val port9090: Boolean = false,
    val route5591: Boolean = false,
    val route5592: Boolean = false,
    val route1053: Boolean = false,
    val route65534: Boolean = false
)

data class SystemState(
    val checking: Boolean = true,
    val rootAvailable: Boolean = false,
    val boxModuleReady: Boolean = false,
    val aghModuleReady: Boolean = false,
    val box: BoxState = BoxState(),
    val domestic: AghInstanceState = AghInstanceState("Domestic", 5591, 3000),
    val foreign: AghInstanceState = AghInstanceState("Foreign", 5592, 3001),
    val dns: DnsState = DnsState(),
    val health: HealthState = HealthState.CHECKING,
    val healthDetail: String = "正在检查 Root 与网络服务状态"
) {
    val routingMode: RoutingMode
        get() = RoutingMode.fromRaw(box.proxyMode)

    val blacklistMode: Boolean
        get() = routingMode == RoutingMode.BLACKLIST

    val coreMode: Boolean
        get() = routingMode == RoutingMode.CORE

    fun domesticDnsTarget(): String {
        if (box.userStopped || !box.running) return "系统 DNS（Box stopped）"
        if (dns.route5591 && dns.port5591) return "Domestic :" + domestic.dnsPort
        return if (domestic.running) "Domestic :" + domestic.dnsPort else "Android 系统 DNS"
    }

    fun foreignDnsTarget(): String {
        if (box.userStopped || !box.running) return "系统 DNS（Box stopped）"
        if (dns.route5592 && dns.port5592) return "Foreign :" + foreign.dnsPort
        if (dns.route1053 && dns.port1053) return "Mihomo :1053 fallback"
        if (dns.route65534) return ":65534 FAIL-CLOSED"
        if (foreign.running) return "Foreign :" + foreign.dnsPort
        if (dns.port1053) return "Mihomo :1053 fallback"
        return "FAIL-CLOSED"
    }
}
