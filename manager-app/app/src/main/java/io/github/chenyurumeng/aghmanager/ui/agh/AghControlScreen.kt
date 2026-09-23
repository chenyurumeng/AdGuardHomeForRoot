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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.model.AghDnsConfig
import io.github.chenyurumeng.aghmanager.model.AghInstance
import io.github.chenyurumeng.aghmanager.model.AghStructuralConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AghControlScreen(
    viewModel: AghControlViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenQueryLog: () -> Unit,
    onOpenClients: () -> Unit,
    onOpenRewrites: () -> Unit,
    onOpenAccess: () -> Unit,
    onOpenBlockedServices: () -> Unit,
    onOpenProtection: () -> Unit,
    onOpenTls: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenWeb: (String, String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCredentialDialog by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var foreignUpstreamUnlocked by remember { mutableStateOf(false) }

    BackHandler(
        enabled = !state.applyingStructure && !state.applyingDns,
        onBack = onBack
    )

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = { Text(viewModel.instance.label + " AGH") },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    enabled = !state.applyingStructure && !state.applyingDns
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            }
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                SummaryCard(
                    instance = viewModel.instance,
                    structure = state.pendingStructure,
                    apiAvailable = state.apiAvailable
                )
            }

            item { SectionHeader("管理凭据") }
            item {
                ListItem(
                    headlineContent = {
                        Text(if (state.credentialBound) "已绑定" else "未绑定")
                    },
                    supportingContent = {
                        Text(
                            if (state.credentialBound) {
                                "用户名 " + state.credentialUsername +
                                    " · 密码由 Android Keystore 保护"
                            } else {
                                "绑定后才能使用 DNS / 过滤 / 查询日志等原生 API"
                            }
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = { showCredentialDialog = true }) {
                            Text(if (state.credentialBound) "重新绑定" else "绑定")
                        }
                    }
                )
            }
            if (state.credentialBound) {
                item {
                    TextButton(
                        onClick = viewModel::clearCredential,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text("清除保存凭据")
                    }
                }
            }

            item { SectionHeader("监听与账户") }
            item {
                StructureEditor(
                    current = state.structure,
                    pending = state.pendingStructure,
                    newPassword = newPassword,
                    confirmPassword = confirmPassword,
                    enabled = !state.applyingStructure,
                    onChange = viewModel::setStructure,
                    onNewPasswordChange = { newPassword = it },
                    onConfirmPasswordChange = { confirmPassword = it }
                )
            }

            if (state.structureDirty || newPassword.isNotBlank() || confirmPassword.isNotBlank()) {
                item {
                    PendingActions(
                        label = if (state.applyingStructure) "正在应用结构配置…" else "监听/账户有待应用修改",
                        applying = state.applyingStructure,
                        onDiscard = {
                            viewModel.discardStructure()
                            newPassword = ""
                            confirmPassword = ""
                        },
                        onApply = {
                            viewModel.applyStructure(newPassword, confirmPassword)
                            newPassword = ""
                            confirmPassword = ""
                        }
                    )
                }
            }

            item { SectionHeader("DNS 设置") }
            when {
                !state.credentialBound -> {
                    item {
                        HintCard("请先绑定 AGH 管理凭据，再读取和修改 DNS 设置。")
                    }
                }
                state.pendingDns == null -> {
                    item {
                        HintCard(
                            state.error.ifBlank {
                                "AGH API 暂不可用，请确认实例正在运行并刷新。"
                            }
                        )
                    }
                }
                else -> {
                    item {
                        DnsEditor(
                            instance = viewModel.instance,
                            current = state.dns ?: state.pendingDns!!,
                            pending = state.pendingDns!!,
                            foreignUpstreamUnlocked = foreignUpstreamUnlocked,
                            enabled = !state.applyingDns,
                            onUnlockForeign = { foreignUpstreamUnlocked = true },
                            onChange = viewModel::setDns
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = viewModel::testUpstreams,
                                enabled = !state.testingUpstreams && !state.applyingDns,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (state.testingUpstreams) "测试中…" else "测试上游")
                            }
                            OutlinedButton(
                                onClick = viewModel::clearCache,
                                enabled = !state.applyingDns,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("清空 DNS Cache")
                            }
                        }
                    }

                    if (state.upstreamTests.isNotEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                tonalElevation = 2.dp,
                                shape = MaterialTheme.shapes.large
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("上游测试", style = MaterialTheme.typography.titleSmall)
                                    state.upstreamTests.forEach {
                                        Text(
                                            (if (it.ok) "✓ " else "✕ ") +
                                                it.server + " · " + it.result,
                                            modifier = Modifier.padding(top = 4.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (it.ok) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (state.dnsDirty) {
                        item {
                            PendingActions(
                                label = if (state.applyingDns) "正在应用 DNS 设置…" else "DNS 设置有待应用修改",
                                applying = state.applyingDns,
                                onDiscard = viewModel::discardDns,
                                onApply = viewModel::applyDns
                            )
                        }
                    }
                }
            }

            item { SectionHeader("客户端与 DNS Rewrite") }
            item {
                ListItem(
                    modifier = Modifier.clickable(
                        enabled = state.credentialBound && state.apiAvailable,
                        onClick = onOpenClients
                    ),
                    headlineContent = { Text("Clients") },
                    supportingContent = {
                        Text(
                            if (state.credentialBound && state.apiAvailable) {
                                "持久客户端、自动发现、客户端专用 DNS 设置"
                            } else {
                                "需要先绑定有效 AGH 管理凭据"
                            }
                        )
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = "打开 Clients")
                    }
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(
                        enabled = state.credentialBound && state.apiAvailable,
                        onClick = onOpenRewrites
                    ),
                    headlineContent = { Text("DNS Rewrite") },
                    supportingContent = {
                        Text(
                            if (state.credentialBound && state.apiAvailable) {
                                "域名重写、A/AAAA/CNAME Answer、规则启停"
                            } else {
                                "需要先绑定有效 AGH 管理凭据"
                            }
                        )
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = "打开 DNS Rewrite")
                    }
                )
            }

            item { SectionHeader("安全与访问控制") }
            item {
                ListItem(
                    modifier = Modifier.clickable(
                        enabled = state.credentialBound && state.apiAvailable,
                        onClick = onOpenProtection
                    ),
                    headlineContent = { Text("安全保护") },
                    supportingContent = {
                        Text(
                            if (state.credentialBound && state.apiAvailable) {
                                "Safe Browsing、Safe Search 与 Parental Control"
                            } else {
                                "需要先绑定有效 AGH 管理凭据"
                            }
                        )
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = "打开安全保护")
                    }
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(
                        enabled = state.credentialBound && state.apiAvailable,
                        onClick = onOpenAccess
                    ),
                    headlineContent = { Text("Access Control") },
                    supportingContent = {
                        Text(
                            if (state.credentialBound && state.apiAvailable) {
                                "允许/禁止 DNS 客户端与禁止域名"
                            } else {
                                "需要先绑定有效 AGH 管理凭据"
                            }
                        )
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = "打开 Access Control")
                    }
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(
                        enabled = state.credentialBound && state.apiAvailable,
                        onClick = onOpenBlockedServices
                    ),
                    headlineContent = { Text("Blocked Services") },
                    supportingContent = {
                        Text(
                            if (state.credentialBound && state.apiAvailable) {
                                "按服务阻止网站/应用，保留现有时间表"
                            } else {
                                "需要先绑定有效 AGH 管理凭据"
                            }
                        )
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = "打开 Blocked Services")
                    }
                )
            }

            item { SectionHeader("加密 DNS") }
            item {
                ListItem(
                    modifier = Modifier.clickable(
                        enabled = state.credentialBound && state.apiAvailable,
                        onClick = onOpenTls
                    ),
                    headlineContent = { Text("TLS / Encrypted DNS") },
                    supportingContent = {
                        Text(
                            if (state.credentialBound && state.apiAvailable) {
                                "HTTPS/DoH、DoT、DoQ、证书与私钥管理"
                            } else {
                                "需要先绑定有效 AGH 管理凭据"
                            }
                        )
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = "打开 TLS 管理")
                    }
                )
            }

            item { SectionHeader("统计") }
            item {
                ListItem(
                    modifier = Modifier.clickable(
                        enabled = state.credentialBound && state.apiAvailable,
                        onClick = onOpenStatistics
                    ),
                    headlineContent = { Text("Statistics") },
                    supportingContent = {
                        Text(
                            if (state.credentialBound && state.apiAvailable) {
                                "DNS 查询、阻止率、Top 域名/客户端/上游与趋势"
                            } else {
                                "需要先绑定有效 AGH 管理凭据"
                            }
                        )
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = "打开 Statistics")
                    }
                )
            }

            item { SectionHeader("过滤规则") }
            item {
                ListItem(
                    modifier = Modifier.clickable(
                        enabled = state.credentialBound && state.apiAvailable,
                        onClick = onOpenFilters
                    ),
                    headlineContent = { Text("过滤器与 User Rules") },
                    supportingContent = {
                        Text(
                            if (state.credentialBound && state.apiAvailable) {
                                "订阅过滤器、白名单、User Rules、域名过滤检查"
                            } else {
                                "需要先绑定有效 AGH 管理凭据"
                            }
                        )
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = "打开过滤规则")
                    }
                )
            }

            item { SectionHeader("查询日志") }
            item {
                ListItem(
                    modifier = Modifier.clickable(
                        enabled = state.credentialBound && state.apiAvailable,
                        onClick = onOpenQueryLog
                    ),
                    headlineContent = { Text("Query Log") },
                    supportingContent = {
                        Text(
                            if (state.credentialBound && state.apiAvailable) {
                                "实时查询、搜索筛选、详情、阻止/允许及 User Rule 编辑"
                            } else {
                                "需要先绑定有效 AGH 管理凭据"
                            }
                        )
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = "打开 Query Log")
                    }
                )
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

            item { SectionHeader("高级入口") }
            item {
                ListItem(
                    modifier = Modifier.clickable {
                        onOpenWeb(
                            viewModel.instance.label + " AGH",
                            state.pendingStructure.webUrl()
                        )
                    },
                    headlineContent = { Text("打开完整 AGH WebUI") },
                    supportingContent = { Text(state.pendingStructure.webUrl()) },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = "打开 WebUI")
                    }
                )
            }
        }
    }

    if (showCredentialDialog) {
        CredentialDialog(
            defaultUsername = state.structure.username,
            busy = state.loading,
            onDismiss = { showCredentialDialog = false },
            onBind = { username, password ->
                showCredentialDialog = false
                viewModel.bindCredential(username, password)
            }
        )
    }
}

@Composable
private fun SummaryCard(
    instance: AghInstance,
    structure: AghStructuralConfig,
    apiAvailable: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(instance.label, style = MaterialTheme.typography.titleLarge)
            Text(
                "DNS " + structure.dnsBindHosts.joinToString(", ") +
                    ":" + structure.dnsPort,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Web " + structure.webUrl(),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                if (apiAvailable) "Native API Ready" else "Native API 未连接",
                modifier = Modifier.padding(top = 6.dp),
                color = if (apiAvailable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun StructureEditor(
    current: AghStructuralConfig,
    pending: AghStructuralConfig,
    newPassword: String,
    confirmPassword: String,
    enabled: Boolean,
    onChange: (AghStructuralConfig) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = pending.webHost,
            onValueChange = { onChange(pending.copy(webHost = it.trim())) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Web 监听地址") },
            supportingText = {
                if (pending.webHost == "0.0.0.0" || pending.webHost == "::") {
                    Text("管理界面将暴露到网络接口，请确保密码足够安全")
                }
            }
        )
        OutlinedTextField(
            value = pending.webPort.toString(),
            onValueChange = {
                onChange(pending.copy(webPort = it.filter(Char::isDigit).toIntOrNull() ?: 0))
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text("Web 端口") }
        )
        OutlinedTextField(
            value = pending.dnsBindHosts.joinToString("\n"),
            onValueChange = { onChange(pending.copy(dnsBindHosts = parseLines(it))) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            minLines = 2,
            label = { Text("DNS 监听地址（每行一个）") }
        )
        OutlinedTextField(
            value = pending.dnsPort.toString(),
            onValueChange = {
                onChange(pending.copy(dnsPort = it.filter(Char::isDigit).toIntOrNull() ?: 0))
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text("DNS 端口") },
            supportingText = {
                if (pending.dnsPort != current.dnsPort) {
                    Text("端口变化会联动 AGH settings 与 Box settings，并重建 DNS 路由")
                }
            }
        )
        OutlinedTextField(
            value = pending.username,
            onValueChange = { onChange(pending.copy(username = it.trim())) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
            label = { Text("管理员用户名") }
        )
        OutlinedTextField(
            value = newPassword,
            onValueChange = onNewPasswordChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("新密码（留空则不修改）") }
        )
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("确认新密码") }
        )
    }
}

@Composable
private fun DnsEditor(
    instance: AghInstance,
    current: AghDnsConfig,
    pending: AghDnsConfig,
    foreignUpstreamUnlocked: Boolean,
    enabled: Boolean,
    onUnlockForeign: () -> Unit,
    onChange: (AghDnsConfig) -> Unit
) {
    val upstreamEditable =
        instance != AghInstance.FOREIGN || foreignUpstreamUnlocked

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ToggleSetting(
            title = "DNS 保护",
            checked = pending.protectionEnabled,
            enabled = enabled,
            onChange = { onChange(pending.copy(protectionEnabled = it)) }
        )

        if (instance == AghInstance.FOREIGN && !foreignUpstreamUnlocked) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Foreign 上游保护",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "当前 Foreign 默认经 127.0.0.1:1053 进入 Mihomo DNS。修改上游可能绕过现有防 DNS 泄漏链路。",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onUnlockForeign) {
                        Text("解锁 Foreign 上游编辑")
                    }
                }
            }
        }

        OutlinedTextField(
            value = pending.upstreamDns.joinToString("\n"),
            onValueChange = {
                if (upstreamEditable) {
                    onChange(pending.copy(upstreamDns = parseLines(it)))
                }
            },
            enabled = enabled && upstreamEditable,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            minLines = 3,
            label = { Text("上游 DNS（每行一个）") }
        )
        OutlinedTextField(
            value = pending.bootstrapDns.joinToString("\n"),
            onValueChange = { onChange(pending.copy(bootstrapDns = parseLines(it))) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            minLines = 2,
            label = { Text("Bootstrap DNS") }
        )
        OutlinedTextField(
            value = pending.fallbackDns.joinToString("\n"),
            onValueChange = { onChange(pending.copy(fallbackDns = parseLines(it))) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            minLines = 2,
            label = { Text("Fallback DNS") }
        )

        Text(
            "上游模式",
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelLarge
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "load_balance" to "负载均衡",
                "parallel" to "并行",
                "fastest_addr" to "最快地址"
            ).forEach { (value, label) ->
                FilterChip(
                    selected = pending.upstreamMode == value,
                    onClick = { onChange(pending.copy(upstreamMode = value)) },
                    label = { Text(label) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        ToggleSetting(
            "启用缓存",
            pending.cacheEnabled,
            enabled
        ) { onChange(pending.copy(cacheEnabled = it)) }

        OutlinedTextField(
            value = pending.cacheSize.toString(),
            onValueChange = {
                onChange(pending.copy(cacheSize = it.filter(Char::isDigit).toLongOrNull() ?: 0L))
            },
            enabled = enabled && pending.cacheEnabled,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text("Cache 大小（bytes）") }
        )

        ToggleSetting(
            "Optimistic Cache",
            pending.cacheOptimistic,
            enabled && pending.cacheEnabled
        ) { onChange(pending.copy(cacheOptimistic = it)) }

        ToggleSetting(
            "DNSSEC",
            pending.dnssecEnabled,
            enabled
        ) { onChange(pending.copy(dnssecEnabled = it)) }

        ToggleSetting(
            "禁用 AAAA / IPv6 响应",
            pending.disableIpv6,
            enabled
        ) { onChange(pending.copy(disableIpv6 = it)) }

        OutlinedTextField(
            value = pending.ratelimit.toString(),
            onValueChange = {
                onChange(pending.copy(ratelimit = it.filter(Char::isDigit).toIntOrNull() ?: 0))
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text("Rate limit（0 = 不限制）") }
        )

        OutlinedTextField(
            value = pending.upstreamTimeout.toString(),
            onValueChange = {
                onChange(
                    pending.copy(
                        upstreamTimeout = it.filter(Char::isDigit).toIntOrNull() ?: 1
                    )
                )
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text("上游超时（秒）") }
        )

        if (current != pending) {
            Text(
                "修改尚未生效，点击下方“应用”后一次提交。",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ToggleSetting(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
        headlineContent = { Text(title) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled
            )
        }
    )
    HorizontalDivider()
}

@Composable
private fun PendingActions(
    label: String,
    applying: Boolean,
    onDiscard: () -> Unit,
    onApply: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        tonalElevation = 3.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDiscard,
                    enabled = !applying,
                    modifier = Modifier.weight(1f)
                ) { Text("放弃") }
                Button(
                    onClick = onApply,
                    enabled = !applying,
                    modifier = Modifier.weight(1f)
                ) { Text(if (applying) "应用中…" else "应用") }
            }
        }
    }
}

@Composable
private fun CredentialDialog(
    defaultUsername: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onBind: (String, String) -> Unit
) {
    var username by remember(defaultUsername) { mutableStateOf(defaultUsername) }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("绑定 AGH 管理凭据") },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("用户名") }
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("密码") }
                )
                Text(
                    "凭据只用于本机 AGH API，并由 Android Keystore 加密保存；不会进入日志或诊断报告。",
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onBind(username.trim(), password) },
                enabled = !busy && username.isNotBlank() && password.isNotBlank()
            ) { Text("验证并保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        }
    )
}

@Composable
private fun HintCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

private fun parseLines(value: String): List<String> =
    value.lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toList()
