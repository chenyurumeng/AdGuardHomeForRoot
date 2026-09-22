package io.github.chenyurumeng.aghmanager.ui.box

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MihomoConnectionDetailScreen(
    viewModel: MihomoConnectionDetailViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler(onBack = onBack)

    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refreshNow()
                delay(2_000)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = { Text("连接详情") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            }
        )

        when {
            state.loading -> {
                Text(
                    "正在读取连接详情…",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.connection != null -> {
                val connection = state.connection!!
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item { DetailRow("目标", connection.destinationLabel) }
                    item { HorizontalDivider() }
                    item { DetailRow("Host", connection.host.ifBlank { "-" }) }
                    item { HorizontalDivider() }
                    item {
                        DetailRow(
                            "目标 IP",
                            connection.destinationIp.ifBlank { "-" } +
                                if (connection.destinationPort.isBlank()) "" else ":" + connection.destinationPort
                        )
                    }
                    item { HorizontalDivider() }
                    item {
                        DetailRow(
                            "来源",
                            connection.sourceIp.ifBlank { "-" } +
                                if (connection.sourcePort.isBlank()) "" else ":" + connection.sourcePort
                        )
                    }
                    item { HorizontalDivider() }
                    item { DetailRow("网络", connection.network.uppercase().ifBlank { "-" }) }
                    item { HorizontalDivider() }
                    item { DetailRow("应用 / 进程", connection.processLabel) }
                    item { HorizontalDivider() }
                    item { DetailRow("进程路径", connection.processPath.ifBlank { "-" }) }
                    item { HorizontalDivider() }
                    item { DetailRow("UID", connection.uid.ifBlank { "-" }) }
                    item { HorizontalDivider() }
                    item { DetailRow("规则", connection.rule.ifBlank { "-" }) }
                    item { HorizontalDivider() }
                    item { DetailRow("Rule Payload", connection.rulePayload.ifBlank { "-" }) }
                    item { HorizontalDivider() }
                    item { DetailRow("代理链", connection.chains.joinToString(" → ").ifBlank { "-" }) }
                    item { HorizontalDivider() }
                    item { DetailRow("开始时间", connection.start.ifBlank { "-" }) }
                    item { HorizontalDivider() }
                    item {
                        DetailRow(
                            "流量",
                            "↓ " + formatDetailBytes(connection.download) +
                                " · ↑ " + formatDetailBytes(connection.upload)
                        )
                    }
                }

                Button(
                    onClick = { viewModel.close(onBack) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text(if (state.busy) "正在关闭…" else "关闭该连接")
                }
            }
            state.missing -> {
                Text(
                    "该连接已经结束。",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.error.isNotBlank() -> {
                Text(
                    state.error,
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value) }
    )
}

private fun formatDetailBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L).toDouble()
    return when {
        value >= 1024.0 * 1024.0 * 1024.0 ->
            String.format("%.2f GiB", value / (1024.0 * 1024.0 * 1024.0))
        value >= 1024.0 * 1024.0 ->
            String.format("%.2f MiB", value / (1024.0 * 1024.0))
        value >= 1024.0 ->
            String.format("%.1f KiB", value / 1024.0)
        else -> bytes.coerceAtLeast(0L).toString() + " B"
    }
}
