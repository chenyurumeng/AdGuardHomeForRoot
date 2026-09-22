package io.github.chenyurumeng.aghmanager.ui.box

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.model.BoxConfigUiState
import io.github.chenyurumeng.aghmanager.model.DnsHijackMode
import io.github.chenyurumeng.aghmanager.model.NetworkMode
import io.github.chenyurumeng.aghmanager.model.RoutingMode
import io.github.chenyurumeng.aghmanager.model.SystemState
import io.github.chenyurumeng.aghmanager.ui.theme.StatusError
import io.github.chenyurumeng.aghmanager.ui.theme.StatusHealthy
import io.github.chenyurumeng.aghmanager.ui.theme.StatusStopped

private enum class ConfigSelector {
    PROXY_MODE,
    NETWORK_MODE,
    DNS_HIJACK_MODE
}

@Composable
fun BoxScreen(
    viewModel: BoxViewModel,
    contentPadding: PaddingValues,
    onManageApps: () -> Unit,
    onMihomoControl: () -> Unit,
    onConnections: () -> Unit,
    onSubscriptions: () -> Unit,
    onOpenDashboard: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val config by viewModel.configState.collectAsStateWithLifecycle()
    var selector by remember { mutableStateOf<ConfigSelector?>(null) }
    var confirmRestartApply by remember { mutableStateOf(false) }

    val controlsEnabled = !busy && !config.applying

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item { BoxStatusHeader(state) }

            item { SectionHeader("基础配置") }
            item {
                ChoiceRow(
                    title = "代理模式",
                    current = config.current.proxyMode.label,
                    pending = config.pending.proxyMode.label,
                    enabled = controlsEnabled && config.loaded,
                    onClick = { selector = ConfigSelector.PROXY_MODE }
                )
            }
            item { Divider() }
            item {
                ChoiceRow(
                    title = "网络模式",
                    current = config.current.networkMode.label,
                    pending = config.pending.networkMode.label,
                    enabled = controlsEnabled && config.loaded,
                    onClick = { selector = ConfigSelector.NETWORK_MODE }
                )
            }
            item { Divider() }
            item {
                ChoiceRow(
                    title = "DNS 劫持",
                    current = config.current.dnsHijackMode.label,
                    pending = config.pending.dnsHijackMode.label,
                    enabled = controlsEnabled && config.loaded,
                    onClick = { selector = ConfigSelector.DNS_HIJACK_MODE }
                )
            }
            item { Divider() }
            item {
                ToggleRow("IPv6", config.current.ipv6, config.pending.ipv6, controlsEnabled && config.loaded, viewModel::setIpv6)
            }
            item { Divider() }
            item {
                ToggleRow("Proxy TCP", config.current.proxyTcp, config.pending.proxyTcp, controlsEnabled && config.loaded, viewModel::setProxyTcp)
            }
            item { Divider() }
            item {
                ToggleRow("Proxy UDP", config.current.proxyUdp, config.pending.proxyUdp, controlsEnabled && config.loaded, viewModel::setProxyUdp)
            }
            item { Divider() }
            item {
                ToggleRow("DNS Hijack TCP", config.current.dnsHijackTcp, config.pending.dnsHijackTcp, controlsEnabled && config.loaded, viewModel::setDnsHijackTcp)
            }
            item { Divider() }
            item {
                ToggleRow("DNS Hijack UDP", config.current.dnsHijackUdp, config.pending.dnsHijackUdp, controlsEnabled && config.loaded, viewModel::setDnsHijackUdp)
            }
            item { Divider() }
            item {
                ToggleRow("QUIC", config.current.quic, config.pending.quic, controlsEnabled && config.loaded, viewModel::setQuic)
            }
            item { Divider() }
            item {
                ToggleRow(
                    "Mihomo DNS Forward",
                    config.current.mihomoDnsForward,
                    config.pending.mihomoDnsForward,
                    controlsEnabled && config.loaded,
                    viewModel::setMihomoDnsForward
                )
            }

            if (config.error.isNotBlank()) {
                item {
                    Text(
                        config.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item { SectionHeader("运行状态") }
            item { InfoRow("Core", state.box.coreName.ifBlank { "-" }) }
            item { Divider() }
            item { InfoRow("PID", state.box.pid.ifBlank { "-" }) }
            item { Divider() }
            item { InfoRow("Controller", state.box.controller.ifBlank { "127.0.0.1:9090" }) }
            item { Divider() }
            item { InfoRow("Mihomo DNS", if (state.dns.port1053) ":1053 listening" else ":1053 down") }
            item { Divider() }
            item { InfoRow("Stop Guard", if (state.box.userStopped) "present" else "clear") }

            item { SectionHeader("应用分流") }
            item {
                ListItem(
                    modifier = Modifier.clickable(enabled = controlsEnabled, onClick = onManageApps),
                    headlineContent = { Text("应用分流管理") },
                    supportingContent = {
                        Text(
                            when (config.pending.proxyMode) {
                                RoutingMode.CORE -> "Core：package.list 暂不参与代理选择"
                                RoutingMode.WHITELIST -> "Whitelist：选中应用走代理"
                                RoutingMode.BLACKLIST -> "Blacklist：选中应用直连"
                            }
                        )
                    },
                    trailingContent = {
                        androidx.compose.material3.Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "管理应用分流"
                        )
                    }
                )
            }

            item { SectionHeader("Mihomo") }
            item {
                ListItem(
                    headlineContent = { Text("Mihomo 控制中心") },
                    supportingContent = { Text("策略组、节点、测速、Provider 与 DNS Cache") },
                    modifier = Modifier.clickable(onClick = onMihomoControl),
                    trailingContent = {
                        androidx.compose.material3.Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "打开 Mihomo 控制中心"
                        )
                    }
                )
            }
            item { Divider() }
            item {
                ListItem(
                    headlineContent = { Text("活动连接与实时监控") },
                    supportingContent = { Text("搜索、筛选、详情、按应用断开、实时流量与内存") },
                    modifier = Modifier.clickable(onClick = onConnections),
                    trailingContent = {
                        androidx.compose.material3.Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "打开活动连接"
                        )
                    }
                )
            }
            item { Divider() }
            item {
                ListItem(
                    headlineContent = { Text("订阅管理") },
                    supportingContent = { Text("添加 / 编辑 / 启停 / 删除 Proxy Provider") },
                    modifier = Modifier.clickable(onClick = onSubscriptions),
                    trailingContent = {
                        androidx.compose.material3.Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "打开订阅管理"
                        )
                    }
                )
            }

            item { SectionHeader("Mihomo Dashboard") }
            item {
                val url = dashboardUrl(state)
                ListItem(
                    headlineContent = { Text("打开完整 Dashboard") },
                    supportingContent = { Text(url) },
                    modifier = Modifier.clickable(enabled = controlsEnabled) {
                        onOpenDashboard(url)
                    },
                    trailingContent = {
                        androidx.compose.material3.Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "打开 Mihomo Dashboard"
                        )
                    }
                )
            }

            item { SectionHeader("Box 控制") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = viewModel::start,
                        enabled = controlsEnabled,
                        modifier = Modifier.weight(1f)
                    ) { Text("启动") }
                    OutlinedButton(
                        onClick = viewModel::restart,
                        enabled = controlsEnabled,
                        modifier = Modifier.weight(1f)
                    ) { Text("重启") }
                    OutlinedButton(
                        onClick = viewModel::stop,
                        enabled = controlsEnabled,
                        modifier = Modifier.weight(1f)
                    ) { Text("停止") }
                }
            }
            item {
                Text(
                    "服务控制继续通过 box.service；基础配置先暂存，点击“应用”后才写入 settings.ini。",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (config.dirty || config.applying) {
            PendingApplyBar(
                config = config,
                boxRunning = state.box.running,
                onDiscard = viewModel::discardConfig,
                onApply = {
                    if (requiresRestart(config) && state.box.running) {
                        confirmRestartApply = true
                    } else {
                        viewModel.applyConfig()
                    }
                }
            )
        }
    }

    when (selector) {
        ConfigSelector.PROXY_MODE -> OptionDialog(
            title = "代理模式",
            options = RoutingMode.entries.map { it.label to { viewModel.setProxyMode(it) } },
            onDismiss = { selector = null }
        )
        ConfigSelector.NETWORK_MODE -> OptionDialog(
            title = "网络模式",
            options = NetworkMode.entries.map { it.label to { viewModel.setNetworkMode(it) } },
            onDismiss = { selector = null }
        )
        ConfigSelector.DNS_HIJACK_MODE -> OptionDialog(
            title = "DNS 劫持模式",
            options = DnsHijackMode.entries.map { it.label to { viewModel.setDnsHijackMode(it) } },
            onDismiss = { selector = null }
        )
        null -> Unit
    }

    if (confirmRestartApply) {
        AlertDialog(
            onDismissRequest = { confirmRestartApply = false },
            title = { Text("应用并重启 Box？") },
            text = {
                Text(
                    "这些修改涉及网络、DNS 或协议开关。将事务写入配置并重启一次 Box；若重启失败会尝试恢复原配置。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestartApply = false
                        viewModel.applyConfig()
                    }
                ) { Text("应用并重启") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestartApply = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun BoxStatusHeader(state: SystemState) {
    val running = state.box.running
    val color = if (running) StatusHealthy else if (state.box.userStopped) StatusStopped else StatusError

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                "Box / " + state.box.coreName.ifBlank { "core" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (running) "Running" else if (state.box.userStopped) "Stopped · user_stopped" else "Stopped",
                modifier = Modifier.padding(top = 4.dp),
                color = color,
                style = MaterialTheme.typography.labelLarge
            )
            if (state.box.version.isNotBlank()) {
                Text(
                    state.box.version,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    current: String,
    pending: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val changed = current != pending
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                if (changed) "当前 $current → 待应用 $pending" else pending,
                color = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            androidx.compose.material3.Icon(Icons.Default.ChevronRight, contentDescription = "修改$title")
        }
    )
}

@Composable
private fun ToggleRow(
    title: String,
    current: Boolean,
    pending: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    val changed = current != pending
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { onChange(!pending) },
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                if (changed) {
                    "当前 " + onOff(current) + " → 待应用 " + onOff(pending)
                } else {
                    onOff(pending)
                },
                color = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(checked = pending, onCheckedChange = null, enabled = enabled)
        }
    )
}

@Composable
private fun PendingApplyBar(
    config: BoxConfigUiState,
    boxRunning: Boolean,
    onDiscard: () -> Unit,
    onApply: () -> Unit
) {
    Surface(tonalElevation = 4.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                if (config.applying) {
                    "正在应用配置…"
                } else {
                    config.dirtyKeys.size.toString() + " 项待应用" +
                        if (!boxRunning) " · Box 已停止，仅保存配置" else ""
                },
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDiscard,
                    enabled = !config.applying,
                    modifier = Modifier.weight(1f)
                ) { Text("放弃") }
                Button(
                    onClick = onApply,
                    enabled = config.dirty && !config.applying,
                    modifier = Modifier.weight(1f)
                ) { Text(if (config.applying) "应用中…" else "应用") }
            }
        }
    }
}

@Composable
private fun OptionDialog(
    title: String,
    options: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, action) ->
                    TextButton(
                        onClick = {
                            action()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(label) }
                }
            }
        },
        confirmButton = {}
    )
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

private fun onOff(value: Boolean): String = if (value) "开启" else "关闭"

private fun requiresRestart(config: BoxConfigUiState): Boolean =
    config.dirtyKeys.any { it != "proxy_mode" }

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
