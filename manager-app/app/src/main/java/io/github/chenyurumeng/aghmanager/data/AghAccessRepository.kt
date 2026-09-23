package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.AghAccessList
import io.github.chenyurumeng.aghmanager.model.AghInstance
import org.json.JSONArray
import org.json.JSONObject

class AghAccessRepository(
    private val apiRepository: AghApiRepository
) {
    suspend fun load(instance: AghInstance): Result<AghAccessList> =
        apiRepository.control(
            instance = instance,
            method = "GET",
            path = "/control/access/list"
        ).mapCatching { response ->
            val json = JSONObject(response)
            AghAccessList(
                allowedClients = json.stringList("allowed_clients"),
                disallowedClients = json.stringList("disallowed_clients"),
                blockedHosts = json.stringList("blocked_hosts")
            ).normalized()
        }

    suspend fun update(
        instance: AghInstance,
        baseline: AghAccessList,
        pending: AghAccessList
    ): Result<Unit> {
        val normalizedBaseline = baseline.normalized()
        val normalizedPending = pending.normalized()
        validate(normalizedPending)?.let {
            return Result.failure(IllegalArgumentException(it))
        }

        val latest = load(instance)
        if (latest.isFailure) return Result.failure(latest.exceptionOrNull()!!)
        if (latest.getOrThrow().normalized() != normalizedBaseline) {
            return Result.failure(
                IllegalStateException(
                    "Access Control 已在其它位置发生变化，请刷新后重新编辑，避免覆盖新修改"
                )
            )
        }

        val body = JSONObject()
            .put("allowed_clients", JSONArray(normalizedPending.allowedClients))
            .put("disallowed_clients", JSONArray(normalizedPending.disallowedClients))
            .put("blocked_hosts", JSONArray(normalizedPending.blockedHosts))
            .toString()

        return apiRepository.control(
            instance = instance,
            method = "POST",
            path = "/control/access/set",
            body = body
        ).map { Unit }
    }

    private fun validate(list: AghAccessList): String? {
        val overlap = list.allowedClients.toSet().intersect(list.disallowedClients.toSet())
        if (overlap.isNotEmpty()) {
            return "允许与禁止客户端不能包含相同项目：" + overlap.take(3).joinToString(", ")
        }

        val all = list.allowedClients + list.disallowedClients + list.blockedHosts
        if (all.any { it.contains('\n') || it.contains('\r') || it.contains('\u0000') }) {
            return "访问控制列表包含非法字符"
        }
        return null
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
