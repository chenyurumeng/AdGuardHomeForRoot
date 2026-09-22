package io.github.chenyurumeng.aghmanager.ui.agh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.AghApiRepository
import io.github.chenyurumeng.aghmanager.data.AghConfigRepository
import io.github.chenyurumeng.aghmanager.data.AghCredentialStore
import io.github.chenyurumeng.aghmanager.data.StatusRepository
import io.github.chenyurumeng.aghmanager.model.AghControlUiState
import io.github.chenyurumeng.aghmanager.model.AghCredential
import io.github.chenyurumeng.aghmanager.model.AghDnsConfig
import io.github.chenyurumeng.aghmanager.model.AghInstance
import io.github.chenyurumeng.aghmanager.model.AghStructuralConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mindrot.jbcrypt.BCrypt

class AghControlViewModel(
    val instance: AghInstance,
    private val configRepository: AghConfigRepository,
    private val apiRepository: AghApiRepository,
    private val credentialStore: AghCredentialStore,
    private val statusRepository: StatusRepository
) : ViewModel() {
    private val _state = MutableStateFlow(
        AghControlUiState(
            structure = AghStructuralConfig(
                webPort = instance.defaultWebPort,
                dnsPort = instance.defaultDnsPort
            ),
            pendingStructure = AghStructuralConfig(
                webPort = instance.defaultWebPort,
                dnsPort = instance.defaultDnsPort
            )
        )
    )
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 3)
    val messages = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.applyingStructure || _state.value.applyingDns) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = "")
            val structureResult = configRepository.load(instance)
            if (structureResult.isFailure) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = structureResult.exceptionOrNull()?.message ?: "AGH 配置读取失败"
                )
                return@launch
            }

            val structure = structureResult.getOrThrow()
            val credential = credentialStore.load(instance)
            val dnsResult = apiRepository.loadDns(instance)

            _state.value = _state.value.copy(
                loading = false,
                structure = structure,
                pendingStructure = structure,
                credentialBound = credential != null,
                credentialUsername = credential?.username.orEmpty(),
                dns = dnsResult.getOrNull(),
                pendingDns = dnsResult.getOrNull(),
                apiAvailable = dnsResult.isSuccess,
                error = if (dnsResult.isFailure) {
                    dnsResult.exceptionOrNull()?.message ?: "AGH API 不可用"
                } else {
                    ""
                }
            )
        }
    }

    fun bindCredential(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _messages.tryEmit("请输入用户名和密码")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = "")
            apiRepository.verifyCredential(instance, username.trim(), password)
                .onSuccess {
                    credentialStore.save(
                        instance,
                        AghCredential(username.trim(), password)
                    )
                    _messages.emit("AGH 凭据验证成功")
                    refresh()
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "凭据验证失败"
                    )
                    _messages.emit("AGH 凭据验证失败")
                }
        }
    }

    fun clearCredential() {
        credentialStore.clear(instance)
        _state.value = _state.value.copy(
            credentialBound = false,
            credentialUsername = "",
            apiAvailable = false,
            dns = null,
            pendingDns = null
        )
        _messages.tryEmit("已清除保存的 AGH 凭据")
    }

    fun setStructure(value: AghStructuralConfig) {
        _state.value = _state.value.copy(
            pendingStructure = value,
            error = ""
        )
    }

    fun discardStructure() {
        _state.value = _state.value.copy(
            pendingStructure = _state.value.structure,
            error = ""
        )
    }

    fun applyStructure(newPassword: String, confirmPassword: String) {
        val snapshot = _state.value
        if (snapshot.applyingStructure) return

        configRepository.validate(snapshot.pendingStructure)?.let {
            _state.value = snapshot.copy(error = it)
            return
        }

        if (newPassword != confirmPassword) {
            _state.value = snapshot.copy(error = "两次输入的新密码不一致")
            return
        }

        val saved = credentialStore.load(instance)
        val usernameChanged =
            snapshot.pendingStructure.username != snapshot.structure.username

        if (usernameChanged && saved == null && newPassword.isBlank()) {
            _state.value = snapshot.copy(
                error = "修改用户名时需先绑定当前凭据，或同时设置新密码"
            )
            return
        }

        viewModelScope.launch {
            _state.value = snapshot.copy(applyingStructure = true, error = "")
            val passwordHash = if (newPassword.isNotBlank()) {
                BCrypt.hashpw(newPassword, BCrypt.gensalt(12))
            } else {
                null
            }

            configRepository.apply(
                instance = instance,
                current = snapshot.structure,
                pending = snapshot.pendingStructure,
                passwordHash = passwordHash
            ).onSuccess {
                val effectivePassword = newPassword.ifBlank { saved?.password.orEmpty() }
                if (effectivePassword.isNotBlank()) {
                    val newCredential = AghCredential(
                        username = snapshot.pendingStructure.username,
                        password = effectivePassword
                    )
                    apiRepository.verifyCredential(
                        instance,
                        newCredential.username,
                        newCredential.password
                    ).onSuccess {
                        credentialStore.save(instance, newCredential)
                    }.onFailure {
                        credentialStore.clear(instance)
                        _messages.emit("结构配置已应用，但新凭据验证失败；请重新绑定")
                    }
                }

                statusRepository.refresh()
                _messages.emit("AGH 监听/账户配置已应用")
                _state.value = _state.value.copy(applyingStructure = false)
                refresh()
            }.onFailure {
                _state.value = _state.value.copy(
                    applyingStructure = false,
                    error = it.message ?: "结构配置应用失败"
                )
                _messages.emit("AGH 结构配置应用失败")
            }
        }
    }

    fun setDns(value: AghDnsConfig) {
        _state.value = _state.value.copy(
            pendingDns = value,
            upstreamTests = emptyList(),
            error = ""
        )
    }

    fun discardDns() {
        _state.value = _state.value.copy(
            pendingDns = _state.value.dns,
            upstreamTests = emptyList(),
            error = ""
        )
    }

    fun applyDns() {
        val pending = _state.value.pendingDns ?: return
        if (_state.value.applyingDns) return

        viewModelScope.launch {
            _state.value = _state.value.copy(applyingDns = true, error = "")
            apiRepository.updateDns(instance, pending)
                .onSuccess {
                    _messages.emit("DNS 设置已应用")
                    _state.value = _state.value.copy(applyingDns = false)
                    refresh()
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        applyingDns = false,
                        error = it.message ?: "DNS 设置应用失败"
                    )
                    _messages.emit("DNS 设置应用失败")
                }
        }
    }

    fun testUpstreams() {
        val pending = _state.value.pendingDns ?: return
        if (_state.value.testingUpstreams) return

        viewModelScope.launch {
            _state.value = _state.value.copy(testingUpstreams = true, error = "")
            apiRepository.testUpstreams(instance, pending)
                .onSuccess {
                    _state.value = _state.value.copy(
                        testingUpstreams = false,
                        upstreamTests = it
                    )
                    _messages.emit("上游 DNS 测试完成")
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        testingUpstreams = false,
                        error = it.message ?: "上游测试失败"
                    )
                    _messages.emit("上游 DNS 测试失败")
                }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            apiRepository.clearCache(instance)
                .onSuccess { _messages.emit("AGH DNS Cache 已清空") }
                .onFailure { _messages.emit(it.message ?: "清空 DNS Cache 失败") }
        }
    }

    class Factory(
        private val instance: AghInstance,
        private val configRepository: AghConfigRepository,
        private val apiRepository: AghApiRepository,
        private val credentialStore: AghCredentialStore,
        private val statusRepository: StatusRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AghControlViewModel::class.java)) {
                return AghControlViewModel(
                    instance,
                    configRepository,
                    apiRepository,
                    credentialStore,
                    statusRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
