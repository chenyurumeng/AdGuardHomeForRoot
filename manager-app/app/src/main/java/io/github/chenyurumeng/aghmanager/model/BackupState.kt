package io.github.chenyurumeng.aghmanager.model

object BackupModuleKeys {
    const val BOX_SETTINGS = "box.settings"
    const val BOX_APPS = "box.apps"
    const val BOX_SUBSCRIPTIONS = "box.subscriptions"

    fun agh(instance: AghInstance, module: String): String =
        "agh." + instance.key + "." + module

    const val STRUCTURAL = "structural"
    const val FILTERING = "filtering"
    const val CLIENTS = "clients"
    const val REWRITES = "rewrites"
    const val ACCESS = "access"
    const val BLOCKED = "blocked_services"
    const val STATISTICS = "statistics"
}

data class BackupModulePreview(
    val key: String,
    val title: String,
    val changed: Boolean,
    val selected: Boolean = true,
    val note: String = ""
)

data class BackupPreview(
    val createdAt: String,
    val modules: List<BackupModulePreview>
)

data class BackupRestoreResult(
    val preRestoreSnapshotPath: String,
    val warnings: List<String> = emptyList()
)

data class BackupUiState(
    val exporting: Boolean = false,
    val importing: Boolean = false,
    val restoring: Boolean = false,
    val imported: Boolean = false,
    val sourceCreatedAt: String = "",
    val modules: List<BackupModulePreview> = emptyList(),
    val error: String = "",
    val lastSnapshotPath: String = ""
)
