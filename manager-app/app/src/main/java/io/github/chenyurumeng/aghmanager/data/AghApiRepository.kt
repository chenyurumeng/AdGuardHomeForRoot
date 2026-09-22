package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.AghCredential
import io.github.chenyurumeng.aghmanager.model.AghDnsConfig
import io.github.chenyurumeng.aghmanager.model.AghInstance
import io.github.chenyurumeng.aghmanager.model.AghUpstreamTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64

class AghApiRepository(
    private val configRepository: AghConfigRepository,
    private val credentialStore: AghCredentialStore
) {
    suspend fun verifyCredential(
        instance: AghInstance,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = endpoint(instance).getOrThrow()
            request(
                endpoint = endpoint,
                method = "GET",
                path = "/control/profile",
                credential = AghCredential(username, password)
            ).getOrThrow()
            Unit
        }
    }

    suspend fun loadDns(instance: AghInstance): Result<AghDnsConfig> =
        withContext(Dispatchers.IO) {
            runCatching {
                val endpoint = endpoint(instance).getOrThrow()
                val response = request(
                    endpoint = endpoint,
                    method = "GET",
                    path = "/control/dns_info",
                    credential = credentialStore.load(instance)
                ).getOrThrow()
                parseDns(JSONObject(response))
            }
        }

    suspend fun updateDns(
        instance: AghInstance,
        config: AghDnsConfig
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = endpoint(instance).getOrThrow()
            val credential = credentialStore.load(instance)

            val body = JSONObject()
                .put("upstream_dns", JSONArray(config.upstreamDns))
                .put("bootstrap_dns", JSONArray(config.bootstrapDns))
                .put("fallback_dns", JSONArray(config.fallbackDns))
                .put("upstream_mode", config.upstreamMode)
                .put("cache_enabled", config.cacheEnabled)
                .put("cache_size", config.cacheSize)
                .put("cache_optimistic", config.cacheOptimistic)
                .put("dnssec_enabled", config.dnssecEnabled)
                .put("disable_ipv6", config.disableIpv6)
                .put("ratelimit", config.ratelimit)
                .put("upstream_timeout", config.upstreamTimeout)

            request(
                endpoint = endpoint,
                method = "POST",
                path = "/control/dns_config",
                credential = credential,
                body = body.toString()
            ).getOrThrow()

            request(
                endpoint = endpoint,
                method = "POST",
                path = "/control/protection",
                credential = credential,
                body = JSONObject()
                    .put("enabled", config.protectionEnabled)
                    .toString()
            ).getOrThrow()

            Unit
        }
    }

    suspend fun testUpstreams(
        instance: AghInstance,
        config: AghDnsConfig
    ): Result<List<AghUpstreamTestResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = endpoint(instance).getOrThrow()
            val body = JSONObject()
                .put("upstream_dns", JSONArray(config.upstreamDns))
                .put("bootstrap_dns", JSONArray(config.bootstrapDns))
                .put("fallback_dns", JSONArray(config.fallbackDns))
                .put("private_upstream", JSONArray())

            val response = request(
                endpoint = endpoint,
                method = "POST",
                path = "/control/test_upstream_dns",
                credential = credentialStore.load(instance),
                body = body.toString(),
                readTimeoutMs = 20_000
            ).getOrThrow()

            val json = JSONObject(response)
            buildList {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val server = keys.next()
                    add(
                        AghUpstreamTestResult(
                            server = server,
                            result = json.optString(server, "Unknown")
                        )
                    )
                }
            }.sortedBy { it.server }
        }
    }

    suspend fun clearCache(instance: AghInstance): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val endpoint = endpoint(instance).getOrThrow()
                request(
                    endpoint = endpoint,
                    method = "POST",
                    path = "/control/cache_clear",
                    credential = credentialStore.load(instance)
                ).getOrThrow()
                Unit
            }
        }

    suspend fun profileName(instance: AghInstance): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val endpoint = endpoint(instance).getOrThrow()
                val response = request(
                    endpoint = endpoint,
                    method = "GET",
                    path = "/control/profile",
                    credential = credentialStore.load(instance)
                ).getOrThrow()
                JSONObject(response).optString("name", "")
            }
        }

    internal suspend fun control(
        instance: AghInstance,
        method: String,
        path: String,
        body: String? = null,
        readTimeoutMs: Int = 15_000
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = endpoint(instance).getOrThrow()
            request(
                endpoint = endpoint,
                method = method,
                path = path,
                credential = credentialStore.load(instance),
                body = body,
                readTimeoutMs = readTimeoutMs
            ).getOrThrow()
        }
    }

    private suspend fun endpoint(instance: AghInstance): Result<String> =
        configRepository.load(instance).map { structure ->
            structure.webUrl().trimEnd('/')
        }

    private fun request(
        endpoint: String,
        method: String,
        path: String,
        credential: AghCredential?,
        body: String? = null,
        readTimeoutMs: Int = 15_000
    ): Result<String> = runCatching {
        val connection = URL(endpoint + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 4_000
            connection.readTimeout = readTimeoutMs
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")

            if (credential != null) {
                val token = Base64.getEncoder().encodeToString(
                    (credential.username + ":" + credential.password)
                        .toByteArray(StandardCharsets.UTF_8)
                )
                connection.setRequestProperty("Authorization", "Basic " + token)
            }

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use {
                    it.write(body.toByteArray(StandardCharsets.UTF_8))
                }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) {
                runCatching { connection.inputStream }.getOrNull()
            } else {
                connection.errorStream
            }
            val response = stream
                ?.bufferedReader(StandardCharsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (code !in 200..299) {
                throw IllegalStateException(
                    when (code) {
                        401, 403 -> "AGH 管理凭据无效或未绑定"
                        else -> "AGH API HTTP " + code +
                            if (response.isBlank()) "" else ": " + response.take(200)
                    }
                )
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun parseDns(json: JSONObject): AghDnsConfig =
        AghDnsConfig(
            protectionEnabled = json.optBoolean("protection_enabled", true),
            upstreamDns = json.stringList("upstream_dns"),
            bootstrapDns = json.stringList("bootstrap_dns"),
            fallbackDns = json.stringList("fallback_dns"),
            upstreamMode = json.optString("upstream_mode", "parallel")
                .ifBlank { "load_balance" },
            cacheEnabled = json.optBoolean("cache_enabled", true),
            cacheSize = json.optLong("cache_size", 33_554_432L),
            cacheOptimistic = json.optBoolean("cache_optimistic", false),
            dnssecEnabled = json.optBoolean("dnssec_enabled", false),
            disableIpv6 = json.optBoolean("disable_ipv6", false),
            ratelimit = json.optInt("ratelimit", 0),
            upstreamTimeout = json.optInt("upstream_timeout", 10)
        )

    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index, "").trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }
}
