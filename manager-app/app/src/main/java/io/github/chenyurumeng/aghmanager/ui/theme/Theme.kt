package io.github.chenyurumeng.aghmanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val StatusHealthy = Color(0xFF56C596)
val StatusDegraded = Color(0xFFFFB74D)
val StatusError = Color(0xFFFF6B6B)
val StatusStopped = Color(0xFF9AA6B2)

private val ManagerDarkColors = darkColorScheme(
    primary = Color(0xFF9DB7FF),
    secondary = Color(0xFFBEC6DC),
    tertiary = StatusHealthy,
    error = StatusError
)

@Composable
fun ManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ManagerDarkColors,
        content = content
    )
}
