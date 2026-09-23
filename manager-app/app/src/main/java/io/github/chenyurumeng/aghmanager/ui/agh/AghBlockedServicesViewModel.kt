package io.github.chenyurumeng.aghmanager.ui.agh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.AghBlockedServicesRepository
import io.github.chenyurumeng.aghmanager.model.AghBlockedServicesUiState
import io.github.chenyurumeng.aghmanager.model.AghInstance
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AghBlockedServicesViewModel(
    val instance: AghInstance,
    private val repository: AghBlockedServicesRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AghBlockedServicesUiState())
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
                .onSuccess { snapshot ->
                    _state.value = _state.value.copy(
                        loading = false,
                        services = snapshot.services,
                        currentIds = snapshot.selectedIds,
                        pendingIds = snapshot.selectedIds,
                        scheduleJson = snapshot.scheduleJson,
                        error = ""
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "Blocked Services 读取失败"
                    )
                }
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun setGroup(value: String) {
        _state.value = _state.value.copy(groupId = value)
    }

    fun toggle(id: String, selected: Boolean) {
        val next = _state.value.pendingIds.toMutableSet()
        if (selected) next.add(id) else next.remove(id)
        _state.value = _state.value.copy(pendingIds = next, error = "")
    }

    fun setMany(ids: Set<String>, selected: Boolean) {
        val next = _state.value.pendingIds.toMutableSet()
        if (selected) next.addAll(ids) else next.removeAll(ids)
        _state.value = _state.value.copy(pendingIds = next, error = "")
    }

    fun discard() {
        _state.value = _state.value.copy(
            pendingIds = _state.value.currentIds,
            error = ""
        )
    }

    fun apply() {
        val snapshot = _state.value
        if (snapshot.applying || !snapshot.dirty) return

        viewModelScope.launch {
            _state.value = snapshot.copy(applying = true, error = "")
            repository.update(
                instance = instance,
                baselineIds = snapshot.currentIds,
                selectedIds = snapshot.pendingIds
            ).onSuccess {
                repository.load(instance)
                    .onSuccess { latest ->
                        _state.value = _state.value.copy(
                            loading = false,
                            applying = false,
                            services = latest.services,
                            currentIds = latest.selectedIds,
                            pendingIds = latest.selectedIds,
                            scheduleJson = latest.scheduleJson,
                            error = ""
                        )
                        _messages.emit("Blocked Services 已应用")
                    }
                    .onFailure {
                        _state.value = _state.value.copy(
                            applying = false,
                            error = it.message ?: "已应用，但刷新 Blocked Services 失败"
                        )
                    }
            }.onFailure {
                _state.value = _state.value.copy(
                    applying = false,
                    error = it.message ?: "Blocked Services 应用失败"
                )
                _messages.emit(it.message ?: "Blocked Services 应用失败")
            }
        }
    }

    class Factory(
        private val instance: AghInstance,
        private val repository: AghBlockedServicesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AghBlockedServicesViewModel::class.java)) {
                return AghBlockedServicesViewModel(instance, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
