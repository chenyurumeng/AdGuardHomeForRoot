package io.github.chenyurumeng.aghmanager.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.LogRepository
import io.github.chenyurumeng.aghmanager.model.LogSource
import io.github.chenyurumeng.aghmanager.model.LogUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LogsViewModel(private val repository: LogRepository) : ViewModel() {
    private val _state = MutableStateFlow(LogUiState())
    val state = _state.asStateFlow()

    private val _copyEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val copyEvents = _copyEvents.asSharedFlow()

    init { refresh() }

    fun select(source: LogSource) {
        if (_state.value.source == source) return
        _state.value = _state.value.copy(source = source)
        refresh()
    }

    fun refresh() {
        if (_state.value.loading) return
        val source = _state.value.source
        _state.value = _state.value.copy(loading = true, content = "正在读取日志…", error = false)

        viewModelScope.launch {
            val result = repository.load(source)
            _state.value = _state.value.copy(
                loading = false,
                content = when {
                    result.ok && result.stdout.isBlank() -> "(暂无日志)"
                    result.stdout.isNotBlank() -> result.stdout
                    else -> "日志读取失败（exit=" + result.exitCode + "）"
                },
                error = !result.ok
            )
        }
    }

    fun copy() {
        val content = _state.value.content
        if (content.isBlank() || _state.value.loading) return
        _copyEvents.tryEmit(content)
    }

    class Factory(private val repository: LogRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LogsViewModel::class.java)) {
                return LogsViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
