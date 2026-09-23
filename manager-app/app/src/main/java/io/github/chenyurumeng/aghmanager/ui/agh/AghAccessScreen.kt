package io.github.chenyurumeng.aghmanager.ui.agh

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.model.AghAccessList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AghAccessScreen(
    viewModel: AghAccessViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmBack by rememberSaveable { mutableStateOf(false) }
    val pending = state.pending

    fun requestBack() {
        if (state.dirty) confirmBack = true else onBack()
    }

    BackHandler(enabled = !state.applying, onBack = ::requestBack)

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = { Text(viewModel.instance.label + " · Access Control") },
            navigationIcon = {
                IconButton(onClick = ::requestBack, enabled = !state.applying) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(
                    onClick = viewModel::refresh,
                    enabled = !state.applying && !state.loading && !state.dirty
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "访问控制直接影响哪些 DNS 客户端能够使用此 AGH 实例。",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            "允许客户端列表非空时，相当于启用 allowlist；请确认需要访问 DNS 的本机、局域网或 ClientID 已包含在其中。",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "所有修改只保存在草稿中，按“应用”后才写入 AGH。",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (pending != null) {
                item {
                    AccessEditor(
                        value = pending,
                        enabled = !state.applying,
                        onChange = viewModel::setPending
                    )
                }
            } else if (!state.loading) {
                item {
                    Text(
                        "Access Control 尚未读取成功。",
                        modifier = Modifier.padding(16.dp),
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
            title = { Text("放弃 Access Control 修改？") },
            text = { Text("当前草稿尚未写入 AdGuard Home。") },
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
private fun AccessEditor(
    value: AghAccessList,
    enabled: Boolean,
    onChange: (AghAccessList) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = value.allowedClients.joinToString("\n"),
            onValueChange = {
                onChange(value.copy(allowedClients = parseAccessLines(it)))
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            label = { Text("允许客户端") },
            supportingText = {
                Text("每行一个 IP、CIDR 或 ClientID；留空表示不启用客户端 allowlist")
            }
        )

        OutlinedTextField(
            value = value.disallowedClients.joinToString("\n"),
            onValueChange = {
                onChange(value.copy(disallowedClients = parseAccessLines(it)))
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            minLines = 4,
            label = { Text("禁止客户端") },
            supportingText = { Text("每行一个 IP、CIDR 或 ClientID") }
        )

        OutlinedTextField(
            value = value.blockedHosts.joinToString("\n"),
            onValueChange = {
                onChange(value.copy(blockedHosts = parseAccessLines(it)))
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            minLines = 4,
            label = { Text("禁止域名") },
            supportingText = { Text("每行一个主机名或域名") }
        )
    }
}

private fun parseAccessLines(value: String): List<String> =
    value.lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .toList()
