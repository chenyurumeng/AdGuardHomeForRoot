package io.github.chenyurumeng.aghmanager.model

data class AghAccessList(
    val allowedClients: List<String> = emptyList(),
    val disallowedClients: List<String> = emptyList(),
    val blockedHosts: List<String> = emptyList()
) {
    fun normalized(): AghAccessList = copy(
        allowedClients = allowedClients.map(String::trim).filter(String::isNotBlank).distinct(),
        disallowedClients = disallowedClients.map(String::trim).filter(String::isNotBlank).distinct(),
        blockedHosts = blockedHosts.map(String::trim).filter(String::isNotBlank).distinct()
    )
}

data class AghAccessUiState(
    val loading: Boolean = true,
    val applying: Boolean = false,
    val current: AghAccessList? = null,
    val pending: AghAccessList? = null,
    val error: String = ""
) {
    val dirty: Boolean
        get() = current != null && pending != null &&
            current.normalized() != pending.normalized()
}

data class AghBlockedService(
    val id: String,
    val name: String,
    val groupId: String,
    val rulesCount: Int
)

data class AghBlockedServicesSnapshot(
    val services: List<AghBlockedService>,
    val selectedIds: Set<String>,
    val scheduleJson: String
)

data class AghBlockedServicesUiState(
    val loading: Boolean = true,
    val applying: Boolean = false,
    val services: List<AghBlockedService> = emptyList(),
    val currentIds: Set<String> = emptySet(),
    val pendingIds: Set<String> = emptySet(),
    val scheduleJson: String = "",
    val query: String = "",
    val groupId: String = "",
    val error: String = ""
) {
    val dirty: Boolean
        get() = currentIds != pendingIds

    val groups: List<String>
        get() = services.map { it.groupId }.filter(String::isNotBlank).distinct().sorted()
}
