package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.MihomoGroup
import io.github.chenyurumeng.aghmanager.model.MihomoConnection
import io.github.chenyurumeng.aghmanager.model.MihomoProcessMetrics
import io.github.chenyurumeng.aghmanager.model.MihomoProvider
import io.github.chenyurumeng.aghmanager.model.MihomoRuntimeSnapshot
import io.github.chenyurumeng.aghmanager.model.MihomoSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MihomoApiRepository {
    companion object {
        private const val CONFIG = "/data/adb/box/mihomo/config.yaml"
        private const val DEFAULT_TEST_URL = "https://cp.cloudflare.com"
    }

    suspend fun loadSnapshot(): Result<MihomoSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = loadEndpoint().getOrThrow()
            val groups = loadGroups(endpoint).getOrThrow()
            val providers = loadProviders(endpoint).getOrDefault(emptyList())
            val version = loadVersion(endpoint).getOrDefault("")

            MihomoSnapshot(
                controller = endpoint.baseUrl,
                version = version,
                authenticated = endpoint.secret.isNotBlank(),
                groups = groups,
                providers = providers
            )
        }
    }

    suspend fun loadRuntime(): Result<MihomoRuntimeSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = loadEndpoint().getOrThrow()
            val response = request(endpoint, "GET", "/connections").getOrThrow()
            val root = JSONObject(response.body)
            val array = root.optJSONArray("connections") ?: JSONArray()
            val connections = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val metadata = item.optJSONObject("metadata") ?: JSONObject()
                    val chainArray = item.optJSONArray("chains") ?: JSONArray()
                    val chains = buildList {
                        for (chainIndex in 0 until chainArray.length()) {
                            val value = chainArray.optString(chainIndex, "")
                            if (value.isNotBlank()) add(value)
                        }
                    }
                    add(
                        MihomoConnection(
                            id = item.optString("id", ""),
                            host = metadata.optString("host", ""),
                            destinationIp = metadata.optString("destinationIP", ""),
                            destinationPort = metadata.optString("destinationPort", ""),
                            sourceIp = metadata.optString("sourceIP", ""),
                            sourcePort = metadata.optString("sourcePort", ""),
                            network = metadata.optString("network", ""),
                            process = metadata.optString("process", ""),
                            processPath = metadata.optString("processPath", ""),
                            uid = metadata.opt("uid")?.toString().orEmpty(),
                            rule = item.optString("rule", ""),
                            rulePayload = item.optString("rulePayload", ""),
                            chains = chains,
                            upload = item.optLong("upload", 0L),
                            download = item.optLong("download", 0L),
                            start = item.optString("start", "")
                        )
                    )
                }
            }

            MihomoRuntimeSnapshot(
                uploadTotal = root.optLong("uploadTotal", 0L),
                downloadTotal = root.optLong("downloadTotal", 0L),
                connections = connections,
                process = loadProcessMetrics()
            )
        }
    }

    suspend fun closeConnection(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = loadEndpoint().getOrThrow()
            request(
                endpoint = endpoint,
                method = "DELETE",
                path = "/connections/" + encode(id)
            ).getOrThrow()
            Unit
        }
    }

    suspend fun closeAllConnections(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = loadEndpoint().getOrThrow()
            request(
                endpoint = endpoint,
                method = "DELETE",
                path = "/connections"
            ).getOrThrow()
            Unit
        }
    }

    private suspend fun loadProcessMetrics(): MihomoProcessMetrics {
        val sh = '
        withContext(Dispatchers.IO) {
            runCatching {
                val endpoint = loadEndpoint().getOrThrow()
                val body = JSONObject().put("name", proxyName).toString()
                request(
                    endpoint = endpoint,
                    method = "PUT",
                    path = "/proxies/" + encode(groupName),
                    body = body
                ).getOrThrow()
                Unit
            }
        }

    suspend fun testGroup(
        groupName: String,
        testUrl: String = DEFAULT_TEST_URL,
        timeoutMs: Int = 5_000
    ): Result<Map<String, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = loadEndpoint().getOrThrow()
            val path = "/group/" + encode(groupName) + "/delay?url=" +
                encode(testUrl.ifBlank { DEFAULT_TEST_URL }) +
                "&timeout=" + timeoutMs
            val response = request(
                endpoint = endpoint,
                method = "GET",
                path = path,
                readTimeoutMs = timeoutMs + 4_000
            ).getOrThrow()

            val json = JSONObject(response.body)
            val values = linkedMapOf<String, Int>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val delay = json.optInt(key, 0)
                if (delay > 0) values[key] = delay
            }
            values
        }
    }

    suspend fun updateProvider(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = loadEndpoint().getOrThrow()
            request(
                endpoint = endpoint,
                method = "PUT",
                path = "/providers/proxies/" + encode(name),
                readTimeoutMs = 30_000
            ).getOrThrow()
            Unit
        }
    }

    suspend fun updateAllProviders(names: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = loadEndpoint().getOrThrow()
            var updated = 0
            names.distinct().forEach { name ->
                request(
                    endpoint = endpoint,
                    method = "PUT",
                    path = "/providers/proxies/" + encode(name),
                    readTimeoutMs = 30_000
                ).getOrThrow()
                updated++
            }
            updated
        }
    }

    suspend fun flushDnsCache(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = loadEndpoint().getOrThrow()
            request(
                endpoint = endpoint,
                method = "POST",
                path = "/cache/dns/flush"
            ).getOrThrow()
            Unit
        }
    }

    private suspend fun loadEndpoint(): Result<Endpoint> {
        val command =
            "[ -r " + CONFIG + " ] || exit 2; " +
                "grep -E '^(external-controller|secret):' " + CONFIG + " 2>/dev/null || true"

        val result = RootShell.exec(command, 15)
        if (!result.ok) {
            return Result.failure(
                IllegalStateException("无法读取 Mihomo Controller 配置")
            )
        }

        var controller = ""
        var secret = ""
        result.stdout.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("external-controller:") ->
                    controller = cleanYamlScalar(line.substringAfter(':'))

                line.startsWith("secret:") ->
                    secret = cleanYamlScalar(line.substringAfter(':'))
            }
        }

        if (controller.isBlank()) {
            return Result.failure(
                IllegalStateException("Mihomo 未配置 external-controller")
            )
        }

        return Result.success(
            Endpoint(
                baseUrl = normalizeController(controller),
                secret = secret
            )
        )
    }

    private fun loadGroups(endpoint: Endpoint): Result<List<MihomoGroup>> {
        val groupResponse = request(endpoint, "GET", "/group")
        if (groupResponse.isSuccess) {
            val json = JSONObject(groupResponse.getOrThrow().body)
            val array = json.optJSONArray("proxies") ?: JSONArray()
            return Result.success(parseGroupArray(array))
        }

        // Older cores may not expose /group. /proxies contains the same group objects.
        return runCatching {
            val response = request(endpoint, "GET", "/proxies").getOrThrow()
            val root = JSONObject(response.body).optJSONObject("proxies") ?: JSONObject()
            val groups = mutableListOf<MihomoGroup>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = root.optJSONObject(key) ?: continue
                val type = item.optString("type", "")
                if (type in setOf("Selector", "URLTest", "Fallback", "LoadBalance")) {
                    parseGroup(item)?.let(groups::add)
                }
            }
            groups.sortedBy { it.name }
        }
    }

    private fun loadProviders(endpoint: Endpoint): Result<List<MihomoProvider>> = runCatching {
        val response = request(endpoint, "GET", "/providers/proxies").getOrThrow()
        val providers = JSONObject(response.body).optJSONObject("providers") ?: JSONObject()
        val values = mutableListOf<MihomoProvider>()
        val keys = providers.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val item = providers.optJSONObject(name) ?: JSONObject()
            values += MihomoProvider(
                name = name,
                vehicleType = item.optString("vehicleType", item.optString("type", "")),
                updatedAt = item.optString("updatedAt", "")
            )
        }
        values.sortedBy { it.name }
    }

    private fun loadVersion(endpoint: Endpoint): Result<String> = runCatching {
        val response = request(endpoint, "GET", "/version").getOrThrow()
        JSONObject(response.body).optString("version", "")
    }

    private fun parseGroupArray(array: JSONArray): List<MihomoGroup> {
        val groups = mutableListOf<MihomoGroup>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            parseGroup(item)?.let(groups::add)
        }
        return groups
            .filter { !it.hidden }
            .sortedBy { it.name }
    }

    private fun parseGroup(item: JSONObject): MihomoGroup? {
        val name = item.optString("name", "").trim()
        if (name.isBlank()) return null

        val allArray = item.optJSONArray("all") ?: JSONArray()
        val all = buildList {
            for (index in 0 until allArray.length()) {
                val value = allArray.optString(index, "").trim()
                if (value.isNotBlank()) add(value)
            }
        }

        return MihomoGroup(
            name = name,
            type = item.optString("type", ""),
            now = item.optString("now", ""),
            all = all,
            testUrl = item.optString("testUrl", ""),
            hidden = item.optBoolean("hidden", false)
        )
    }

    private fun request(
        endpoint: Endpoint,
        method: String,
        path: String,
        body: String? = null,
        readTimeoutMs: Int = 15_000
    ): Result<HttpResponse> = runCatching {
        val connection = URL(endpoint.baseUrl.trimEnd('/') + path)
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 4_000
            connection.readTimeout = readTimeoutMs
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (endpoint.secret.isNotBlank()) {
                connection.setRequestProperty(
                    "Authorization",
                    "Bearer " + endpoint.secret
                )
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
            val responseBody = stream
                ?.bufferedReader(StandardCharsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (code !in 200..299) {
                throw IllegalStateException(
                    "Mihomo API HTTP " + code +
                        if (responseBody.isBlank()) "" else ": " + responseBody.take(240)
                )
            }

            HttpResponse(code, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeController(raw: String): String {
        val value = cleanYamlScalar(raw).trimEnd('/')
        return when {
            value.startsWith("http://") || value.startsWith("https://") -> {
                value
                    .replace("://0.0.0.0:", "://127.0.0.1:")
                    .replace("://*:", "://127.0.0.1:")
                    .replace("://[::]:", "://127.0.0.1:")
            }
            value.startsWith("0.0.0.0:") -> "http://127.0.0.1:" + value.substringAfter(':')
            value.startsWith("*:") -> "http://127.0.0.1:" + value.substringAfter(':')
            value.startsWith("[::]:") -> "http://127.0.0.1:" + value.substringAfterLast(':')
            value.startsWith(":") -> "http://127.0.0.1" + value
            else -> "http://" + value
        }
    }

    private fun cleanYamlScalar(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length >= 2) {
            val first = trimmed.first()
            val last = trimmed.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return trimmed.substring(1, trimmed.length - 1)
            }
        }
        return trimmed
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")

    private data class Endpoint(
        val baseUrl: String,
        val secret: String
    )

    private data class HttpResponse(
        val code: Int,
        val body: String
    )
}

        val command = "pid=" + sh + "(cat /data/adb/box/run/box.pid 2>/dev/null); " +
            "[ -n \"" + sh + "pid\" ] && kill -0 \"" + sh + "pid\" 2>/dev/null || exit 2; " +
            "rss=" + sh + "(awk '/^VmRSS:/{print " + sh + "2; exit}' /proc/" + sh + "pid/status 2>/dev/null); " +
            "swap=" + sh + "(awk '/^VmSwap:/{print " + sh + "2; exit}' /proc/" + sh + "pid/status 2>/dev/null); " +
            "cpu=" + sh + "(ps -p \"" + sh + "pid\" -o %cpu 2>/dev/null | awk 'NR==2{print " + sh + "1}'); " +
            "echo RSS=" + sh + "{rss:-0}; echo SWAP=" + sh + "{swap:-0}; echo CPU=" + sh + "{cpu:-0}"

        val result = RootShell.exec(command, 10)
        if (!result.ok) return MihomoProcessMetrics()

        var rss = 0L
        var swap = 0L
        var cpu = 0.0
        result.stdout.lineSequence().forEach { line ->
            when {
                line.startsWith("RSS=") -> rss = line.substringAfter('=').trim().toLongOrNull() ?: 0L
                line.startsWith("SWAP=") -> swap = line.substringAfter('=').trim().toLongOrNull() ?: 0L
                line.startsWith("CPU=") -> cpu = line.substringAfter('=').trim().toDoubleOrNull() ?: 0.0
            }
        }
        return MihomoProcessMetrics(rssKb = rss, swapKb = swap, cpuPercent = cpu)
    }

    suspend fun selectProxy(groupName: String, proxyName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val endpoint = loadEndpoint().getOrThrow()
                val body = JSONObject().put("name", proxyName).toString()
                request(
                    endpoint = endpoint,
                    method = "PUT",
                    path = "/proxies/" + encode(groupName),
                    body = body
                ).getOrThrow()
                Unit
            }
        }

    suspend fun testGroup(
        groupName: String,
        testUrl: String = DEFAULT_TEST_URL,
        timeoutMs: Int = 5_000
    ): Result<Map<String, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = loadEndpoint().getOrThrow()
            val path = "/group/" + encode(groupName) + "/delay?url=" +
                encode(testUrl.ifBlank { DEFAULT_TEST_URL }) +
                "&timeout=" + timeoutMs
            val response = request(
                endpoint = endpoint,
                method = "GET",
                path = path,
                readTimeoutMs = timeoutMs + 4_000
            ).getOrThrow()

            val json = JSONObject(response.body)
            val values = linkedMapOf<String, Int>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val delay = json.optInt(key, 0)
                if (delay > 0) values[key] = delay
            }
            values
        }
    }

    suspend fun updateProvider(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = loadEndpoint().getOrThrow()
            request(
                endpoint = endpoint,
                method = "PUT",
                path = "/providers/proxies/" + encode(name),
                readTimeoutMs = 30_000
            ).getOrThrow()
            Unit
        }
    }

    suspend fun updateAllProviders(names: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = loadEndpoint().getOrThrow()
            var updated = 0
            names.distinct().forEach { name ->
                request(
                    endpoint = endpoint,
                    method = "PUT",
                    path = "/providers/proxies/" + encode(name),
                    readTimeoutMs = 30_000
                ).getOrThrow()
                updated++
            }
            updated
        }
    }

    suspend fun flushDnsCache(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = loadEndpoint().getOrThrow()
            request(
                endpoint = endpoint,
                method = "POST",
                path = "/cache/dns/flush"
            ).getOrThrow()
            Unit
        }
    }

    private suspend fun loadEndpoint(): Result<Endpoint> {
        val command =
            "[ -r " + CONFIG + " ] || exit 2; " +
                "grep -E '^(external-controller|secret):' " + CONFIG + " 2>/dev/null || true"

        val result = RootShell.exec(command, 15)
        if (!result.ok) {
            return Result.failure(
                IllegalStateException("无法读取 Mihomo Controller 配置")
            )
        }

        var controller = ""
        var secret = ""
        result.stdout.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("external-controller:") ->
                    controller = cleanYamlScalar(line.substringAfter(':'))

                line.startsWith("secret:") ->
                    secret = cleanYamlScalar(line.substringAfter(':'))
            }
        }

        if (controller.isBlank()) {
            return Result.failure(
                IllegalStateException("Mihomo 未配置 external-controller")
            )
        }

        return Result.success(
            Endpoint(
                baseUrl = normalizeController(controller),
                secret = secret
            )
        )
    }

    private fun loadGroups(endpoint: Endpoint): Result<List<MihomoGroup>> {
        val groupResponse = request(endpoint, "GET", "/group")
        if (groupResponse.isSuccess) {
            val json = JSONObject(groupResponse.getOrThrow().body)
            val array = json.optJSONArray("proxies") ?: JSONArray()
            return Result.success(parseGroupArray(array))
        }

        // Older cores may not expose /group. /proxies contains the same group objects.
        return runCatching {
            val response = request(endpoint, "GET", "/proxies").getOrThrow()
            val root = JSONObject(response.body).optJSONObject("proxies") ?: JSONObject()
            val groups = mutableListOf<MihomoGroup>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = root.optJSONObject(key) ?: continue
                val type = item.optString("type", "")
                if (type in setOf("Selector", "URLTest", "Fallback", "LoadBalance")) {
                    parseGroup(item)?.let(groups::add)
                }
            }
            groups.sortedBy { it.name }
        }
    }

    private fun loadProviders(endpoint: Endpoint): Result<List<MihomoProvider>> = runCatching {
        val response = request(endpoint, "GET", "/providers/proxies").getOrThrow()
        val providers = JSONObject(response.body).optJSONObject("providers") ?: JSONObject()
        val values = mutableListOf<MihomoProvider>()
        val keys = providers.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val item = providers.optJSONObject(name) ?: JSONObject()
            values += MihomoProvider(
                name = name,
                vehicleType = item.optString("vehicleType", item.optString("type", "")),
                updatedAt = item.optString("updatedAt", "")
            )
        }
        values.sortedBy { it.name }
    }

    private fun loadVersion(endpoint: Endpoint): Result<String> = runCatching {
        val response = request(endpoint, "GET", "/version").getOrThrow()
        JSONObject(response.body).optString("version", "")
    }

    private fun parseGroupArray(array: JSONArray): List<MihomoGroup> {
        val groups = mutableListOf<MihomoGroup>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            parseGroup(item)?.let(groups::add)
        }
        return groups
            .filter { !it.hidden }
            .sortedBy { it.name }
    }

    private fun parseGroup(item: JSONObject): MihomoGroup? {
        val name = item.optString("name", "").trim()
        if (name.isBlank()) return null

        val allArray = item.optJSONArray("all") ?: JSONArray()
        val all = buildList {
            for (index in 0 until allArray.length()) {
                val value = allArray.optString(index, "").trim()
                if (value.isNotBlank()) add(value)
            }
        }

        return MihomoGroup(
            name = name,
            type = item.optString("type", ""),
            now = item.optString("now", ""),
            all = all,
            testUrl = item.optString("testUrl", ""),
            hidden = item.optBoolean("hidden", false)
        )
    }

    private fun request(
        endpoint: Endpoint,
        method: String,
        path: String,
        body: String? = null,
        readTimeoutMs: Int = 15_000
    ): Result<HttpResponse> = runCatching {
        val connection = URL(endpoint.baseUrl.trimEnd('/') + path)
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 4_000
            connection.readTimeout = readTimeoutMs
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (endpoint.secret.isNotBlank()) {
                connection.setRequestProperty(
                    "Authorization",
                    "Bearer " + endpoint.secret
                )
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
            val responseBody = stream
                ?.bufferedReader(StandardCharsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (code !in 200..299) {
                throw IllegalStateException(
                    "Mihomo API HTTP " + code +
                        if (responseBody.isBlank()) "" else ": " + responseBody.take(240)
                )
            }

            HttpResponse(code, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeController(raw: String): String {
        val value = cleanYamlScalar(raw).trimEnd('/')
        return when {
            value.startsWith("http://") || value.startsWith("https://") -> {
                value
                    .replace("://0.0.0.0:", "://127.0.0.1:")
                    .replace("://*:", "://127.0.0.1:")
                    .replace("://[::]:", "://127.0.0.1:")
            }
            value.startsWith("0.0.0.0:") -> "http://127.0.0.1:" + value.substringAfter(':')
            value.startsWith("*:") -> "http://127.0.0.1:" + value.substringAfter(':')
            value.startsWith("[::]:") -> "http://127.0.0.1:" + value.substringAfterLast(':')
            value.startsWith(":") -> "http://127.0.0.1" + value
            else -> "http://" + value
        }
    }

    private fun cleanYamlScalar(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length >= 2) {
            val first = trimmed.first()
            val last = trimmed.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return trimmed.substring(1, trimmed.length - 1)
            }
        }
        return trimmed
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")

    private data class Endpoint(
        val baseUrl: String,
        val secret: String
    )

    private data class HttpResponse(
        val code: Int,
        val body: String
    )
}
