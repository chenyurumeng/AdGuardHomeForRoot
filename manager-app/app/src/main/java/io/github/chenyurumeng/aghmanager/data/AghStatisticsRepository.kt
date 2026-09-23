package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.AghInstance
import io.github.chenyurumeng.aghmanager.model.AghStatistics
import io.github.chenyurumeng.aghmanager.model.AghStatsConfig
import io.github.chenyurumeng.aghmanager.model.AghStatsTopEntry
import org.json.JSONArray
import org.json.JSONObject

class AghStatisticsRepository(
    private val apiRepository: AghApiRepository
) {
    companion object {
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L
    }

    suspend fun loadConfig(instance: AghInstance): Result<AghStatsConfig> =
        apiRepository.control(
            instance = instance,
            method = "GET",
            path = "/control/stats/config"
        ).mapCatching { response ->
            val json = JSONObject(response)
            AghStatsConfig(
                enabled = json.optBoolean("enabled", true),
                intervalMs = json.optDouble("interval", DAY_MS.toDouble()).toLong(),
                ignored = json.stringList("ignored"),
                ignoredEnabled = json.optBoolean("ignored_enabled", false)
            ).normalized()
        }

    suspend fun loadStats(
        instance: AghInstance,
        recentMs: Long
    ): Result<AghStatistics> {
        if (recentMs <= 0L || recentMs % HOUR_MS != 0L) {
            return Result.failure(
                IllegalArgumentException("统计时间范围必须是至少 1 小时的整小时倍数")
            )
        }

        return apiRepository.control(
            instance = instance,
            method = "GET",
            path = "/control/stats?recent=" + recentMs
        ).mapCatching { response ->
            parseStats(JSONObject(response))
        }
    }

    suspend fun updateConfig(
        instance: AghInstance,
        baseline: AghStatsConfig,
        pending: AghStatsConfig
    ): Result<Unit> {
        val normalizedBaseline = baseline.normalized()
        val normalizedPending = pending.normalized()

        validateConfig(normalizedPending)?.let {
            return Result.failure(IllegalArgumentException(it))
        }

        val latest = loadConfig(instance)
        if (latest.isFailure) return Result.failure(latest.exceptionOrNull()!!)
        if (latest.getOrThrow().normalized() != normalizedBaseline) {
            return Result.failure(
                IllegalStateException(
                    "Statistics 设置已在其它位置发生变化，请刷新后重新编辑，避免覆盖新修改"
                )
            )
        }

        val body = JSONObject()
            .put("enabled", normalizedPending.enabled)
            .put("interval", normalizedPending.intervalMs)
            .put("ignored", JSONArray(normalizedPending.ignored))
            .put("ignored_enabled", normalizedPending.ignoredEnabled)
            .toString()

        return apiRepository.control(
            instance = instance,
            method = "PUT",
            path = "/control/stats/config/update",
            body = body
        ).map { Unit }
    }

    suspend fun reset(instance: AghInstance): Result<Unit> =
        apiRepository.control(
            instance = instance,
            method = "POST",
            path = "/control/stats_reset"
        ).map { Unit }

    private fun validateConfig(config: AghStatsConfig): String? {
        if (config.enabled && config.intervalMs < HOUR_MS) {
            return "统计保留周期至少为 1 小时"
        }
        if (config.intervalMs % HOUR_MS != 0L) {
            return "统计保留周期必须是整小时倍数"
        }
        if (config.ignored.any { it.contains('\n') || it.contains('\r') || it.contains('\u0000') }) {
            return "Statistics 忽略列表包含非法字符"
        }
        return null
    }

    private fun parseStats(json: JSONObject): AghStatistics =
        AghStatistics(
            timeUnits = json.optString("time_units", "hours"),
            totalQueries = json.optLong("num_dns_queries", 0L),
            blockedFiltering = json.optLong("num_blocked_filtering", 0L),
            safeBrowsingBlocked = json.optLong("num_replaced_safebrowsing", 0L),
            safeSearchReplaced = json.optLong("num_replaced_safesearch", 0L),
            parentalBlocked = json.optLong("num_replaced_parental", 0L),
            averageProcessingSeconds = json.optDouble("avg_processing_time", 0.0),
            topQueriedDomains = parseTop(json.optJSONArray("top_queried_domains")),
            topClients = parseTop(json.optJSONArray("top_clients")),
            topBlockedDomains = parseTop(json.optJSONArray("top_blocked_domains")),
            topUpstreamResponses = parseTop(json.optJSONArray("top_upstreams_responses")),
            topUpstreamAverageSeconds = parseTop(json.optJSONArray("top_upstreams_avg_time")),
            dnsQueriesSeries = json.longList("dns_queries"),
            blockedSeries = json.longList("blocked_filtering"),
            safeBrowsingSeries = json.longList("replaced_safebrowsing"),
            parentalSeries = json.longList("replaced_parental")
        )

    private fun parseTop(array: JSONArray?): List<AghStatsTopEntry> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val keys = item.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    add(
                        AghStatsTopEntry(
                            key = key,
                            value = item.optDouble(key, 0.0)
                        )
                    )
                }
            }
        }
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

    private fun JSONObject.longList(key: String): List<Long> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                add(array.optLong(i, 0L))
            }
        }
    }
}
