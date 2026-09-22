package io.github.chenyurumeng.aghmanager.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.chenyurumeng.aghmanager.model.HealthState
import io.github.chenyurumeng.aghmanager.model.SystemState
import io.github.chenyurumeng.aghmanager.ui.theme.StatusDegraded
import io.github.chenyurumeng.aghmanager.ui.theme.StatusError
import io.github.chenyurumeng.aghmanager.ui.theme.StatusHealthy
import io.github.chenyurumeng.aghmanager.ui.theme.StatusStopped
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    contentPadding: PaddingValues,
    onOpenLegacyManager: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val restarting by viewModel.restarting.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    var confirmRestart by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.refreshNow()
            val interval = viewModel.refreshIntervalMs()
            if (interval <= 0L) {
                awaitCancellation()
            } else {
                while (true) {
                    delay(interval)
                    viewModel.refreshNow()
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            HealthSummary(state)
        }

        item { SectionHeader("系统状态") }
        item {
            ServiceRow(
                title = if (state.box.coreName.isBlank()) "Box" else state.box.coreName,
                detail = "PID " + state.box.pid.ifBlank { "-" } +
                    " · " + state.box.proxyMode.ifBlank { "?" } +
                    " · " + state.box.networkMode.ifBlank { "?" },
                running = state.box.running,
                stoppedText = if (state.box.userStopped) "主动停止" else "已停止"
            )
        }
        item { Divider() }
        item {
            ServiceRow(
                title = "Domestic AGH",
                detail = "DNS :5591 · Web :3000" +
                    if (state.domestic.pid.isBlank()) "" else " · PID " + state.domestic.pid,
                running = state.domestic.running
            )
        }
        item { Divider() }
        item {
            ServiceRow(
                title = "Foreign AGH",
                detail = "DNS :5592 · Web :3001" +
                    if (state.foreign.pid.isBlank()) "" else " · PID " + state.foreign.pid,
                running = state.foreign.running
            )
        }

        item { SectionHeader("DNS 路由") }
        item {
            SettingRow(
                title = "代理模式",
                value = state.box.proxyMode.ifBlank { "Checking…" }
            )
        }
        item { Divider() }
        item {
            val selectedLabel = if (state.blacklistMode) "黑名单应用" else "白名单应用"
            val selectedTarget = if (state.blacklistMode) {
                state.domesticDnsTarget()
            } else {
                state.foreignDnsTarget()
            }
            SettingRow(selectedLabel, selectedTarget)
        }
        item { Divider() }
        item {
            val otherTarget = if (state.blacklistMode) {
                state.foreignDnsTarget()
            } else {
                state.domesticDnsTarget()
            }
            SettingRow("其它应用", otherTarget)
        }
        item { Divider() }
        item {
            SettingRow(
                "Mihomo DNS",
                if (state.dns.port1053) ":1053 listening" else ":1053 down"
            )
        }

        item { SectionHeader("监听与控制") }
        item {
            SettingRow(
                "监听端口",
                listOf(
                    "5591 " + if (state.dns.port5591) "UP" else "DOWN",
                    "5592 " + if (state.dns.port5592) "UP" else "DOWN",
                    "1053 " + if (state.dns.port1053) "UP" else "DOWN",
                    "9090 " + if (state.dns.port9090) "UP" else "DOWN"
                ).joinToString(" · ")
            )
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { confirmRestart = true },
                    enabled = !restarting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (restarting) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    }
                    Text(if (restarting) "正在重启…" else "重启网络服务")
                }

                OutlinedButton(
                    onClick = onOpenLegacyManager,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("打开 v0.4.x 旧版控制台")
                }
            }
        }
    }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("重启网络服务") },
            text = { Text("将按 Box stop → AGH restart → Box start 的既有顺序执行。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestart = false
                        viewModel.restartNetwork()
                    }
                ) {
                    Text("重启")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestart = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun HealthSummary(state: SystemState) {
    val color = when (state.health) {
        HealthState.HEALTHY -> StatusHealthy
        HealthState.DEGRADED -> StatusDegraded
        HealthState.FAIL_CLOSED, HealthState.ERROR -> StatusError
        HealthState.STOPPED -> StatusStopped
        HealthState.CHECKING -> MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = state.health.name.replace('_', '-'),
                color = color,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = state.healthDetail,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Root " + if (state.rootAvailable) "OK" else "?" +
                    " · Box module " + if (state.boxModuleReady) "READY" else "?" +
                    " · AGH module " + if (state.aghModuleReady) "READY" else "?",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ServiceRow(
    title: String,
    detail: String,
    running: Boolean,
    stoppedText: String = "已停止"
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        trailingContent = {
            Text(
                text = if (running) "● 运行中" else "● " + stoppedText,
                color = if (running) StatusHealthy else StatusError,
                style = MaterialTheme.typography.labelMedium
            )
        }
    )
}

@Composable
private fun SettingRow(title: String, value: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
