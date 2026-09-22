package io.github.chenyurumeng.aghmanager.ui.agh

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenyurumeng.aghmanager.model.AghInstanceState
import io.github.chenyurumeng.aghmanager.ui.theme.StatusError
import io.github.chenyurumeng.aghmanager.ui.theme.StatusHealthy

@Composable
fun AghScreen(
    viewModel: AghViewModel,
    contentPadding: PaddingValues,
    onOpenWeb: (String, String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busyAction by viewModel.busyAction.collectAsStateWithLifecycle()
    val enabled = busyAction == null

    LaunchedEffect(Unit) { viewModel.refresh() }

    LazyColumn(contentPadding = contentPadding) {
        item {
            Text(
                "Dual DNS · Domestic / Foreign",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item { SectionHeader("全部实例") }
        item {
            ActionRow(
                enabled = enabled,
                onStart = viewModel::startAll,
                onRestart = viewModel::restartAll,
                onStop = viewModel::stopAll
            )
        }

        item { SectionHeader("Domestic") }
        item {
            InstanceCard(
                instance = state.domestic,
                enabled = enabled,
                onStart = viewModel::startDomestic,
                onRestart = viewModel::restartDomestic,
                onStop = viewModel::stopDomestic,
                onOpenWeb = { onOpenWeb("Domestic AGH", "http://127.0.0.1:3000") }
            )
        }

        item { SectionHeader("Foreign") }
        item {
            InstanceCard(
                instance = state.foreign,
                enabled = enabled,
                onStart = viewModel::startForeign,
                onRestart = viewModel::restartForeign,
                onStop = viewModel::stopForeign,
                onOpenWeb = { onOpenWeb("Foreign AGH", "http://127.0.0.1:3001") }
            )
        }
    }
}

@Composable
private fun InstanceCard(
    instance: AghInstanceState,
    enabled: Boolean,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit,
    onOpenWeb: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(instance.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (instance.running) "● Running" else "● Stopped",
                modifier = Modifier.padding(top = 4.dp),
                color = if (instance.running) StatusHealthy else StatusError,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                "DNS :" + instance.dnsPort + " · Web :" + instance.webPort +
                    if (instance.pid.isBlank()) "" else " · PID " + instance.pid,
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            ActionRow(
                enabled = enabled,
                onStart = onStart,
                onRestart = onRestart,
                onStop = onStop,
                modifier = Modifier.padding(top = 14.dp)
            )
            OutlinedButton(
                onClick = onOpenWeb,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("打开 " + instance.name + " 管理界面")
            }
        }
    }
}

@Composable
private fun ActionRow(
    enabled: Boolean,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = onStart, enabled = enabled, modifier = Modifier.weight(1f)) { Text("启动") }
        OutlinedButton(onClick = onRestart, enabled = enabled, modifier = Modifier.weight(1f)) { Text("重启") }
        OutlinedButton(onClick = onStop, enabled = enabled, modifier = Modifier.weight(1f)) { Text("停止") }
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
