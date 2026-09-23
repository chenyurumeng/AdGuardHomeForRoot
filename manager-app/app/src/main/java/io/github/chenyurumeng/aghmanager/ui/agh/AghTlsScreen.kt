package io.github.chenyurumeng.aghmanager.ui.agh

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.model.AghTlsDraft
import io.github.chenyurumeng.aghmanager.model.AghTlsValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AghTlsScreen(
    viewModel: AghTlsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmBack by rememberSaveable { mutableStateOf(false) }
    var confirmKeyClear by remember { mutableStateOf(false) }
    var showPrivateKey by remember { mutableStateOf(false) }

    val certificatePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                readTextFile(context, uri)
                    .onSuccess(viewModel::setCertificatePem)
                    .onFailure {
                        viewModel.reportError(it.message ?: "证书文件读取失败")
                    }
            }
        }
    }
    val privateKeyPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                readTextFile(context, uri)
                    .onSuccess(viewModel::setPrivateKeyPem)
                    .onFailure {
                        viewModel.reportError(it.message ?: "私钥文件读取失败")
                    }
            }
        }
    }

    fun requestBack() {
        if (state.dirty) confirmBack = true else onBack()
    }

    BackHandler(enabled = !state.applying && !state.validating, onBack = ::requestBack)

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = { Text(viewModel.instance.label + " · TLS / Encrypted DNS") },
            navigationIcon = {
                IconButton(
                    onClick = ::requestBack,
                    enabled = !state.applying && !state.validating
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(
                    onClick = viewModel::refresh,
                    enabled = !state.loading &&
                        !state.applying &&
                        !state.validating &&
                        !state.dirty
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        )

        if (state.loading || state.applying || state.validating) {
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
                        Text("官方 AGH TLS API", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "HTTPS 端口同时承载 DoH（默认路径 /dns-query）。私钥不会从 AGH 回传，也不会写入 APK 本地持久化存储。",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (current != null) {
                            Text(
                                "Plain DNS：" +
                                    (if (current.servePlainDns) "保留" else "关闭") +
                                    " · Force HTTPS：" +
                                    (if (current.forceHttps) "开启" else "关闭"),
                                modifier = Modifier.padding(top = 6.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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

            if (pending == null || current == null) {
                if (!state.loading) {
                    item {
                        Text(
                            "暂时无法读取 TLS 配置。",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item { SectionLabel("服务") }
                item {
                    ListItem(
                        modifier = Modifier.clickable(
                            enabled = !state.applying && !state.validating
                        ) {
                            viewModel.update { it.copy(enabled = !it.enabled) }
                        },
                        headlineContent = { Text("启用加密 DNS / HTTPS") },
                        supportingContent = {
                            Text("控制 AGH TLS 总开关；Plain DNS 保持现有值，不由 RC16 自动关闭。")
                        },
                        trailingContent = {
                            Switch(
                                checked = pending.enabled,
                                onCheckedChange = null,
                                enabled = !state.applying && !state.validating
                            )
                        }
                    )
                }
                item {
                    OutlinedTextField(
                        value = pending.serverName,
                        onValueChange = { value ->
                            viewModel.update { it.copy(serverName = value) }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        singleLine = true,
                        enabled = !state.applying && !state.validating,
                        label = { Text("Server Name") },
                        supportingText = { Text("例如 dns.example.com，应与证书 SAN 匹配") }
                    )
                }

                item { SectionLabel("端口") }
                item {
                    PortFields(
                        draft = pending,
                        enabled = !state.applying && !state.validating,
                        onUpdate = viewModel::update
                    )
                }

                item { SectionLabel("证书") }
                item {
                    OutlinedTextField(
                        value = pending.certificatePem,
                        onValueChange = viewModel::setCertificatePem,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .heightIn(min = 120.dp),
                        enabled = !state.applying && !state.validating,
                        label = { Text("Certificate Chain (PEM)") },
                        supportingText = {
                            Text("可粘贴 PEM；导入文件后内容只保留在当前 Pending，Apply 后交给 AGH 保存。")
                        },
                        minLines = 4,
                        maxLines = 10
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                certificatePicker.launch(
                                    arrayOf("text/*", "application/x-pem-file", "application/octet-stream")
                                )
                            },
                            enabled = !state.applying && !state.validating,
                            modifier = Modifier.weight(1f)
                        ) { Text("导入证书文件") }
                        OutlinedButton(
                            onClick = { viewModel.setCertificatePem("") },
                            enabled = !state.applying && !state.validating &&
                                pending.certificatePem.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("清空 PEM") }
                    }
                }
                item {
                    OutlinedTextField(
                        value = pending.certificatePath,
                        onValueChange = viewModel::setCertificatePath,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        singleLine = true,
                        enabled = !state.applying && !state.validating,
                        label = { Text("AGH 服务器证书路径（可选）") },
                        supportingText = { Text("与 PEM 内容二选一；设置路径会清空 Pending PEM。") }
                    )
                }

                item { SectionLabel("私钥") }
                item {
                    OutlinedTextField(
                        value = pending.privateKeyPem,
                        onValueChange = viewModel::setPrivateKeyPem,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .heightIn(min = 120.dp),
                        enabled = !state.applying && !state.validating,
                        label = { Text("Private Key (PEM)") },
                        supportingText = {
                            Text(
                                when {
                                    pending.replacePrivateKey && pending.privateKeyPem.isBlank() &&
                                        pending.privateKeyPath.isBlank() ->
                                        "Pending 将清除当前私钥。"
                                    pending.replacePrivateKey ->
                                        "Pending 使用新的私钥；不会写入 SharedPreferences 或诊断报告。"
                                    current.privateKeySaved ->
                                        "AGH 已保存私钥；服务端不会把原文返回 APK。"
                                    current.privateKeyPath.isNotBlank() ->
                                        "当前使用 AGH 服务器私钥路径。"
                                    else -> "当前没有已保存的私钥。"
                                }
                            )
                        },
                        visualTransformation = if (showPrivateKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        minLines = 4,
                        maxLines = 10
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                privateKeyPicker.launch(
                                    arrayOf("text/*", "application/x-pem-file", "application/octet-stream")
                                )
                            },
                            enabled = !state.applying && !state.validating,
                            modifier = Modifier.weight(1f)
                        ) { Text("导入私钥文件") }
                        OutlinedButton(
                            onClick = { showPrivateKey = !showPrivateKey },
                            enabled = pending.privateKeyPem.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text(if (showPrivateKey) "隐藏私钥" else "显示私钥") }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = viewModel::keepExistingPrivateKey,
                            enabled = !state.applying && !state.validating &&
                                pending.replacePrivateKey,
                            modifier = Modifier.weight(1f)
                        ) { Text("恢复现有私钥") }
                        OutlinedButton(
                            onClick = { confirmKeyClear = true },
                            enabled = !state.applying && !state.validating &&
                                (current.privateKeySaved || current.privateKeyPath.isNotBlank()),
                            modifier = Modifier.weight(1f)
                        ) { Text("清除私钥") }
                    }
                }
                item {
                    OutlinedTextField(
                        value = pending.privateKeyPath,
                        onValueChange = viewModel::setPrivateKeyPath,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        singleLine = true,
                        enabled = !state.applying && !state.validating,
                        label = { Text("AGH 服务器私钥路径（可选）") },
                        supportingText = { Text("与 PEM 私钥二选一；修改路径视为替换私钥来源。") }
                    )
                }

                item { SectionLabel("验证状态") }
                item {
                    ValidationPanel(state.validation ?: current.validation)
                }

                if (state.dirty) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = viewModel::discard,
                                enabled = !state.applying && !state.validating,
                                modifier = Modifier.weight(1f)
                            ) { Text("放弃") }
                            OutlinedButton(
                                onClick = viewModel::validate,
                                enabled = !state.applying && !state.validating,
                                modifier = Modifier.weight(1f)
                            ) { Text(if (state.validating) "验证中…" else "验证") }
                            Button(
                                onClick = viewModel::apply,
                                enabled = !state.applying && !state.validating,
                                modifier = Modifier.weight(1f)
                            ) { Text(if (state.applying) "应用中…" else "应用") }
                        }
                    }
                }
            }
        }
    }

    if (confirmBack) {
        AlertDialog(
            onDismissRequest = { confirmBack = false },
            title = { Text("放弃 TLS 修改？") },
            text = { Text("当前 Pending 中可能包含尚未写入 AGH 的证书或私钥内容。") },
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
                TextButton(onClick = { confirmBack = false }) { Text("继续编辑") }
            }
        )
    }

    if (confirmKeyClear) {
        AlertDialog(
            onDismissRequest = { confirmKeyClear = false },
            title = { Text("清除 AGH 私钥？") },
            text = {
                Text("这只修改 Pending。点击页面“应用”后才会提交；AGH 不会把旧私钥返回，因此请确保你保留了原始私钥。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmKeyClear = false
                        viewModel.clearPrivateKey()
                    }
                ) { Text("加入 Pending") }
            },
            dismissButton = {
                TextButton(onClick = { confirmKeyClear = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PortFields(
    draft: AghTlsDraft,
    enabled: Boolean,
    onUpdate: ((AghTlsDraft) -> AghTlsDraft) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = draft.httpsPort,
            onValueChange = { value -> onUpdate { it.copy(httpsPort = value) } },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            singleLine = true,
            enabled = enabled,
            label = { Text("HTTPS / DoH Port") },
            supportingText = { Text("0 = 关闭 HTTPS/DoH") }
        )
        OutlinedTextField(
            value = draft.dotPort,
            onValueChange = { value -> onUpdate { it.copy(dotPort = value) } },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            singleLine = true,
            enabled = enabled,
            label = { Text("DNS-over-TLS Port") },
            supportingText = { Text("0 = 关闭 DoT") }
        )
        OutlinedTextField(
            value = draft.doqPort,
            onValueChange = { value -> onUpdate { it.copy(doqPort = value) } },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            singleLine = true,
            enabled = enabled,
            label = { Text("DNS-over-QUIC Port") },
            supportingText = { Text("0 = 关闭 DoQ") }
        )
    }
}

@Composable
private fun ValidationPanel(value: AghTlsValidation) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "证书 " + mark(value.validCert) +
                    " · CA Chain " + mark(value.validChain) +
                    " · 私钥 " + mark(value.validKey) +
                    " · Pair " + mark(value.validPair),
                style = MaterialTheme.typography.bodyMedium
            )
            if (value.subject.isNotBlank()) {
                Text("Subject: " + value.subject, style = MaterialTheme.typography.bodySmall)
            }
            if (value.issuer.isNotBlank()) {
                Text("Issuer: " + value.issuer, style = MaterialTheme.typography.bodySmall)
            }
            if (value.keyType.isNotBlank()) {
                Text("Key: " + value.keyType, style = MaterialTheme.typography.bodySmall)
            }
            if (value.notAfter.isNotBlank()) {
                Text("有效期至: " + value.notAfter, style = MaterialTheme.typography.bodySmall)
            }
            if (value.dnsNames.isNotEmpty()) {
                Text(
                    "DNS Names: " + value.dnsNames.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (value.warning.isNotBlank()) {
                Text(
                    value.warning,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(value: String) {
    Text(
        value,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

private fun mark(value: Boolean): String = if (value) "✓" else "—"

private suspend fun readTextFile(context: Context, uri: Uri): Result<String> =
    withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 512 * 1024) {
                        "证书/私钥文件不能超过 512 KiB"
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: throw IllegalStateException("无法读取所选文件")
            bytes.toString(Charsets.UTF_8)
        }
    }
