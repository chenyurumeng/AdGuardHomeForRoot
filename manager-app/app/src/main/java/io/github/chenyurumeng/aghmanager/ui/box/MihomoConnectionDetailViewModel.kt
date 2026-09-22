package io.github.chenyurumeng.aghmanager.ui.box

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.MihomoApiRepository
import io.github.chenyurumeng.aghmanager.model.MihomoConnectionDetailUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MihomoConnectionDetailViewModel(
    private val connectionId: String,
    private val repository: MihomoApiRepository
) : ViewModel() {
    private val _state = MutableStateFlow(MihomoConnectionDetailUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    suspend fun refreshNow() {
        repository.loadRuntime()
            .onSuccess { snapshot ->
                val found = snapshot.connections.firstOrNull { it.id == connectionId }
                _state.value = _state.value.copy(
                    loading = false,
                    connection = found,
                    missing = found == null,
                    error = ""
                )
            }
            .onFailure {
                _state.value = _state.value.copy(
                    loading = false,
                    error = it.message ?: "连接详情读取失败"
                )
            }
    }

    fun close(onClosed: () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            repository.closeConnection(connectionId)
                .onSuccess {
                    _messages.emit("连接已关闭")
                    _state.value = _state.value.copy(busy = false, missing = true, connection = null)
                    onClosed()
                }
                .onFailure {
                    _state.value = _state.value.copy(busy = false)
                    _messages.emit(it.message ?: "关闭连接失败")
                }
        }
    }

    class Factory(
        private val connectionId: String,
        private val repository: MihomoApiRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MihomoConnectionDetailViewModel::class.java)) {
                return MihomoConnectionDetailViewModel(connectionId, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
