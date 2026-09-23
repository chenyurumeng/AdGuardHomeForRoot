package io.github.chenyurumeng.aghmanager.ui.agh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.chenyurumeng.aghmanager.data.AghBlockedServicesRepository
import io.github.chenyurumeng.aghmanager.model.AGH_SCHEDULE_DAY_KEYS
import io.github.chenyurumeng.aghmanager.model.AghBlockedScheduleDayDraft
import io.github.chenyurumeng.aghmanager.model.AghBlockedScheduleDraft
import io.github.chenyurumeng.aghmanager.model.AghBlockedServicesUiState
import io.github.chenyurumeng.aghmanager.model.AghInstance
import io.github.chenyurumeng.aghmanager.model.toDraft
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AghBlockedServicesViewModel(
    val instance: AghInstance,
    private val repository: AghBlockedServicesRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AghBlockedServicesUiState())
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
                .onSuccess { snapshot ->
                    _state.value = _state.value.copy(
                        loading = false,
                        services = snapshot.services,
                        currentIds = snapshot.selectedIds,
                        pendingIds = snapshot.selectedIds,
                        currentSchedule = snapshot.schedule,
                        pendingSchedule = snapshot.schedule.toDraft(),
                        error = ""
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "Blocked Services 读取失败"
                    )
                }
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun setGroup(value: String) {
        _state.value = _state.value.copy(groupId = value)
    }

    fun toggle(id: String, selected: Boolean) {
        val next = _state.value.pendingIds.toMutableSet()
        if (selected) next.add(id) else next.remove(id)
        _state.value = _state.value.copy(pendingIds = next, error = "")
    }

    fun setMany(ids: Set<String>, selected: Boolean) {
        val next = _state.value.pendingIds.toMutableSet()
        if (selected) next.addAll(ids) else next.removeAll(ids)
        _state.value = _state.value.copy(pendingIds = next, error = "")
    }

    fun setTimeZone(value: String) {
        _state.value = _state.value.copy(
            pendingSchedule = _state.value.pendingSchedule.copy(timeZone = value),
            error = ""
        )
    }

    fun setDayEnabled(key: String, enabled: Boolean) {
        updateDay(key) { it.copy(enabled = enabled) }
    }

    fun setDayStart(key: String, value: String) {
        updateDay(key) { it.copy(startText = value) }
    }

    fun setDayEnd(key: String, value: String) {
        updateDay(key) { it.copy(endText = value) }
    }

    fun applyScheduleTemplate(template: String) {
        val selected = when (template) {
            "all" -> AGH_SCHEDULE_DAY_KEYS.toSet()
            "weekdays" -> setOf("mon", "tue", "wed", "thu", "fri")
            "weekend" -> setOf("sat", "sun")
            "clear" -> emptySet()
            else -> return
        }

        val nextDays = AGH_SCHEDULE_DAY_KEYS.associateWith { key ->
            val old = _state.value.pendingSchedule.days[key] ?: AghBlockedScheduleDayDraft()
            if (template == "clear") {
                old.copy(enabled = false)
            } else {
                old.copy(
                    enabled = key in selected,
                    startText = if (key in selected) "00:00" else old.startText,
                    endText = if (key in selected) "24:00" else old.endText
                )
            }
        }

        _state.value = _state.value.copy(
            pendingSchedule = _state.value.pendingSchedule.copy(days = nextDays),
            error = ""
        )
    }

    fun discard() {
        _state.value = _state.value.copy(
            pendingIds = _state.value.currentIds,
            pendingSchedule = _state.value.currentSchedule.toDraft(),
            error = ""
        )
    }

    fun apply() {
        val snapshot = _state.value
        if (snapshot.applying || !snapshot.dirty) return

        viewModelScope.launch {
            _state.value = snapshot.copy(applying = true, error = "")
            repository.update(
                instance = instance,
                baselineIds = snapshot.currentIds,
                baselineSchedule = snapshot.currentSchedule,
                selectedIds = snapshot.pendingIds,
                pendingSchedule = snapshot.pendingSchedule
            ).onSuccess { latest ->
                _state.value = _state.value.copy(
                    loading = false,
                    applying = false,
                    services = latest.services,
                    currentIds = latest.selectedIds,
                    pendingIds = latest.selectedIds,
                    currentSchedule = latest.schedule,
                    pendingSchedule = latest.schedule.toDraft(),
                    error = ""
                )
                _messages.emit("Blocked Services 与不生效时段已应用")
            }.onFailure {
                _state.value = _state.value.copy(
                    applying = false,
                    error = it.message ?: "Blocked Services 应用失败"
                )
                _messages.emit(it.message ?: "Blocked Services 应用失败")
            }
        }
    }

    private fun updateDay(
        key: String,
        transform: (AghBlockedScheduleDayDraft) -> AghBlockedScheduleDayDraft
    ) {
        if (key !in AGH_SCHEDULE_DAY_KEYS) return
        val days = _state.value.pendingSchedule.days.toMutableMap()
        days[key] = transform(days[key] ?: AghBlockedScheduleDayDraft())
        _state.value = _state.value.copy(
            pendingSchedule = _state.value.pendingSchedule.copy(days = days),
            error = ""
        )
    }

    class Factory(
        private val instance: AghInstance,
        private val repository: AghBlockedServicesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AghBlockedServicesViewModel::class.java)) {
                return AghBlockedServicesViewModel(instance, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
        }
    }
}
