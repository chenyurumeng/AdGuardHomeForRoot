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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.model.AghProtectionConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AghProtectionScreen(
    viewModel: AghProtectionViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmBack by remember { mutableStateOf(false) }

    fun requestBack() {
        if (state.dirty) confirmBack = true else onBack()
    }

    BackHandler(enabled = !state.applying, onBack = ::requestBack)

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = { Text(viewModel.instance.label + " · 安全保护") },
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

        val current = state.current
        val pending = state.pending

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Protection Services", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "修改只进入 Pending；点击“应用”前会重新读取服务器状态，检测到 WebUI 并发修改时拒绝覆盖。",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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

            if (current == null || pending == null) {
                if (!state.loading) {
                    item {
                        Text(
                            "暂时无法读取安全保护设置。",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item { SectionTitle("基础保护") }
                item {
                    ProtectionToggleRow(
                        title = "Safe Browsing",
                        description = "阻止已知恶意软件与钓鱼网站",
                        current = current.safeBrowsingEnabled,
                        pending = pending.safeBrowsingEnabled,
                        enabled = !state.applying,
                        onChange = viewModel::setSafeBrowsing
                    )
                }
                item { HorizontalDivider() }
                item {
                    ProtectionToggleRow(
                        title = "Parental Control",
                        description = if (current.parentalSensitivity == null) {
                            "阻止成人与露骨内容"
                        } else {
                            "阻止成人与露骨内容 · Sensitivity " +
                                current.parentalSensitivity +
                                "（当前 API 只读）"
                        },
                        current = current.parentalEnabled,
                        pending = pending.parentalEnabled,
                        enabled = !state.applying,
                        onChange = viewModel::setParental
                    )
                }

                item { SectionTitle("Safe Search") }
                item {
                    ProtectionToggleRow(
                        title = "Safe Search",
                        description = "为支持的搜索服务强制安全搜索",
                        current = current.safeSearch.enabled,
                        pending = pending.safeSearch.enabled,
                        enabled = !state.applying,
                        onChange = viewModel::setSafeSearch
                    )
                }
                item { HorizontalDivider() }

                safeSearchRows(
                    current = current,
                    pending = pending
                ).forEach { row ->
                    item(key = row.key) {
                        ProviderToggleRow(
                            title = row.label,
                            current = row.current,
                            pending = row.pending,
                            enabled = !state.applying,
                            onChange = { viewModel.setSafeSearchEngine(row.key, it) }
                        )
                    }
                    item(key = row.key + "-divider") { HorizontalDivider() }
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
    }

    if (confirmBack) {
        AlertDialog(
            onDismissRequest = { confirmBack = false },
            title = { Text("放弃安全保护修改？") },
            text = { Text("当前 Pending 修改尚未写入 AdGuard Home。") },
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

private data class SafeSearchRow(
    val key: String,
    val label: String,
    val current: Boolean,
    val pending: Boolean
)

private fun safeSearchRows(
    current: AghProtectionConfig,
    pending: AghProtectionConfig
): List<SafeSearchRow> {
    val rows = mutableListOf<SafeSearchRow>()
    fun add(key: String, label: String, currentValue: Boolean?, pendingValue: Boolean?) {
        if (currentValue != null && pendingValue != null) {
            rows += SafeSearchRow(key, label, currentValue, pendingValue)
        }
    }

    add("bing", "Bing", current.safeSearch.bing, pending.safeSearch.bing)
    add(
        "duckduckgo",
        "DuckDuckGo",
        current.safeSearch.duckDuckGo,
        pending.safeSearch.duckDuckGo
    )
    add("ecosia", "Ecosia", current.safeSearch.ecosia, pending.safeSearch.ecosia)
    add("google", "Google", current.safeSearch.google, pending.safeSearch.google)
    add("pixabay", "Pixabay", current.safeSearch.pixabay, pending.safeSearch.pixabay)
    add("yandex", "Yandex", current.safeSearch.yandex, pending.safeSearch.yandex)
    add("youtube", "YouTube", current.safeSearch.youtube, pending.safeSearch.youtube)
    return rows
}

@Composable
private fun ProtectionToggleRow(
    title: String,
    description: String,
    current: Boolean,
    pending: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { onChange(!pending) },
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(description)
                Text(
                    "当前：" + stateText(current) + " · Pending：" + stateText(pending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = pending,
                onCheckedChange = null,
                enabled = enabled
            )
        }
    )
}

@Composable
private fun ProviderToggleRow(
    title: String,
    current: Boolean,
    pending: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { onChange(!pending) },
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                "当前：" + stateText(current) + " · Pending：" + stateText(pending),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(
                checked = pending,
                onCheckedChange = null,
                enabled = enabled
            )
        }
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

private fun stateText(value: Boolean): String = if (value) "开启" else "关闭"
