package io.github.chenyurumeng.aghmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.BackupRepository
import io.github.chenyurumeng.aghmanager.model.BackupUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BackupViewModel(
    private val repository: BackupRepository
) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state = _state.asStateFlow()

    private val _exportEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val exportEvents = _exportEvents.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private var importedRaw: String = ""

    fun prepareExport() {
        if (_state.value.exporting || _state.value.restoring) return
        viewModelScope.launch {
            _state.value = _state.value.copy(exporting = true, error = "")
            repository.createBackup()
                .onSuccess {
                    _state.value = _state.value.copy(exporting = false)
                    _exportEvents.emit(it)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        exporting = false,
                        error = it.message ?: "备份生成失败"
                    )
                }
        }
    }

    fun importBackup(raw: String) {
        if (_state.value.importing || _state.value.restoring) return
        viewModelScope.launch {
            _state.value = _state.value.copy(importing = true, error = "")
            repository.preview(raw)
                .onSuccess { preview ->
                    importedRaw = raw
                    _state.value = _state.value.copy(
                        importing = false,
                        imported = true,
                        sourceCreatedAt = preview.createdAt,
                        modules = preview.modules,
                        error = ""
                    )
                }
                .onFailure {
                    importedRaw = ""
                    _state.value = _state.value.copy(
                        importing = false,
                        imported = false,
                        modules = emptyList(),
                        error = it.message ?: "备份导入失败"
                    )
                }
        }
    }

    fun toggleModule(key: String, selected: Boolean) {
        _state.value = _state.value.copy(
            modules = _state.value.modules.map {
                if (it.key == key) it.copy(selected = selected) else it
            }
        )
    }

    fun restoreSelected() {
        val raw = importedRaw
        if (raw.isBlank() || _state.value.restoring) return
        val selected = _state.value.modules.filter { it.selected }.mapTo(linkedSetOf()) { it.key }
        if (selected.isEmpty()) {
            _state.value = _state.value.copy(error = "至少选择一个需要恢复的模块")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(restoring = true, error = "")
            repository.restore(raw, selected)
                .onSuccess { result ->
                    _state.value = _state.value.copy(
                        restoring = false,
                        lastSnapshotPath = result.preRestoreSnapshotPath,
                        error = ""
                    )
                    val suffix = if (result.warnings.isEmpty()) {
                        ""
                    } else {
                        "；" + result.warnings.take(2).joinToString("；")
                    }
                    _messages.emit("恢复完成，已保留恢复前快照" + suffix)
                    repository.preview(raw).onSuccess { preview ->
                        _state.value = _state.value.copy(modules = preview.modules)
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        restoring = false,
                        error = it.message ?: "恢复失败"
                    )
                    _messages.emit(it.message ?: "恢复失败")
                }
        }
    }

    fun reportError(message: String) {
        _state.value = _state.value.copy(error = message.take(500))
    }

    class Factory(
        private val repository: BackupRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BackupViewModel::class.java)) {
                return BackupViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
