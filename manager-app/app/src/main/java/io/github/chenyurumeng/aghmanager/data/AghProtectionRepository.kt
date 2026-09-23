package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.AghInstance
import io.github.chenyurumeng.aghmanager.model.AghProtectionConfig
import io.github.chenyurumeng.aghmanager.model.AghSafeSearchConfig
import org.json.JSONObject

class AghProtectionRepository(
    private val apiRepository: AghApiRepository
) {
    suspend fun load(instance: AghInstance): Result<AghProtectionConfig> {
        val safeBrowsing = apiRepository.control(
            instance = instance,
            method = "GET",
            path = "/control/safebrowsing/status"
        )
        if (safeBrowsing.isFailure) {
            return Result.failure(safeBrowsing.exceptionOrNull()!!)
        }

        val parental = apiRepository.control(
            instance = instance,
            method = "GET",
            path = "/control/parental/status"
        )
        if (parental.isFailure) {
            return Result.failure(parental.exceptionOrNull()!!)
        }

        val safeSearch = apiRepository.control(
            instance = instance,
            method = "GET",
            path = "/control/safesearch/status"
        )
        if (safeSearch.isFailure) {
            return Result.failure(safeSearch.exceptionOrNull()!!)
        }

        return runCatching {
            val browsingJson = JSONObject(safeBrowsing.getOrThrow())
            val parentalJson = JSONObject(parental.getOrThrow())
            val searchJson = JSONObject(safeSearch.getOrThrow())

            AghProtectionConfig(
                safeBrowsingEnabled = browsingJson.optBoolean("enabled", false),
                parentalEnabled = parentalJson.optBoolean("enabled", false),
                parentalSensitivity = parentalJson.optionalInt("sensitivity"),
                safeSearch = parseSafeSearch(searchJson)
            )
        }
    }

    suspend fun update(
        instance: AghInstance,
        baseline: AghProtectionConfig,
        pending: AghProtectionConfig
    ): Result<AghProtectionConfig> {
        val latestResult = load(instance)
        if (latestResult.isFailure) {
            return Result.failure(latestResult.exceptionOrNull()!!)
        }
        val latest = latestResult.getOrThrow()
        if (latest != baseline) {
            return Result.failure(
                IllegalStateException(
                    "安全保护设置已在其它位置发生变化，请刷新后重新编辑，避免覆盖 WebUI 的新修改"
                )
            )
        }

        var safeSearchApplied = false
        var safeBrowsingApplied = false
        var parentalApplied = false

        return try {
            if (pending.safeSearch != baseline.safeSearch) {
                updateSafeSearch(instance, pending.safeSearch).getOrThrow()
                safeSearchApplied = true
            }

            if (pending.safeBrowsingEnabled != baseline.safeBrowsingEnabled) {
                setToggle(
                    instance = instance,
                    prefix = "/control/safebrowsing",
                    enabled = pending.safeBrowsingEnabled
                ).getOrThrow()
                safeBrowsingApplied = true
            }

            if (pending.parentalEnabled != baseline.parentalEnabled) {
                setToggle(
                    instance = instance,
                    prefix = "/control/parental",
                    enabled = pending.parentalEnabled
                ).getOrThrow()
                parentalApplied = true
            }

            val verified = load(instance).getOrThrow()
            if (verified != pending) {
                throw IllegalStateException("服务器回读状态与待应用设置不一致")
            }
            Result.success(verified)
        } catch (error: Throwable) {
            val rollbackErrors = mutableListOf<String>()

            if (parentalApplied) {
                setToggle(
                    instance = instance,
                    prefix = "/control/parental",
                    enabled = baseline.parentalEnabled
                ).onFailure { rollbackErrors += "Parental" }
            }
            if (safeBrowsingApplied) {
                setToggle(
                    instance = instance,
                    prefix = "/control/safebrowsing",
                    enabled = baseline.safeBrowsingEnabled
                ).onFailure { rollbackErrors += "Safe Browsing" }
            }
            if (safeSearchApplied) {
                updateSafeSearch(instance, baseline.safeSearch)
                    .onFailure { rollbackErrors += "Safe Search" }
            }

            val rollbackState = load(instance)
            if (rollbackState.isFailure || rollbackState.getOrNull() != baseline) {
                rollbackErrors += "状态校验"
            }

            val reason = error.message?.take(180) ?: "未知错误"
            val message = if (rollbackErrors.isEmpty()) {
                "安全保护应用失败，已恢复原设置：" + reason
            } else {
                "安全保护应用失败，自动回滚不完整（" +
                    rollbackErrors.distinct().joinToString(" / ") +
                    "），请刷新确认实际状态：" + reason
            }
            Result.failure(IllegalStateException(message))
        }
    }

    private suspend fun setToggle(
        instance: AghInstance,
        prefix: String,
        enabled: Boolean
    ): Result<Unit> =
        apiRepository.control(
            instance = instance,
            method = "POST",
            path = prefix + if (enabled) "/enable" else "/disable"
        ).map { Unit }

    private suspend fun updateSafeSearch(
        instance: AghInstance,
        config: AghSafeSearchConfig
    ): Result<Unit> {
        val body = JSONObject().put("enabled", config.enabled)
        config.bing?.let { body.put("bing", it) }
        config.duckDuckGo?.let { body.put("duckduckgo", it) }
        config.ecosia?.let { body.put("ecosia", it) }
        config.google?.let { body.put("google", it) }
        config.pixabay?.let { body.put("pixabay", it) }
        config.yandex?.let { body.put("yandex", it) }
        config.youtube?.let { body.put("youtube", it) }

        return apiRepository.control(
            instance = instance,
            method = "PUT",
            path = "/control/safesearch/settings",
            body = body.toString()
        ).map { Unit }
    }

    private fun parseSafeSearch(json: JSONObject): AghSafeSearchConfig =
        AghSafeSearchConfig(
            enabled = json.optBoolean("enabled", false),
            bing = json.optionalBoolean("bing"),
            duckDuckGo = json.optionalBoolean("duckduckgo"),
            ecosia = json.optionalBoolean("ecosia"),
            google = json.optionalBoolean("google"),
            pixabay = json.optionalBoolean("pixabay"),
            yandex = json.optionalBoolean("yandex"),
            youtube = json.optionalBoolean("youtube")
        )

    private fun JSONObject.optionalBoolean(key: String): Boolean? =
        if (has(key) && !isNull(key)) optBoolean(key) else null

    private fun JSONObject.optionalInt(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null
}
