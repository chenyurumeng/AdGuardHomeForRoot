package io.github.chenyurumeng.aghmanager.ui.box

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.StatusRepository
import io.github.chenyurumeng.aghmanager.domain.BoxController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BoxViewModel(
    private val statusRepository: StatusRepository,
    private val boxController: BoxController
) : ViewModel() {
    val state = statusRepository.state

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    fun refresh() {
        if (_busy.value) return
        viewModelScope.launch {
            statusRepository.refresh()
        }
    }

    fun start() = runAction("启动 Box") { boxController.start() }

    fun restart() = runAction("重启 Box") { boxController.restart() }

    fun stop() = runAction("停止 Box") { boxController.stop() }

    private fun runAction(
        label: String,
        block: suspend () -> io.github.chenyurumeng.aghmanager.data.ShellResult
    ) {
        if (_busy.value) return

        viewModelScope.launch {
            _busy.value = true
            try {
                val result = block()
                _messages.emit(
                    if (result.ok) {
                        label + "完成"
                    } else {
                        label + "失败（exit=" + result.exitCode + "）"
                    }
                )
            } finally {
                _busy.value = false
            }
        }
    }

    class Factory(
        private val statusRepository: StatusRepository,
        private val boxController: BoxController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BoxViewModel::class.java)) {
                return BoxViewModel(statusRepository, boxController) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
