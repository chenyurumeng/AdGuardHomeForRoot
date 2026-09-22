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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.model.AghFilterCheckResult
import io.github.chenyurumeng.aghmanager.model.AghFilterSubscription

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AghFiltersScreen(
    viewModel: AghFiltersViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onUserRules: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AghFilterSubscription?>(null) }
    var deleting by remember { mutableStateOf<AghFilterSubscription?>(null) }
    var confirmBack by remember { mutableStateOf(false) }

    val status = state.status
    val source = if (state.showWhitelist) {
        status?.whitelistFilters.orEmpty()
    } else {
        status?.filters.orEmpty()
    }
    val query = state.query.trim()
    val visible = if (query.isBlank()) {
        source
    } else {
        source.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.url.contains(query, ignoreCase = true)
        }
    }

    fun requestBack() {
        if (state.settingsDirty) confirmBack = true else onBack()
    }

    BackHandler(onBack = ::requestBack)

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = { Text(viewModel.instance.label + " · 过滤规则") },
            navigationIcon = {
                IconButton(onClick = ::requestBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(
                    onClick = viewModel::refreshAllFilters,
                    enabled = !state.busy
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新过滤器")
                }
                IconButton(
                    onClick = { showAdd = true },
                    enabled = !state.busy
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加过滤器")
                }
            }
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            item { SectionHeaderRc8("过滤设置") }
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        ListItem(
                            headlineContent = { Text("DNS 过滤") },
                            supportingContent = {
                                Text(if (state.pendingEnabled) "已启用" else "已停用")
                            },
                            trailingContent = {
                                Switch(
                                    checked = state.pendingEnabled,
                                    onCheckedChange = viewModel::setPendingEnabled,
                                    enabled = !state.busy && status != null
                                )
                            }
                        )

                        OutlinedTextField(
                            value = state.pendingIntervalHours.toString(),
                            onValueChange = {
                                viewModel.setPendingInterval(
                                    it.filter(Char::isDigit).toIntOrNull() ?: 0
                                )
                            },
                            enabled = !state.busy && status != null,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            singleLine = true,
                            label = { Text("过滤器自动更新周期（小时）") },
                            supportingText = { Text("例如 72 = 每 3 天更新一次") }
                        )

                        if (state.settingsDirty) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
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
                                ) { Text(if (state.busy) "应用中…" else "应用") }
                            }
                        }
                    }
                }
            }

            item { SectionHeaderRc8("User Rules") }
            item {
                ListItem(
                    modifier = Modifier.clickable(enabled = status != null) { onUserRules() },
                    headlineContent = { Text("自定义过滤规则") },
                    supportingContent = {
                        Text(
                            if (status == null) "等待读取"
                            else status.userRules.size.toString() + " 条规则 · 支持阻止、放行和高级 AdGuard 语法"
                        )
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = "打开 User Rules")
                    }
                )
            }
            item { HorizontalDivider() }

            item { SectionHeaderRc8("域名过滤检查") }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        OutlinedTextField(
                            value = state.checkHost,
                            onValueChange = viewModel::setCheckHost,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !state.checkingHost,
                            label = { Text("域名，例如 doubleclick.net") }
                        )
                        Button(
                            onClick = { viewModel.checkHost() },
                            enabled = !state.checkingHost && state.checkHost.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text(if (state.checkingHost) "检查中…" else "检查过滤结果")
                        }
                        state.checkResult?.let { FilterCheckResultCard(it) }
                    }
                }
            }

            item { SectionHeaderRc8("过滤器订阅") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !state.showWhitelist,
                        onClick = { viewModel.setShowWhitelist(false) },
                        label = { Text("拦截过滤器") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.showWhitelist,
                        onClick = { viewModel.setShowWhitelist(true) },
                        label = { Text("白名单过滤器") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    label = { Text("搜索名称或 URL") }
                )
            }

            if (status != null) {
                item {
                    Text(
                        visible.size.toString() + " / " + source.size + " 个过滤器",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(
                count = visible.size,
                key = { index -> (if (visible[index].whitelist) "w:" else "b:") + visible[index].id }
            ) { index ->
                val filter = visible[index]
                FilterRow(
                    filter = filter,
                    busy = state.busy,
                    onToggle = { enabled -> viewModel.toggleFilter(filter, enabled) },
                    onEdit = { editing = filter }
                )
                HorizontalDivider()
            }

            if (!state.loading && status != null && visible.isEmpty()) {
                item {
                    Text(
                        if (query.isBlank()) "暂无过滤器" else "没有匹配的过滤器",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.error.isNotBlank()) {
                item {
                    Text(
                        state.error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (showAdd) {
        FilterEditDialog(
            title = "添加过滤器",
            initial = null,
            defaultWhitelist = state.showWhitelist,
            busy = state.busy,
            onDismiss = { showAdd = false },
            onSave = { name, url, _, whitelist ->
                showAdd = false
                viewModel.addFilter(name, url, whitelist)
            },
            onDelete = null
        )
    }

    editing?.let { filter ->
        FilterEditDialog(
            title = "编辑过滤器",
            initial = filter,
            defaultWhitelist = filter.whitelist,
            busy = state.busy,
            onDismiss = { editing = null },
            onSave = { name, url, enabled, _ ->
                editing = null
                viewModel.updateFilter(filter, name, url, enabled)
            },
            onDelete = {
                editing = null
                deleting = filter
            }
        )
    }

    deleting?.let { filter ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除过滤器？") },
            text = {
                Text(
                    filter.name + "\n\n" +
                        "删除后该订阅不会再参与 " +
                        (if (filter.whitelist) "白名单过滤" else "拦截过滤") + "。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        viewModel.removeFilter(filter)
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }

    if (confirmBack) {
        AlertDialog(
            onDismissRequest = { confirmBack = false },
            title = { Text("放弃过滤设置修改？") },
            text = { Text("过滤总开关或更新周期还有未应用修改。") },
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
                TextButton(onClick = { confirmBack = false }) { Text("继续编辑") }
            }
        )
    }
}

@Composable
private fun FilterRow(
    filter: AghFilterSubscription,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(enabled = !busy, onClick = onEdit),
        headlineContent = {
            Text(
                filter.name.ifBlank { "(未命名过滤器)" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                Text(
                    filter.url,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(filter.rulesCount).append(" 条规则")
                        if (filter.lastUpdated.isNotBlank()) {
                            append(" · ").append(filter.lastUpdated)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = filter.enabled,
                onCheckedChange = onToggle,
                enabled = !busy
            )
        }
    )
}

@Composable
private fun FilterCheckResultCard(result: AghFilterCheckResult) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                if (result.filtered) "命中过滤规则" else "未被过滤",
                style = MaterialTheme.typography.titleSmall,
                color = if (result.filtered) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Text(
                result.reason,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall
            )
            result.rules.forEach { rule ->
                Text(
                    rule.text.ifBlank { "(空规则)" } +
                        if (rule.filterListId != 0L) " · List " + rule.filterListId else "",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (result.serviceName.isNotBlank()) {
                Text("Service: " + result.serviceName, style = MaterialTheme.typography.bodySmall)
            }
            if (result.cname.isNotBlank()) {
                Text("CNAME: " + result.cname, style = MaterialTheme.typography.bodySmall)
            }
            if (result.ipAddresses.isNotEmpty()) {
                Text(
                    "IP: " + result.ipAddresses.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun FilterEditDialog(
    title: String,
    initial: AghFilterSubscription?,
    defaultWhitelist: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String, enabled: Boolean, whitelist: Boolean) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember(initial?.id) { mutableStateOf(initial?.url.orEmpty()) }
    var enabled by remember(initial?.id) { mutableStateOf(initial?.enabled ?: true) }
    var whitelist by remember(initial?.id, defaultWhitelist) {
        mutableStateOf(initial?.whitelist ?: defaultWhitelist)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("名称") }
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 2,
                    label = { Text("URL 或绝对文件路径") }
                )

                if (initial == null) {
                    Text(
                        "类型",
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !whitelist,
                            onClick = { whitelist = false },
                            label = { Text("拦截") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = whitelist,
                            onClick = { whitelist = true },
                            label = { Text("白名单") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    ListItem(
                        headlineContent = { Text("启用过滤器") },
                        trailingContent = {
                            Switch(
                                checked = enabled,
                                onCheckedChange = { enabled = it },
                                enabled = !busy
                            )
                        }
                    )
                    onDelete?.let {
                        TextButton(
                            onClick = it,
                            enabled = !busy
                        ) {
                            Text("删除此过滤器", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), url.trim(), enabled, whitelist) },
                enabled = !busy && name.isNotBlank() && url.isNotBlank()
            ) { Text(if (busy) "处理中…" else "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        }
    )
}

@Composable
private fun SectionHeaderRc8(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}
