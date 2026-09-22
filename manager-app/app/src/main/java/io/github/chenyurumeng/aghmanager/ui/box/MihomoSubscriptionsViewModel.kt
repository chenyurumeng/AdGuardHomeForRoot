package io.github.chenyurumeng.aghmanager.ui.box

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.MihomoApiRepository
import io.github.chenyurumeng.aghmanager.data.MihomoSubscriptionRepository
import io.github.chenyurumeng.aghmanager.model.MihomoSubscriptionsUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MihomoSubscriptionsViewModel(
    private val repository: MihomoSubscriptionRepository,
    private val apiRepository: MihomoApiRepository
) : ViewModel() {
    private val _state = MutableStateFlow(MihomoSubscriptionsUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = "")
            repository.load()
                .onSuccess { subscriptions ->
                    val runtime = apiRepository.loadSnapshot()
                        .getOrNull()
                        ?.providers
                        .orEmpty()
                        .associateBy { it.name }

                    _state.value = _state.value.copy(
                        loading = false,
                        subscriptions = subscriptions,
                        runtimeProviders = runtime,
                        error = ""
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "订阅读取失败"
                    )
                }
        }
    }

    fun save(
        originalName: String?,
        name: String,
        url: String,
        intervalSeconds: Int,
        onComplete: (Boolean) -> Unit
    ) {
        mutate(
            action = {
                repository.save(originalName, name, url, intervalSeconds)
            },
            successMessage = if (originalName == null) "订阅已添加" else "订阅已保存",
            afterSuccess = {
                val current = _state.value.subscriptions.firstOrNull { it.name == originalName }
                if (originalName == null || current?.enabled != false) {
                    apiRepository.updateProvider(name)
                }
            },
            onComplete = onComplete
        )
    }

    fun setEnabled(name: String, enabled: Boolean) {
        mutate(
            action = { repository.setEnabled(name, enabled) },
            successMessage = if (enabled) "订阅已启用" else "订阅已停用",
            afterSuccess = {
                if (enabled) apiRepository.updateProvider(name)
            }
        )
    }

    fun delete(name: String) {
        mutate(
            action = { repository.delete(name) },
            successMessage = "订阅已删除"
        )
    }

    private fun mutate(
        action: suspend () -> Result<Unit>,
        successMessage: String,
        afterSuccess: suspend () -> Unit = {},
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (_state.value.saving) return

        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = "")
            action()
                .onSuccess {
                    afterSuccess()
                    _messages.emit(successMessage)
                    _state.value = _state.value.copy(saving = false)
                    refresh()
                    onComplete(true)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        saving = false,
                        error = it.message ?: "订阅操作失败"
                    )
                    _messages.emit(it.message ?: "订阅操作失败")
                    onComplete(false)
                }
        }
    }

    class Factory(
        private val repository: MihomoSubscriptionRepository,
        private val apiRepository: MihomoApiRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MihomoSubscriptionsViewModel::class.java)) {
                return MihomoSubscriptionsViewModel(repository, apiRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
