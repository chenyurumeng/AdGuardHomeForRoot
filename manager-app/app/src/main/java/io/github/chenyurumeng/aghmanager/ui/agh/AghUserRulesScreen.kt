package io.github.chenyurumeng.aghmanager.ui.agh

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AghUserRulesScreen(
    viewModel: AghUserRulesViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmBack by rememberSaveable { mutableStateOf(false) }

    fun requestBack() {
        if (state.dirty) confirmBack = true else onBack()
    }

    BackHandler(enabled = !state.saving, onBack = ::requestBack)

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        TopAppBar(
            title = {
                Column {
                    Text(viewModel.instance.label + " · User Rules")
                    Text(
                        state.rules.size.toString() + " 条规则",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = ::requestBack, enabled = !state.saving) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(
                    onClick = viewModel::refresh,
                    enabled = !state.loading && !state.saving && !state.dirty
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "重新读取")
                }
            }
        )

        Text(
            "支持 AdGuard DNS 过滤语法。每行一条，例如 ||example.com^ 或 @@||example.com^。保存前会再次读取服务器规则，若 WebUI 已有新修改则拒绝覆盖。",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = state.text,
            onValueChange = viewModel::setText,
            enabled = !state.loading && !state.saving,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            label = { Text("User Rules") },
            placeholder = {
                Text("||ads.example.com^\n@@||allowed.example.com^\n# comment")
            }
        )

        if (state.error.isNotBlank()) {
            Text(
                state.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = viewModel::discard,
                enabled = state.dirty && !state.saving,
                modifier = Modifier.weight(1f)
            ) { Text("放弃") }
            Button(
                onClick = viewModel::save,
                enabled = state.dirty && !state.saving,
                modifier = Modifier.weight(1f)
            ) { Text(if (state.saving) "保存中…" else "保存并生效") }
        }
    }

    if (confirmBack) {
        AlertDialog(
            onDismissRequest = { confirmBack = false },
            title = { Text("放弃未保存的 User Rules？") },
            text = { Text("当前编辑内容尚未写入 AdGuard Home。") },
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
}
