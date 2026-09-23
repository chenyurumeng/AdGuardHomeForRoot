package io.github.chenyurumeng.aghmanager.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.model.BackupModulePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: BackupViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportPayload by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val payload = exportPayload
        exportPayload = null
        if (uri != null && payload != null) {
            scope.launch {
                writeText(context, uri, payload)
                    .onFailure { viewModel.reportError(it.message ?: "备份文件写入失败") }
            }
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                readText(context, uri)
                    .onSuccess(viewModel::importBackup)
                    .onFailure { viewModel.reportError(it.message ?: "备份文件读取失败") }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.exportEvents.collect { payload ->
            exportPayload = payload
            val stamp = Instant.now().toString()
                .replace(":", "")
                .replace("-", "")
                .substringBefore('.')
            createDocument.launch("BoxAghBackup-v1-" + stamp + ".json")
        }
    }

    BackHandler(enabled = !state.restoring, onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = { Text("备份 / 恢复 / 迁移") },
            navigationIcon = {
                IconButton(onClick = onBack, enabled = !state.restoring) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            }
        )

        if (state.exporting || state.importing || state.restoring) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("BoxAghBackup v1", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "普通备份不包含 AGH 明文密码、Android Keystore、TLS 私钥或 Mihomo 订阅 URL。恢复前会自动生成仅保存在本应用私有目录的完整恢复前快照。",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = viewModel::prepareExport,
                                enabled = !state.exporting && !state.importing && !state.restoring,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (state.exporting) "生成中…" else "导出备份")
                            }
                            OutlinedButton(
                                onClick = { openDocument.launch(arrayOf("application/json", "text/plain")) },
                                enabled = !state.exporting && !state.importing && !state.restoring,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("导入备份")
                            }
                        }
                    }
                }
            }

            if (state.imported) {
                item {
                    Text(
                        "导入预览",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    Text(
                        "备份时间：" + state.sourceCreatedAt.ifBlank { "未知" } +
                            "。仅勾选需要恢复的模块；未变化模块默认不选。",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(state.modules, key = { it.key }) { module ->
                    ModuleRow(
                        module = module,
                        enabled = !state.restoring,
                        onToggle = { viewModel.toggleModule(module.key, it) }
                    )
                    HorizontalDivider()
                }

                item {
                    Button(
                        onClick = { confirmRestore = true },
                        enabled = !state.restoring && state.modules.any { it.selected },
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Text(if (state.restoring) "恢复中…" else "恢复所选模块")
                    }
                }
            }

            if (state.lastSnapshotPath.isNotBlank()) {
                item {
                    Text(
                        "最近恢复前快照保存在应用私有目录：" + state.lastSnapshotPath,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.error.isNotBlank()) {
                item {
                    Text(
                        state.error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { if (!state.restoring) confirmRestore = false },
            title = { Text("确认恢复所选模块？") },
            text = {
                Text(
                    "恢复会修改 Box / AGH 当前配置。开始前会先创建本机恢复前快照；若中途失败，会对本次已成功写入的模块执行回滚。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        viewModel.restoreSelected()
                    },
                    enabled = !state.restoring
                ) { Text("确认恢复") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmRestore = false },
                    enabled = !state.restoring
                ) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ModuleRow(
    module: BackupModulePreview,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { onToggle(!module.selected) },
        headlineContent = { Text(module.title) },
        supportingContent = {
            Column {
                Text(if (module.changed) "当前状态与备份不同" else "与当前状态一致")
                if (module.note.isNotBlank()) {
                    Text(
                        module.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        trailingContent = {
            Checkbox(
                checked = module.selected,
                onCheckedChange = null,
                enabled = enabled
            )
        }
    )
}

private suspend fun readText(context: Context, uri: Uri): Result<String> =
    withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 2 * 1024 * 1024) { "备份文件不能超过 2 MiB" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: throw IllegalStateException("无法读取所选备份文件")
            bytes.toString(Charsets.UTF_8)
        }
    }

private suspend fun writeText(context: Context, uri: Uri, value: String): Result<Unit> =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(value.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("无法写入目标文件")
        }
    }
