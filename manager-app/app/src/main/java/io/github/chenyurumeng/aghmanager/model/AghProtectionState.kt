package io.github.chenyurumeng.aghmanager.model

data class AghSafeSearchConfig(
    val enabled: Boolean = false,
    val bing: Boolean? = null,
    val duckDuckGo: Boolean? = null,
    val ecosia: Boolean? = null,
    val google: Boolean? = null,
    val pixabay: Boolean? = null,
    val yandex: Boolean? = null,
    val youtube: Boolean? = null
)

data class AghProtectionConfig(
    val safeBrowsingEnabled: Boolean = false,
    val parentalEnabled: Boolean = false,
    val parentalSensitivity: Int? = null,
    val safeSearch: AghSafeSearchConfig = AghSafeSearchConfig()
)

data class AghProtectionUiState(
    val loading: Boolean = false,
    val applying: Boolean = false,
    val current: AghProtectionConfig? = null,
    val pending: AghProtectionConfig? = null,
    val error: String = ""
) {
    val dirty: Boolean
        get() = current != null && pending != null && current != pending
}
