package io.github.chenyurumeng.aghmanager.ui.agh

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.model.AghBlockedService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AghBlockedServicesScreen(
    viewModel: AghBlockedServicesViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmBack by remember { mutableStateOf(false) }

    val query = state.query.trim()
    val visible = remember(state.services, state.groupId, query) {
        state.services.filter { service ->
            (state.groupId.isBlank() || service.groupId == state.groupId) &&
                (
                    query.isBlank() ||
                        service.name.contains(query, ignoreCase = true) ||
                        service.id.contains(query, ignoreCase = true) ||
                        service.groupId.contains(query, ignoreCase = true)
                    )
        }
    }
    val visibleIds = visible.mapTo(linkedSetOf()) { it.id }

    fun requestBack() {
        if (state.dirty) confirmBack = true else onBack()
    }

    BackHandler(enabled = !state.applying, onBack = ::requestBack)

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = { Text(viewModel.instance.label + " · Blocked Services") },
            navigationIcon = {
                IconButton(onClick = ::requestBack, enabled = !state.applying) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(
                    onClick = viewModel::refresh,
                    enabled = !state.applying && !state.dirty
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        )

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "已选择 " + state.pendingIds.size + " / " + state.services.size + " 个服务",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    if (state.scheduleJson.isBlank()) {
                        "当前未读取到服务阻止时间表；本页只修改服务选择。"
                    } else {
                        "服务端已有 Blocked Services 时间表。本页应用时会 fresh-fetch 并原样保留最新 schedule。"
                    },
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            singleLine = true,
            label = { Text("搜索服务名称 / ID / Group") }
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                FilterChip(
                    selected = state.groupId.isBlank(),
                    onClick = { viewModel.setGroup("") },
                    label = { Text("全部") }
                )
            }
            items(state.groups, key = { it }) { group ->
                FilterChip(
                    selected = state.groupId == group,
                    onClick = { viewModel.setGroup(group) },
                    label = { Text(group) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.setMany(visibleIds, true) },
                enabled = !state.applying && visibleIds.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text("选择当前筛选")
            }
            OutlinedButton(
                onClick = { viewModel.setMany(visibleIds, false) },
                enabled = !state.applying && visibleIds.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text("清除当前筛选")
            }
        }

        Text(
            "当前筛选 " + visible.size + " 个服务",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            items(visible, key = { it.id }) { service ->
                BlockedServiceRow(
                    service = service,
                    checked = service.id in state.pendingIds,
                    enabled = !state.applying,
                    onCheckedChange = {
                        viewModel.toggle(service.id, it)
                    }
                )
                HorizontalDivider()
            }

            if (!state.loading && visible.isEmpty()) {
                item {
                    Text(
                        if (state.services.isEmpty()) "当前没有可用 Blocked Services"
                        else "没有匹配的服务",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.dirty) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedButton(
                    onClick = viewModel::discard,
                    enabled = !state.applying,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("放弃")
                }
                Button(
                    onClick = viewModel::apply,
                    enabled = !state.applying,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                ) {
                    Text(if (state.applying) "应用中…" else "应用")
                }
            }
        }
    }

    if (confirmBack) {
        AlertDialog(
            onDismissRequest = { confirmBack = false },
            title = { Text("放弃 Blocked Services 修改？") },
            text = { Text("当前服务选择尚未写入 AdGuard Home。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmBack = false
                        viewModel.discard()
                        onBack()
                    }
                ) { Text("放弃并返回") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBack = false }) {
                    Text("继续编辑")
                }
            }
        )
    }
}

@Composable
private fun BlockedServiceRow(
    service: AghBlockedService,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) {
            onCheckedChange(!checked)
        },
        headlineContent = {
            Text(
                service.name.ifBlank { service.id },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                listOf(
                    service.id,
                    service.groupId.takeIf(String::isNotBlank).orEmpty(),
                    service.rulesCount.toString() + " 条规则"
                ).filter(String::isNotBlank).joinToString(" · "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        },
        trailingContent = {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled
            )
        }
    )
}
