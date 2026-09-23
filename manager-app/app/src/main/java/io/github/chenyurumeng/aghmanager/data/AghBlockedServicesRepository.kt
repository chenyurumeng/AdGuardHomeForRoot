package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.AGH_SCHEDULE_DAY_KEYS
import io.github.chenyurumeng.aghmanager.model.AghBlockedScheduleDay
import io.github.chenyurumeng.aghmanager.model.AghBlockedScheduleDraft
import io.github.chenyurumeng.aghmanager.model.AghBlockedService
import io.github.chenyurumeng.aghmanager.model.AghBlockedServicesSchedule
import io.github.chenyurumeng.aghmanager.model.AghBlockedServicesSnapshot
import io.github.chenyurumeng.aghmanager.model.AghInstance
import org.json.JSONArray
import org.json.JSONObject

class AghBlockedServicesRepository(
    private val apiRepository: AghApiRepository
) {
    suspend fun load(instance: AghInstance): Result<AghBlockedServicesSnapshot> {
        val all = apiRepository.control(
            instance = instance,
            method = "GET",
            path = "/control/blocked_services/all"
        )
        if (all.isFailure) return Result.failure(all.exceptionOrNull()!!)

        val current = apiRepository.control(
            instance = instance,
            method = "GET",
            path = "/control/blocked_services/get"
        )
        if (current.isFailure) return Result.failure(current.exceptionOrNull()!!)

        return runCatching {
            val allJson = JSONObject(all.getOrThrow())
            val currentJson = JSONObject(current.getOrThrow())
            AghBlockedServicesSnapshot(
                services = parseServices(allJson.optJSONArray("blocked_services")),
                selectedIds = currentJson.stringList("ids").toSet(),
                schedule = parseSchedule(currentJson.optJSONObject("schedule"))
            )
        }
    }

    suspend fun update(
        instance: AghInstance,
        baselineIds: Set<String>,
        baselineSchedule: AghBlockedServicesSchedule,
        selectedIds: Set<String>,
        pendingSchedule: AghBlockedScheduleDraft
    ): Result<AghBlockedServicesSnapshot> {
        val parsedSchedule = parseDraft(pendingSchedule)
        if (parsedSchedule.isFailure) {
            return Result.failure(parsedSchedule.exceptionOrNull()!!)
        }
        val targetSchedule = parsedSchedule.getOrThrow()

        val latest = load(instance)
        if (latest.isFailure) return Result.failure(latest.exceptionOrNull()!!)
        val snapshot = latest.getOrThrow()

        if (
            snapshot.selectedIds != baselineIds ||
            snapshot.schedule != baselineSchedule
        ) {
            return Result.failure(
                IllegalStateException(
                    "Blocked Services 或不生效时段已在其它位置发生变化，请刷新后重新编辑，避免覆盖新修改"
                )
            )
        }

        if (snapshot.selectedIds == selectedIds && snapshot.schedule == targetSchedule) {
            return Result.success(snapshot)
        }

        val body = JSONObject()
            .put("ids", JSONArray(selectedIds.sorted()))
            .put("schedule", scheduleJson(targetSchedule))

        val update = apiRepository.control(
            instance = instance,
            method = "PUT",
            path = "/control/blocked_services/update",
            body = body.toString()
        )
        if (update.isFailure) return Result.failure(update.exceptionOrNull()!!)

        val verified = load(instance)
        if (verified.isFailure) return Result.failure(verified.exceptionOrNull()!!)
        val verifiedSnapshot = verified.getOrThrow()

        if (
            verifiedSnapshot.selectedIds != selectedIds ||
            verifiedSnapshot.schedule != targetSchedule
        ) {
            return Result.failure(
                IllegalStateException("Blocked Services 已提交，但服务器回读状态与 Pending 不一致")
            )
        }

        return Result.success(verifiedSnapshot)
    }

    private fun parseSchedule(json: JSONObject?): AghBlockedServicesSchedule {
        if (json == null) return AghBlockedServicesSchedule()

        val days = buildMap {
            AGH_SCHEDULE_DAY_KEYS.forEach { key ->
                val day = json.optJSONObject(key) ?: return@forEach
                val start = day.optLong("start", -1L)
                val end = day.optLong("end", -1L)
                if (start >= 0L && end >= 0L) {
                    put(key, AghBlockedScheduleDay(startMs = start, endMs = end))
                }
            }
        }

        return AghBlockedServicesSchedule(
            timeZone = json.optString("time_zone", "Local").ifBlank { "Local" },
            days = days
        )
    }

    private fun parseDraft(
        draft: AghBlockedScheduleDraft
    ): Result<AghBlockedServicesSchedule> = runCatching {
        val timeZone = draft.timeZone.trim()
        require(timeZone.isNotBlank()) { "时区不能为空，可使用 Local、UTC 或 IANA 时区名称" }

        val days = buildMap {
            AGH_SCHEDULE_DAY_KEYS.forEach { key ->
                val day = draft.days[key] ?: return@forEach
                if (!day.enabled) return@forEach

                val start = parseClock(day.startText, allow24 = false)
                val end = parseClock(day.endText, allow24 = true)
                require(start < end) {
                    dayLabel(key) + " 的开始时间必须早于结束时间；AGH 单日区间不支持直接跨午夜"
                }
                put(
                    key,
                    AghBlockedScheduleDay(
                        startMs = start * 60_000L,
                        endMs = end * 60_000L
                    )
                )
            }
        }

        AghBlockedServicesSchedule(timeZone = timeZone, days = days)
    }

    private fun parseClock(value: String, allow24: Boolean): Long {
        val match = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(value.trim())
            ?: throw IllegalArgumentException("时间格式应为 HH:mm")
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()

        if (allow24 && hour == 24 && minute == 0) return 1_440L
        require(hour in 0..23 && minute in 0..59) {
            "时间必须在 00:00–23:59" + if (allow24) "，结束时间也可为 24:00" else ""
        }
        return hour * 60L + minute
    }

    private fun scheduleJson(schedule: AghBlockedServicesSchedule): JSONObject {
        val json = JSONObject().put("time_zone", schedule.timeZone)
        AGH_SCHEDULE_DAY_KEYS.forEach { key ->
            val day = schedule.days[key] ?: return@forEach
            json.put(
                key,
                JSONObject()
                    .put("start", day.startMs)
                    .put("end", day.endMs)
            )
        }
        return json
    }

    private fun dayLabel(key: String): String = when (key) {
        "mon" -> "周一"
        "tue" -> "周二"
        "wed" -> "周三"
        "thu" -> "周四"
        "fri" -> "周五"
        "sat" -> "周六"
        "sun" -> "周日"
        else -> key
    }

    private fun parseServices(array: JSONArray?): List<AghBlockedService> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id", "").trim()
                if (id.isBlank()) continue
                add(
                    AghBlockedService(
                        id = id,
                        name = item.optString("name", id),
                        groupId = item.optString("group_id", ""),
                        rulesCount = item.optJSONArray("rules")?.length() ?: 0
                    )
                )
            }
        }.sortedWith(
            compareBy<AghBlockedService> { it.groupId.lowercase() }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i, "").trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }
}
