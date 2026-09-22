package io.github.chenyurumeng.aghmanager.model

enum class AghInstance(
    val key: String,
    val label: String,
    val configPath: String,
    val defaultDnsPort: Int,
    val defaultWebPort: Int
) {
    DOMESTIC(
        key = "domestic",
        label = "Domestic",
        configPath = "/data/adb/agh/instances/domestic/AdGuardHome.yaml",
        defaultDnsPort = 5591,
        defaultWebPort = 3000
    ),
    FOREIGN(
        key = "foreign",
        label = "Foreign",
        configPath = "/data/adb/agh/instances/foreign/AdGuardHome.yaml",
        defaultDnsPort = 5592,
        defaultWebPort = 3001
    );

    companion object {
        fun fromKey(value: String?): AghInstance =
            entries.firstOrNull { it.key == value } ?: DOMESTIC
    }
}

data class AghCredential(
    val username: String,
    val password: String
)

data class AghStructuralConfig(
    val webHost: String = "127.0.0.1",
    val webPort: Int = 3000,
    val dnsBindHosts: List<String> = listOf("127.0.0.1", "::1"),
    val dnsPort: Int = 5591,
    val username: String = "root",
    val hasUser: Boolean = true
) {
    fun webUrl(): String {
        val host = when (webHost) {
            "", "0.0.0.0", "*" -> "127.0.0.1"
            "::", "[::]" -> "::1"
            else -> webHost
        }
        val rendered = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        return "http://$rendered:$webPort"
    }
}

data class AghDnsConfig(
    val protectionEnabled: Boolean = true,
    val upstreamDns: List<String> = emptyList(),
    val bootstrapDns: List<String> = emptyList(),
    val fallbackDns: List<String> = emptyList(),
    val upstreamMode: String = "parallel",
    val cacheEnabled: Boolean = true,
    val cacheSize: Long = 33_554_432L,
    val cacheOptimistic: Boolean = true,
    val dnssecEnabled: Boolean = false,
    val disableIpv6: Boolean = false,
    val ratelimit: Int = 0,
    val upstreamTimeout: Int = 10
)

data class AghUpstreamTestResult(
    val server: String,
    val result: String
) {
    val ok: Boolean
        get() = result.equals("OK", true)
}

data class AghControlUiState(
    val loading: Boolean = true,
    val applyingStructure: Boolean = false,
    val applyingDns: Boolean = false,
    val testingUpstreams: Boolean = false,
    val credentialBound: Boolean = false,
    val credentialUsername: String = "",
    val structure: AghStructuralConfig = AghStructuralConfig(),
    val pendingStructure: AghStructuralConfig = AghStructuralConfig(),
    val dns: AghDnsConfig? = null,
    val pendingDns: AghDnsConfig? = null,
    val upstreamTests: List<AghUpstreamTestResult> = emptyList(),
    val apiAvailable: Boolean = false,
    val error: String = ""
) {
    val structureDirty: Boolean
        get() = structure != pendingStructure

    val dnsDirty: Boolean
        get() = dns != null && pendingDns != null && dns != pendingDns
}
