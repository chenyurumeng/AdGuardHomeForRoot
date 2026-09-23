package io.github.chenyurumeng.aghmanager.ui.agh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.AghTlsRepository
import io.github.chenyurumeng.aghmanager.model.AghInstance
import io.github.chenyurumeng.aghmanager.model.AghTlsDraft
import io.github.chenyurumeng.aghmanager.model.AghTlsUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AghTlsViewModel(
    val instance: AghInstance,
    private val repository: AghTlsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AghTlsUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 3)
    val messages = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        val state = _state.value
        if (state.applying || state.validating) return
        viewModelScope.launch {
            _state.value = state.copy(loading = true, error = "")
            repository.load(instance)
                .onSuccess { config ->
                    _state.value = _state.value.copy(
                        loading = false,
                        current = config,
                        pending = config.toDraft(),
                        validation = config.validation,
                        error = ""
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "TLS 状态读取失败"
                    )
                }
        }
    }

    fun update(transform: (AghTlsDraft) -> AghTlsDraft) {
        val pending = _state.value.pending ?: return
        _state.value = _state.value.copy(
            pending = transform(pending),
            validation = null,
            error = ""
        )
    }

    fun setCertificatePem(value: String) {
        update {
            it.copy(
                certificatePem = value,
                certificatePath = if (value.isNotBlank()) "" else it.certificatePath
            )
        }
    }

    fun setCertificatePath(value: String) {
        update {
            it.copy(
                certificatePath = value,
                certificatePem = if (value.isNotBlank()) "" else it.certificatePem
            )
        }
    }

    fun setPrivateKeyPem(value: String) {
        update {
            it.copy(
                privateKeyPem = value,
                privateKeyPath = if (value.isNotBlank()) "" else it.privateKeyPath,
                replacePrivateKey = true
            )
        }
    }

    fun setPrivateKeyPath(value: String) {
        update {
            it.copy(
                privateKeyPath = value,
                privateKeyPem = if (value.isNotBlank()) "" else it.privateKeyPem,
                replacePrivateKey = true
            )
        }
    }

    fun clearPrivateKey() {
        update {
            it.copy(
                privateKeyPem = "",
                privateKeyPath = "",
                replacePrivateKey = true
            )
        }
    }

    fun keepExistingPrivateKey() {
        val current = _state.value.current ?: return
        update {
            it.copy(
                privateKeyPem = "",
                privateKeyPath = current.privateKeyPath,
                replacePrivateKey = false,
                existingPrivateKeySaved = current.privateKeySaved
            )
        }
    }

    fun discard() {
        val current = _state.value.current ?: return
        _state.value = _state.value.copy(
            pending = current.toDraft(),
            validation = current.validation,
            error = ""
        )
    }

    fun validate() {
        val snapshot = _state.value
        val current = snapshot.current ?: return
        val pending = snapshot.pending ?: return
        if (snapshot.validating || snapshot.applying) return

        viewModelScope.launch {
            _state.value = snapshot.copy(validating = true, error = "")
            repository.validate(instance, current, pending)
                .onSuccess {
                    _state.value = _state.value.copy(
                        validating = false,
                        validation = it,
                        error = ""
                    )
                    _messages.emit(
                        if (it.validPair || !pending.enabled) "TLS 配置验证完成"
                        else "TLS 配置验证完成，但证书/私钥状态异常"
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        validating = false,
                        validation = null,
                        error = it.message ?: "TLS 配置验证失败"
                    )
                }
        }
    }

    fun apply() {
        val snapshot = _state.value
        val current = snapshot.current ?: return
        val pending = snapshot.pending ?: return
        if (snapshot.applying || snapshot.validating || !snapshot.dirty) return

        viewModelScope.launch {
            _state.value = snapshot.copy(applying = true, error = "")
            repository.update(instance, current, pending)
                .onSuccess { config ->
                    _state.value = _state.value.copy(
                        loading = false,
                        applying = false,
                        current = config,
                        pending = config.toDraft(),
                        validation = config.validation,
                        error = ""
                    )
                    _messages.emit("TLS / Encrypted DNS 配置已应用")
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        applying = false,
                        error = it.message ?: "TLS 配置应用失败"
                    )
                    _messages.emit("TLS 配置未完成，请查看页面错误")
                }
        }
    }

    class Factory(
        private val instance: AghInstance,
        private val repository: AghTlsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AghTlsViewModel::class.java)) {
                return AghTlsViewModel(instance, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
