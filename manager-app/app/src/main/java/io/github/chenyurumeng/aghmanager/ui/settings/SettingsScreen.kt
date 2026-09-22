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
fun SettingsScreen(viewModel: SettingsViewModel, contentPadding: PaddingValues) {
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
                    Text("生成 Box、Mihomo、AGH、监听端口、user_stopped 与 NAT_DNS_HIJACK 的统一快照。")
                    Button(
                        onClick = viewModel::generateDiagnostic,
                        enabled = !generating,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text(if (generating) "正在生成…" else "复制完整诊断报告")
                    }
                }
            }
        }

        item { SectionHeader("关于") }
        item { InfoRow("版本", "0.5.0-rc5") }
        item { HorizontalDivider() }
        item { InfoRow("Box 后端", StatusRepository.BOX_SERVICE) }
        item { HorizontalDivider() }
        item { InfoRow("AGH 后端", StatusRepository.AGH_TOOL) }
        item { HorizontalDivider() }
        item { InfoRow("Mihomo Dashboard", dashboardUrl(state)) }
        item { HorizontalDivider() }
        item { InfoRow("Domestic", "DNS 5591 / Web 3000") }
        item { HorizontalDivider() }
        item { InfoRow("Foreign", "DNS 5592 / Web 3001") }
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
