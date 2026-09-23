package io.github.chenyurumeng.aghmanager.model

data class AghAccessList(
    val allowedClients: List<String> = emptyList(),
    val disallowedClients: List<String> = emptyList(),
    val blockedHosts: List<String> = emptyList()
) {
    fun normalized(): AghAccessList = copy(
        allowedClients = allowedClients.map(String::trim).filter(String::isNotBlank).distinct(),
        disallowedClients = disallowedClients.map(String::trim).filter(String::isNotBlank).distinct(),
        blockedHosts = blockedHosts.map(String::trim).filter(String::isNotBlank).distinct()
    )
}

data class AghAccessUiState(
    val loading: Boolean = true,
    val applying: Boolean = false,
    val current: AghAccessList? = null,
    val pending: AghAccessList? = null,
    val error: String = ""
) {
    val dirty: Boolean
        get() = current != null && pending != null &&
            current.normalized() != pending.normalized()
}

data class AghBlockedService(
    val id: String,
    val name: String,
    val groupId: String,
    val rulesCount: Int
)

val AGH_SCHEDULE_DAY_KEYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

data class AghBlockedScheduleDay(
    val startMs: Long,
    val endMs: Long
)

data class AghBlockedServicesSchedule(
    val timeZone: String = "Local",
    val days: Map<String, AghBlockedScheduleDay> = emptyMap()
)

data class AghBlockedScheduleDayDraft(
    val enabled: Boolean = false,
    val startText: String = "00:00",
    val endText: String = "08:00"
)

data class AghBlockedScheduleDraft(
    val timeZone: String = "Local",
    val days: Map<String, AghBlockedScheduleDayDraft> =
        AGH_SCHEDULE_DAY_KEYS.associateWith { AghBlockedScheduleDayDraft() }
)

fun AghBlockedServicesSchedule.toDraft(): AghBlockedScheduleDraft =
    AghBlockedScheduleDraft(
        timeZone = timeZone,
        days = AGH_SCHEDULE_DAY_KEYS.associateWith { key ->
            val day = days[key]
            if (day == null) {
                AghBlockedScheduleDayDraft()
            } else {
                AghBlockedScheduleDayDraft(
                    enabled = true,
                    startText = formatScheduleTime(day.startMs),
                    endText = formatScheduleTime(day.endMs)
                )
            }
        }
    )

fun formatScheduleTime(milliseconds: Long): String {
    if (milliseconds == 86_400_000L) return "24:00"
    val totalMinutes = (milliseconds / 60_000L).coerceIn(0L, 1_439L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return hours.toString().padStart(2, '0') + ":" +
        minutes.toString().padStart(2, '0')
}

data class AghBlockedServicesSnapshot(
    val services: List<AghBlockedService>,
    val selectedIds: Set<String>,
    val schedule: AghBlockedServicesSchedule
)

data class AghBlockedServicesUiState(
    val loading: Boolean = true,
    val applying: Boolean = false,
    val services: List<AghBlockedService> = emptyList(),
    val currentIds: Set<String> = emptySet(),
    val pendingIds: Set<String> = emptySet(),
    val currentSchedule: AghBlockedServicesSchedule = AghBlockedServicesSchedule(),
    val pendingSchedule: AghBlockedScheduleDraft = AghBlockedScheduleDraft(),
    val query: String = "",
    val groupId: String = "",
    val error: String = ""
) {
    val dirty: Boolean
        get() = currentIds != pendingIds || currentSchedule.toDraft() != pendingSchedule

    val groups: List<String>
        get() = services.map { it.groupId }.filter(String::isNotBlank).distinct().sorted()
}
