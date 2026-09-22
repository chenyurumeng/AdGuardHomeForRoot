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
    val busyAction: String? = null,
    val error: String = ""
)
