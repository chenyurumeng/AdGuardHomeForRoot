package io.github.chenyurumeng.aghmanager.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.model.HealthCheckItem
import io.github.chenyurumeng.aghmanager.model.HealthCheckStatus
import io.github.chenyurumeng.aghmanager.model.HealthRepairAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthCenterScreen(
    viewModel: HealthCenterViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingRepair by remember { mutableStateOf<HealthRepairAction?>(null) }

    BackHandler(enabled = state.repairing == null, onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = { Text("健康中心") },
            navigationIcon = {
                IconButton(onClick = onBack, enabled = state.repairing == null) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(
                    onClick = viewModel::copyReport,
                    enabled = state.snapshot != null && state.repairing == null
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制脱敏报告")
                }
                IconButton(
                    onClick = viewModel::refresh,
                    enabled = !state.loading && state.repairing == null
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "重新检查")
                }
            }
        )

        if (state.loading || state.repairing != null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            val snapshot = state.snapshot
            if (snapshot != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("RC18 Health Center", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "正常 " + snapshot.healthyCount +
                                    " · 配置不一致 " + snapshot.mismatchCount +
                                    " · 异常 " + snapshot.errorCount,
                                modifier = Modifier.padding(top = 6.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "检查 Root、Box、Mihomo、双 AGH、DNS Chain、iptables、IPv6、Controller 与配置一致性。报告只使用结构化结果，不采集密码、URL、Token、私钥或 Keystore 数据。",
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(snapshot.items, key = { it.id }) { item ->
                    HealthRow(
                        item = item,
                        busy = state.repairing != null,
                        onRepair = { pendingRepair = it }
                    )
                    HorizontalDivider()
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

    val action = pendingRepair
    if (action != null) {
        AlertDialog(
            onDismissRequest = { if (state.repairing == null) pendingRepair = null },
            title = { Text("确认执行修复？") },
            text = {
                Text(
                    action.label +
                        " 会改变当前运行状态。健康中心只执行这个单一动作，不会自动改写其它配置。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRepair = null
                        viewModel.repair(action)
                    },
                    enabled = state.repairing == null
                ) { Text("确认执行") }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRepair = null },
                    enabled = state.repairing == null
                ) { Text("取消") }
            }
        )
    }
}

@Composable
private fun HealthRow(
    item: HealthCheckItem,
    busy: Boolean,
    onRepair: (HealthRepairAction) -> Unit
) {
    ListItem(
        headlineContent = {
            Row {
                Text(item.title, modifier = Modifier.weight(1f))
                Text(
                    item.status.label,
                    color = statusColor(item.status),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        supportingContent = {
            Column {
                Text(item.detail)
                val repair = item.repair
                if (repair != null) {
                    Button(
                        onClick = { onRepair(repair) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(repair.label)
                    }
                }
            }
        }
    )
}

@Composable
private fun statusColor(status: HealthCheckStatus) =
    when (status) {
        HealthCheckStatus.HEALTHY -> MaterialTheme.colorScheme.primary
        HealthCheckStatus.DEGRADED -> MaterialTheme.colorScheme.tertiary
        HealthCheckStatus.MISMATCH,
        HealthCheckStatus.ERROR -> MaterialTheme.colorScheme.error
        HealthCheckStatus.STOPPED,
        HealthCheckStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
