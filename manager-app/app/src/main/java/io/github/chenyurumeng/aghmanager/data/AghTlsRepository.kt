package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.AghInstance
import io.github.chenyurumeng.aghmanager.model.AghTlsConfig
import io.github.chenyurumeng.aghmanager.model.AghTlsDraft
import io.github.chenyurumeng.aghmanager.model.AghTlsValidation
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64

class AghTlsRepository(
    private val apiRepository: AghApiRepository
) {
    suspend fun load(instance: AghInstance): Result<AghTlsConfig> =
        apiRepository.control(
            instance = instance,
            method = "GET",
            path = "/control/tls/status"
        ).mapCatching { parseConfig(JSONObject(it)) }
            .mapError("TLS 状态读取失败")

    suspend fun validate(
        instance: AghInstance,
        baseline: AghTlsConfig,
        draft: AghTlsDraft
    ): Result<AghTlsValidation> {
        val body = buildRequest(baseline, draft)
        if (body.isFailure) return Result.failure(body.exceptionOrNull()!!)

        return apiRepository.control(
            instance = instance,
            method = "POST",
            path = "/control/tls/validate",
            body = body.getOrThrow().toString(),
            readTimeoutMs = 20_000
        ).mapCatching { parseValidation(JSONObject(it)) }
            .mapError("TLS 配置验证失败")
    }

    suspend fun update(
        instance: AghInstance,
        baseline: AghTlsConfig,
        draft: AghTlsDraft
    ): Result<AghTlsConfig> {
        val latestResult = load(instance)
        if (latestResult.isFailure) return latestResult
        val latest = latestResult.getOrThrow()

        if (latest != baseline) {
            return Result.failure(
                IllegalStateException(
                    "TLS 配置已在其它位置发生变化，请刷新后重新编辑，避免覆盖 WebUI 的新修改"
                )
            )
        }

        val bodyResult = buildRequest(baseline, draft)
        if (bodyResult.isFailure) return Result.failure(bodyResult.exceptionOrNull()!!)
        val body = bodyResult.getOrThrow()

        val validationResult = apiRepository.control(
            instance = instance,
            method = "POST",
            path = "/control/tls/validate",
            body = body.toString(),
            readTimeoutMs = 20_000
        )
        if (validationResult.isFailure) {
            return Result.failure(
                IllegalStateException(
                    safeMessage(validationResult.exceptionOrNull(), "TLS 配置验证失败")
                )
            )
        }

        val validationJson = JSONObject(validationResult.getOrThrow())
        val validation = parseValidation(validationJson)
        validateServerResult(draft, validation)?.let {
            return Result.failure(IllegalStateException(it))
        }

        val configureResult = apiRepository.control(
            instance = instance,
            method = "POST",
            path = "/control/tls/configure",
            body = body.toString(),
            readTimeoutMs = 25_000
        )
        if (configureResult.isFailure) {
            return Result.failure(
                IllegalStateException(
                    safeMessage(configureResult.exceptionOrNull(), "TLS 配置应用失败")
                )
            )
        }

        val configureJson = JSONObject(configureResult.getOrThrow())
        val configuredValidation = parseValidation(configureJson)
        validateServerResult(draft, configuredValidation)?.let {
            return Result.failure(
                IllegalStateException("AGH 未接受新的 TLS 证书/私钥配置：" + it)
            )
        }

        val verifiedResult = load(instance)
        if (verifiedResult.isFailure) {
            return Result.failure(
                IllegalStateException(
                    "TLS 配置已提交，但回读失败；请重新进入页面确认实际状态"
                )
            )
        }

        val verified = verifiedResult.getOrThrow()
        if (!matchesDraft(verified, baseline, draft)) {
            return Result.failure(
                IllegalStateException(
                    "TLS 配置已提交，但服务器回读状态与 Pending 不一致；请刷新确认实际状态"
                )
            )
        }

        return Result.success(verified)
    }

    private fun buildRequest(
        baseline: AghTlsConfig,
        draft: AghTlsDraft
    ): Result<JSONObject> = runCatching {
        val httpsPort = parsePort("HTTPS / DoH", draft.httpsPort)
        val dotPort = parsePort("DoT", draft.dotPort)
        val doqPort = parsePort("DoQ", draft.doqPort)

        val certificatePem = draft.certificatePem.trim()
        val certificatePath = draft.certificatePath.trim()
        require(certificatePem.isBlank() || certificatePath.isBlank()) {
            "证书 PEM 内容和服务器证书路径不能同时设置"
        }

        val privateKeyPath = draft.privateKeyPath.trim()
        val privateKeyPem = draft.privateKeyPem
        require(!draft.replacePrivateKey || privateKeyPem.isBlank() || privateKeyPath.isBlank()) {
            "私钥 PEM 内容和服务器私钥路径不能同时设置"
        }

        val privateKeySaved = if (draft.replacePrivateKey) {
            false
        } else {
            baseline.privateKeySaved
        }

        JSONObject()
            .put("enabled", draft.enabled)
            .put("server_name", draft.serverName.trim())
            .put("force_https", draft.forceHttps)
            .put("port_https", httpsPort)
            .put("port_dns_over_tls", dotPort)
            .put("port_dns_over_quic", doqPort)
            .put(
                "certificate_chain",
                if (certificatePem.isBlank()) "" else encode(certificatePem)
            )
            .put("certificate_path", certificatePath)
            .put(
                "private_key",
                if (draft.replacePrivateKey && privateKeyPem.isNotBlank()) {
                    encode(privateKeyPem)
                } else {
                    ""
                }
            )
            .put("private_key_path", privateKeyPath)
            .put("private_key_saved", privateKeySaved)
            .put("serve_plain_dns", draft.servePlainDns)
            .put("port_dnscrypt", draft.dnscryptPort)
            .put("dnscrypt_config_file", draft.dnscryptConfigFile)
    }

    private fun validateServerResult(
        draft: AghTlsDraft,
        validation: AghTlsValidation
    ): String? {
        if (!draft.enabled) return null
        if (!validation.validCert) return "证书无效"
        if (!validation.validKey) return "私钥无效"
        if (!validation.validPair) return "证书与私钥不匹配"
        return null
    }

    private fun matchesDraft(
        verified: AghTlsConfig,
        baseline: AghTlsConfig,
        draft: AghTlsDraft
    ): Boolean {
        val expectedHttps = draft.httpsPort.trim().toIntOrNull() ?: return false
        val expectedDot = draft.dotPort.trim().toIntOrNull() ?: return false
        val expectedDoq = draft.doqPort.trim().toIntOrNull() ?: return false

        val expectedPrivateKeySaved = when {
            draft.replacePrivateKey && draft.privateKeyPem.isNotBlank() -> true
            draft.replacePrivateKey -> false
            else -> baseline.privateKeySaved
        }

        return verified.enabled == draft.enabled &&
            verified.serverName == draft.serverName.trim() &&
            verified.forceHttps == draft.forceHttps &&
            verified.httpsPort == expectedHttps &&
            verified.dotPort == expectedDot &&
            verified.doqPort == expectedDoq &&
            verified.certificatePem.trim() == draft.certificatePem.trim() &&
            verified.certificatePath == draft.certificatePath.trim() &&
            verified.privateKeyPath == draft.privateKeyPath.trim() &&
            verified.privateKeySaved == expectedPrivateKeySaved &&
            verified.servePlainDns == draft.servePlainDns &&
            verified.dnscryptPort == draft.dnscryptPort &&
            verified.dnscryptConfigFile == draft.dnscryptConfigFile
    }

    private fun parseConfig(json: JSONObject): AghTlsConfig =
        AghTlsConfig(
            enabled = json.optBoolean("enabled", false),
            serverName = json.optString("server_name", ""),
            forceHttps = json.optBoolean("force_https", false),
            httpsPort = json.optInt("port_https", 0),
            dotPort = json.optInt("port_dns_over_tls", 0),
            doqPort = json.optInt("port_dns_over_quic", 0),
            certificatePem = decode(json.optString("certificate_chain", "")),
            certificatePath = json.optString("certificate_path", ""),
            privateKeyPath = json.optString("private_key_path", ""),
            privateKeySaved = json.optBoolean("private_key_saved", false),
            servePlainDns = if (json.has("serve_plain_dns")) {
                json.optBoolean("serve_plain_dns", true)
            } else {
                true
            },
            dnscryptPort = json.optInt("port_dnscrypt", 0),
            dnscryptConfigFile = json.optString("dnscrypt_config_file", ""),
            validation = parseValidation(json)
        )

    private fun parseValidation(json: JSONObject): AghTlsValidation =
        AghTlsValidation(
            validCert = json.optBoolean("valid_cert", false),
            validChain = json.optBoolean("valid_chain", false),
            validKey = json.optBoolean("valid_key", false),
            validPair = json.optBoolean("valid_pair", false),
            warning = redactPem(json.optString("warning_validation", "")).take(300),
            subject = json.optString("subject", ""),
            issuer = json.optString("issuer", ""),
            keyType = json.optString("key_type", ""),
            notBefore = json.optString("not_before", ""),
            notAfter = json.optString("not_after", ""),
            dnsNames = json.stringList("dns_names")
        )

    private fun parsePort(label: String, value: String): Int {
        val port = value.trim().toIntOrNull()
            ?: throw IllegalArgumentException(label + " 端口必须是 0–65535 的整数")
        require(port in 0..65535) { label + " 端口必须在 0–65535" }
        return port
    }

    private fun encode(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
        }.getOrDefault(value)
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val array = optJSONArray(key) ?: JSONArray()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i, "").trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun <T> Result<T>.mapError(fallback: String): Result<T> =
        fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(IllegalStateException(safeMessage(it, fallback))) }
        )

    private fun safeMessage(error: Throwable?, fallback: String): String {
        val raw = error?.message.orEmpty()
        val safe = redactPem(raw).replace(Regex("""(?i)authorization:\s*basic\s+\S+"""), "Authorization: [REDACTED]")
        return safe.take(300).ifBlank { fallback }
    }

    private fun redactPem(value: String): String =
        value.replace(
            Regex("""-----BEGIN[\s\S]*?-----END[^-]*-----"""),
            "[REDACTED PEM]"
        )
}
