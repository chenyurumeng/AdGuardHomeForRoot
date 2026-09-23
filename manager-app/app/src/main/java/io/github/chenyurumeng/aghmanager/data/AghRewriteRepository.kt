package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.AghInstance
import io.github.chenyurumeng.aghmanager.model.AghRewriteRule
import org.json.JSONArray
import org.json.JSONObject

class AghRewriteRepository(
    private val apiRepository: AghApiRepository
) {
    suspend fun load(instance: AghInstance): Result<Pair<Boolean, List<AghRewriteRule>>> {
        val rules = apiRepository.control(
            instance = instance,
            method = "GET",
            path = "/control/rewrite/list"
        )
        if (rules.isFailure) return Result.failure(rules.exceptionOrNull()!!)

        val settings = apiRepository.control(
            instance = instance,
            method = "GET",
            path = "/control/rewrite/settings"
        )
        if (settings.isFailure) return Result.failure(settings.exceptionOrNull()!!)

        return runCatching {
            val enabled = JSONObject(settings.getOrThrow()).optBoolean("enabled", true)
            enabled to parseRules(JSONArray(rules.getOrThrow()))
        }
    }

    suspend fun setEnabled(
        instance: AghInstance,
        enabled: Boolean
    ): Result<Unit> =
        apiRepository.control(
            instance = instance,
            method = "PUT",
            path = "/control/rewrite/settings/update",
            body = JSONObject().put("enabled", enabled).toString()
        ).map { Unit }

    suspend fun add(
        instance: AghInstance,
        rule: AghRewriteRule
    ): Result<Unit> {
        validate(rule)?.let {
            return Result.failure(IllegalArgumentException(it))
        }
        return apiRepository.control(
            instance = instance,
            method = "POST",
            path = "/control/rewrite/add",
            body = ruleJson(rule).toString()
        ).map { Unit }
    }

    suspend fun update(
        instance: AghInstance,
        original: AghRewriteRule,
        updated: AghRewriteRule
    ): Result<Unit> {
        validate(updated)?.let {
            return Result.failure(IllegalArgumentException(it))
        }
        val body = JSONObject()
            .put("target", ruleJson(original))
            .put("update", ruleJson(updated))
            .toString()
        return apiRepository.control(
            instance = instance,
            method = "PUT",
            path = "/control/rewrite/update",
            body = body
        ).map { Unit }
    }

    suspend fun delete(
        instance: AghInstance,
        rule: AghRewriteRule
    ): Result<Unit> =
        apiRepository.control(
            instance = instance,
            method = "POST",
            path = "/control/rewrite/delete",
            body = ruleJson(rule).toString()
        ).map { Unit }

    private fun validate(rule: AghRewriteRule): String? {
        if (rule.domain.trim().isBlank()) return "Rewrite 域名不能为空"
        if (rule.answer.trim().isBlank()) return "Rewrite Answer 不能为空"
        if (
            rule.domain.any { it == '\n' || it == '\r' || it == '\u0000' } ||
            rule.answer.any { it == '\n' || it == '\r' || it == '\u0000' }
        ) {
            return "Rewrite 内容包含非法字符"
        }
        return null
    }

    private fun ruleJson(rule: AghRewriteRule): JSONObject =
        JSONObject()
            .put("domain", rule.domain.trim())
            .put("answer", rule.answer.trim())
            .put("enabled", rule.enabled)

    private fun parseRules(array: JSONArray): List<AghRewriteRule> =
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    AghRewriteRule(
                        domain = item.optString("domain", ""),
                        answer = item.optString("answer", ""),
                        enabled = item.optBoolean("enabled", true)
                    )
                )
            }
        }.sortedWith(
            compareBy<AghRewriteRule> { it.domain.lowercase() }
                .thenBy { it.answer.lowercase() }
        )
}
