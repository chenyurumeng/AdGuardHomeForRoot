package io.github.chenyurumeng.aghmanager.model

enum class LogSource(val label: String) {
    BOX_CORE("Box Core"),
    BOX_SERVICE("Box Service"),
    BOX_TOOL("Box Tool"),
    DOMESTIC("Domestic"),
    FOREIGN("Foreign"),
    AGH_MODULE("AGH Module")
}

data class LogUiState(
    val source: LogSource = LogSource.BOX_CORE,
    val loading: Boolean = false,
    val content: String = "正在读取日志…",
    val error: Boolean = false
)
