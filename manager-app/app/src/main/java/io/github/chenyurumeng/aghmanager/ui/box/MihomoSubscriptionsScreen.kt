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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.model.MihomoSubscription

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MihomoSubscriptionsScreen(
    viewModel: MihomoSubscriptionsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MihomoSubscription?>(null) }
    var deleting by remember { mutableStateOf<MihomoSubscription?>(null) }
    var adding by remember { mutableStateOf(false) }
    var sortByName by remember { mutableStateOf(false) }

    val displayed = remember(state.subscriptions, sortByName) {
        if (sortByName) state.subscriptions.sortedBy { it.name.lowercase() }
        else state.subscriptions
    }

    BackHandler { if (!state.saving) onBack() }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = { Text("订阅管理") },
            navigationIcon = {
                IconButton(onClick = onBack, enabled = !state.saving) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = viewModel::refresh, enabled = !state.saving) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
                IconButton(onClick = { adding = true }, enabled = !state.saving) {
                    Icon(Icons.Default.Add, contentDescription = "添加订阅")
                }
            }
        )

        Text(
            "支持添加、编辑、停用、启用和删除 HTTP Proxy Provider。所有配置变更均先经 Mihomo 校验；运行中热重载，失败自动回滚。",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { sortByName = false },
                enabled = sortByName,
                modifier = Modifier.weight(1f)
            ) { Text("配置顺序") }
            OutlinedButton(
                onClick = { sortByName = true },
                enabled = !sortByName,
                modifier = Modifier.weight(1f)
            ) { Text("按名称") }
        }

        if (state.error.isNotBlank()) {
            Text(
                state.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(displayed, key = { it.name }) { subscription ->
                ListItem(
                    headlineContent = {
                        Text(
                            subscription.name +
                                if (subscription.enabled) "" else " · 已停用"
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(
                                subscription.maskedUrl,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                subscription.type + " · interval " +
                                    subscription.intervalSeconds + "s" +
                                    if (subscription.path.isBlank()) "" else " · " + subscription.path,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    leadingContent = {
                        Switch(
                            checked = subscription.enabled,
                            onCheckedChange = {
                                viewModel.setEnabled(subscription.name, it)
                            },
                            enabled = subscription.editable && !state.saving
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { editing = subscription },
                                enabled = subscription.editable && !state.saving
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑 " + subscription.name)
                            }
                            IconButton(
                                onClick = { deleting = subscription },
                                enabled = !state.saving
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "删除 " + subscription.name)
                            }
                        }
                    }
                )
                HorizontalDivider()
            }

            if (!state.loading && state.subscriptions.isEmpty()) {
                item {
                    Text(
                        "当前没有可管理的 Proxy Provider。",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Button(
            onClick = { adding = true },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Text(if (state.saving) "正在应用配置…" else "添加订阅")
        }
    }

    if (adding) {
        SubscriptionEditorDialog(
            existing = null,
            saving = state.saving,
            onDismiss = { if (!state.saving) adding = false },
            onSave = { name, url, interval ->
                viewModel.save(null, name, url, interval) { success ->
                    if (success) adding = false
                }
            }
        )
    }

    editing?.let { target ->
        SubscriptionEditorDialog(
            existing = target,
            saving = state.saving,
            onDismiss = { if (!state.saving) editing = null },
            onSave = { name, url, interval ->
                viewModel.save(target.name, name, url, interval) { success ->
                    if (success) editing = null
                }
            }
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!state.saving) deleting = null },
            title = { Text("删除 " + target.name + "？") },
            text = {
                Text(
                    "将从 config.yaml 删除该 Provider 配置。若其它配置仍引用它，Mihomo 校验会阻止删除并保留原配置。订阅缓存文件不会自动删除。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        viewModel.delete(target.name)
                    },
                    enabled = !state.saving
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleting = null },
                    enabled = !state.saving
                ) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SubscriptionEditorDialog(
    existing: MihomoSubscription?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit
) {
    var name by remember(existing?.name) { mutableStateOf(existing?.name.orEmpty()) }
    var url by remember(existing?.url) { mutableStateOf(existing?.url.orEmpty()) }
    var interval by remember(existing?.intervalSeconds) {
        mutableStateOf((existing?.intervalSeconds ?: 86_400).toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加订阅" else "编辑 " + existing.name) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (existing == null) name = it },
                    enabled = existing == null && !saving,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Provider 名称") },
                    supportingText = {
                        Text(if (existing == null) "例如 proxy2" else "编辑时名称固定，避免破坏其它引用")
                    }
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    singleLine = true,
                    label = { Text("订阅 URL") },
                    placeholder = { Text("https://example.com/sub?...") }
                )
                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it.filter(Char::isDigit) },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("更新间隔（秒）") },
                    supportingText = { Text("300–604800；默认 86400") }
                )
                Text(
                    "订阅 URL 不会出现在日志或诊断报告中。编辑停用中的订阅不会自动启用它。",
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(name.trim(), url.trim(), interval.toIntOrNull() ?: 0)
                },
                enabled = !saving && name.isNotBlank() && url.isNotBlank()
            ) {
                Text(if (saving) "保存中…" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
        }
    )
}
