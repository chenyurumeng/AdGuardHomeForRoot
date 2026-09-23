package io.github.chenyurumeng.aghmanager.ui.agh

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.data.AghStatisticsRepository
import io.github.chenyurumeng.aghmanager.model.AghStatistics
import io.github.chenyurumeng.aghmanager.model.AghStatsConfig
import io.github.chenyurumeng.aghmanager.model.AghStatsTopEntry
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AghStatisticsScreen(
    viewModel: AghStatisticsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    BackHandler(
        enabled = !state.savingConfig && !state.resetting,
        onBack = onBack
    )

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = { Text(viewModel.instance.label + " · Statistics") },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    enabled = !state.savingConfig && !state.resetting
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(
                    onClick = viewModel::refreshStats,
                    enabled = state.config?.enabled == true &&
                        !state.refreshing &&
                        !state.loading
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新统计")
                }
                IconButton(
                    onClick = { showSettings = true },
                    enabled = state.pendingConfig != null
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Statistics 设置")
                }
            }
        )

        val periods = viewModel.periodOptions()
        if (periods.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(periods, key = { it.milliseconds }) { period ->
                    FilterChip(
                        selected = state.recentMs == period.milliseconds,
                        onClick = { viewModel.setPeriod(period.milliseconds) },
                        enabled = !state.refreshing && !state.loading,
                        label = { Text(period.label) }
                    )
                }
            }
        }

        if (state.error.isNotBlank()) {
            Text(
                state.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        when {
            state.loading -> {
                Text(
                    "正在读取 Statistics…",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.config?.enabled == false -> {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Statistics 当前已停用", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "可以从右上角设置中启用统计并选择保留周期。",
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showSettings = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) {
                            Text("打开 Statistics 设置")
                        }
                    }
                }
            }

            state.stats != null -> {
                StatisticsContent(
                    stats = state.stats!!,
                    refreshing = state.refreshing,
                    onReset = { confirmReset = true }
                )
            }

            else -> {
                Text(
                    "当前没有可显示的统计数据。",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showSettings && state.pendingConfig != null) {
        StatisticsSettingsDialog(
            config = state.pendingConfig!!,
            dirty = state.configDirty,
            saving = state.savingConfig,
            onChange = viewModel::setPendingConfig,
            onDiscard = viewModel::discardConfig,
            onSave = {
                viewModel.applyConfig { success ->
                    if (success) showSettings = false
                }
            },
            onDismiss = {
                if (!state.savingConfig) {
                    if (state.configDirty) viewModel.discardConfig()
                    showSettings = false
                }
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("重置全部 Statistics？") },
            text = {
                Text(
                    "此操作会清空 " + viewModel.instance.label +
                        " 当前累计的全部 DNS 统计数据，无法恢复。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        viewModel.reset()
                    },
                    enabled = !state.resetting
                ) {
                    Text("重置", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmReset = false },
                    enabled = !state.resetting
                ) { Text("取消") }
            }
        )
    }
}

@Composable
private fun StatisticsContent(
    stats: AghStatistics,
    refreshing: Boolean,
    onReset: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMetricCard(
                    label = "DNS 查询",
                    value = formatCount(stats.totalQueries),
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    label = "已阻止",
                    value = formatCount(stats.totalBlocked),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMetricCard(
                    label = "阻止率",
                    value = String.format(Locale.US, "%.1f%%", stats.blockedPercent),
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    label = "平均耗时",
                    value = formatDurationSeconds(stats.averageProcessingSeconds),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item { StatisticsSectionHeader("查询趋势") }
        item {
            TrendCard(
                title = "DNS 查询",
                values = stats.dnsQueriesSeries,
                unit = stats.timeUnits
            )
        }
        item {
            TrendCard(
                title = "Filtering 阻止",
                values = stats.blockedSeries,
                unit = stats.timeUnits
            )
        }

        if (
            stats.safeBrowsingBlocked > 0L ||
            stats.safeSearchReplaced > 0L ||
            stats.parentalBlocked > 0L
        ) {
            item { StatisticsSectionHeader("安全模块") }
            item {
                ListItem(
                    headlineContent = { Text("Safe Browsing") },
                    trailingContent = { Text(formatCount(stats.safeBrowsingBlocked)) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Safe Search") },
                    trailingContent = { Text(formatCount(stats.safeSearchReplaced)) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Parental Control") },
                    trailingContent = { Text(formatCount(stats.parentalBlocked)) }
                )
            }
        }

        item { StatisticsSectionHeader("Top Queried Domains") }
        topEntries(stats.topQueriedDomains, valueFormatter = ::formatTopCount)

        item { StatisticsSectionHeader("Top Blocked Domains") }
        topEntries(stats.topBlockedDomains, valueFormatter = ::formatTopCount)

        item { StatisticsSectionHeader("Top Clients") }
        topEntries(stats.topClients, valueFormatter = ::formatTopCount)

        item { StatisticsSectionHeader("Upstream Responses") }
        topEntries(stats.topUpstreamResponses, valueFormatter = ::formatTopCount)

        item { StatisticsSectionHeader("Upstream Average Time") }
        topEntries(
            stats.topUpstreamAverageSeconds,
            valueFormatter = ::formatTopDuration
        )

        item {
            OutlinedButton(
                onClick = onReset,
                enabled = !refreshing,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("重置 Statistics")
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.topEntries(
    entries: List<AghStatsTopEntry>,
    valueFormatter: (Double) -> String
) {
    if (entries.isEmpty()) {
        item {
            Text(
                "暂无数据",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    items(entries.take(10), key = { it.key }) { entry ->
        ListItem(
            headlineContent = {
                Text(
                    entry.key.ifBlank { "(未知)" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = {
                Text(valueFormatter(entry.value))
            }
        )
        HorizontalDivider()
    }
}

@Composable
private fun StatMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                modifier = Modifier.padding(top = 5.dp),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun TrendCard(
    title: String,
    values: List<Long>,
    unit: String
) {
    val recent = values.takeLast(24)
    val max = recent.maxOrNull()?.coerceAtLeast(1L) ?: 1L

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                "最近 " + recent.size + " 个" + if (unit == "days") "日区间" else "小时区间" +
                    " · 合计 " + formatCount(recent.sum()),
                modifier = Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (recent.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(58.dp).padding(top = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    recent.forEach { value ->
                        val ratio = value.toFloat() / max.toFloat()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height((4f + 46f * ratio).dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.shapes.extraSmall
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsSettingsDialog(
    config: AghStatsConfig,
    dirty: Boolean,
    saving: Boolean,
    onChange: (AghStatsConfig) -> Unit,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val standardIntervals = listOf(
        AghStatisticsRepository.HOUR_MS to "1 小时",
        6L * AghStatisticsRepository.HOUR_MS to "6 小时",
        AghStatisticsRepository.DAY_MS to "1 天",
        7L * AghStatisticsRepository.DAY_MS to "7 天",
        30L * AghStatisticsRepository.DAY_MS to "30 天",
        90L * AghStatisticsRepository.DAY_MS to "90 天"
    )
    val intervalOptions = remember(config.intervalMs) {
        (standardIntervals + listOf(config.intervalMs to formatInterval(config.intervalMs)))
            .filter { it.first >= AghStatisticsRepository.HOUR_MS }
            .distinctBy { it.first }
            .sortedBy { it.first }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Statistics 设置") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    ListItem(
                        headlineContent = { Text("启用 Statistics") },
                        trailingContent = {
                            Switch(
                                checked = config.enabled,
                                onCheckedChange = {
                                    onChange(config.copy(enabled = it))
                                },
                                enabled = !saving
                            )
                        }
                    )
                }

                item {
                    Text(
                        "保留周期",
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                items(intervalOptions, key = { it.first }) { (value, label) ->
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
                        supportingContent = { Text("这些域名不计入 Statistics") },
                        trailingContent = {
                            Switch(
                                checked = config.ignoredEnabled,
                                onCheckedChange = {
                                    onChange(config.copy(ignoredEnabled = it))
                                },
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
                        minLines = 4,
                        label = { Text("忽略域名（每行一个）") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = dirty && !saving
            ) {
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

@Composable
private fun StatisticsSectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

private fun formatCount(value: Long): String =
    when {
        value >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", value / 1_000_000_000.0)
        value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 1_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }

private fun formatTopCount(value: Double): String = formatCount(value.roundToLong())

private fun formatDurationSeconds(value: Double): String =
    when {
        value < 0.001 -> String.format(Locale.US, "%.0f μs", value * 1_000_000.0)
        value < 1.0 -> String.format(Locale.US, "%.1f ms", value * 1_000.0)
        else -> String.format(Locale.US, "%.2f s", value)
    }

private fun formatTopDuration(value: Double): String = formatDurationSeconds(value)

private fun formatInterval(milliseconds: Long): String =
    when {
        milliseconds % (30L * AghStatisticsRepository.DAY_MS) == 0L ->
            (milliseconds / (30L * AghStatisticsRepository.DAY_MS)).toString() + " × 30 天"
        milliseconds % AghStatisticsRepository.DAY_MS == 0L ->
            (milliseconds / AghStatisticsRepository.DAY_MS).toString() + " 天"
        else ->
            (milliseconds / AghStatisticsRepository.HOUR_MS).toString() + " 小时"
    }
