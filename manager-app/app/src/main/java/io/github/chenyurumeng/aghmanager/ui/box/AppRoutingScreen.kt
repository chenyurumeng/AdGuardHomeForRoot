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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.model.AppEntry
import io.github.chenyurumeng.aghmanager.model.AppFilter
import io.github.chenyurumeng.aghmanager.model.AppRoutingUiState
import io.github.chenyurumeng.aghmanager.model.RoutingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoutingScreen(
    viewModel: AppRoutingViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var confirmDiscard by remember { mutableStateOf(false) }

    fun requestBack() {
        if (state.applying) return
        if (state.dirty) confirmDiscard = true else onBack()
    }

    BackHandler(enabled = state.applying || state.dirty) {
        requestBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("应用分流")
                    Text(
                        "Box App Routing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = ::requestBack,
                    enabled = !state.applying
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(
                    onClick = viewModel::syncNow,
                    enabled = !state.syncing && !state.applying
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "同步应用")
                }
            }
        )

        ModeSelector(
            mode = state.mode,
            enabled = !state.applying,
            onMode = viewModel::setMode
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            label = { Text("搜索") },
            placeholder = { Text("应用名 / 包名 / 用户") }
        )

        FilterSelector(
            filter = state.filter,
            onFilter = viewModel::setFilter
        )

        RoutingSummary(state)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f)
        ) {
            items(
                items = state.visibleApps,
                key = { it.key }
            ) { app ->
                AppRow(
                    app = app,
                    checked = app.key in state.selected,
                    enabled = !state.applying,
                    onToggle = { viewModel.toggleApp(app.key) }
                )
                HorizontalDivider()
            }

            if (state.cacheLoaded && state.visibleApps.isEmpty()) {
                item {
                    Text(
                        if (state.apps.isEmpty()) {
                            "缓存中暂无应用；后台同步完成后会自动更新。"
                        } else {
                            "当前筛选条件没有匹配应用。"
                        },
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = viewModel::apply,
                    enabled = state.dirty && !state.applying,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.applying) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 4.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(if (state.applying) "正在应用…" else "应用")
                }
                Text(
                    "选择、取消或切换模式只在本页暂存；只有点击“应用”后才写入 Box。",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃未应用修改？") },
            text = { Text("当前选择或模式尚未写入 Box。返回后这些未应用修改会丢失。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onBack()
                    }
                ) {
                    Text("放弃并返回")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text("继续编辑")
                }
            }
        )
    }
}

@Composable
private fun ModeSelector(
    mode: RoutingMode,
    enabled: Boolean,
    onMode: (RoutingMode) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            "代理模式",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = mode == RoutingMode.WHITELIST,
                onClick = { onMode(RoutingMode.WHITELIST) },
                enabled = enabled,
                label = { Text("Whitelist") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = mode == RoutingMode.BLACKLIST,
                onClick = { onMode(RoutingMode.BLACKLIST) },
                enabled = enabled,
                label = { Text("Blacklist") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FilterSelector(
    filter: AppFilter,
    onFilter: (AppFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppFilter.values().forEach { value ->
            val label = when (value) {
                AppFilter.ALL -> "All"
                AppFilter.USER -> "User"
                AppFilter.SYSTEM -> "System"
            }
            FilterChip(
                selected = filter == value,
                onClick = { onFilter(value) },
                label = { Text(label) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RoutingSummary(state: AppRoutingUiState) {
    val semantics = if (state.mode == RoutingMode.WHITELIST) {
        "选中应用走代理 / Foreign 5592"
    } else {
        "选中应用直连 / Domestic 5591"
    }

    val statusColor = if (state.statusIsError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            "已选 " + state.selected.size +
                " · 显示 " + state.visibleApps.size +
                " / " + state.apps.size +
                " · " + semantics +
                if (state.dirty) " · 有未应用更改" else "",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            state.statusText,
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.bodySmall,
            color = statusColor
        )
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val userSuffix = if (app.userId == 0) {
        ""
    } else {
        "  [" + app.userName + " " + app.userId + "]"
    }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle),
        headlineContent = {
            Text(
                app.label + userSuffix,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                app.packageName + if (app.system) " · System" else " · User",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                enabled = enabled
            )
        }
    )
}
