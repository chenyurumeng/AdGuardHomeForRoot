package io.github.chenyurumeng.aghmanager.ui.agh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.AghRewriteRepository
import io.github.chenyurumeng.aghmanager.model.AghInstance
import io.github.chenyurumeng.aghmanager.model.AghRewriteRule
import io.github.chenyurumeng.aghmanager.model.AghRewritesUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AghRewritesViewModel(
    val instance: AghInstance,
    private val repository: AghRewriteRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AghRewritesUiState())
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
                .onSuccess { (enabled, rules) ->
                    _state.value = _state.value.copy(
                        loading = false,
                        enabled = enabled,
                        pendingEnabled = enabled,
                        rules = rules,
                        error = ""
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "DNS Rewrite 读取失败"
                    )
                }
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun setPendingEnabled(value: Boolean) {
        _state.value = _state.value.copy(pendingEnabled = value, error = "")
    }

    fun discardSettings() {
        _state.value = _state.value.copy(
            pendingEnabled = _state.value.enabled,
            error = ""
        )
    }

    fun applySettings() {
        val enabled = _state.value.pendingEnabled
        mutate(
            message = if (enabled) "DNS Rewrite 已启用" else "DNS Rewrite 已停用",
            block = { repository.setEnabled(instance, enabled) }
        )
    }

    fun add(rule: AghRewriteRule, onComplete: (Boolean) -> Unit = {}) {
        mutate("Rewrite 已添加", { repository.add(instance, rule) }, onComplete)
    }

    fun update(
        original: AghRewriteRule,
        updated: AghRewriteRule,
        onComplete: (Boolean) -> Unit = {}
    ) {
        mutate(
            "Rewrite 已更新",
            { repository.update(instance, original, updated) },
            onComplete
        )
    }

    fun toggle(rule: AghRewriteRule, enabled: Boolean) {
        update(rule, rule.copy(enabled = enabled))
    }

    fun delete(rule: AghRewriteRule) {
        mutate("Rewrite 已删除", { repository.delete(instance, rule) })
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
                        .onSuccess { (enabled, rules) ->
                            _state.value = _state.value.copy(
                                loading = false,
                                busy = false,
                                enabled = enabled,
                                pendingEnabled = enabled,
                                rules = rules,
                                error = ""
                            )
                        }
                        .onFailure {
                            _state.value = _state.value.copy(
                                busy = false,
                                error = it.message ?: "操作成功，但刷新 Rewrite 列表失败"
                            )
                        }
                    onComplete(true)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        busy = false,
                        error = it.message ?: "DNS Rewrite 操作失败"
                    )
                    _messages.emit(it.message ?: "DNS Rewrite 操作失败")
                    onComplete(false)
                }
        }
    }

    class Factory(
        private val instance: AghInstance,
        private val repository: AghRewriteRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AghRewritesViewModel::class.java)) {
                return AghRewritesViewModel(instance, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
