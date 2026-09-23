package io.github.chenyurumeng.aghmanager.model

enum class HealthCheckStatus(val label: String) {
    HEALTHY("正常"),
    DEGRADED("降级"),
    MISMATCH("配置不一致"),
    STOPPED("已停止"),
    ERROR("异常"),
    UNKNOWN("未知")
}

enum class HealthRepairAction(val label: String) {
    REAPPLY_APP_RULES("重新应用 Box 应用规则"),
    RESTART_DOMESTIC("重启 Domestic AGH"),
    RESTART_FOREIGN("重启 Foreign AGH")
}

data class HealthCheckItem(
    val id: String,
    val title: String,
    val status: HealthCheckStatus,
    val detail: String,
    val repair: HealthRepairAction? = null
)

data class HealthSnapshot(
    val generatedAt: String = "",
    val items: List<HealthCheckItem> = emptyList()
) {
    val mismatchCount: Int
        get() = items.count { it.status == HealthCheckStatus.MISMATCH }

    val errorCount: Int
        get() = items.count { it.status == HealthCheckStatus.ERROR }

    val healthyCount: Int
        get() = items.count { it.status == HealthCheckStatus.HEALTHY }
}

data class HealthCenterUiState(
    val loading: Boolean = true,
    val repairing: HealthRepairAction? = null,
    val snapshot: HealthSnapshot? = null,
    val error: String = ""
)
