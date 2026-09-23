package io.github.chenyurumeng.aghmanager.ui.agh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.AghStatisticsRepository
import io.github.chenyurumeng.aghmanager.model.AghInstance
import io.github.chenyurumeng.aghmanager.model.AghStatisticsUiState
import io.github.chenyurumeng.aghmanager.model.AghStatsConfig
import io.github.chenyurumeng.aghmanager.model.AghStatsPeriod
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AghStatisticsViewModel(
    val instance: AghInstance,
    private val repository: AghStatisticsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AghStatisticsUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        if (_state.value.savingConfig || _state.value.resetting) return

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = "")
            repository.loadConfig(instance)
                .onSuccess { config ->
                    val recent = chooseInitialRecent(config)
                    if (!config.enabled || config.intervalMs < AghStatisticsRepository.HOUR_MS) {
                        _state.value = _state.value.copy(
                            loading = false,
                            stats = null,
                            config = config,
                            pendingConfig = config,
                            recentMs = recent,
                            error = ""
                        )
                    } else {
                        repository.loadStats(instance, recent)
                            .onSuccess { stats ->
                                _state.value = _state.value.copy(
                                    loading = false,
                                    stats = stats,
                                    config = config,
                                    pendingConfig = config,
                                    recentMs = recent,
                                    error = ""
                                )
                            }
                            .onFailure {
                                _state.value = _state.value.copy(
                                    loading = false,
                                    config = config,
                                    pendingConfig = config,
                                    recentMs = recent,
                                    error = it.message ?: "Statistics 读取失败"
                                )
                            }
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "Statistics 设置读取失败"
                    )
                }
        }
    }

    fun refreshStats() {
        val config = _state.value.config ?: return
        val recent = _state.value.recentMs
        if (!config.enabled || recent <= 0L || _state.value.refreshing) return

        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true, error = "")
            repository.loadStats(instance, recent)
                .onSuccess {
                    _state.value = _state.value.copy(
                        refreshing = false,
                        stats = it,
                        error = ""
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        refreshing = false,
                        error = it.message ?: "Statistics 刷新失败"
                    )
                }
        }
    }

    fun setPeriod(milliseconds: Long) {
        val config = _state.value.config ?: return
        val normalized = milliseconds
            .coerceAtLeast(AghStatisticsRepository.HOUR_MS)
            .coerceAtMost(config.intervalMs)
        if (normalized == _state.value.recentMs) return

        _state.value = _state.value.copy(recentMs = normalized)
        refreshStats()
    }

    fun periodOptions(): List<AghStatsPeriod> {
        val max = _state.value.config?.intervalMs ?: 0L
        if (max < AghStatisticsRepository.HOUR_MS) return emptyList()

        val standard = listOf(
            6L * AghStatisticsRepository.HOUR_MS to "6 小时",
            AghStatisticsRepository.DAY_MS to "1 天",
            7L * AghStatisticsRepository.DAY_MS to "7 天",
            30L * AghStatisticsRepository.DAY_MS to "30 天",
            90L * AghStatisticsRepository.DAY_MS to "90 天"
        )

        val result = mutableListOf<AghStatsPeriod>()
        standard.filter { it.first <= max }.forEach {
            result += AghStatsPeriod(it.second, it.first)
        }

        if (result.none { it.milliseconds == max }) {
            result += AghStatsPeriod("全部保留", max)
        } else if (result.lastOrNull()?.milliseconds == max) {
            val last = result.removeAt(result.lastIndex)
            result += last.copy(label = last.label + "（全部）")
        }

        return result.distinctBy { it.milliseconds }
    }

    fun setPendingConfig(value: AghStatsConfig) {
        _state.value = _state.value.copy(
            pendingConfig = value.normalized(),
            error = ""
        )
    }

    fun discardConfig() {
        _state.value = _state.value.copy(
            pendingConfig = _state.value.config,
            error = ""
        )
    }

    fun applyConfig(onComplete: (Boolean) -> Unit = {}) {
        val baseline = _state.value.config ?: return
        val pending = _state.value.pendingConfig ?: return
        if (_state.value.savingConfig || !_state.value.configDirty) return

        viewModelScope.launch {
            _state.value = _state.value.copy(savingConfig = true, error = "")
            repository.updateConfig(instance, baseline, pending)
                .onSuccess {
                    repository.loadConfig(instance)
                        .onSuccess { latest ->
                            val recent = chooseInitialRecent(latest)
                            _state.value = _state.value.copy(
                                savingConfig = false,
                                config = latest,
                                pendingConfig = latest,
                                recentMs = recent,
                                error = ""
                            )
                            if (latest.enabled && recent > 0L) {
                                repository.loadStats(instance, recent)
                                    .onSuccess { stats ->
                                        _state.value = _state.value.copy(stats = stats)
                                    }
                                    .onFailure {
                                        _state.value = _state.value.copy(
                                            stats = null,
                                            error = it.message ?: "设置已保存，但 Statistics 刷新失败"
                                        )
                                    }
                            } else {
                                _state.value = _state.value.copy(stats = null)
                            }
                            _messages.emit("Statistics 设置已保存")
                            onComplete(true)
                        }
                        .onFailure {
                            _state.value = _state.value.copy(
                                savingConfig = false,
                                error = it.message ?: "设置已保存，但重新读取失败"
                            )
                            onComplete(false)
                        }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        savingConfig = false,
                        error = it.message ?: "Statistics 设置保存失败"
                    )
                    _messages.emit(it.message ?: "Statistics 设置保存失败")
                    onComplete(false)
                }
        }
    }

    fun reset() {
        if (_state.value.resetting) return

        viewModelScope.launch {
            _state.value = _state.value.copy(resetting = true, error = "")
            repository.reset(instance)
                .onSuccess {
                    _messages.emit("Statistics 已重置")
                    val config = _state.value.config
                    val recent = _state.value.recentMs
                    if (config?.enabled == true && recent > 0L) {
                        repository.loadStats(instance, recent)
                            .onSuccess {
                                _state.value = _state.value.copy(
                                    resetting = false,
                                    stats = it
                                )
                            }
                            .onFailure {
                                _state.value = _state.value.copy(
                                    resetting = false,
                                    stats = null,
                                    error = it.message ?: "Statistics 已重置，但刷新失败"
                                )
                            }
                    } else {
                        _state.value = _state.value.copy(
                            resetting = false,
                            stats = null
                        )
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        resetting = false,
                        error = it.message ?: "Statistics 重置失败"
                    )
                    _messages.emit(it.message ?: "Statistics 重置失败")
                }
        }
    }

    private fun chooseInitialRecent(config: AghStatsConfig): Long {
        if (!config.enabled || config.intervalMs < AghStatisticsRepository.HOUR_MS) return 0L
        return minOf(config.intervalMs, AghStatisticsRepository.DAY_MS)
            .coerceAtLeast(AghStatisticsRepository.HOUR_MS)
    }

    class Factory(
        private val instance: AghInstance,
        private val repository: AghStatisticsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AghStatisticsViewModel::class.java)) {
                return AghStatisticsViewModel(instance, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
