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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.model.AghRewriteRule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AghRewritesScreen(
    viewModel: AghRewritesViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AghRewriteRule?>(null) }
    var deleting by remember { mutableStateOf<AghRewriteRule?>(null) }
    var confirmBack by rememberSaveable { mutableStateOf(false) }

    val query = state.query.trim()
    val visible = remember(state.rules, query) {
        if (query.isBlank()) state.rules
        else state.rules.filter {
            it.domain.contains(query, ignoreCase = true) ||
                it.answer.contains(query, ignoreCase = true)
        }
    }

    fun requestBack() {
        if (state.settingsDirty) confirmBack = true else onBack()
    }

    BackHandler(enabled = !state.busy, onBack = ::requestBack)

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = { Text(viewModel.instance.label + " · DNS Rewrite") },
            navigationIcon = {
                IconButton(onClick = ::requestBack, enabled = !state.busy) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(
                    onClick = viewModel::refresh,
                    enabled = !state.busy && !state.loading && !state.settingsDirty
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
                IconButton(onClick = { adding = true }, enabled = !state.busy) {
                    Icon(Icons.Default.Add, contentDescription = "添加 Rewrite")
                }
            }
        )

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                ListItem(
                    headlineContent = { Text("DNS Rewrite") },
                    supportingContent = {
                        Text(if (state.pendingEnabled) "重写规则生效" else "所有重写规则暂停")
                    },
                    trailingContent = {
                        Switch(
                            checked = state.pendingEnabled,
                            onCheckedChange = viewModel::setPendingEnabled,
                            enabled = !state.busy
                        )
                    }
                )
                if (state.settingsDirty) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = viewModel::discardSettings,
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("放弃") }
                        Button(
                            onClick = viewModel::applySettings,
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("应用") }
                    }
                }
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            singleLine = true,
            label = { Text("搜索域名或 Answer") }
        )

        Text(
            visible.size.toString() + " / " + state.rules.size + " 条 Rewrite",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
            items(visible, key = { it.key }) { rule ->
                ListItem(
                    modifier = Modifier.clickable(enabled = !state.busy) {
                        editing = rule
                    },
                    headlineContent = {
                        Text(
                            rule.domain,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(
                            rule.answer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = {
                                viewModel.toggle(rule, it)
                            },
                            enabled = !state.busy
                        )
                    }
                )
                HorizontalDivider()
            }

            if (!state.loading && visible.isEmpty()) {
                item {
                    Text(
                        if (query.isBlank()) "当前没有 DNS Rewrite"
                        else "没有匹配的 Rewrite",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Button(
            onClick = { adding = true },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Text("添加 DNS Rewrite")
        }
    }

    if (adding) {
        RewriteEditorDialog(
            title = "添加 DNS Rewrite",
            initial = null,
            busy = state.busy,
            onDismiss = { if (!state.busy) adding = false },
            onSave = { rule ->
                viewModel.add(rule) { success ->
                    if (success) adding = false
                }
            },
            onDelete = null
        )
    }

    editing?.let { rule ->
        RewriteEditorDialog(
            title = "编辑 DNS Rewrite",
            initial = rule,
            busy = state.busy,
            onDismiss = { if (!state.busy) editing = null },
            onSave = { updated ->
                viewModel.update(rule, updated) { success ->
                    if (success) editing = null
                }
            },
            onDelete = {
                editing = null
                deleting = rule
            }
        )
    }

    deleting?.let { rule ->
        AlertDialog(
            onDismissRequest = { if (!state.busy) deleting = null },
            title = { Text("删除 DNS Rewrite？") },
            text = { Text(rule.domain + "\n→ " + rule.answer) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        viewModel.delete(rule)
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

    if (confirmBack) {
        AlertDialog(
            onDismissRequest = { confirmBack = false },
            title = { Text("放弃 Rewrite 总开关修改？") },
            text = { Text("全局 DNS Rewrite 开关还有未应用修改。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmBack = false
                        viewModel.discardSettings()
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
private fun RewriteEditorDialog(
    title: String,
    initial: AghRewriteRule?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (AghRewriteRule) -> Unit,
    onDelete: (() -> Unit)?
) {
    var domain by remember(initial?.key) { mutableStateOf(initial?.domain.orEmpty()) }
    var answer by remember(initial?.key) { mutableStateOf(initial?.answer.orEmpty()) }
    var enabled by remember(initial?.key) { mutableStateOf(initial?.enabled ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("域名") },
                    placeholder = { Text("example.com") }
                )
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                    label = { Text("Answer") },
                    supportingText = { Text("支持 IPv4、IPv6 或 CNAME 目标") }
                )
                ListItem(
                    headlineContent = { Text("启用此规则") },
                    trailingContent = {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            enabled = !busy
                        )
                    }
                )
                onDelete?.let {
                    TextButton(onClick = it, enabled = !busy) {
                        Text("删除此 Rewrite", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        AghRewriteRule(
                            domain = domain.trim(),
                            answer = answer.trim(),
                            enabled = enabled
                        )
                    )
                },
                enabled = !busy && domain.isNotBlank() && answer.isNotBlank()
            ) { Text(if (busy) "保存中…" else "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        }
    )
}
