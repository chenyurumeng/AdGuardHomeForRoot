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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.model.AGH_SCHEDULE_DAY_KEYS
import io.github.chenyurumeng.aghmanager.model.AghBlockedScheduleDayDraft
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
    var scheduleExpanded by remember { mutableStateOf(true) }

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

        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "已选择 " + state.pendingIds.size + " / " +
                                state.services.size + " 个服务",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "RC15 同时管理服务选择和官方 Inactivity Schedule。",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "不生效时段 · Inactivity Schedule",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "这些时段内 Blocked Services 不执行过滤；不是“阻止时段”。",
                                    modifier = Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { scheduleExpanded = !scheduleExpanded },
                                enabled = !state.applying
                            ) {
                                Text(if (scheduleExpanded) "收起" else "编辑")
                            }
                        }

                        if (scheduleExpanded) {
                            OutlinedTextField(
                                value = state.pendingSchedule.timeZone,
                                onValueChange = viewModel::setTimeZone,
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                singleLine = true,
                                enabled = !state.applying,
                                label = { Text("时区") },
                                supportingText = {
                                    Text("使用 Local、UTC 或 IANA 名称，例如 Asia/Shanghai")
                                }
                            )

                            Text(
                                "快捷模板只修改 Pending，不会立即生效：",
                                modifier = Modifier.padding(top = 10.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    OutlinedButton(
                                        onClick = { viewModel.applyScheduleTemplate("all") },
                                        enabled = !state.applying
                                    ) { Text("每天全天不生效") }
                                }
                                item {
                                    OutlinedButton(
                                        onClick = { viewModel.applyScheduleTemplate("weekdays") },
                                        enabled = !state.applying
                                    ) { Text("工作日全天不生效") }
                                }
                                item {
                                    OutlinedButton(
                                        onClick = { viewModel.applyScheduleTemplate("weekend") },
                                        enabled = !state.applying
                                    ) { Text("周末全天不生效") }
                                }
                                item {
                                    OutlinedButton(
                                        onClick = { viewModel.applyScheduleTemplate("clear") },
                                        enabled = !state.applying
                                    ) { Text("清空不生效时段") }
                                }
                            }

                            Text(
                                "AGH 每天只支持一个区间，且 start < end；因此 22:00→06:00 这类跨午夜区间不能作为同一天直接提交。",
                                modifier = Modifier.padding(top = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            AGH_SCHEDULE_DAY_KEYS.forEach { key ->
                                ScheduleDayEditor(
                                    dayKey = key,
                                    draft = state.pendingSchedule.days[key]
                                        ?: AghBlockedScheduleDayDraft(),
                                    enabled = !state.applying,
                                    onEnabledChange = { viewModel.setDayEnabled(key, it) },
                                    onStartChange = { viewModel.setDayStart(key, it) },
                                    onEndChange = { viewModel.setDayEnd(key, it) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    singleLine = true,
                    enabled = !state.applying,
                    label = { Text("搜索服务名称 / ID / Group") }
                )
            }

            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.groupId.isBlank(),
                            onClick = { viewModel.setGroup("") },
                            enabled = !state.applying,
                            label = { Text("全部") }
                        )
                    }
                    items(state.groups, key = { it }) { group ->
                        FilterChip(
                            selected = state.groupId == group,
                            onClick = { viewModel.setGroup(group) },
                            enabled = !state.applying,
                            label = { Text(group) }
                        )
                    }
                }
            }

            item {
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
            }

            item {
                Text(
                    "当前筛选 " + visible.size + " 个服务",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.error.isNotBlank()) {
                item {
                    Text(
                        state.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

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

            if (state.dirty) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (state.applying) "应用中…" else "应用")
                        }
                    }
                }
            }
        }
    }

    if (confirmBack) {
        AlertDialog(
            onDismissRequest = { confirmBack = false },
            title = { Text("放弃 Blocked Services 修改？") },
            text = { Text("当前服务选择或不生效时段修改尚未写入 AdGuard Home。") },
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
private fun ScheduleDayEditor(
    dayKey: String,
    draft: AghBlockedScheduleDayDraft,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onEnabledChange(!draft.enabled) },
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(dayLabel(dayKey), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (draft.enabled) "Blocked Services 在该区间内不生效" else "全天正常执行 Blocked Services",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = draft.enabled,
                onCheckedChange = null,
                enabled = enabled
            )
        }

        if (draft.enabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft.startText,
                    onValueChange = onStartChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = enabled,
                    label = { Text("开始") },
                    supportingText = { Text("HH:mm") }
                )
                OutlinedTextField(
                    value = draft.endText,
                    onValueChange = onEndChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = enabled,
                    label = { Text("结束") },
                    supportingText = { Text("HH:mm / 24:00") }
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
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

private fun dayLabel(key: String): String = when (key) {
    "mon" -> "周一"
    "tue" -> "周二"
    "wed" -> "周三"
    "thu" -> "周四"
    "fri" -> "周五"
    "sat" -> "周六"
    "sun" -> "周日"
    else -> key
}
