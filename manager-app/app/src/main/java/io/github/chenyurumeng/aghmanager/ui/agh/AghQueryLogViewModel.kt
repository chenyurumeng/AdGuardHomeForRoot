package io.github.chenyurumeng.aghmanager.ui.agh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.AghFilteringRepository
import io.github.chenyurumeng.aghmanager.data.AghQueryLogRepository
import io.github.chenyurumeng.aghmanager.model.AghInstance
import io.github.chenyurumeng.aghmanager.model.AghQueryFilter
import io.github.chenyurumeng.aghmanager.model.AghQueryLogConfig
import io.github.chenyurumeng.aghmanager.model.AghQueryLogEntry
import io.github.chenyurumeng.aghmanager.model.AghQueryLogUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AghQueryLogViewModel(
    val instance: AghInstance,
    private val repository: AghQueryLogRepository,
    private val filteringRepository: AghFilteringRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AghQueryLogUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        loadFirstPage(initial = true)
    }

    fun refresh() {
        if (_state.value.loading) return
        loadFirstPage(initial = false)
    }

    fun refreshLatest() {
        if (_state.value.loading || _state.value.loadingMore) return
        viewModelScope.launch {
            repository.loadPage(
                instance = instance,
                search = _state.value.search,
                limit = 100
            ).onSuccess { page ->
                _state.value = _state.value.copy(
                    loading = false,
                    entries = page.entries,
                    oldest = page.oldest,
                    error = ""
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    loading = false,
                    error = it.message ?: "Query Log 刷新失败"
                )
            }
        }
    }

    private fun loadFirstPage(initial: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                entries = if (initial) emptyList() else _state.value.entries,
                oldest = if (initial) "" else _state.value.oldest,
                error = ""
            )

            val pageResult = repository.loadPage(
                instance = instance,
                search = _state.value.search,
                limit = 100
            )
            val configResult = repository.loadConfig(instance)

            pageResult.onSuccess { page ->
                _state.value = _state.value.copy(
                    loading = false,
                    entries = page.entries,
                    oldest = page.oldest,
                    config = configResult.getOrNull() ?: _state.value.config,
                    pendingConfig = configResult.getOrNull() ?: _state.value.pendingConfig,
                    error = if (configResult.isFailure) {
                        configResult.exceptionOrNull()?.message ?: "Query Log 设置读取失败"
                    } else {
                        ""
                    }
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    loading = false,
                    error = it.message ?: "Query Log 读取失败"
                )
            }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.loadingMore || snapshot.oldest.isBlank()) return

        viewModelScope.launch {
            _state.value = snapshot.copy(loadingMore = true, error = "")
            repository.loadPage(
                instance = instance,
                search = snapshot.search,
                olderThan = snapshot.oldest,
                limit = 100
            ).onSuccess { page ->
                val known = snapshot.entries.mapTo(HashSet()) { it.stableKey }
                val appended = page.entries.filter { it.stableKey !in known }
                _state.value = _state.value.copy(
                    loadingMore = false,
                    entries = snapshot.entries + appended,
                    oldest = page.oldest,
                    error = ""
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    loadingMore = false,
                    error = it.message ?: "加载更早日志失败"
                )
            }
        }
    }

    fun setSearch(value: String) {
        _state.value = _state.value.copy(search = value)
    }

    fun applySearch() {
        loadFirstPage(initial = true)
    }

    fun setFilter(value: AghQueryFilter) {
        _state.value = _state.value.copy(filter = value)
    }

    fun select(entry: AghQueryLogEntry?) {
        if (entry == null) {
            _state.value = _state.value.copy(
                selected = null,
                selectedUserRules = emptyList()
            )
            return
        }

        _state.value = _state.value.copy(selected = entry, selectedUserRules = emptyList())
        viewModelScope.launch {
            filteringRepository.load(instance)
                .onSuccess {
                    _state.value = _state.value.copy(selectedUserRules = it.userRules)
                }
                .onFailure {
                    _messages.emit(it.message ?: "User Rules 读取失败")
                }
        }
    }

    fun saveRule(original: String?, rule: String, onComplete: (Boolean) -> Unit = {}) {
        if (_state.value.mutatingRule) return
        val normalized = rule.trim()
        if (normalized.isBlank()) {
            _messages.tryEmit("规则不能为空")
            onComplete(false)
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(mutatingRule = true, error = "")
            val result = if (original == null) {
                filteringRepository.addUserRule(instance, normalized)
            } else {
                filteringRepository.replaceUserRule(instance, original, normalized)
            }

            result.onSuccess {
                _messages.emit(
                    if (original == null) "规则已添加并立即生效"
                    else "规则已更新并立即生效"
                )
                refreshSelectedRules()
                _state.value = _state.value.copy(mutatingRule = false)
                onComplete(true)
            }.onFailure {
                _state.value = _state.value.copy(
                    mutatingRule = false,
                    error = it.message ?: "User Rule 操作失败"
                )
                _messages.emit(it.message ?: "User Rule 操作失败")
                onComplete(false)
            }
        }
    }

    fun deleteRule(rule: String) {
        if (_state.value.mutatingRule) return
        viewModelScope.launch {
            _state.value = _state.value.copy(mutatingRule = true, error = "")
            filteringRepository.deleteUserRule(instance, rule)
                .onSuccess {
                    _messages.emit("User Rule 已删除")
                    refreshSelectedRules()
                    _state.value = _state.value.copy(mutatingRule = false)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        mutatingRule = false,
                        error = it.message ?: "删除 User Rule 失败"
                    )
                    _messages.emit(it.message ?: "删除 User Rule 失败")
                }
        }
    }

    private suspend fun refreshSelectedRules() {
        filteringRepository.load(instance)
            .onSuccess {
                _state.value = _state.value.copy(selectedUserRules = it.userRules)
            }
    }

    fun setPendingConfig(value: AghQueryLogConfig) {
        _state.value = _state.value.copy(pendingConfig = value, error = "")
    }

    fun discardConfig() {
        _state.value = _state.value.copy(
            pendingConfig = _state.value.config,
            error = ""
        )
    }

    fun saveConfig(onComplete: (Boolean) -> Unit = {}) {
        val pending = _state.value.pendingConfig ?: return
        if (_state.value.savingConfig) return

        viewModelScope.launch {
            _state.value = _state.value.copy(savingConfig = true, error = "")
            repository.updateConfig(instance, pending)
                .onSuccess {
                    _messages.emit("Query Log 设置已保存")
                    _state.value = _state.value.copy(
                        savingConfig = false,
                        config = pending,
                        pendingConfig = pending
                    )
                    onComplete(true)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        savingConfig = false,
                        error = it.message ?: "Query Log 设置保存失败"
                    )
                    _messages.emit(it.message ?: "Query Log 设置保存失败")
                    onComplete(false)
                }
        }
    }

    fun clearLog() {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = "")
            repository.clear(instance)
                .onSuccess {
                    _messages.emit("Query Log 已清空")
                    _state.value = _state.value.copy(
                        loading = false,
                        entries = emptyList(),
                        oldest = ""
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "清空 Query Log 失败"
                    )
                    _messages.emit(it.message ?: "清空 Query Log 失败")
                }
        }
    }

    class Factory(
        private val instance: AghInstance,
        private val repository: AghQueryLogRepository,
        private val filteringRepository: AghFilteringRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AghQueryLogViewModel::class.java)) {
                return AghQueryLogViewModel(instance, repository, filteringRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
