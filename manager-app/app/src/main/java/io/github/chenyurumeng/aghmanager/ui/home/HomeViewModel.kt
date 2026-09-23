package io.github.chenyurumeng.aghmanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.SettingsRepository
import io.github.chenyurumeng.aghmanager.data.StatusRepository
import io.github.chenyurumeng.aghmanager.domain.SystemOrchestrator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class HomeViewModel(
    private val statusRepository: StatusRepository,
    private val orchestrator: SystemOrchestrator,
    settingsRepository: SettingsRepository
) : ViewModel() {
    val state = statusRepository.state
    val refreshIntervalMs = settingsRepository.refreshInterval

    private val _restarting = MutableStateFlow(false)
    val restarting = _restarting.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    private val refreshMutex = Mutex()

    suspend fun refreshNow() {
        if (_restarting.value || !refreshMutex.tryLock()) return
        try {
            statusRepository.refresh()
        } finally {
            refreshMutex.unlock()
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    fun restartNetwork() {
        if (_restarting.value) return
        _restarting.value = true
        viewModelScope.launch {
            try {
                val result = orchestrator.restartNetwork()
                _messages.emit(
                    if (result.ok) "网络服务已重启"
                    else "网络服务重启失败（exit=" + result.exitCode + "）"
                )
            } finally {
                _restarting.value = false
            }
        }
    }

    class Factory(
        private val statusRepository: StatusRepository,
        private val orchestrator: SystemOrchestrator,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(statusRepository, orchestrator, settingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
