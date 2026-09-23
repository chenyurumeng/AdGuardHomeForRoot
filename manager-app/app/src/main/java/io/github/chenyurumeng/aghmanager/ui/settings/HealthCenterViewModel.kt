package io.github.chenyurumeng.aghmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.HealthCenterRepository
import io.github.chenyurumeng.aghmanager.model.HealthCenterUiState
import io.github.chenyurumeng.aghmanager.model.HealthRepairAction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HealthCenterViewModel(
    private val repository: HealthCenterRepository
) : ViewModel() {
    private val _state = MutableStateFlow(HealthCenterUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 3)
    val messages = _messages.asSharedFlow()

    private val _copyEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val copyEvents = _copyEvents.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.repairing != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = "")
            repository.load()
                .onSuccess {
                    _state.value = _state.value.copy(
                        loading = false,
                        snapshot = it,
                        error = ""
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "健康检查失败"
                    )
                }
        }
    }

    fun copyReport() {
        val snapshot = _state.value.snapshot ?: return
        _copyEvents.tryEmit(repository.formatReport(snapshot))
    }

    fun repair(action: HealthRepairAction) {
        if (_state.value.repairing != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(repairing = action, error = "")
            repository.repair(action)
                .onSuccess {
                    _messages.emit(action.label + "完成，正在重新检查")
                    repository.load()
                        .onSuccess { snapshot ->
                            _state.value = _state.value.copy(
                                loading = false,
                                repairing = null,
                                snapshot = snapshot,
                                error = ""
                            )
                        }
                        .onFailure { error ->
                            _state.value = _state.value.copy(
                                repairing = null,
                                error = error.message ?: "修复后复检失败"
                            )
                        }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        repairing = null,
                        error = it.message ?: action.label + "失败"
                    )
                    _messages.emit(it.message ?: action.label + "失败")
                }
        }
    }

    class Factory(
        private val repository: HealthCenterRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HealthCenterViewModel::class.java)) {
                return HealthCenterViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
