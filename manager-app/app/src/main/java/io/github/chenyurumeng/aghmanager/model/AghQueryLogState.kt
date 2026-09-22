package io.github.chenyurumeng.aghmanager.model

data class AghDnsAnswer(
    val type: String,
    val value: String,
    val ttl: Long
)

data class AghQueryRule(
    val text: String,
    val filterListId: Long
)

data class AghQueryLogEntry(
    val time: String,
    val domain: String,
    val unicodeDomain: String,
    val qtype: String,
    val qclass: String,
    val client: String,
    val clientName: String,
    val clientProto: String,
    val status: String,
    val reason: String,
    val upstream: String,
    val cached: Boolean,
    val elapsedMs: String,
    val answerDnssec: Boolean,
    val answers: List<AghDnsAnswer>,
    val originalAnswers: List<AghDnsAnswer>,
    val rules: List<AghQueryRule>,
    val serviceName: String
) {
    val displayDomain: String
        get() = unicodeDomain.ifBlank { domain }

    val blocked: Boolean
        get() = reason.startsWith("Filtered")

    val whitelisted: Boolean
        get() = reason == "NotFilteredWhiteList"

    val processed: Boolean
        get() = reason == "NotFilteredNotFound" || reason == "NotFilteredError"

    val stableKey: String
        get() = time + "|" + domain + "|" + client + "|" + qtype
}

data class AghQueryLogPage(
    val entries: List<AghQueryLogEntry>,
    val oldest: String
)

data class AghQueryLogConfig(
    val enabled: Boolean = true,
    val intervalMs: Long = 86_400_000L,
    val anonymizeClientIp: Boolean = false,
    val ignored: List<String> = emptyList(),
    val ignoredEnabled: Boolean = false
)

enum class AghQueryFilter(val label: String) {
    ALL("全部"),
    BLOCKED("已阻止"),
    ALLOWED("已允许"),
    PROCESSED("已处理")
}

data class AghQueryLogUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val entries: List<AghQueryLogEntry> = emptyList(),
    val oldest: String = "",
    val search: String = "",
    val filter: AghQueryFilter = AghQueryFilter.ALL,
    val selected: AghQueryLogEntry? = null,
    val selectedUserRules: List<String> = emptyList(),
    val config: AghQueryLogConfig? = null,
    val pendingConfig: AghQueryLogConfig? = null,
    val savingConfig: Boolean = false,
    val mutatingRule: Boolean = false,
    val error: String = ""
) {
    val visibleEntries: List<AghQueryLogEntry>
        get() = entries.filter {
            when (filter) {
                AghQueryFilter.ALL -> true
                AghQueryFilter.BLOCKED -> it.blocked
                AghQueryFilter.ALLOWED -> it.whitelisted
                AghQueryFilter.PROCESSED -> it.processed
            }
        }

    val configDirty: Boolean
        get() = config != null && pendingConfig != null && config != pendingConfig
}
