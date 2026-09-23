package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.AghAutoClient
import io.github.chenyurumeng.aghmanager.model.AghClient
import io.github.chenyurumeng.aghmanager.model.AghClientsSnapshot
import io.github.chenyurumeng.aghmanager.model.AghInstance
import org.json.JSONArray
import org.json.JSONObject

class AghClientsRepository(
    private val apiRepository: AghApiRepository
) {
    suspend fun load(instance: AghInstance): Result<AghClientsSnapshot> =
        apiRepository.control(
            instance = instance,
            method = "GET",
            path = "/control/clients"
        ).mapCatching { response ->
            val json = JSONObject(response)
            AghClientsSnapshot(
                clients = parseClients(json.optJSONArray("clients")),
                autoClients = parseAutoClients(json.optJSONArray("auto_clients")),
                supportedTags = json.stringList("supported_tags")
            )
        }

    suspend fun add(
        instance: AghInstance,
        client: AghClient
    ): Result<Unit> {
        validate(client)?.let {
            return Result.failure(IllegalArgumentException(it))
        }
        return apiRepository.control(
            instance = instance,
            method = "POST",
            path = "/control/clients/add",
            body = clientJson(client, null).toString()
        ).map { Unit }
    }

    suspend fun update(
        instance: AghInstance,
        original: AghClient,
        updated: AghClient
    ): Result<Unit> {
        validate(updated)?.let {
            return Result.failure(IllegalArgumentException(it))
        }

        val data = clientJson(updated, original.rawJson)
        val body = JSONObject()
            .put("name", original.name)
            .put("data", data)
            .toString()

        return apiRepository.control(
            instance = instance,
            method = "POST",
            path = "/control/clients/update",
            body = body
        ).map { Unit }
    }

    suspend fun delete(
        instance: AghInstance,
        client: AghClient
    ): Result<Unit> =
        apiRepository.control(
            instance = instance,
            method = "POST",
            path = "/control/clients/delete",
            body = JSONObject().put("name", client.name).toString()
        ).map { Unit }

    private fun validate(client: AghClient): String? {
        if (client.name.trim().isBlank()) return "客户端名称不能为空"
        if (client.name.any { it == '\n' || it == '\r' || it == '\u0000' }) {
            return "客户端名称包含非法字符"
        }
        if (client.ids.isEmpty()) return "至少需要一个客户端 ID"
        if (client.ids.any { it.isBlank() || it.contains('\n') || it.contains('\r') }) {
            return "客户端 ID 包含非法值"
        }
        if (client.upstreams.any { it.contains('\n') || it.contains('\r') }) {
            return "客户端 Upstream 包含非法值"
        }
        return null
    }

    private fun clientJson(client: AghClient, rawJson: String?): JSONObject {
        val json = runCatching {
            rawJson?.takeIf { it.isNotBlank() }?.let(::JSONObject) ?: JSONObject()
        }.getOrElse { JSONObject() }

        json.put("name", client.name.trim())
        json.put("ids", JSONArray(client.ids.distinct()))
        json.put("use_global_settings", client.useGlobalSettings)
        json.put("filtering_enabled", client.filteringEnabled)
        json.put("parental_enabled", client.parentalEnabled)
        json.put("safebrowsing_enabled", client.safeBrowsingEnabled)
        json.put("upstreams", JSONArray(client.upstreams))
        json.put("tags", JSONArray(client.tags))
        json.put("ignore_querylog", client.ignoreQueryLog)
        json.put("ignore_statistics", client.ignoreStatistics)
        return json
    }

    private fun parseClients(array: JSONArray?): List<AghClient> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    AghClient(
                        name = item.optString("name", ""),
                        ids = item.stringList("ids"),
                        useGlobalSettings = item.optBoolean("use_global_settings", true),
                        filteringEnabled = item.optBoolean("filtering_enabled", true),
                        parentalEnabled = item.optBoolean("parental_enabled", false),
                        safeBrowsingEnabled = item.optBoolean("safebrowsing_enabled", false),
                        upstreams = item.stringList("upstreams"),
                        tags = item.stringList("tags"),
                        ignoreQueryLog = item.optBoolean("ignore_querylog", false),
                        ignoreStatistics = item.optBoolean("ignore_statistics", false),
                        rawJson = item.toString()
                    )
                )
            }
        }.sortedBy { it.name.lowercase() }
    }

    private fun parseAutoClients(array: JSONArray?): List<AghAutoClient> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    AghAutoClient(
                        ip = item.optString("ip", ""),
                        name = item.optString("name", ""),
                        source = item.optString("source", "")
                    )
                )
            }
        }.sortedBy { it.ip }
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
