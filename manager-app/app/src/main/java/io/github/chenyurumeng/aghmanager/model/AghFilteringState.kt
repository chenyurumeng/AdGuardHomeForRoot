package io.github.chenyurumeng.aghmanager.model

data class AghFilterSubscription(
    val id: Long,
    val enabled: Boolean,
    val name: String,
    val url: String,
    val rulesCount: Int,
    val lastUpdated: String,
    val whitelist: Boolean
)

data class AghFilteringStatus(
    val enabled: Boolean = true,
    val intervalHours: Int = 72,
    val filters: List<AghFilterSubscription> = emptyList(),
    val whitelistFilters: List<AghFilterSubscription> = emptyList(),
    val userRules: List<String> = emptyList()
) {
    val allFilters: List<AghFilterSubscription>
        get() = filters + whitelistFilters
}

data class AghFilterMatch(
    val text: String,
    val filterListId: Long
)

data class AghFilterCheckResult(
    val host: String,
    val reason: String,
    val rules: List<AghFilterMatch>,
    val serviceName: String = "",
    val cname: String = "",
    val ipAddresses: List<String> = emptyList()
) {
    val filtered: Boolean
        get() = reason.startsWith("Filtered") ||
            reason == "Rewrite" ||
            reason == "RewriteRule"
}

data class AghFiltersUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val status: AghFilteringStatus? = null,
    val pendingEnabled: Boolean = true,
    val pendingIntervalHours: Int = 72,
    val query: String = "",
    val showWhitelist: Boolean = false,
    val checkHost: String = "",
    val checkingHost: Boolean = false,
    val checkResult: AghFilterCheckResult? = null,
    val error: String = ""
) {
    val settingsDirty: Boolean
        get() = status != null &&
            (pendingEnabled != status.enabled ||
                pendingIntervalHours != status.intervalHours)
}

data class AghUserRulesUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val baselineRules: List<String> = emptyList(),
    val text: String = "",
    val error: String = ""
) {
    val rules: List<String>
        get() = text.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()

    val dirty: Boolean
        get() = rules != baselineRules
}
