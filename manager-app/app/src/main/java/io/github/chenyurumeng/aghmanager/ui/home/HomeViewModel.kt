package io.github.chenyurumeng.aghmanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.StatusRepository
import io.github.chenyurumeng.aghmanager.domain.SystemOrchestrator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val statusRepository: StatusRepository,
    private val orchestrator: SystemOrchestrator,
    private val refreshIntervalProvider: () -> Long
) : ViewModel() {
    val state = statusRepository.state

    private val _restarting = MutableStateFlow(false)
    val restarting = _restarting.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    suspend fun refreshNow() {
        if (!_restarting.value) {
            statusRepository.refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshNow()
        }
    }

    fun refreshIntervalMs(): Long =
        when (val value = refreshIntervalProvider()) {
            0L, 3_000L, 5_000L, 10_000L -> value
            else -> 5_000L
        }

    fun restartNetwork() {
        if (_restarting.value) return

        viewModelScope.launch {
            _restarting.value = true
            try {
                val result = orchestrator.restartNetwork()
                _messages.emit(
                    if (result.ok) {
                        "网络服务已重启"
                    } else {
                        "网络服务重启失败（exit=" + result.exitCode + "）"
                    }
                )
            } finally {
                _restarting.value = false
            }
        }
    }

    class Factory(
        private val statusRepository: StatusRepository,
        private val orchestrator: SystemOrchestrator,
        private val refreshIntervalProvider: () -> Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(
                    statusRepository,
                    orchestrator,
                    refreshIntervalProvider
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
