package io.github.chenyurumeng.aghmanager.ui.box

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.model.SystemState
import io.github.chenyurumeng.aghmanager.ui.theme.StatusError
import io.github.chenyurumeng.aghmanager.ui.theme.StatusHealthy
import io.github.chenyurumeng.aghmanager.ui.theme.StatusStopped

@Composable
fun BoxScreen(
    viewModel: BoxViewModel,
    contentPadding: PaddingValues,
    onManageApps: () -> Unit,
    onOpenDashboard: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LazyColumn(contentPadding = contentPadding) {
        item {
            BoxStatusHeader(state)
        }

        item { SectionHeader("Box 状态") }
        item { InfoRow("Core", state.box.coreName.ifBlank { "-" }) }
        item { Divider() }
        item { InfoRow("PID", state.box.pid.ifBlank { "-" }) }
        item { Divider() }
        item { InfoRow("Proxy Mode", state.box.proxyMode.ifBlank { "-" }) }
        item { Divider() }
        item { InfoRow("Network Mode", state.box.networkMode.ifBlank { "-" }) }
        item { Divider() }
        item { InfoRow("DNS Hijack", state.box.dnsHijackMode.ifBlank { "-" }) }
        item { Divider() }
        item { InfoRow("IPv6", state.box.ipv6.ifBlank { "-" }) }
        item { Divider() }
        item {
            InfoRow(
                "Mihomo DNS",
                if (state.dns.port1053) ":1053 listening" else ":1053 down"
            )
        }
        item { Divider() }
        item {
            InfoRow(
                "Controller",
                state.box.controller.ifBlank { "127.0.0.1:9090" }
            )
        }
        item { Divider() }
        item {
            InfoRow(
                "Stop Guard",
                if (state.box.userStopped) "present" else "clear"
            )
        }

        item { SectionHeader("应用分流") }
        item {
            ListItem(
                modifier = Modifier.clickable(onClick = onManageApps),
                headlineContent = { Text("Whitelist / Blacklist") },
                supportingContent = {
                    Text(
                        if (state.blacklistMode) {
                            "Blacklist：选中应用直连 / Domestic 5591"
                        } else {
                            "Whitelist：选中应用走代理 / Foreign 5592"
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
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("启动")
                }
                OutlinedButton(
                    onClick = viewModel::restart,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("重启")
                }
                OutlinedButton(
                    onClick = viewModel::stop,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("停止")
                }
            }
        }
        item {
            Text(
                "所有控制继续通过 box.service；App 不直接调用 box.iptables，以保留 user_stopped 防竞态。",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item { SectionHeader("Mihomo") }
        item {
            ListItem(
                headlineContent = { Text("Dashboard") },
                supportingContent = { Text(dashboardUrl(state)) },
                modifier = Modifier.clickable {
                    onOpenDashboard(dashboardUrl(state))
                },
                trailingContent = {
                    androidx.compose.material3.Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "打开 Mihomo Dashboard"
                    )
                }
            )
        }
    }
}

@Composable
private fun BoxStatusHeader(state: SystemState) {
    val running = state.box.running
    val color = if (running) StatusHealthy else if (state.box.userStopped) StatusStopped else StatusError

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
        supportingContent = {
            Text(
                value,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

private fun dashboardUrl(state: SystemState): String {
    val controller = state.box.controller.trim()
    if (controller.isEmpty()) return "http://127.0.0.1:9090"
    if (controller.startsWith("http://") || controller.startsWith("https://")) return controller
    if (controller.startsWith(":")) return "http://127.0.0.1" + controller
    if (controller.startsWith("0.0.0.0:")) {
        return "http://127.0.0.1:" + controller.substringAfter(':')
    }
    if (controller.startsWith("*:")) {
        return "http://127.0.0.1:" + controller.substringAfter(':')
    }
    return "http://" + controller
}
