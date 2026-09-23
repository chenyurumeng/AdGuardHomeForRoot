package io.github.chenyurumeng.aghmanager.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.data.StatusRepository
import io.github.chenyurumeng.aghmanager.model.SystemState

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues,
    onOpenBackup: () -> Unit,
    onOpenHealthCenter: () -> Unit
) {
    val refreshInterval by viewModel.refreshInterval.collectAsStateWithLifecycle()
    val generating by viewModel.generatingDiagnostic.collectAsStateWithLifecycle()
    val state by viewModel.systemState.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = contentPadding) {
        item { SectionHeader("自动刷新") }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("选择整套网络栈状态刷新间隔")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            3_000L to "3 秒",
                            5_000L to "5 秒",
                            10_000L to "10 秒",
                            0L to "关闭"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = refreshInterval == value,
                                onClick = { viewModel.setRefreshInterval(value) },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        "设置会立即作用于首页生命周期轮询；切到后台时仍会停止 Root 查询。",
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { SectionHeader("诊断") }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("健康中心提供结构化一致性检查；原始诊断报告保留用于排障。")
                    Button(
                        onClick = onOpenHealthCenter,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text("打开健康中心")
                    }
                    Button(
                        onClick = viewModel::generateDiagnostic,
                        enabled = !generating,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(if (generating) "正在生成…" else "复制原始诊断报告")
                    }
                }
            }
        }

        item { SectionHeader("数据管理") }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("版本化备份、导入预览、分模块恢复与恢复前快照。")
                    Button(
                        onClick = onOpenBackup,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text("备份 / 恢复 / 迁移")
                    }
                }
            }
        }

        item { SectionHeader("关于") }
        item { InfoRow("版本", "0.5.0-rc19") }
        item { HorizontalDivider() }
        item { InfoRow("Box 后端", StatusRepository.BOX_SERVICE) }
        item { HorizontalDivider() }
        item { InfoRow("AGH 后端", StatusRepository.AGH_TOOL) }
        item { HorizontalDivider() }
        item { InfoRow("Mihomo Dashboard", dashboardUrl(state)) }
        item { HorizontalDivider() }
        item {
            InfoRow(
                "Domestic",
                "DNS " + state.domestic.dnsPort + " / Web " + state.domestic.webPort
            )
        }
        item { HorizontalDivider() }
        item {
            InfoRow(
                "Foreign",
                "DNS " + state.foreign.dnsPort + " / Web " + state.foreign.webPort
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    )
}

private fun dashboardUrl(state: SystemState): String {
    val controller = state.box.controller.trim()
    val base = when {
        controller.isEmpty() -> "http://127.0.0.1:9090"
        controller.startsWith("http://") || controller.startsWith("https://") -> controller
        controller.startsWith(":") -> "http://127.0.0.1" + controller
        controller.startsWith("0.0.0.0:") -> "http://127.0.0.1:" + controller.substringAfter(':')
        controller.startsWith("*:") -> "http://127.0.0.1:" + controller.substringAfter(':')
        else -> "http://" + controller
    }
    return base.trimEnd('/') + "/ui/"
}
