package io.github.chenyurumeng.aghmanager.model

enum class RoutingMode(val raw: String, val label: String) {
    CORE("core", "Core"),
    WHITELIST("whitelist", "Whitelist"),
    BLACKLIST("blacklist", "Blacklist");

    companion object {
        fun fromRaw(value: String?): RoutingMode =
            when {
                value.equals("blacklist", true) || value.equals("black", true) -> BLACKLIST
                value.equals("whitelist", true) || value.equals("white", true) -> WHITELIST
                else -> CORE
            }
    }
}

enum class AppFilter {
    ALL,
    USER,
    SYSTEM
}

data class AppEntry(
    val userId: Int,
    val packageName: String,
    val label: String,
    val userName: String,
    val system: Boolean
) {
    val key: String
        get() = userId.toString() + ":" + packageName
}

data class AppRoutingCache(
    val apps: List<AppEntry> = emptyList(),
    val mode: RoutingMode = RoutingMode.CORE,
    val selected: Set<String> = emptySet()
)

data class AppSyncResult(
    val ok: Boolean,
    val apps: List<AppEntry> = emptyList(),
    val mode: RoutingMode = RoutingMode.CORE,
    val selected: Set<String> = emptySet(),
    val added: Int = 0,
    val removed: Int = 0,
    val error: String = ""
)

data class AppRoutingUiState(
    val apps: List<AppEntry> = emptyList(),
    val visibleApps: List<AppEntry> = emptyList(),
    val mode: RoutingMode = RoutingMode.CORE,
    val appliedMode: RoutingMode = RoutingMode.CORE,
    val selected: Set<String> = emptySet(),
    val appliedSelected: Set<String> = emptySet(),
    val filter: AppFilter = AppFilter.ALL,
    val query: String = "",
    val cacheLoaded: Boolean = false,
    val syncing: Boolean = false,
    val applying: Boolean = false,
    val statusText: String = "正在载入本地缓存…",
    val statusIsError: Boolean = false
) {
    val dirty: Boolean
        get() = mode != appliedMode || selected != appliedSelected
}
