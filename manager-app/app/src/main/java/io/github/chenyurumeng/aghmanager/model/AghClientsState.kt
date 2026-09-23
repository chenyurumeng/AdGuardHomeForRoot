package io.github.chenyurumeng.aghmanager.model

data class AghClient(
    val name: String,
    val ids: List<String>,
    val useGlobalSettings: Boolean = true,
    val filteringEnabled: Boolean = true,
    val parentalEnabled: Boolean = false,
    val safeBrowsingEnabled: Boolean = false,
    val upstreams: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val ignoreQueryLog: Boolean = false,
    val ignoreStatistics: Boolean = false,
    val rawJson: String = "{}"
)

data class AghAutoClient(
    val ip: String,
    val name: String,
    val source: String
)

data class AghClientsSnapshot(
    val clients: List<AghClient>,
    val autoClients: List<AghAutoClient>,
    val supportedTags: List<String>
)

data class AghClientsUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val clients: List<AghClient> = emptyList(),
    val autoClients: List<AghAutoClient> = emptyList(),
    val supportedTags: List<String> = emptyList(),
    val query: String = "",
    val showAutoClients: Boolean = false,
    val error: String = ""
)

data class AghRewriteRule(
    val domain: String,
    val answer: String,
    val enabled: Boolean = true
) {
    val key: String
        get() = domain + "\u0000" + answer
}

data class AghRewritesUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val enabled: Boolean = true,
    val pendingEnabled: Boolean = true,
    val rules: List<AghRewriteRule> = emptyList(),
    val query: String = "",
    val error: String = ""
) {
    val settingsDirty: Boolean
        get() = enabled != pendingEnabled
}
