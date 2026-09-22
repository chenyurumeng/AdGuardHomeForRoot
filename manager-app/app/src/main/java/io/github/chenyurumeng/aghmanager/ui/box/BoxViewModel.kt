package io.github.chenyurumeng.aghmanager.ui.box

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.BoxSettingsRepository
import io.github.chenyurumeng.aghmanager.data.ShellResult
import io.github.chenyurumeng.aghmanager.data.StatusRepository
import io.github.chenyurumeng.aghmanager.domain.BoxController
import io.github.chenyurumeng.aghmanager.model.BoxConfig
import io.github.chenyurumeng.aghmanager.model.BoxConfigUiState
import io.github.chenyurumeng.aghmanager.model.DnsHijackMode
import io.github.chenyurumeng.aghmanager.model.NetworkMode
import io.github.chenyurumeng.aghmanager.model.RoutingMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BoxViewModel(
    private val statusRepository: StatusRepository,
    private val boxController: BoxController,
    private val boxSettingsRepository: BoxSettingsRepository
) : ViewModel() {
    val state = statusRepository.state

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _configState = MutableStateFlow(BoxConfigUiState())
    val configState = _configState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_busy.value || _configState.value.applying) return
        viewModelScope.launch {
            statusRepository.refresh()
            reloadConfig(preservePending = true)
        }
    }

    fun setProxyMode(value: RoutingMode) = updatePending { it.copy(proxyMode = value) }
    fun setNetworkMode(value: NetworkMode) = updatePending { it.copy(networkMode = value) }
    fun setDnsHijackMode(value: DnsHijackMode) = updatePending { it.copy(dnsHijackMode = value) }
    fun setIpv6(value: Boolean) = updatePending { it.copy(ipv6 = value) }
    fun setProxyTcp(value: Boolean) = updatePending { it.copy(proxyTcp = value) }
    fun setProxyUdp(value: Boolean) = updatePending { it.copy(proxyUdp = value) }
    fun setDnsHijackTcp(value: Boolean) = updatePending { it.copy(dnsHijackTcp = value) }
    fun setDnsHijackUdp(value: Boolean) = updatePending { it.copy(dnsHijackUdp = value) }
    fun setQuic(value: Boolean) = updatePending { it.copy(quic = value) }
    fun setMihomoDnsForward(value: Boolean) = updatePending { it.copy(mihomoDnsForward = value) }

    fun discardConfig() {
        val current = _configState.value
        if (current.applying) return
        _configState.value = current.copy(
            pending = current.current,
            error = ""
        )
    }

    fun applyConfig() {
        val snapshot = _configState.value
        if (!snapshot.loaded || !snapshot.dirty || snapshot.applying) return

        _configState.value = snapshot.copy(applying = true, error = "")
        viewModelScope.launch {
            val result = boxSettingsRepository.apply(
                current = snapshot.current,
                pending = snapshot.pending,
                boxRunning = state.value.box.running
            )
            if (result.ok) {
                statusRepository.refresh()
                reloadConfig(preservePending = false)
                _messages.emit(
                    if (state.value.box.running) {
                        "Box 配置已应用"
                    } else {
                        "Box 配置已保存，将在下次启动时生效"
                    }
                )
            } else {
                statusRepository.refresh()
                _configState.value = _configState.value.copy(
                    applying = false,
                    error = "应用失败（exit=" + result.exitCode + "），已尝试恢复原配置"
                )
                _messages.emit("配置应用失败，已尝试回滚")
            }
        }
    }

    fun start() = runAction("启动 Box") { boxController.start() }
    fun restart() = runAction("重启 Box") { boxController.restart() }
    fun stop() = runAction("停止 Box") { boxController.stop() }

    private fun updatePending(transform: (BoxConfig) -> BoxConfig) {
        val current = _configState.value
        if (!current.loaded || current.applying) return
        _configState.value = current.copy(
            pending = transform(current.pending),
            error = ""
        )
    }

    private suspend fun reloadConfig(preservePending: Boolean) {
        val before = _configState.value
        boxSettingsRepository.load()
            .onSuccess { loaded ->
                val pending = if (preservePending && before.dirty) before.pending else loaded
                _configState.value = BoxConfigUiState(
                    current = loaded,
                    pending = pending,
                    loaded = true,
                    applying = false,
                    error = ""
                )
            }
            .onFailure {
                _configState.value = before.copy(
                    applying = false,
                    error = it.message ?: "读取 Box 配置失败"
                )
            }
    }

    private fun runAction(label: String, block: suspend () -> ShellResult) {
        if (_busy.value || _configState.value.applying) return

        viewModelScope.launch {
            _busy.value = true
            try {
                val result = block()
                reloadConfig(preservePending = true)
                _messages.emit(
                    if (result.ok) label + "完成"
                    else label + "失败（exit=" + result.exitCode + "）"
                )
            } finally {
                _busy.value = false
            }
        }
    }

    class Factory(
        private val statusRepository: StatusRepository,
        private val boxController: BoxController,
        private val boxSettingsRepository: BoxSettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BoxViewModel::class.java)) {
                return BoxViewModel(
                    statusRepository,
                    boxController,
                    boxSettingsRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
