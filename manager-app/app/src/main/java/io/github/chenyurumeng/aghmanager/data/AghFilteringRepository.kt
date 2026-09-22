package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.AghFilterCheckResult
import io.github.chenyurumeng.aghmanager.model.AghFilterMatch
import io.github.chenyurumeng.aghmanager.model.AghFilterSubscription
import io.github.chenyurumeng.aghmanager.model.AghFilteringStatus
import io.github.chenyurumeng.aghmanager.model.AghInstance
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class AghFilteringRepository(
    private val apiRepository: AghApiRepository
) {
    suspend fun load(instance: AghInstance): Result<AghFilteringStatus> =
        apiRepository.control(
            instance = instance,
            method = "GET",
            path = "/control/filtering/status"
        ).mapCatching { parseStatus(JSONObject(it)) }

    suspend fun updateSettings(
        instance: AghInstance,
        enabled: Boolean,
        intervalHours: Int
    ): Result<Unit> {
        if (intervalHours !in 0..8760) {
            return Result.failure(
                IllegalArgumentException("过滤器更新周期必须在 0–8760 小时")
            )
        }
        val body = JSONObject()
            .put("enabled", enabled)
            .put("interval", intervalHours)
            .toString()
        return apiRepository.control(
            instance = instance,
            method = "POST",
            path = "/control/filtering/config",
            body = body
        ).map { Unit }
    }

    suspend fun add(
        instance: AghInstance,
        name: String,
        url: String,
        whitelist: Boolean
    ): Result<Unit> {
        validateNameUrl(name, url)?.let {
            return Result.failure(IllegalArgumentException(it))
        }
        val body = JSONObject()
            .put("name", name.trim())
            .put("url", url.trim())
            .put("whitelist", whitelist)
            .toString()
        return apiRepository.control(
            instance = instance,
            method = "POST",
            path = "/control/filtering/add_url",
            body = body,
            readTimeoutMs = 30_000
        ).map { Unit }
    }

    suspend fun update(
        instance: AghInstance,
        original: AghFilterSubscription,
        name: String,
        url: String,
        enabled: Boolean
    ): Result<Unit> {
        validateNameUrl(name, url)?.let {
            return Result.failure(IllegalArgumentException(it))
        }
        val body = JSONObject()
            .put("url", original.url)
            .put("whitelist", original.whitelist)
            .put(
                "data",
                JSONObject()
                    .put("name", name.trim())
                    .put("url", url.trim())
                    .put("enabled", enabled)
            )
            .toString()
        return apiRepository.control(
            instance = instance,
            method = "POST",
            path = "/control/filtering/set_url",
            body = body,
            readTimeoutMs = 30_000
        ).map { Unit }
    }

    suspend fun setEnabled(
        instance: AghInstance,
        filter: AghFilterSubscription,
        enabled: Boolean
    ): Result<Unit> =
        update(
            instance = instance,
            original = filter,
            name = filter.name,
            url = filter.url,
            enabled = enabled
        )

    suspend fun remove(
        instance: AghInstance,
        filter: AghFilterSubscription
    ): Result<Unit> {
        val body = JSONObject()
            .put("url", filter.url)
            .put("whitelist", filter.whitelist)
            .toString()
        return apiRepository.control(
            instance = instance,
            method = "POST",
            path = "/control/filtering/remove_url",
            body = body
        ).map { Unit }
    }

    suspend fun refresh(
        instance: AghInstance,
        whitelist: Boolean
    ): Result<Int> {
        val body = JSONObject()
            .put("whitelist", whitelist)
            .toString()
        return apiRepository.control(
            instance = instance,
            method = "POST",
            path = "/control/filtering/refresh",
            body = body,
            readTimeoutMs = 60_000
        ).mapCatching { response ->
            if (response.isBlank()) 0
            else JSONObject(response).optInt("updated", 0)
        }
    }

    suspend fun refreshAll(instance: AghInstance): Result<Int> {
        val normal = refresh(instance, false)
        if (normal.isFailure) return Result.failure(normal.exceptionOrNull()!!)
        val whitelist = refresh(instance, true)
        if (whitelist.isFailure) return Result.failure(whitelist.exceptionOrNull()!!)
        return Result.success(normal.getOrThrow() + whitelist.getOrThrow())
    }

    suspend fun saveUserRules(
        instance: AghInstance,
        baselineRules: List<String>,
        newRules: List<String>
    ): Result<Unit> {
        val latest = load(instance)
        if (latest.isFailure) return Result.failure(latest.exceptionOrNull()!!)
        if (latest.getOrThrow().userRules != baselineRules) {
            return Result.failure(
                IllegalStateException(
                    "User Rules 已在其它位置发生变化，请刷新后重新编辑，避免覆盖新修改"
                )
            )
        }

        val body = JSONObject()
            .put("rules", JSONArray(newRules))
            .toString()
        return apiRepository.control(
            instance = instance,
            method = "POST",
            path = "/control/filtering/set_rules",
            body = body
        ).map { Unit }
    }

    suspend fun addUserRule(
        instance: AghInstance,
        rule: String
    ): Result<Unit> {
        val normalized = rule.trim()
        if (normalized.isBlank()) {
            return Result.failure(IllegalArgumentException("规则不能为空"))
        }
        val latest = load(instance)
        if (latest.isFailure) return Result.failure(latest.exceptionOrNull()!!)
        val baseline = latest.getOrThrow().userRules
        if (normalized in baseline) return Result.success(Unit)
        return saveUserRules(instance, baseline, baseline + normalized)
    }

    suspend fun replaceUserRule(
        instance: AghInstance,
        original: String,
        replacement: String
    ): Result<Unit> {
        val normalizedOriginal = original.trim()
        val normalizedReplacement = replacement.trim()
        if (normalizedReplacement.isBlank()) {
            return Result.failure(IllegalArgumentException("新规则不能为空"))
        }
        val latest = load(instance)
        if (latest.isFailure) return Result.failure(latest.exceptionOrNull()!!)
        val baseline = latest.getOrThrow().userRules
        val index = baseline.indexOf(normalizedOriginal)
        if (index < 0) {
            return Result.failure(
                IllegalStateException("目标 User Rule 已不存在，请刷新后重试")
            )
        }
        val next = baseline.toMutableList()
        next[index] = normalizedReplacement
        return saveUserRules(instance, baseline, next)
    }

    suspend fun deleteUserRule(
        instance: AghInstance,
        rule: String
    ): Result<Unit> {
        val latest = load(instance)
        if (latest.isFailure) return Result.failure(latest.exceptionOrNull()!!)
        val baseline = latest.getOrThrow().userRules
        if (rule !in baseline) {
            return Result.failure(
                IllegalStateException("目标 User Rule 已不存在，请刷新后重试")
            )
        }
        return saveUserRules(instance, baseline, baseline.filterNot { it == rule })
    }

    suspend fun checkHost(
        instance: AghInstance,
        host: String,
        client: String = "",
        qtype: String = ""
    ): Result<AghFilterCheckResult> {
        val normalized = host.trim()
        if (normalized.isBlank()) {
            return Result.failure(IllegalArgumentException("请输入要检查的域名"))
        }
        val query = buildList {
            add("name=" + encode(normalized))
            if (client.isNotBlank()) add("client=" + encode(client.trim()))
            if (qtype.isNotBlank()) add("qtype=" + encode(qtype.trim()))
        }.joinToString("&")

        return apiRepository.control(
            instance = instance,
            method = "GET",
            path = "/control/filtering/check_host?" + query
        ).mapCatching { response ->
            val json = JSONObject(response)
            val rulesArray = json.optJSONArray("rules")
            val rules = buildList {
                if (rulesArray != null) {
                    for (i in 0 until rulesArray.length()) {
                        val item = rulesArray.optJSONObject(i) ?: continue
                        add(
                            AghFilterMatch(
                                text = item.optString("text", ""),
                                filterListId = item.optLong("filter_list_id", 0L)
                            )
                        )
                    }
                } else {
                    val legacy = json.optString("rule", "")
                    if (legacy.isNotBlank()) {
                        add(
                            AghFilterMatch(
                                text = legacy,
                                filterListId = json.optLong("filter_id", 0L)
                            )
                        )
                    }
                }
            }

            AghFilterCheckResult(
                host = normalized,
                reason = json.optString("reason", "NotFilteredNotFound"),
                rules = rules,
                serviceName = json.optString("service_name", ""),
                cname = json.optString("cname", ""),
                ipAddresses = json.stringList("ip_addrs")
            )
        }
    }

    private fun parseStatus(json: JSONObject): AghFilteringStatus =
        AghFilteringStatus(
            enabled = json.optBoolean("enabled", true),
            intervalHours = json.optInt("interval", 72),
            filters = parseFilters(json.optJSONArray("filters"), whitelist = false),
            whitelistFilters = parseFilters(
                json.optJSONArray("whitelist_filters"),
                whitelist = true
            ),
            userRules = json.stringList("user_rules")
        )

    private fun parseFilters(
        array: JSONArray?,
        whitelist: Boolean
    ): List<AghFilterSubscription> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    AghFilterSubscription(
                        id = item.optLong("id", 0L),
                        enabled = item.optBoolean("enabled", true),
                        name = item.optString("name", ""),
                        url = item.optString("url", ""),
                        rulesCount = item.optInt("rules_count", 0),
                        lastUpdated = item.optString("last_updated", ""),
                        whitelist = whitelist
                    )
                )
            }
        }
    }

    private fun validateNameUrl(name: String, url: String): String? {
        if (name.trim().isBlank()) return "过滤器名称不能为空"
        if (url.trim().isBlank()) return "过滤器 URL 不能为空"
        if (name.contains('\n') || name.contains('\r')) return "过滤器名称格式无效"
        if (url.contains('\n') || url.contains('\r')) return "过滤器 URL 格式无效"
        return null
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i, "")
                if (value.isNotBlank()) add(value)
            }
        }
    }
}
