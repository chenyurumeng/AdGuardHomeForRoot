package io.github.chenyurumeng.aghmanager.model

data class MihomoGroup(
    val name: String,
    val type: String,
    val now: String = "",
    val all: List<String> = emptyList(),
    val testUrl: String = "",
    val hidden: Boolean = false
) {
    val selectable: Boolean
        get() = type.equals("Selector", true)
}

data class MihomoProvider(
    val name: String,
    val vehicleType: String = "",
    val updatedAt: String = ""
)

data class MihomoSnapshot(
    val controller: String,
    val version: String = "",
    val authenticated: Boolean = false,
    val groups: List<MihomoGroup> = emptyList(),
    val providers: List<MihomoProvider> = emptyList()
)

data class MihomoQuickUiState(
    val loading: Boolean = false,
    val available: Boolean = false,
    val controller: String = "",
    val version: String = "",
    val authenticated: Boolean = false,
    val groups: List<MihomoGroup> = emptyList(),
    val providers: List<MihomoProvider> = emptyList(),
    val delays: Map<String, Int> = emptyMap(),
    val favorites: Set<String> = emptySet(),
    val busyAction: String? = null,
    val error: String = ""
)

data class MihomoConnection(
    val id: String,
    val host: String = "",
    val destinationIp: String = "",
    val destinationPort: String = "",
    val sourceIp: String = "",
    val sourcePort: String = "",
    val network: String = "",
    val process: String = "",
    val processPath: String = "",
    val uid: String = "",
    val rule: String = "",
    val rulePayload: String = "",
    val chains: List<String> = emptyList(),
    val upload: Long = 0L,
    val download: Long = 0L,
    val start: String = ""
) {
    val destinationLabel: String
        get() {
            val base = host.ifBlank { destinationIp.ifBlank { "unknown" } }
            return if (destinationPort.isBlank()) base else base + ":" + destinationPort
        }

    val processLabel: String
        get() = process.ifBlank {
            processPath.substringAfterLast('/').ifBlank {
                if (uid.isBlank()) "未知进程" else "UID " + uid
            }
        }

    val direct: Boolean
        get() = chains.any { it.equals("DIRECT", true) }
}

data class MihomoProcessMetrics(
    val rssKb: Long = 0L,
    val swapKb: Long = 0L,
    val cpuPercent: Double = 0.0
)

data class MihomoRuntimeSnapshot(
    val uploadTotal: Long = 0L,
    val downloadTotal: Long = 0L,
    val connections: List<MihomoConnection> = emptyList(),
    val process: MihomoProcessMetrics = MihomoProcessMetrics()
)

data class MihomoRuntimeUiState(
    val loading: Boolean = true,
    val uploadTotal: Long = 0L,
    val downloadTotal: Long = 0L,
    val uploadBps: Long = 0L,
    val downloadBps: Long = 0L,
    val connections: List<MihomoConnection> = emptyList(),
    val process: MihomoProcessMetrics = MihomoProcessMetrics(),
    val busyAction: String? = null,
    val error: String = ""
)

data class MihomoSubscription(
    val name: String,
    val url: String,
    val intervalSeconds: Int = 86_400,
    val path: String = "",
    val type: String = "http",
    val editable: Boolean = true,
    val enabled: Boolean = true
) {
    val maskedUrl: String
        get() = try {
            val uri = java.net.URI(url)
            val host = uri.host.orEmpty()
            if (host.isBlank()) "***" else uri.scheme + "://" + host + "/***"
        } catch (_: Exception) {
            "***"
        }
}

data class MihomoSubscriptionsUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val subscriptions: List<MihomoSubscription> = emptyList(),
    val error: String = ""
)
