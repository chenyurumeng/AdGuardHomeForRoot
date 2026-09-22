package io.github.chenyurumeng.aghmanager.ui.agh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.ShellResult
import io.github.chenyurumeng.aghmanager.data.StatusRepository
import io.github.chenyurumeng.aghmanager.domain.AghController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AghViewModel(
    private val statusRepository: StatusRepository,
    private val aghController: AghController
) : ViewModel() {
    val state = statusRepository.state

    private val _busyAction = MutableStateFlow<String?>(null)
    val busyAction = _busyAction.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    fun refresh() {
        if (_busyAction.value != null) return
        viewModelScope.launch { statusRepository.refresh() }
    }

    fun startAll() = runAction("启动全部 AGH") { aghController.startAll() }
    fun restartAll() = runAction("重启全部 AGH") { aghController.restartAll() }
    fun stopAll() = runAction("停止全部 AGH") { aghController.stopAll() }

    fun startDomestic() = runAction("启动 Domestic") { aghController.startDomestic() }
    fun restartDomestic() = runAction("重启 Domestic") { aghController.restartDomestic() }
    fun stopDomestic() = runAction("停止 Domestic") { aghController.stopDomestic() }

    fun startForeign() = runAction("启动 Foreign") { aghController.startForeign() }
    fun restartForeign() = runAction("重启 Foreign") { aghController.restartForeign() }
    fun stopForeign() = runAction("停止 Foreign") { aghController.stopForeign() }

    private fun runAction(label: String, action: suspend () -> ShellResult) {
        if (_busyAction.value != null) return

        viewModelScope.launch {
            _busyAction.value = label
            try {
                val result = action()
                _messages.emit(
                    if (result.ok) label + "完成"
                    else label + "失败（exit=" + result.exitCode + "）"
                )
            } finally {
                _busyAction.value = null
            }
        }
    }

    class Factory(
        private val statusRepository: StatusRepository,
        private val aghController: AghController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AghViewModel::class.java)) {
                return AghViewModel(statusRepository, aghController) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
