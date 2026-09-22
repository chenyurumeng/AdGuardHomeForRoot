package io.github.chenyurumeng.aghmanager.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    companion object {
        private const val PREFS = "agh_manager"
        private const val KEY_REFRESH_MS = "refresh_ms"

        fun sanitizeRefreshInterval(value: Long): Long =
            when (value) {
                0L, 3_000L, 5_000L, 10_000L -> value
                else -> 5_000L
            }
    }

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _refreshInterval = MutableStateFlow(
        sanitizeRefreshInterval(preferences.getLong(KEY_REFRESH_MS, 5_000L))
    )
    val refreshInterval: StateFlow<Long> = _refreshInterval.asStateFlow()

    fun setRefreshInterval(value: Long) {
        val normalized = sanitizeRefreshInterval(value)
        preferences.edit().putLong(KEY_REFRESH_MS, normalized).apply()
        _refreshInterval.value = normalized
    }
}
