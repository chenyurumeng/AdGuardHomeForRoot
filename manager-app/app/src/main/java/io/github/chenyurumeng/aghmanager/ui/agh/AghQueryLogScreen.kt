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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.chenyurumeng.aghmanager.model.AghQueryFilter
import io.github.chenyurumeng.aghmanager.model.AghQueryLogConfig
import io.github.chenyurumeng.aghmanager.model.AghQueryLogEntry
import kotlinx.coroutines.delay

private data class RuleEditorState(
    val original: String?,
    val initial: String,
    val title: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AghQueryLogScreen(
    viewModel: AghQueryLogViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboard = LocalClipboardManager.current

    val visibleEntries = remember(state.entries, state.filter) { state.visibleEntries }

    var autoRefresh by rememberSaveable { mutableStateOf(true) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<RuleEditorState?>(null) }
    var deletingRule by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    LaunchedEffect(lifecycleOwner, autoRefresh, viewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (autoRefresh) {
                delay(5_000)
                if (
                    listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                ) {
                    viewModel.refreshLatest()
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = { Text(viewModel.instance.label + " · Query Log") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(
                    onClick = viewModel::refreshLatest,
                    enabled = !state.loading &&
                        !state.loadingMore &&
                        !state.refreshingLatest
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新日志")
                }
                IconButton(
                    onClick = { showSettings = true },
                    enabled = state.pendingConfig != null
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Query Log 设置")
                }
            }
        )

        if (state.loading || state.refreshingLatest || state.loadingMore) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.search,
                onValueChange = viewModel::setSearch,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("域名 / Client IP") }
            )
            Button(
                onClick = viewModel::applySearch,
                enabled = !state.loading
            ) { Text("搜索") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QueryFilterChip(AghQueryFilter.ALL, state.filter, viewModel::setFilter, Modifier.weight(1f))
            QueryFilterChip(AghQueryFilter.BLOCKED, state.filter, viewModel::setFilter, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QueryFilterChip(AghQueryFilter.ALLOWED, state.filter, viewModel::setFilter, Modifier.weight(1f))
            QueryFilterChip(AghQueryFilter.PROCESSED, state.filter, viewModel::setFilter, Modifier.weight(1f))
        }

        ListItem(
            headlineContent = { Text("实时刷新") },
            supportingContent = {
                Text(if (autoRefresh) "列表在顶部时每 5 秒刷新" else "已关闭自动刷新")
            },
            trailingContent = {
                Switch(checked = autoRefresh, onCheckedChange = { autoRefresh = it })
            }
        )

        Text(
            "显示 " + visibleEntries.size + " / " + state.entries.size + " 条",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.pendingConfig?.enabled == false) {
            Text(
                "Query Log 当前已停用；已有记录仍可查看。",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (state.error.isNotBlank()) {
            Text(
                state.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            items(
                items = visibleEntries,
                key = { it.stableKey }
            ) { entry ->
                QueryLogRow(entry = entry, onClick = { viewModel.select(entry) })
                HorizontalDivider()
            }

            if (!state.loading && visibleEntries.isEmpty()) {
                item {
                    Text(
                        if (state.entries.isEmpty()) "暂无 Query Log 记录"
                        else "没有符合当前筛选条件的记录",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.oldest.isNotBlank()) {
                item {
                    OutlinedButton(
                        onClick = viewModel::loadMore,
                        enabled = !state.loadingMore,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Text(if (state.loadingMore) "加载中…" else "加载更早记录")
                    }
                }
            }
        }
    }

    state.selected?.let { entry ->
        QueryDetailDialog(
            entry = entry,
            userRules = state.selectedUserRules,
            busy = state.mutatingRule,
            onDismiss = { viewModel.select(null) },
            onCopyDomain = {
                clipboard.setText(AnnotatedString(entry.domain.trimEnd('.')))
            },
            onBlock = {
                editor = RuleEditorState(
                    null,
                    "||" + entry.domain.trimEnd('.') + "^",
                    "阻止此域名"
                )
            },
            onAllow = {
                editor = RuleEditorState(
                    null,
                    "@@||" + entry.domain.trimEnd('.') + "^",
                    "允许此域名"
                )
            },
            onCustom = {
                editor = RuleEditorState(
                    null,
                    "||" + entry.domain.trimEnd('.') + "^",
                    "创建自定义规则"
                )
            },
            onEditRule = { rule ->
                editor = RuleEditorState(rule, rule, "编辑 User Rule")
            },
            onDeleteRule = { deletingRule = it }
        )
    }

    editor?.let { target ->
        RuleEditorDialog(
            state = target,
            busy = state.mutatingRule,
            onDismiss = { if (!state.mutatingRule) editor = null },
            onSave = { rule ->
                viewModel.saveRule(target.original, rule) { success ->
                    if (success) editor = null
                }
            }
        )
    }

    deletingRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { if (!state.mutatingRule) deletingRule = null },
            title = { Text("删除 User Rule？") },
            text = { Text(rule) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingRule = null
                        viewModel.deleteRule(rule)
                    },
                    enabled = !state.mutatingRule
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingRule = null },
                    enabled = !state.mutatingRule
                ) { Text("取消") }
            }
        )
    }

    if (showSettings && state.pendingConfig != null) {
        QueryLogSettingsDialog(
            config = state.pendingConfig!!,
            dirty = state.configDirty,
            saving = state.savingConfig,
            onChange = viewModel::setPendingConfig,
            onDiscard = viewModel::discardConfig,
            onSave = {
                viewModel.saveConfig { success ->
                    if (success) showSettings = false
                }
            },
            onClear = { confirmClear = true },
            onDismiss = {
                if (!state.savingConfig) {
                    if (state.configDirty) viewModel.discardConfig()
                    showSettings = false
                }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部 Query Log？") },
            text = { Text("此操作会删除当前实例的全部查询日志，无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        showSettings = false
                        viewModel.clearLog()
                    }
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun QueryFilterChip(
    item: AghQueryFilter,
    selected: AghQueryFilter,
    onClick: (AghQueryFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected == item,
        onClick = { onClick(item) },
        label = { Text(item.label) },
        modifier = modifier
    )
}

@Composable
private fun QueryLogRow(entry: AghQueryLogEntry, onClick: () -> Unit) {
    val stateLabel = when {
        entry.blocked -> "已阻止"
        entry.whitelisted -> "已允许"
        entry.cached -> "缓存"
        else -> "已处理"
    }

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                entry.displayDomain.ifBlank { "(未知域名)" },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                Text(
                    listOf(entry.qtype.ifBlank { "?" }, entry.clientName.ifBlank { entry.client }, stateLabel)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(
                        entry.reason,
                        entry.elapsedMs.takeIf { it.isNotBlank() }?.let { it + " ms" }.orEmpty(),
                        entry.upstream
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Text(
                stateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (entry.blocked) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        }
    )
}

@Composable
private fun QueryDetailDialog(
    entry: AghQueryLogEntry,
    userRules: List<String>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCopyDomain: () -> Unit,
    onBlock: () -> Unit,
    onAllow: () -> Unit,
    onCustom: () -> Unit,
    onEditRule: (String) -> Unit,
    onDeleteRule: (String) -> Unit
) {
    val editableRules = entry.rules
        .map { it.text }
        .filter { it.isNotBlank() && it in userRules }
        .distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(entry.displayDomain.ifBlank { "(未知域名)" })
                Text(
                    listOf(entry.qtype, entry.status, entry.reason)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                item { DetailLine("时间", entry.time) }
                item { DetailLine("Client", entry.clientName.ifBlank { entry.client }) }
                if (entry.clientName.isNotBlank() && entry.client.isNotBlank()) {
                    item { DetailLine("Client IP", entry.client) }
                }
                item { DetailLine("协议", entry.clientProto.ifBlank { "普通 DNS" }) }
                item { DetailLine("上游", entry.upstream.ifBlank { "-" }) }
                item { DetailLine("耗时", if (entry.elapsedMs.isBlank()) "-" else entry.elapsedMs + " ms") }
                item { DetailLine("缓存", if (entry.cached) "是" else "否") }
                item { DetailLine("DNSSEC", if (entry.answerDnssec) "已验证" else "否") }
                if (entry.serviceName.isNotBlank()) {
                    item { DetailLine("Blocked Service", entry.serviceName) }
                }

                if (entry.answers.isNotEmpty()) {
                    item {
                        Text(
                            "响应",
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(entry.answers) { answer ->
                        Text(
                            answer.type + " · " + answer.value + " · TTL " + answer.ttl,
                            modifier = Modifier.padding(vertical = 2.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (entry.rules.isNotEmpty()) {
                    item {
                        Text(
                            "命中规则",
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(entry.rules) { rule ->
                        Text(
                            rule.text.ifBlank { "(空规则)" } +
                                if (rule.filterListId == 0L) "" else " · List " + rule.filterListId,
                            modifier = Modifier.padding(vertical = 2.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                item {
                    Text(
                        "快速操作",
                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBlock,
                            enabled = !busy && entry.domain.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("阻止") }
                        OutlinedButton(
                            onClick = onAllow,
                            enabled = !busy && entry.domain.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("允许") }
                        OutlinedButton(
                            onClick = onCustom,
                            enabled = !busy && entry.domain.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("自定义") }
                    }
                }
                item {
                    TextButton(onClick = onCopyDomain, enabled = entry.domain.isNotBlank()) {
                        Text("复制域名")
                    }
                }

                if (editableRules.isNotEmpty()) {
                    item {
                        Text(
                            "可编辑的 User Rules",
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(editableRules) { rule ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            tonalElevation = 1.dp,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(rule, style = MaterialTheme.typography.bodySmall)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextButton(onClick = { onEditRule(rule) }, enabled = !busy) {
                                        Text("编辑")
                                    }
                                    TextButton(onClick = { onDeleteRule(rule) }, enabled = !busy) {
                                        Text("删除", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("关闭") }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Text(
        label + "： " + value.ifBlank { "-" },
        modifier = Modifier.padding(vertical = 2.dp),
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun RuleEditorDialog(
    state: RuleEditorState,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(state.original, state.initial) { mutableStateOf(state.initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text("AdGuard 过滤规则") }
                )
                Text(
                    "保存前会重新读取 User Rules；若其它界面已修改规则，操作会拒绝覆盖。",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = !busy && text.isNotBlank()
            ) { Text(if (busy) "保存中…" else "保存并立即生效") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        }
    )
}

@Composable
private fun QueryLogSettingsDialog(
    config: AghQueryLogConfig,
    dirty: Boolean,
    saving: Boolean,
    onChange: (AghQueryLogConfig) -> Unit,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val intervals = listOf(
        21_600_000L to "6 小时",
        86_400_000L to "1 天",
        604_800_000L to "7 天",
        2_592_000_000L to "30 天",
        7_776_000_000L to "90 天"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Query Log 设置") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text("启用查询日志") },
                        trailingContent = {
                            Switch(
                                checked = config.enabled,
                                onCheckedChange = { onChange(config.copy(enabled = it)) },
                                enabled = !saving
                            )
                        }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("匿名化 Client IP") },
                        trailingContent = {
                            Switch(
                                checked = config.anonymizeClientIp,
                                onCheckedChange = { onChange(config.copy(anonymizeClientIp = it)) },
                                enabled = !saving
                            )
                        }
                    )
                }
                item {
                    Text(
                        "保留时间",
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                items(intervals) { (value, label) ->
                    FilterChip(
                        selected = config.intervalMs == value,
                        onClick = { onChange(config.copy(intervalMs = value)) },
                        enabled = !saving,
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("忽略指定域名") },
                        supportingContent = { Text("匹配域名不会写入 Query Log") },
                        trailingContent = {
                            Switch(
                                checked = config.ignoredEnabled,
                                onCheckedChange = { onChange(config.copy(ignoredEnabled = it)) },
                                enabled = !saving
                            )
                        }
                    )
                }
                item {
                    OutlinedTextField(
                        value = config.ignored.joinToString("\n"),
                        onValueChange = {
                            onChange(
                                config.copy(
                                    ignored = it.lineSequence()
                                        .map(String::trim)
                                        .filter(String::isNotBlank)
                                        .toList()
                                )
                            )
                        },
                        enabled = !saving && config.ignoredEnabled,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        minLines = 3,
                        label = { Text("忽略域名（每行一个）") }
                    )
                }
                item {
                    TextButton(
                        onClick = onClear,
                        enabled = !saving,
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        Text("清空全部 Query Log", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = dirty && !saving) {
                Text(if (saving) "保存中…" else "保存")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (dirty) onDiscard()
                    onDismiss()
                },
                enabled = !saving
            ) { Text("关闭") }
        }
    )
}
