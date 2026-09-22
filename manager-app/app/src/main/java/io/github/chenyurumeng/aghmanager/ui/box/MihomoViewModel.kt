package io.github.chenyurumeng.aghmanager.ui.box

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.MihomoApiRepository
import io.github.chenyurumeng.aghmanager.data.MihomoPreferencesRepository
import io.github.chenyurumeng.aghmanager.model.MihomoGroup
import io.github.chenyurumeng.aghmanager.model.MihomoQuickUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MihomoViewModel(
    private val repository: MihomoApiRepository,
    private val preferencesRepository: MihomoPreferencesRepository
) : ViewModel() {
    private val _state = MutableStateFlow(
        MihomoQuickUiState(favorites = preferencesRepository.favorites())
    )
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 3)
    val messages = _messages.asSharedFlow()

    fun refresh() {
        if (_state.value.busyAction != null) return
        viewModelScope.launch { refreshInternal(showLoading = true) }
    }

    fun select(groupName: String, proxyName: String) {
        runAction("正在切换 " + groupName + "…") {
            repository.selectProxy(groupName, proxyName)
                .onSuccess {
                    refreshInternal(showLoading = false)
                    _messages.emit(groupName + " → " + proxyName)
                }
        }
    }

    fun toggleFavorite(node: String) {
        _state.value = _state.value.copy(
            favorites = preferencesRepository.toggleFavorite(node)
        )
    }

    fun testGroup(group: MihomoGroup) {
        runAction("正在测速 " + group.name + "…") {
            repository.testGroup(
                groupName = group.name,
                testUrl = group.testUrl.ifBlank { "https://cp.cloudflare.com" }
            ).onSuccess { delays ->
                _state.value = _state.value.copy(
                    delays = _state.value.delays + delays
                )
                _messages.emit(group.name + " 测速完成 · " + delays.size + " 项")
            }
        }
    }

    fun selectFastest(group: MihomoGroup) {
        if (!group.selectable) {
            _messages.tryEmit("该策略组不是 Selector，不能固定节点")
            return
        }

        runAction("正在测速并选择最快节点…") {
            repository.testGroup(
                groupName = group.name,
                testUrl = group.testUrl.ifBlank { "https://cp.cloudflare.com" }
            ).fold(
                onSuccess = { delays ->
                    _state.value = _state.value.copy(
                        delays = _state.value.delays + delays
                    )
                    val fastest = group.all
                        .asSequence()
                        .filter { it !in setOf("DIRECT", "REJECT", "REJECT-DROP", "PASS") }
                        .mapNotNull { node -> delays[node]?.takeIf { it > 0 }?.let { node to it } }
                        .minByOrNull { it.second }

                    if (fastest == null) {
                        Result.failure(IllegalStateException("没有获得有效节点延迟"))
                    } else {
                        repository.selectProxy(group.name, fastest.first)
                            .onSuccess {
                                refreshInternal(showLoading = false)
                                _messages.emit(
                                    "已切换最快节点：" + fastest.first + " · " + fastest.second + " ms"
                                )
                            }
                    }
                },
                onFailure = { Result.failure(it) }
            )
        }
    }

    fun testAll() {
        val groups = _state.value.groups
        val target = groups.firstOrNull { it.name == "国外代理" }
            ?: groups.maxByOrNull { it.all.size }

        if (target == null) {
            _messages.tryEmit("没有可测速的策略组")
            return
        }
        testGroup(target)
    }

    fun updateProvider(name: String) {
        runAction("正在更新 " + name + "…") {
            repository.updateProvider(name)
                .onSuccess {
                    refreshInternal(showLoading = false)
                    _messages.emit(name + " 更新完成")
                }
        }
    }

    fun updateAllProviders() {
        val names = _state.value.providers.map { it.name }
        if (names.isEmpty()) {
            _messages.tryEmit("没有可更新的 Proxy Provider")
            return
        }

        runAction("正在更新全部 Provider…") {
            repository.updateAllProviders(names)
                .onSuccess { count ->
                    refreshInternal(showLoading = false)
                    _messages.emit("已更新 " + count + " 个 Proxy Provider")
                }
        }
    }

    fun flushDns() {
        runAction("正在清空 DNS Cache…") {
            repository.flushDnsCache()
                .onSuccess {
                    _messages.emit("Mihomo DNS Cache 已清空")
                }
        }
    }

    private fun runAction(
        label: String,
        block: suspend () -> Result<*>
    ) {
        if (_state.value.busyAction != null) return

        viewModelScope.launch {
            _state.value = _state.value.copy(busyAction = label, error = "")
            val result = block()
            if (result.isFailure) {
                val message = result.exceptionOrNull()?.message ?: "Mihomo API 操作失败"
                _state.value = _state.value.copy(error = message)
                _messages.emit(message)
            }
            _state.value = _state.value.copy(busyAction = null)
        }
    }

    private suspend fun refreshInternal(showLoading: Boolean) {
        if (showLoading) {
            _state.value = _state.value.copy(loading = true, error = "")
        }

        repository.loadSnapshot()
            .onSuccess { snapshot ->
                _state.value = _state.value.copy(
                    loading = false,
                    available = true,
                    controller = snapshot.controller,
                    version = snapshot.version,
                    authenticated = snapshot.authenticated,
                    groups = snapshot.groups,
                    providers = snapshot.providers,
                    favorites = preferencesRepository.favorites(),
                    error = ""
                )
            }
            .onFailure {
                _state.value = _state.value.copy(
                    loading = false,
                    available = false,
                    error = it.message ?: "Mihomo Controller 不可用"
                )
            }
    }

    class Factory(
        private val repository: MihomoApiRepository,
        private val preferencesRepository: MihomoPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MihomoViewModel::class.java)) {
                return MihomoViewModel(repository, preferencesRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
