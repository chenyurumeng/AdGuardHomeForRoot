package io.github.chenyurumeng.aghmanager.ui.box

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

private enum class ConnectionFilter(val label: String) {
    ALL("全部"),
    PROXY("代理"),
    DIRECT("直连")
}

private enum class ConnectionSort(val label: String) {
    DEFAULT("默认"),
    TRAFFIC("流量"),
    APP("应用")
}

private data class AppAggregate(
    val label: String,
    val count: Int,
    val upload: Long,
    val download: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MihomoConnectionsScreen(
    viewModel: MihomoConnectionsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onDetails: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()

    var confirmCloseAll by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ConnectionFilter.ALL) }
    var sort by remember { mutableStateOf(ConnectionSort.DEFAULT) }
    var groupByApp by remember { mutableStateOf(false) }

    val filteredConnections = remember(state.connections, query, filter, sort) {
        val needle = query.trim().lowercase()
        val filtered = state.connections.filter { connection ->
            val matchesFilter = when (filter) {
                ConnectionFilter.ALL -> true
                ConnectionFilter.PROXY -> !connection.direct
                ConnectionFilter.DIRECT -> connection.direct
            }
            val matchesQuery = needle.isBlank() ||
                connection.host.lowercase().contains(needle) ||
                connection.destinationIp.lowercase().contains(needle) ||
                connection.processLabel.lowercase().contains(needle) ||
                connection.uid.lowercase().contains(needle) ||
                connection.rule.lowercase().contains(needle) ||
                connection.rulePayload.lowercase().contains(needle) ||
                connection.chains.any { it.lowercase().contains(needle) }

            matchesFilter && matchesQuery
        }

        when (sort) {
            ConnectionSort.DEFAULT -> filtered
            ConnectionSort.TRAFFIC -> filtered.sortedByDescending { it.download + it.upload }
            ConnectionSort.APP -> filtered.sortedWith(
                compareBy<MihomoConnection> { it.processLabel.lowercase() }
                    .thenByDescending { it.download + it.upload }
            )
        }
    }

    val aggregates = remember(filteredConnections) {
        filteredConnections
            .groupBy { it.processLabel }
            .map { (label, values) ->
                AppAggregate(
                    label = label,
                    count = values.size,
                    upload = values.sumOf { it.upload },
                    download = values.sumOf { it.download }
                )
            }
            .sortedWith(
                compareByDescending<AppAggregate> { it.download + it.upload }
                    .thenBy { it.label.lowercase() }
            )
    }

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

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            singleLine = true,
            label = { Text("搜索连接") },
            placeholder = { Text("域名 / IP / 应用 / UID / 规则 / 代理链") }
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ConnectionFilter.entries.forEach { item ->
                OutlinedButton(
                    onClick = { filter = item },
                    enabled = filter != item,
                    modifier = Modifier.weight(1f)
                ) { Text(item.label) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ConnectionSort.entries.forEach { item ->
                OutlinedButton(
                    onClick = { sort = item },
                    enabled = sort != item,
                    modifier = Modifier.weight(1f)
                ) { Text(item.label) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { groupByApp = !groupByApp },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (groupByApp) "显示连接明细" else "按应用聚合")
            }
            OutlinedButton(
                onClick = { confirmCloseAll = true },
                enabled = state.connections.isNotEmpty() && state.busyAction == null,
                modifier = Modifier.weight(1f)
            ) {
                Text("关闭全部")
            }
        }

        Text(
            "显示 " + filteredConnections.size + " / " + state.connections.size +
                " · 2 秒实时刷新",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.error.isNotBlank()) {
            Text(
                state.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            if (groupByApp) {
                items(aggregates, key = { "app:" + it.label }) { aggregate ->
                    AppAggregateRow(
                        aggregate = aggregate,
                        enabled = state.busyAction == null,
                        onClose = { viewModel.closeByProcess(aggregate.label) }
                    )
                    HorizontalDivider()
                }
            } else {
                items(
                    items = filteredConnections,
                    key = { it.id.ifBlank { it.destinationLabel + it.start } }
                ) { connection ->
                    ConnectionRow(
                        connection = connection,
                        enabled = state.busyAction == null,
                        onClose = { viewModel.closeConnection(connection.id) },
                        onDetails = { onDetails(connection.id) }
                    )
                    HorizontalDivider()
                }
            }

            if (!state.loading && filteredConnections.isEmpty()) {
                item {
                    Text(
                        if (state.connections.isEmpty()) {
                            "当前没有活动连接。"
                        } else {
                            "没有符合当前搜索或筛选条件的连接。"
                        },
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
            Text(state.connections.size.toString() + " 个活动连接", style = MaterialTheme.typography.titleMedium)
            Text(
                "↓ " + formatRate(state.downloadBps) + "   ↑ " + formatRate(state.uploadBps),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "总计 ↓ " + formatBytes(state.downloadTotal) + " · ↑ " + formatBytes(state.uploadTotal),
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
private fun AppAggregateRow(
    aggregate: AppAggregate,
    enabled: Boolean,
    onClose: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(aggregate.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                aggregate.count.toString() + " 个连接 · ↓ " +
                    formatBytes(aggregate.download) + " · ↑ " + formatBytes(aggregate.upload)
            )
        },
        trailingContent = {
            TextButton(onClick = onClose, enabled = enabled) {
                Text("全部断开")
            }
        }
    )
}

@Composable
private fun ConnectionRow(
    connection: MihomoConnection,
    enabled: Boolean,
    onClose: () -> Unit,
    onDetails: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onDetails),
        headlineContent = {
            Text(connection.destinationLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                Text(
                    connection.processLabel + " · " + connection.network.uppercase(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(
                        connection.rule +
                            if (connection.rulePayload.isBlank()) "" else
                                " (" + connection.rulePayload + ")",
                        connection.chains.joinToString(" → ")
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "↓ " + formatBytes(connection.download) +
                        " · ↑ " + formatBytes(connection.upload),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        trailingContent = {
            TextButton(
                onClick = onClose,
                enabled = enabled && connection.id.isNotBlank()
            ) { Text("关闭") }
        }
    )
}

private fun formatRate(bytesPerSecond: Long): String = formatBytes(bytesPerSecond) + "/s"
private fun formatKb(kb: Long): String = formatBytes(kb * 1024L)

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
