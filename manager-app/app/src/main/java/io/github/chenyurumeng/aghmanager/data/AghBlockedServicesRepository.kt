package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.AghBlockedService
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
                scheduleJson = currentJson.optJSONObject("schedule")?.toString().orEmpty()
            )
        }
    }

    suspend fun update(
        instance: AghInstance,
        baselineIds: Set<String>,
        selectedIds: Set<String>
    ): Result<Unit> {
        val latest = load(instance)
        if (latest.isFailure) return Result.failure(latest.exceptionOrNull()!!)
        val snapshot = latest.getOrThrow()

        if (snapshot.selectedIds != baselineIds) {
            return Result.failure(
                IllegalStateException(
                    "Blocked Services 已在其它位置发生变化，请刷新后重新编辑，避免覆盖新修改"
                )
            )
        }

        val body = JSONObject()
            .put("ids", JSONArray(selectedIds.sorted()))

        if (snapshot.scheduleJson.isNotBlank()) {
            val schedule = runCatching { JSONObject(snapshot.scheduleJson) }.getOrElse {
                return Result.failure(
                    IllegalStateException("最新 Blocked Services schedule 无法解析，已拒绝覆盖")
                )
            }
            body.put("schedule", schedule)
        }

        return apiRepository.control(
            instance = instance,
            method = "PUT",
            path = "/control/blocked_services/update",
            body = body.toString()
        ).map { Unit }
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
