package io.github.chenyurumeng.aghmanager.ui.agh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.AghFilteringRepository
import io.github.chenyurumeng.aghmanager.model.AghInstance
import io.github.chenyurumeng.aghmanager.model.AghUserRulesUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AghUserRulesViewModel(
    val instance: AghInstance,
    private val repository: AghFilteringRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AghUserRulesUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 3)
    val messages = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = "")
            repository.load(instance)
                .onSuccess { status ->
                    _state.value = AghUserRulesUiState(
                        loading = false,
                        baselineRules = status.userRules,
                        text = status.userRules.joinToString("\n")
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "User Rules 读取失败"
                    )
                }
        }
    }

    fun setText(value: String) {
        _state.value = _state.value.copy(text = value, error = "")
    }

    fun discard() {
        val baseline = _state.value.baselineRules
        _state.value = _state.value.copy(
            text = baseline.joinToString("\n"),
            error = ""
        )
    }

    fun save() {
        val snapshot = _state.value
        if (snapshot.saving || !snapshot.dirty) return

        viewModelScope.launch {
            _state.value = snapshot.copy(saving = true, error = "")
            repository.saveUserRules(
                instance = instance,
                baselineRules = snapshot.baselineRules,
                newRules = snapshot.rules
            ).onSuccess {
                _messages.emit("User Rules 已保存并立即生效")
                _state.value = AghUserRulesUiState(
                    loading = false,
                    baselineRules = snapshot.rules,
                    text = snapshot.rules.joinToString("\n")
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    saving = false,
                    error = it.message ?: "User Rules 保存失败"
                )
                _messages.emit(it.message ?: "User Rules 保存失败")
            }
        }
    }

    class Factory(
        private val instance: AghInstance,
        private val repository: AghFilteringRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AghUserRulesViewModel::class.java)) {
                return AghUserRulesViewModel(instance, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
