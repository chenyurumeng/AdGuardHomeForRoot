package io.github.chenyurumeng.aghmanager.ui.agh

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.model.AghAutoClient
import io.github.chenyurumeng.aghmanager.model.AghClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AghClientsScreen(
    viewModel: AghClientsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AghClient?>(null) }
    var deleting by remember { mutableStateOf<AghClient?>(null) }
    var adding by remember { mutableStateOf(false) }
    var autoSeed by remember { mutableStateOf<AghAutoClient?>(null) }

    val query = state.query.trim()
    val clients = remember(state.clients, query) {
        if (query.isBlank()) state.clients
        else state.clients.filter { client ->
            client.name.contains(query, ignoreCase = true) ||
                client.ids.any { it.contains(query, ignoreCase = true) }
        }
    }
    val autoClients = remember(state.autoClients, query) {
        if (query.isBlank()) state.autoClients
        else state.autoClients.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.ip.contains(query, ignoreCase = true) ||
                it.source.contains(query, ignoreCase = true)
        }
    }

    BackHandler(enabled = !state.busy, onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = { Text(viewModel.instance.label + " · Clients") },
            navigationIcon = {
                IconButton(onClick = onBack, enabled = !state.busy) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = viewModel::refresh, enabled = !state.busy) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
                IconButton(
                    onClick = {
                        autoSeed = null
                        adding = true
                    },
                    enabled = !state.busy
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加客户端")
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !state.showAutoClients,
                onClick = { viewModel.setShowAuto(false) },
                label = { Text("持久客户端") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = state.showAutoClients,
                onClick = { viewModel.setShowAuto(true) },
                label = { Text("自动发现") },
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            singleLine = true,
            label = { Text("搜索名称 / IP / CIDR / ClientID") }
        )

        if (state.error.isNotBlank()) {
            Text(
                state.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (!state.showAutoClients) {
                items(clients, key = { it.name }) { client ->
                    ClientRow(
                        client = client,
                        enabled = !state.busy,
                        onClick = { editing = client }
                    )
                    HorizontalDivider()
                }
                if (!state.loading && clients.isEmpty()) {
                    item {
                        EmptyText(
                            if (query.isBlank()) "当前没有持久客户端"
                            else "没有匹配的持久客户端"
                        )
                    }
                }
            } else {
                item {
                    Text(
                        "自动发现客户端由 AGH 根据 hosts/rDNS/ARP 等来源识别，只读显示。点击可转为持久客户端。",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(autoClients, key = { it.ip + "|" + it.source }) { client ->
                    ListItem(
                        modifier = Modifier.clickable(enabled = !state.busy) {
                            autoSeed = client
                            adding = true
                        },
                        headlineContent = {
                            Text(client.name.ifBlank { client.ip })
                        },
                        supportingContent = {
                            Text(
                                listOf(client.ip, client.source)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · ")
                            )
                        },
                        trailingContent = { Text("添加") }
                    )
                    HorizontalDivider()
                }
                if (!state.loading && autoClients.isEmpty()) {
                    item {
                        EmptyText(
                            if (query.isBlank()) "当前没有自动发现客户端"
                            else "没有匹配的自动发现客户端"
                        )
                    }
                }
            }
        }

        if (!state.showAutoClients) {
            Button(
                onClick = {
                    autoSeed = null
                    adding = true
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("添加持久客户端")
            }
        }
    }

    if (adding) {
        ClientEditorDialog(
            title = "添加客户端",
            initial = autoSeed?.let {
                AghClient(
                    name = it.name.ifBlank { it.ip },
                    ids = listOf(it.ip)
                )
            },
            isNew = true,
            supportedTags = state.supportedTags,
            busy = state.busy,
            onDismiss = {
                if (!state.busy) {
                    adding = false
                    autoSeed = null
                }
            },
            onSave = { client ->
                viewModel.add(client) { success ->
                    if (success) {
                        adding = false
                        autoSeed = null
                    }
                }
            },
            onDelete = null
        )
    }

    editing?.let { client ->
        ClientEditorDialog(
            title = "编辑 " + client.name,
            initial = client,
            isNew = false,
            supportedTags = state.supportedTags,
            busy = state.busy,
            onDismiss = { if (!state.busy) editing = null },
            onSave = { updated ->
                viewModel.update(client, updated) { success ->
                    if (success) editing = null
                }
            },
            onDelete = {
                editing = null
                deleting = client
            }
        )
    }

    deleting?.let { client ->
        AlertDialog(
            onDismissRequest = { if (!state.busy) deleting = null },
            title = { Text("删除客户端？") },
            text = {
                Text(
                    client.name + "\n\n" +
                        client.ids.joinToString(", ") +
                        "\n\n删除后该客户端将恢复为自动识别/全局行为。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        viewModel.delete(client)
                    },
                    enabled = !state.busy
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleting = null },
                    enabled = !state.busy
                ) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ClientRow(
    client: AghClient,
    enabled: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(client.name) },
        supportingContent = {
            Column {
                Text(
                    client.ids.joinToString(", "),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (client.useGlobalSettings) {
                        "使用全局设置"
                    } else {
                        listOf(
                            if (client.filteringEnabled) "过滤" else "不过滤",
                            if (client.safeBrowsingEnabled) "安全浏览" else null,
                            if (client.parentalEnabled) "家长控制" else null
                        ).filterNotNull().joinToString(" · ")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (client.upstreams.isNotEmpty()) {
                    Text(
                        "Upstream: " + client.upstreams.joinToString(", "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        trailingContent = { Text("编辑") }
    )
}

@Composable
private fun ClientEditorDialog(
    title: String,
    initial: AghClient?,
    isNew: Boolean,
    supportedTags: List<String>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (AghClient) -> Unit,
    onDelete: (() -> Unit)?
) {
    val seed = initial ?: AghClient(name = "", ids = emptyList())
    var name by remember(initial?.name, isNew) { mutableStateOf(seed.name) }
    var ids by remember(initial?.name, isNew) { mutableStateOf(seed.ids.joinToString("\n")) }
    var useGlobal by remember(initial?.name, isNew) { mutableStateOf(seed.useGlobalSettings) }
    var filtering by remember(initial?.name, isNew) { mutableStateOf(seed.filteringEnabled) }
    var parental by remember(initial?.name, isNew) { mutableStateOf(seed.parentalEnabled) }
    var safeBrowsing by remember(initial?.name, isNew) { mutableStateOf(seed.safeBrowsingEnabled) }
    var upstreams by remember(initial?.name, isNew) {
        mutableStateOf(seed.upstreams.joinToString("\n"))
    }
    var tags by remember(initial?.name, isNew) { mutableStateOf(seed.tags.joinToString("\n")) }
    var ignoreQueryLog by remember(initial?.name, isNew) { mutableStateOf(seed.ignoreQueryLog) }
    var ignoreStatistics by remember(initial?.name, isNew) { mutableStateOf(seed.ignoreStatistics) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("名称") }
                    )
                }
                item {
                    OutlinedTextField(
                        value = ids,
                        onValueChange = { ids = it },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        minLines = 2,
                        label = { Text("客户端 ID（每行一个）") },
                        supportingText = { Text("支持 IP、CIDR、MAC 或 ClientID") }
                    )
                }
                item {
                    ToggleClientSetting(
                        "使用全局设置",
                        useGlobal,
                        !busy
                    ) { useGlobal = it }
                }
                item {
                    ToggleClientSetting(
                        "DNS 过滤",
                        filtering,
                        !busy && !useGlobal
                    ) { filtering = it }
                }
                item {
                    ToggleClientSetting(
                        "安全浏览",
                        safeBrowsing,
                        !busy && !useGlobal
                    ) { safeBrowsing = it }
                }
                item {
                    ToggleClientSetting(
                        "家长控制",
                        parental,
                        !busy && !useGlobal
                    ) { parental = it }
                }
                item {
                    OutlinedTextField(
                        value = upstreams,
                        onValueChange = { upstreams = it },
                        enabled = !busy && !useGlobal,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        minLines = 2,
                        label = { Text("客户端专用 Upstream（每行一个）") }
                    )
                }
                if (supportedTags.isNotEmpty()) {
                    item {
                        OutlinedTextField(
                            value = tags,
                            onValueChange = { tags = it },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            minLines = 2,
                            label = { Text("Tags（每行一个）") },
                            supportingText = {
                                Text(
                                    "可用：" + supportedTags.take(8).joinToString(", ") +
                                        if (supportedTags.size > 8) "…" else ""
                                )
                            }
                        )
                    }
                }
                item {
                    ToggleClientSetting(
                        "不记录到 Query Log",
                        ignoreQueryLog,
                        !busy
                    ) { ignoreQueryLog = it }
                }
                item {
                    ToggleClientSetting(
                        "不计入 Statistics",
                        ignoreStatistics,
                        !busy
                    ) { ignoreStatistics = it }
                }
                if (!isNew && onDelete != null) {
                    item {
                        TextButton(onClick = onDelete, enabled = !busy) {
                            Text("删除客户端", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        seed.copy(
                            name = name.trim(),
                            ids = parseClientLines(ids),
                            useGlobalSettings = useGlobal,
                            filteringEnabled = filtering,
                            parentalEnabled = parental,
                            safeBrowsingEnabled = safeBrowsing,
                            upstreams = parseClientLines(upstreams),
                            tags = parseClientLines(tags),
                            ignoreQueryLog = ignoreQueryLog,
                            ignoreStatistics = ignoreStatistics
                        )
                    )
                },
                enabled = !busy && name.isNotBlank() && parseClientLines(ids).isNotEmpty()
            ) { Text(if (busy) "保存中…" else "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        }
    )
}

@Composable
private fun ToggleClientSetting(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
        headlineContent = { Text(title) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled
            )
        }
    )
}

@Composable
private fun EmptyText(value: String) {
    Text(
        value,
        modifier = Modifier.padding(24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun parseClientLines(value: String): List<String> =
    value.lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .toList()
