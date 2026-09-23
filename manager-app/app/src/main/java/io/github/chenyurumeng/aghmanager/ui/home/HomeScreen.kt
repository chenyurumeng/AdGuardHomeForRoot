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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.chenyurumeng.aghmanager.model.HealthState
import io.github.chenyurumeng.aghmanager.model.RoutingMode
import io.github.chenyurumeng.aghmanager.model.SystemState
import io.github.chenyurumeng.aghmanager.ui.theme.StatusDegraded
import io.github.chenyurumeng.aghmanager.ui.theme.StatusError
import io.github.chenyurumeng.aghmanager.ui.theme.StatusHealthy
import io.github.chenyurumeng.aghmanager.ui.theme.StatusStopped
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeScreen(viewModel: HomeViewModel, contentPadding: PaddingValues) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val restarting by viewModel.restarting.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    var confirmRestart by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.refreshNow()
            viewModel.refreshIntervalMs.collectLatest { interval ->
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
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item { HealthSummary(state) }
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
        item { HorizontalDivider() }
        item {
            ServiceRow(
                title = "Domestic AGH",
                detail = "DNS :" + state.domestic.dnsPort + " · Web :" + state.domestic.webPort +
                    if (state.domestic.pid.isBlank()) "" else " · PID " + state.domestic.pid,
                running = state.domestic.running
            )
        }
        item { HorizontalDivider() }
        item {
            ServiceRow(
                title = "Foreign AGH",
                detail = "DNS :" + state.foreign.dnsPort + " · Web :" + state.foreign.webPort +
                    if (state.foreign.pid.isBlank()) "" else " · PID " + state.foreign.pid,
                running = state.foreign.running
            )
        }

        item { SectionHeader("DNS 路由") }
        item { SettingRow("代理模式", state.routingMode.label) }
        item { HorizontalDivider() }

        when (state.routingMode) {
            RoutingMode.CORE -> {
                item {
                    SettingRow(
                        "应用分流",
                        "Core 模式：package.list 不参与代理选择"
                    )
                }
            }
            RoutingMode.WHITELIST -> {
                item { SettingRow("白名单应用", state.foreignDnsTarget()) }
                item { HorizontalDivider() }
                item { SettingRow("其它应用", state.domesticDnsTarget()) }
            }
            RoutingMode.BLACKLIST -> {
                item { SettingRow("黑名单应用", state.domesticDnsTarget()) }
                item { HorizontalDivider() }
                item { SettingRow("其它应用", state.foreignDnsTarget()) }
            }
        }

        item { HorizontalDivider() }
        item { SettingRow("Mihomo DNS", if (state.dns.port1053) ":1053 listening" else ":1053 down") }
        item { SectionHeader("监听与控制") }
        item {
            SettingRow(
                "监听端口",
                listOf(
                    state.domestic.dnsPort.toString() + " " + if (state.dns.port5591) "UP" else "DOWN",
                    state.foreign.dnsPort.toString() + " " + if (state.dns.port5592) "UP" else "DOWN",
                    "1053 " + if (state.dns.port1053) "UP" else "DOWN",
                    state.box.controllerPort.toString() + " " + if (state.dns.port9090) "API" else "DOWN"
                ).joinToString(" · ")
            )
        }
        item {
            Button(
                onClick = { confirmRestart = true },
                enabled = !restarting,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                if (restarting) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 10.dp))
                }
                Text(if (restarting) "正在重启…" else "重启网络服务")
            }
        }
    }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("重启网络服务") },
            text = { Text("将按 Box stop → AGH restart → Box start 的既有顺序执行。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestart = false
                    viewModel.restartNetwork()
                }) { Text("重启") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestart = false }) { Text("取消") }
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                state.health.name.replace('_', '-'),
                color = color,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(state.healthDetail, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
            Text(
                "Root " + if (state.rootAvailable) "OK" else "?" +
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
        title,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ServiceRow(title: String, detail: String, running: Boolean, stoppedText: String = "已停止") {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        trailingContent = {
            Text(
                if (running) "● 运行中" else "● " + stoppedText,
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
        supportingContent = { Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    )
}
