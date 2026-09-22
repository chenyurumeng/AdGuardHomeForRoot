package io.github.chenyurumeng.aghmanager.ui.box

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.model.MihomoGroup
import io.github.chenyurumeng.aghmanager.model.MihomoQuickUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MihomoControlScreen(
    viewModel: MihomoViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenDashboard: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedGroupName by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = { Text("Mihomo 控制中心") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            }
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                ControllerStatusCard(state = state, onRefresh = viewModel::refresh)
            }

            item {
                Text(
                    "策略组",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(state.groups, key = { it.name }) { group ->
                ListItem(
                    modifier = Modifier.clickable(enabled = state.busyAction == null) {
                        selectedGroupName = group.name
                    },
                    headlineContent = { Text(group.name) },
                    supportingContent = {
                        Text(
                            group.now.ifBlank { group.type } +
                                state.delays[group.now]?.let { " · " + it + " ms" }.orEmpty()
                        )
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = "管理 " + group.name)
                    }
                )
                HorizontalDivider()
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = viewModel::testAll,
                        enabled = state.busyAction == null && state.groups.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("全部测速") }
                    OutlinedButton(
                        onClick = viewModel::updateAllProviders,
                        enabled = state.busyAction == null && state.providers.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("更新全部订阅") }
                }
            }

            if (state.providers.isNotEmpty()) {
                item {
                    Text(
                        "Proxy Providers",
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(state.providers, key = { "provider:" + it.name }) { provider ->
                    ListItem(
                        headlineContent = { Text(provider.name) },
                        supportingContent = {
                            Text(
                                listOf(
                                    provider.vehicleType.ifBlank { "Provider" },
                                    provider.proxyCount.toString() + " 节点",
                                    provider.updatedAt.takeIf { it.isNotBlank() }?.let { "更新 " + it }.orEmpty()
                                ).filter { it.isNotBlank() }.joinToString(" · "),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = { viewModel.updateProvider(provider.name) },
                                enabled = state.busyAction == null
                            ) { Text("更新") }
                        }
                    )
                    HorizontalDivider()
                }
            }

            item {
                OutlinedButton(
                    onClick = viewModel::flushDns,
                    enabled = state.busyAction == null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("清空 Mihomo DNS Cache") }
            }

            item {
                OutlinedButton(
                    onClick = {
                        val controller = state.controller.trimEnd('/')
                        val url = when {
                            controller.isBlank() -> "http://127.0.0.1:9090/ui/"
                            controller.endsWith("/ui") -> controller + "/"
                            controller.endsWith("/ui/") -> controller
                            else -> controller + "/ui/"
                        }
                        onOpenDashboard(url)
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("打开完整 Dashboard") }
            }
        }
    }

    val selected = selectedGroupName?.let { name -> state.groups.firstOrNull { it.name == name } }
    if (selected != null) {
        AlertDialog(
            onDismissRequest = { selectedGroupName = null },
            title = {
                Column {
                    Text(selected.name)
                    Text(
                        selected.type + " · 当前 " + selected.now.ifBlank { "-" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("搜索节点") }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { favoritesOnly = !favoritesOnly },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (favoritesOnly) "显示全部" else "只看收藏")
                        }
                        OutlinedButton(
                            onClick = { viewModel.testGroup(selected) },
                            enabled = state.busyAction == null,
                            modifier = Modifier.weight(1f)
                        ) { Text("测速") }
                        OutlinedButton(
                            onClick = { viewModel.selectFastest(selected) },
                            enabled = state.busyAction == null && selected.selectable,
                            modifier = Modifier.weight(1f)
                        ) { Text("最快") }
                    }

                    val visible = remember(
                        selected.all,
                        search,
                        favoritesOnly,
                        state.favorites,
                        state.delays
                    ) {
                        val needle = search.trim().lowercase()
                        selected.all
                            .filter { !favoritesOnly || it in state.favorites }
                            .filter { needle.isBlank() || it.lowercase().contains(needle) }
                            .sortedWith(
                                compareByDescending<String> { it in state.favorites }
                                    .thenBy { state.delays[it] ?: Int.MAX_VALUE }
                                    .thenBy { it.lowercase() }
                            )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 8.dp)
                    ) {
                        items(visible, key = { it }) { node ->
                            val favorite = node in state.favorites
                            val current = node == selected.now
                            ListItem(
                                modifier = Modifier.clickable(
                                    enabled = state.busyAction == null && selected.selectable
                                ) {
                                    selectedGroupName = null
                                    viewModel.select(selected.name, node)
                                },
                                headlineContent = {
                                    Text((if (favorite) "★ " else "") + node)
                                },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(
                                            state.delays[node]?.let { it.toString() + " ms" },
                                            if (current) "当前节点" else null
                                        ).joinToString(" · ").ifBlank { selected.type }
                                    )
                                },
                                trailingContent = {
                                    TextButton(
                                        onClick = { viewModel.toggleFavorite(node) },
                                        enabled = state.busyAction == null
                                    ) {
                                        Text(if (favorite) "取消收藏" else "收藏")
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedGroupName = null }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun ControllerStatusCard(
    state: MihomoQuickUiState,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                when {
                    state.loading -> "正在连接 Controller…"
                    state.available -> "Controller Ready"
                    else -> "Controller Unavailable"
                },
                style = MaterialTheme.typography.titleMedium
            )
            if (state.available) {
                Text(
                    state.controller +
                        if (state.version.isBlank()) "" else " · " + state.version +
                        if (state.authenticated) " · Bearer auth" else "",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.error.isNotBlank()) {
                Text(
                    state.error,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            state.busyAction?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            TextButton(
                onClick = onRefresh,
                enabled = state.busyAction == null,
                modifier = Modifier.padding(top = 4.dp)
            ) { Text("刷新") }
        }
    }
}
