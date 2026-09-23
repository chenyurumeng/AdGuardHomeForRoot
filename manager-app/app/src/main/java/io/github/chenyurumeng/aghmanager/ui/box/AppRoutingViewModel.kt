package io.github.chenyurumeng.aghmanager.ui.box

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.AppRepository
import io.github.chenyurumeng.aghmanager.data.StatusRepository
import io.github.chenyurumeng.aghmanager.model.AppEntry
import io.github.chenyurumeng.aghmanager.model.AppFilter
import io.github.chenyurumeng.aghmanager.model.AppRoutingUiState
import io.github.chenyurumeng.aghmanager.model.RoutingMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class AppRoutingViewModel(
    private val appRepository: AppRepository,
    private val statusRepository: StatusRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AppRoutingUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    init {
        loadCacheAndSync()
    }

    fun setMode(mode: RoutingMode) {
        if (_state.value.applying) return
        updateState(_state.value.copy(mode = mode))
    }

    fun setFilter(filter: AppFilter) {
        updateState(_state.value.copy(filter = filter))
    }

    fun setQuery(query: String) {
        updateState(_state.value.copy(query = query))
    }

    fun toggleApp(key: String) {
        if (_state.value.applying) return

        val selected = _state.value.selected.toMutableSet()
        if (!selected.add(key)) selected.remove(key)
        updateState(_state.value.copy(selected = selected))
    }

    fun syncNow() {
        if (_state.value.syncing || _state.value.applying) return
        viewModelScope.launch {
            syncFromDevice()
        }
    }

    fun apply() {
        val current = _state.value
        if (!current.dirty || current.applying) return

        val mode = current.mode
        val selected = current.selected.toSet()
        val apps = current.apps.toList()

        _state.value = current.copy(
            applying = true,
            statusText = "正在应用 " + mode.raw + "…",
            statusIsError = false
        )

        viewModelScope.launch {
            val result = appRepository.applyRouting(mode, selected, apps)
            if (result.ok) {
                updateState(
                    _state.value.copy(
                        appliedMode = mode,
                        appliedSelected = selected,
                        applying = false,
                        statusText = "已应用 · " + mode.raw + " · " + selected.size + " 个应用",
                        statusIsError = false
                    )
                )
                statusRepository.refresh()
                _messages.emit("应用分流已生效")
            } else {
                _state.value = _state.value.copy(
                    applying = false,
                    statusText = "应用失败 · exit=" + result.exitCode,
                    statusIsError = true
                )
                _messages.emit("应用分流失败")
            }
        }
    }

    private fun loadCacheAndSync() {
        viewModelScope.launch {
            val cache = appRepository.loadCache()
            updateState(
                _state.value.copy(
                    apps = cache.apps,
                    mode = cache.mode,
                    appliedMode = cache.mode,
                    selected = cache.selected,
                    appliedSelected = cache.selected,
                    cacheLoaded = true,
                    syncing = true,
                    statusText = if (cache.apps.isEmpty()) {
                        "首次建立应用缓存 · 正在读取设备应用…"
                    } else {
                        "已从本地缓存加载 " + cache.apps.size + " 个应用 · 后台增量检查中…"
                    },
                    statusIsError = false
                )
            )
            syncFromDevice(alreadyMarkedSyncing = true)
        }
    }

    private suspend fun syncFromDevice(alreadyMarkedSyncing: Boolean = false) {
        if (!alreadyMarkedSyncing) {
            _state.value = _state.value.copy(
                syncing = true,
                statusText = "后台增量检查中…",
                statusIsError = false
            )
        }

        val result = appRepository.syncIncrementally()
        if (!result.ok) {
            _state.value = _state.value.copy(
                syncing = false,
                statusText = "后台同步失败 · 继续使用本地缓存",
                statusIsError = true
            )
            return
        }

        val current = _state.value
        val keepPending = current.dirty
        val liveKeys = result.apps.asSequence().map { it.key }.toSet()
        val backendSelected = result.selected
        val cleanedBackendSelected = backendSelected.filterTo(linkedSetOf()) { it in liveKeys }
        val cleanedPendingSelected = current.selected.filterTo(linkedSetOf()) { it in liveKeys }
        val staleCount = backendSelected.size - cleanedBackendSelected.size

        val delta = buildString {
            if (result.added == 0 && result.removed == 0) {
                append("无应用变化")
            } else {
                append("新增 ")
                append(result.added)
                append(" · 删除 ")
                append(result.removed)
            }
            if (staleCount > 0) {
                append(" · 待清理失效选项 ")
                append(staleCount)
            }
        }

        updateState(
            current.copy(
                apps = result.apps,
                appliedMode = result.mode,
                appliedSelected = backendSelected,
                mode = if (keepPending) current.mode else result.mode,
                selected = if (keepPending) cleanedPendingSelected else cleanedBackendSelected,
                syncing = false,
                statusText = "缓存已同步 · " + result.apps.size + " 个应用 · " + delta,
                statusIsError = false
            )
        )
    }

    private fun updateState(newState: AppRoutingUiState) {
        val previous = _state.value
        val filterInputsUnchanged =
            newState.apps === previous.apps &&
                newState.filter == previous.filter &&
                newState.query == previous.query
        val visible = if (filterInputsUnchanged) {
            previous.visibleApps
        } else {
            filterApps(
                apps = newState.apps,
                filter = newState.filter,
                query = newState.query
            )
        }
        _state.value = newState.copy(visibleApps = visible)
    }

    private fun filterApps(
        apps: List<AppEntry>,
        filter: AppFilter,
        query: String
    ): List<AppEntry> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        return apps.filter { app ->
            val typeMatches = when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.system
                AppFilter.SYSTEM -> app.system
            }
            if (!typeMatches) return@filter false

            if (normalizedQuery.isEmpty()) {
                true
            } else {
                val haystack = (
                    app.label + " " +
                        app.packageName + " " +
                        app.userName + " " +
                        app.userId
                    ).lowercase(Locale.ROOT)
                haystack.contains(normalizedQuery)
            }
        }
    }

    class Factory(
        private val appRepository: AppRepository,
        private val statusRepository: StatusRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AppRoutingViewModel::class.java)) {
                return AppRoutingViewModel(appRepository, statusRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
