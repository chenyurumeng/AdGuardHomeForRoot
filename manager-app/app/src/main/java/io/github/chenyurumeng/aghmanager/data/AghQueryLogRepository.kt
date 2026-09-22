package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.AghDnsAnswer
import io.github.chenyurumeng.aghmanager.model.AghInstance
import io.github.chenyurumeng.aghmanager.model.AghQueryLogConfig
import io.github.chenyurumeng.aghmanager.model.AghQueryLogEntry
import io.github.chenyurumeng.aghmanager.model.AghQueryLogPage
import io.github.chenyurumeng.aghmanager.model.AghQueryRule
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class AghQueryLogRepository(
    private val apiRepository: AghApiRepository
) {
    suspend fun loadPage(
        instance: AghInstance,
        search: String = "",
        olderThan: String = "",
        limit: Int = 100
    ): Result<AghQueryLogPage> {
        val query = buildList {
            add("limit=" + limit.coerceIn(1, 500))
            if (search.isNotBlank()) add("search=" + encode(search.trim()))
            if (olderThan.isNotBlank()) add("older_than=" + encode(olderThan))
        }.joinToString("&")

        return apiRepository.control(
            instance = instance,
            method = "GET",
            path = "/control/querylog?" + query,
            readTimeoutMs = 20_000
        ).mapCatching { response ->
            val json = JSONObject(response)
            val data = json.optJSONArray("data")
            AghQueryLogPage(
                entries = parseEntries(data),
                oldest = json.optString("oldest", "")
            )
        }
    }

    suspend fun loadConfig(instance: AghInstance): Result<AghQueryLogConfig> =
        apiRepository.control(
            instance = instance,
            method = "GET",
            path = "/control/querylog/config"
        ).mapCatching { response ->
            val json = JSONObject(response)
            AghQueryLogConfig(
                enabled = json.optBoolean("enabled", true),
                intervalMs = json.optDouble("interval", 86_400_000.0).toLong(),
                anonymizeClientIp = json.optBoolean("anonymize_client_ip", false),
                ignored = json.stringList("ignored"),
                ignoredEnabled = json.optBoolean("ignored_enabled", false)
            )
        }

    suspend fun updateConfig(
        instance: AghInstance,
        config: AghQueryLogConfig
    ): Result<Unit> {
        if (config.intervalMs < 0L) {
            return Result.failure(IllegalArgumentException("日志保留时间不能为负数"))
        }
        val body = JSONObject()
            .put("enabled", config.enabled)
            .put("interval", config.intervalMs)
            .put("anonymize_client_ip", config.anonymizeClientIp)
            .put("ignored", JSONArray(config.ignored))
            .put("ignored_enabled", config.ignoredEnabled)
            .toString()

        return apiRepository.control(
            instance = instance,
            method = "PUT",
            path = "/control/querylog/config/update",
            body = body
        ).map { Unit }
    }

    suspend fun clear(instance: AghInstance): Result<Unit> =
        apiRepository.control(
            instance = instance,
            method = "POST",
            path = "/control/querylog_clear"
        ).map { Unit }

    private fun parseEntries(array: JSONArray?): List<AghQueryLogEntry> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val question = item.optJSONObject("question") ?: JSONObject()
                val clientInfo = item.optJSONObject("client_info") ?: JSONObject()
                val rulesArray = item.optJSONArray("rules")
                val rules = buildList {
                    if (rulesArray != null) {
                        for (j in 0 until rulesArray.length()) {
                            val rule = rulesArray.optJSONObject(j) ?: continue
                            add(
                                AghQueryRule(
                                    text = rule.optString("text", ""),
                                    filterListId = rule.optLong("filter_list_id", 0L)
                                )
                            )
                        }
                    } else {
                        val legacy = item.optString("rule", "")
                        if (legacy.isNotBlank()) {
                            add(
                                AghQueryRule(
                                    text = legacy,
                                    filterListId = item.optLong("filterId", 0L)
                                )
                            )
                        }
                    }
                }

                add(
                    AghQueryLogEntry(
                        time = item.optString("time", ""),
                        domain = question.optString("name", ""),
                        unicodeDomain = question.optString("unicode_name", ""),
                        qtype = question.optString("type", ""),
                        qclass = question.optString("class", ""),
                        client = item.optString("client", ""),
                        clientName = clientInfo.optString("name", ""),
                        clientProto = item.optString("client_proto", ""),
                        status = item.optString("status", ""),
                        reason = item.optString("reason", ""),
                        upstream = item.optString("upstream", ""),
                        cached = item.optBoolean("cached", false),
                        elapsedMs = item.optString("elapsedMs", ""),
                        answerDnssec = item.optBoolean("answer_dnssec", false),
                        answers = parseAnswers(item.optJSONArray("answer")),
                        originalAnswers = parseAnswers(item.optJSONArray("original_answer")),
                        rules = rules,
                        serviceName = item.optString("service_name", "")
                    )
                )
            }
        }
    }

    private fun parseAnswers(array: JSONArray?): List<AghDnsAnswer> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    AghDnsAnswer(
                        type = item.optString("type", ""),
                        value = item.optString("value", ""),
                        ttl = item.optLong("ttl", 0L)
                    )
                )
            }
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

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
