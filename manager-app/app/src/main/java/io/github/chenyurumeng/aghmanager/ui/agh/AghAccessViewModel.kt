package io.github.chenyurumeng.aghmanager.ui.agh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.AghAccessRepository
import io.github.chenyurumeng.aghmanager.model.AghAccessList
import io.github.chenyurumeng.aghmanager.model.AghAccessUiState
import io.github.chenyurumeng.aghmanager.model.AghInstance
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AghAccessViewModel(
    val instance: AghInstance,
    private val repository: AghAccessRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AghAccessUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 3)
    val messages = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.applying) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = "")
            repository.load(instance)
                .onSuccess { current ->
                    _state.value = AghAccessUiState(
                        loading = false,
                        current = current,
                        pending = current
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "Access Control 读取失败"
                    )
                }
        }
    }

    fun setPending(value: AghAccessList) {
        _state.value = _state.value.copy(pending = value, error = "")
    }

    fun discard() {
        _state.value = _state.value.copy(
            pending = _state.value.current,
            error = ""
        )
    }

    fun apply() {
        val current = _state.value.current ?: return
        val pending = _state.value.pending ?: return
        if (_state.value.applying || ! _state.value.dirty) return

        viewModelScope.launch {
            _state.value = _state.value.copy(applying = true, error = "")
            repository.update(instance, current, pending)
                .onSuccess {
                    val refreshed = repository.load(instance)
                    refreshed.onSuccess { latest ->
                        _state.value = AghAccessUiState(
                            loading = false,
                            current = latest,
                            pending = latest
                        )
                        _messages.emit("Access Control 已应用")
                    }.onFailure {
                        _state.value = _state.value.copy(
                            applying = false,
                            error = it.message ?: "已应用，但刷新 Access Control 失败"
                        )
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        applying = false,
                        error = it.message ?: "Access Control 应用失败"
                    )
                    _messages.emit(it.message ?: "Access Control 应用失败")
                }
        }
    }

    class Factory(
        private val instance: AghInstance,
        private val repository: AghAccessRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AghAccessViewModel::class.java)) {
                return AghAccessViewModel(instance, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
