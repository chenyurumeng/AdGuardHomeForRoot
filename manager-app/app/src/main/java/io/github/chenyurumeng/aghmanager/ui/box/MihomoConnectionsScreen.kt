package io.github.chenyurumeng.aghmanager.ui.box

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.chenyurumeng.aghmanager.model.MihomoConnection
import io.github.chenyurumeng.aghmanager.model.MihomoRuntimeUiState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MihomoConnectionsScreen(
    viewModel: MihomoConnectionsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    var confirmCloseAll by remember { mutableStateOf(false) }

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
            title = { Text("活动连接") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            }
        )

        RuntimeSummary(state)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { confirmCloseAll = true },
                enabled = state.connections.isNotEmpty() && state.busyAction == null,
                modifier = Modifier.weight(1f)
            ) {
                Text("关闭全部连接")
            }
            Button(
                onClick = { /* 轮询会自动刷新 */ },
                enabled = false,
                modifier = Modifier.weight(1f)
            ) {
                Text("2 秒实时刷新")
            }
        }

        if (state.error.isNotBlank()) {
            Text(
                state.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f)
        ) {
            items(
                items = state.connections,
                key = { it.id.ifBlank { it.destinationLabel + it.start } }
            ) { connection ->
                ConnectionRow(
                    connection = connection,
                    enabled = state.busyAction == null,
                    onClose = { viewModel.closeConnection(connection.id) }
                )
                HorizontalDivider()
            }

            if (!state.loading && state.connections.isEmpty()) {
                item {
                    Text(
                        "当前没有活动连接。",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (confirmCloseAll) {
        AlertDialog(
            onDismissRequest = { confirmCloseAll = false },
            title = { Text("关闭全部活动连接？") },
            text = { Text("现有连接会被立即断开，应用可能自动重新建立连接。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCloseAll = false
                        viewModel.closeAll()
                    }
                ) { Text("全部关闭") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCloseAll = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun RuntimeSummary(state: MihomoRuntimeUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                state.connections.size.toString() + " 个活动连接",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "↓ " + formatRate(state.downloadBps) +
                    "   ↑ " + formatRate(state.uploadBps),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "总计 ↓ " + formatBytes(state.downloadTotal) +
                    " · ↑ " + formatBytes(state.uploadTotal),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Mihomo RSS " + formatKb(state.process.rssKb) +
                    " · Swap " + formatKb(state.process.swapKb) +
                    " · CPU " + String.format("%.1f%%", state.process.cpuPercent),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.busyAction?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ConnectionRow(
    connection: MihomoConnection,
    enabled: Boolean,
    onClose: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                connection.destinationLabel,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                val process = connection.process.ifBlank {
                    connection.processPath.substringAfterLast('/').ifBlank {
                        if (connection.uid.isBlank()) "未知进程" else "UID " + connection.uid
                    }
                }
                Text(
                    process + " · " + connection.network.uppercase(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(
                        connection.rule +
                            if (connection.rulePayload.isBlank()) "" else " (" + connection.rulePayload + ")",
                        connection.chains.joinToString(" → ")
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "↓ " + formatBytes(connection.download) +
                        " · ↑ " + formatBytes(connection.upload) +
                        if (connection.sourceIp.isBlank()) "" else
                            " · " + connection.sourceIp + ":" + connection.sourcePort,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        trailingContent = {
            TextButton(
                onClick = onClose,
                enabled = enabled && connection.id.isNotBlank()
            ) {
                Text("关闭")
            }
        }
    )
}

private fun formatRate(bytesPerSecond: Long): String =
    formatBytes(bytesPerSecond) + "/s"

private fun formatKb(kb: Long): String =
    formatBytes(kb * 1024L)

private fun formatBytes(bytes: Long): String {
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
