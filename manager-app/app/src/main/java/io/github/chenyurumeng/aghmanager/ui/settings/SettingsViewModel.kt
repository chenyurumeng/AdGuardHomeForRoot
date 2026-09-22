package io.github.chenyurumeng.aghmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.DiagnosticRepository
import io.github.chenyurumeng.aghmanager.data.SettingsRepository
import io.github.chenyurumeng.aghmanager.data.StatusRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val diagnosticRepository: DiagnosticRepository,
    private val statusRepository: StatusRepository
) : ViewModel() {
    val refreshInterval = settingsRepository.refreshInterval
    val systemState = statusRepository.state

    private val _generatingDiagnostic = MutableStateFlow(false)
    val generatingDiagnostic = _generatingDiagnostic.asStateFlow()

    private val _copyEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val copyEvents = _copyEvents.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    init { viewModelScope.launch { statusRepository.refresh() } }

    fun setRefreshInterval(value: Long) {
        settingsRepository.setRefreshInterval(value)
        _messages.tryEmit(if (value == 0L) "自动刷新已关闭" else "自动刷新已设为 " + value / 1000 + " 秒")
    }

    fun generateDiagnostic() {
        if (_generatingDiagnostic.value) return
        viewModelScope.launch {
            _generatingDiagnostic.value = true
            try {
                val result = diagnosticRepository.generate()
                if (result.stdout.isNotBlank()) {
                    _copyEvents.emit(result.stdout)
                    _messages.emit(if (result.ok) "诊断报告已复制" else "诊断报告已复制，但命令返回异常")
                } else {
                    _messages.emit("诊断报告生成失败（exit=" + result.exitCode + "）")
                }
            } finally {
                _generatingDiagnostic.value = false
            }
        }
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val diagnosticRepository: DiagnosticRepository,
        private val statusRepository: StatusRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(settingsRepository, diagnosticRepository, statusRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
