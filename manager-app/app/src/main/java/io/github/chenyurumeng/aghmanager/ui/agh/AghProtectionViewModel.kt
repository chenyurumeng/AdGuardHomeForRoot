package io.github.chenyurumeng.aghmanager.ui.agh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.AghProtectionRepository
import io.github.chenyurumeng.aghmanager.model.AghInstance
import io.github.chenyurumeng.aghmanager.model.AghProtectionConfig
import io.github.chenyurumeng.aghmanager.model.AghProtectionUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AghProtectionViewModel(
    val instance: AghInstance,
    private val repository: AghProtectionRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AghProtectionUiState())
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
                .onSuccess { config ->
                    _state.value = _state.value.copy(
                        loading = false,
                        current = config,
                        pending = config,
                        error = ""
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "安全保护状态读取失败"
                    )
                }
        }
    }

    fun setSafeBrowsing(enabled: Boolean) {
        mutate { it.copy(safeBrowsingEnabled = enabled) }
    }

    fun setParental(enabled: Boolean) {
        mutate { it.copy(parentalEnabled = enabled) }
    }

    fun setSafeSearch(enabled: Boolean) {
        mutate { config ->
            config.copy(safeSearch = config.safeSearch.copy(enabled = enabled))
        }
    }

    fun setSafeSearchEngine(engine: String, enabled: Boolean) {
        mutate { config ->
            val search = when (engine) {
                "bing" -> config.safeSearch.copy(bing = enabled)
                "duckduckgo" -> config.safeSearch.copy(duckDuckGo = enabled)
                "ecosia" -> config.safeSearch.copy(ecosia = enabled)
                "google" -> config.safeSearch.copy(google = enabled)
                "pixabay" -> config.safeSearch.copy(pixabay = enabled)
                "yandex" -> config.safeSearch.copy(yandex = enabled)
                "youtube" -> config.safeSearch.copy(youtube = enabled)
                else -> config.safeSearch
            }
            config.copy(safeSearch = search)
        }
    }

    fun discard() {
        _state.value = _state.value.copy(
            pending = _state.value.current,
            error = ""
        )
    }

    fun apply() {
        val snapshot = _state.value
        val baseline = snapshot.current ?: return
        val pending = snapshot.pending ?: return
        if (snapshot.applying || !snapshot.dirty) return

        viewModelScope.launch {
            _state.value = snapshot.copy(applying = true, error = "")
            repository.update(
                instance = instance,
                baseline = baseline,
                pending = pending
            ).onSuccess { verified ->
                _state.value = _state.value.copy(
                    loading = false,
                    applying = false,
                    current = verified,
                    pending = verified,
                    error = ""
                )
                _messages.emit("安全保护设置已应用")
            }.onFailure {
                _state.value = _state.value.copy(
                    applying = false,
                    error = it.message ?: "安全保护设置应用失败"
                )
                _messages.emit(it.message ?: "安全保护设置应用失败")
            }
        }
    }

    private fun mutate(transform: (AghProtectionConfig) -> AghProtectionConfig) {
        val pending = _state.value.pending ?: return
        _state.value = _state.value.copy(
            pending = transform(pending),
            error = ""
        )
    }

    class Factory(
        private val instance: AghInstance,
        private val repository: AghProtectionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AghProtectionViewModel::class.java)) {
                return AghProtectionViewModel(instance, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
