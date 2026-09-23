package io.github.chenyurumeng.aghmanager.ui.agh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.AghClientsRepository
import io.github.chenyurumeng.aghmanager.model.AghClient
import io.github.chenyurumeng.aghmanager.model.AghClientsUiState
import io.github.chenyurumeng.aghmanager.model.AghInstance
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AghClientsViewModel(
    val instance: AghInstance,
    private val repository: AghClientsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AghClientsUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 3)
    val messages = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = "")
            repository.load(instance)
                .onSuccess { snapshot ->
                    _state.value = _state.value.copy(
                        loading = false,
                        clients = snapshot.clients,
                        autoClients = snapshot.autoClients,
                        supportedTags = snapshot.supportedTags,
                        error = ""
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "客户端列表读取失败"
                    )
                }
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun setShowAuto(value: Boolean) {
        _state.value = _state.value.copy(showAutoClients = value)
    }

    fun add(client: AghClient, onComplete: (Boolean) -> Unit = {}) {
        mutate("客户端已添加", { repository.add(instance, client) }, onComplete)
    }

    fun update(
        original: AghClient,
        updated: AghClient,
        onComplete: (Boolean) -> Unit = {}
    ) {
        mutate(
            "客户端已更新",
            { repository.update(instance, original, updated) },
            onComplete
        )
    }

    fun delete(client: AghClient) {
        mutate("客户端已删除", { repository.delete(instance, client) })
    }

    private fun mutate(
        message: String,
        block: suspend () -> Result<Unit>,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = "")
            block()
                .onSuccess {
                    _messages.emit(message)
                    repository.load(instance)
                        .onSuccess { snapshot ->
                            _state.value = _state.value.copy(
                                loading = false,
                                busy = false,
                                clients = snapshot.clients,
                                autoClients = snapshot.autoClients,
                                supportedTags = snapshot.supportedTags,
                                error = ""
                            )
                        }
                        .onFailure {
                            _state.value = _state.value.copy(
                                busy = false,
                                error = it.message ?: "操作成功，但刷新客户端列表失败"
                            )
                        }
                    onComplete(true)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        busy = false,
                        error = it.message ?: "客户端操作失败"
                    )
                    _messages.emit(it.message ?: "客户端操作失败")
                    onComplete(false)
                }
        }
    }

    class Factory(
        private val instance: AghInstance,
        private val repository: AghClientsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AghClientsViewModel::class.java)) {
                return AghClientsViewModel(instance, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
