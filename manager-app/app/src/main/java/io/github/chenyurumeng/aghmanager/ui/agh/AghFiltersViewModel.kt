package io.github.chenyurumeng.aghmanager.ui.agh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.AghFilteringRepository
import io.github.chenyurumeng.aghmanager.model.AghFilterSubscription
import io.github.chenyurumeng.aghmanager.model.AghFiltersUiState
import io.github.chenyurumeng.aghmanager.model.AghInstance
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AghFiltersViewModel(
    val instance: AghInstance,
    private val repository: AghFilteringRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AghFiltersUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = "")
            repository.load(instance)
                .onSuccess { status ->
                    _state.value = _state.value.copy(
                        loading = false,
                        status = status,
                        pendingEnabled = status.enabled,
                        pendingIntervalHours = status.intervalHours,
                        error = ""
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "过滤配置读取失败"
                    )
                }
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun setShowWhitelist(value: Boolean) {
        _state.value = _state.value.copy(showWhitelist = value)
    }

    fun setPendingEnabled(value: Boolean) {
        _state.value = _state.value.copy(pendingEnabled = value, error = "")
    }

    fun setPendingInterval(value: Int) {
        _state.value = _state.value.copy(pendingIntervalHours = value, error = "")
    }

    fun discardSettings() {
        val status = _state.value.status ?: return
        _state.value = _state.value.copy(
            pendingEnabled = status.enabled,
            pendingIntervalHours = status.intervalHours,
            error = ""
        )
    }

    fun applySettings() {
        val snapshot = _state.value
        if (snapshot.busy || snapshot.status == null) return
        mutate("过滤设置已应用") {
            repository.updateSettings(
                instance = instance,
                enabled = snapshot.pendingEnabled,
                intervalHours = snapshot.pendingIntervalHours
            )
        }
    }

    fun addFilter(name: String, url: String, whitelist: Boolean) {
        mutate("过滤器已添加") {
            repository.add(instance, name, url, whitelist)
        }
    }

    fun updateFilter(
        filter: AghFilterSubscription,
        name: String,
        url: String,
        enabled: Boolean
    ) {
        mutate("过滤器已更新") {
            repository.update(instance, filter, name, url, enabled)
        }
    }

    fun toggleFilter(filter: AghFilterSubscription, enabled: Boolean) {
        mutate(if (enabled) "过滤器已启用" else "过滤器已停用") {
            repository.setEnabled(instance, filter, enabled)
        }
    }

    fun removeFilter(filter: AghFilterSubscription) {
        mutate("过滤器已删除") {
            repository.remove(instance, filter)
        }
    }

    fun refreshAllFilters() {
        mutate("过滤器列表已刷新") {
            repository.refreshAll(instance).map { Unit }
        }
    }

    fun checkHost(value: String = _state.value.checkHost) {
        val host = value.trim()
        if (host.isBlank()) {
            _messages.tryEmit("请输入要检查的域名")
            return
        }
        if (_state.value.checkingHost) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                checkHost = host,
                checkingHost = true,
                checkResult = null,
                error = ""
            )
            repository.checkHost(instance, host)
                .onSuccess {
                    _state.value = _state.value.copy(
                        checkingHost = false,
                        checkResult = it
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        checkingHost = false,
                        error = it.message ?: "域名检查失败"
                    )
                }
        }
    }

    fun setCheckHost(value: String) {
        _state.value = _state.value.copy(
            checkHost = value,
            checkResult = null
        )
    }

    private fun mutate(
        successMessage: String,
        block: suspend () -> Result<Unit>
    ) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = "")
            block()
                .onSuccess {
                    _messages.emit(successMessage)
                    val refreshed = repository.load(instance)
                    refreshed.onSuccess { status ->
                        _state.value = _state.value.copy(
                            busy = false,
                            loading = false,
                            status = status,
                            pendingEnabled = status.enabled,
                            pendingIntervalHours = status.intervalHours,
                            error = ""
                        )
                    }.onFailure {
                        _state.value = _state.value.copy(
                            busy = false,
                            error = it.message ?: "操作已完成，但刷新状态失败"
                        )
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        busy = false,
                        error = it.message ?: "过滤操作失败"
                    )
                    _messages.emit(it.message ?: "过滤操作失败")
                }
        }
    }

    class Factory(
        private val instance: AghInstance,
        private val repository: AghFilteringRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AghFiltersViewModel::class.java)) {
                return AghFiltersViewModel(instance, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
