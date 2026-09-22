package io.github.chenyurumeng.aghmanager.ui.box

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.MihomoApiRepository
import io.github.chenyurumeng.aghmanager.model.MihomoRuntimeUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlin.math.max

class MihomoConnectionsViewModel(
    private val repository: MihomoApiRepository
) : ViewModel() {
    private val _state = MutableStateFlow(MihomoRuntimeUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    private val refreshMutex = Mutex()
    private var previousAtMs: Long = 0L
    private var previousUpload: Long = 0L
    private var previousDownload: Long = 0L

    suspend fun refreshNow() {
        refreshMutex.withLock {
            repository.loadRuntime()
                .onSuccess { snapshot ->
                    val now = System.currentTimeMillis()
                    val elapsedMs = if (previousAtMs == 0L) 0L else max(1L, now - previousAtMs)
                    val uploadBps = if (elapsedMs == 0L || snapshot.uploadTotal < previousUpload) {
                        0L
                    } else {
                        (snapshot.uploadTotal - previousUpload) * 1000L / elapsedMs
                    }
                    val downloadBps = if (elapsedMs == 0L || snapshot.downloadTotal < previousDownload) {
                        0L
                    } else {
                        (snapshot.downloadTotal - previousDownload) * 1000L / elapsedMs
                    }

                    previousAtMs = now
                    previousUpload = snapshot.uploadTotal
                    previousDownload = snapshot.downloadTotal

                    _state.value = _state.value.copy(
                        loading = false,
                        uploadTotal = snapshot.uploadTotal,
                        downloadTotal = snapshot.downloadTotal,
                        uploadBps = uploadBps,
                        downloadBps = downloadBps,
                        connections = snapshot.connections,
                        process = snapshot.process,
                        error = ""
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "活动连接读取失败"
                    )
                }
        }
    }

    fun closeConnection(id: String) {
        if (_state.value.busyAction != null || id.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busyAction = "正在关闭连接…")
            repository.closeConnection(id)
                .onSuccess {
                    _messages.emit("连接已关闭")
                    refreshNow()
                }
                .onFailure {
                    _messages.emit(it.message ?: "关闭连接失败")
                }
            _state.value = _state.value.copy(busyAction = null)
        }
    }

    fun closeAll() {
        if (_state.value.busyAction != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busyAction = "正在关闭全部连接…")
            repository.closeAllConnections()
                .onSuccess {
                    _messages.emit("全部活动连接已关闭")
                    refreshNow()
                }
                .onFailure {
                    _messages.emit(it.message ?: "关闭全部连接失败")
                }
            _state.value = _state.value.copy(busyAction = null)
        }
    }

    class Factory(
        private val repository: MihomoApiRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MihomoConnectionsViewModel::class.java)) {
                return MihomoConnectionsViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
