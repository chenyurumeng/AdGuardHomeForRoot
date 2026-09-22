package io.github.chenyurumeng.aghmanager.data

import io.github.chenyurumeng.aghmanager.model.MihomoSubscription
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64

class MihomoSubscriptionRepository {
    companion object {
        private const val CONFIG = "/data/adb/box/mihomo/config.yaml"
        private const val MIHOMO_DIR = "/data/adb/box/mihomo"
        private const val MIHOMO_BIN = "/data/adb/box/bin/mihomo"
        private const val BOX_PID = "/data/adb/box/run/box.pid"
        private const val BOX_TOOL = "/data/adb/box/scripts/box.tool"
        private const val STATE_DIR = "/data/adb/box/run/state"

        private val NAME_REGEX = Regex("[A-Za-z0-9._-]{1,64}")
    }

    suspend fun load(): Result<List<MihomoSubscription>> {
        val result = RootShell.exec("cat " + CONFIG + " 2>/dev/null", 20)
        if (!result.ok || result.stdout.isBlank()) {
            return Result.failure(IllegalStateException("无法读取 Mihomo config.yaml"))
        }

        return runCatching {
            parseProviders(result.stdout).map { it.subscription }
        }
    }

    suspend fun save(
        originalName: String?,
        name: String,
        url: String,
        intervalSeconds: Int
    ): Result<Unit> {
        val normalizedName = name.trim()
        val normalizedUrl = url.trim()

        validate(normalizedName, normalizedUrl, intervalSeconds)?.let {
            return Result.failure(IllegalArgumentException(it))
        }

        val read = RootShell.exec("cat " + CONFIG + " 2>/dev/null", 20)
        if (!read.ok || read.stdout.isBlank()) {
            return Result.failure(IllegalStateException("无法读取 Mihomo config.yaml"))
        }

        val original = read.stdout
        val blocks = parseProviders(original)
        val existing = originalName?.let { target ->
            blocks.firstOrNull { it.subscription.name == target }
        }

        if (originalName == null && blocks.any { it.subscription.name == normalizedName }) {
            return Result.failure(IllegalArgumentException("Provider 名称已存在"))
        }

        if (originalName != null) {
            if (existing == null) {
                return Result.failure(IllegalStateException("目标 Provider 已不存在，请刷新后重试"))
            }
            if (!existing.subscription.editable) {
                return Result.failure(IllegalArgumentException("仅支持编辑 HTTP Proxy Provider"))
            }
            if (normalizedName != originalName) {
                return Result.failure(IllegalArgumentException("编辑现有订阅时不能修改 Provider 名称"))
            }
        }

        val newContent = patchProvider(
            content = original,
            existing = existing,
            name = normalizedName,
            url = normalizedUrl,
            intervalSeconds = intervalSeconds
        )

        val encoded = Base64.getEncoder().encodeToString(
            newContent.toByteArray(StandardCharsets.UTF_8)
        )
        val sh = '$'
        val command = buildString {
            append("cfg=")
            append(CONFIG)
            append("; mkdir -p ")
            append(STATE_DIR)
            append(" || exit 50; ")
            append("bak=")
            append(STATE_DIR)
            append("/mihomo-config.manager.")
            append(sh)
            append(sh)
            append(".bak; tmp=")
            append(STATE_DIR)
            append("/mihomo-config.manager.")
            append(sh)
            append(sh)
            append(".tmp; ")
            append("cp -p \"")
            append(sh)
            append("cfg\" \"")
            append(sh)
            append("bak\" || exit 51; ")
            append("printf '%s' '")
            append(encoded)
            append("' | base64 -d > \"")
            append(sh)
            append("tmp\" || { rm -f \"")
            append(sh)
            append("tmp\" \"")
            append(sh)
            append("bak\"; exit 52; }; ")
            append(MIHOMO_BIN)
            append(" -t -d ")
            append(MIHOMO_DIR)
            append(" -f \"")
            append(sh)
            append("tmp\" >/dev/null 2>&1 || { rm -f \"")
            append(sh)
            append("tmp\" \"")
            append(sh)
            append("bak\"; exit 53; }; ")
            append("mv \"")
            append(sh)
            append("tmp\" \"")
            append(sh)
            append("cfg\" || { cp -p \"")
            append(sh)
            append("bak\" \"")
            append(sh)
            append("cfg\"; rm -f \"")
            append(sh)
            append("tmp\" \"")
            append(sh)
            append("bak\"; exit 54; }; ")
            append("pid=")
            append(sh)
            append("(cat ")
            append(BOX_PID)
            append(" 2>/dev/null); ")
            append("if [ -n \"")
            append(sh)
            append("pid\" ] && kill -0 \"")
            append(sh)
            append("pid\" 2>/dev/null; then ")
            append("if ")
            append(BOX_TOOL)
            append(" reload >/dev/null 2>&1; then rm -f \"")
            append(sh)
            append("bak\"; exit 0; ")
            append("else cp -p \"")
            append(sh)
            append("bak\" \"")
            append(sh)
            append("cfg\"; ")
            append(BOX_TOOL)
            append(" reload >/dev/null 2>&1 || true; rm -f \"")
            append(sh)
            append("bak\"; exit 55; fi; ")
            append("else rm -f \"")
            append(sh)
            append("bak\"; exit 0; fi")
        }

        val result = RootShell.exec(command, 120)
        return if (result.ok) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(
                    when (result.exitCode) {
                        53 -> "Mihomo 配置校验失败，原配置未修改"
                        55 -> "Mihomo reload 失败，已恢复原配置"
                        else -> "订阅保存失败（exit=" + result.exitCode + "）"
                    }
                )
            )
        }
    }

    private fun validate(name: String, url: String, intervalSeconds: Int): String? {
        if (!NAME_REGEX.matches(name)) {
            return "Provider 名称仅允许 1–64 位英文字母、数字、点、下划线和短横线"
        }
        if (intervalSeconds !in 300..604_800) {
            return "更新间隔必须在 300–604800 秒之间"
        }

        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return "订阅 URL 格式无效"
        }

        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            return "订阅 URL 必须是有效的 http/https 地址"
        }
        if (url.any { it == '\n' || it == '\r' || it == '\u0000' }) {
            return "订阅 URL 包含非法字符"
        }
        return null
    }

    private fun parseProviders(content: String): List<ProviderBlock> {
        val lines = content.split('\n')
        val header = lines.indexOfFirst { it.trim() == "proxy-providers:" }
        if (header < 0) return emptyList()

        val sectionEnd = findSectionEnd(lines, header + 1)
        val blocks = mutableListOf<ProviderBlock>()
        var index = header + 1

        while (index < sectionEnd) {
            val line = lines[index]
            if (leadingSpaces(line) == 2 && line.trim().endsWith(":") && !line.trimStart().startsWith("#")) {
                val name = unquote(line.trim().removeSuffix(":").trim())
                val start = index
                var end = index + 1
                while (end < sectionEnd && leadingSpaces(lines[end]) != 2) end++
                val blockLines = lines.subList(start, end)

                var type = ""
                var url = ""
                var path = ""
                var interval = 86_400
                blockLines.drop(1).forEach { child ->
                    if (leadingSpaces(child) != 4 || child.trimStart().startsWith("#")) return@forEach
                    val trimmed = child.trim()
                    when {
                        trimmed.startsWith("type:") -> type = unquote(trimmed.substringAfter(':').trim())
                        trimmed.startsWith("url:") -> url = unquote(trimmed.substringAfter(':').trim())
                        trimmed.startsWith("path:") -> path = unquote(trimmed.substringAfter(':').trim())
                        trimmed.startsWith("interval:") ->
                            interval = trimmed.substringAfter(':').trim().toIntOrNull() ?: 86_400
                    }
                }

                if (name.isNotBlank()) {
                    blocks += ProviderBlock(
                        start = start,
                        endExclusive = end,
                        subscription = MihomoSubscription(
                            name = name,
                            url = url,
                            intervalSeconds = interval,
                            path = path,
                            type = type.ifBlank { "unknown" },
                            editable = type.equals("http", true) && url.isNotBlank()
                        )
                    )
                }
                index = end
            } else {
                index++
            }
        }
        return blocks
    }

    private fun patchProvider(
        content: String,
        existing: ProviderBlock?,
        name: String,
        url: String,
        intervalSeconds: Int
    ): String {
        val lines = content.split('\n').toMutableList()
        val header = lines.indexOfFirst { it.trim() == "proxy-providers:" }
        if (header < 0) {
            throw IllegalStateException("config.yaml 缺少 proxy-providers 段")
        }

        val path = existing?.subscription?.path
            ?.takeIf { it.isNotBlank() }
            ?: "./proxy_provider/" + name + ".yaml"

        val block = renderProvider(name, url, path, intervalSeconds)

        if (existing != null) {
            for (index in existing.endExclusive - 1 downTo existing.start) {
                lines.removeAt(index)
            }
            lines.addAll(existing.start, block)
        } else {
            val insertAt = findSectionEnd(lines, header + 1)
            lines.addAll(insertAt, block + listOf(""))
        }

        return lines.joinToString("\n")
    }

    private fun renderProvider(
        name: String,
        url: String,
        path: String,
        intervalSeconds: Int
    ): List<String> = listOf(
        "  " + name + ":",
        "    type: http",
        "    url: \"" + yamlEscape(url) + "\"",
        "    path: " + path,
        "    interval: " + intervalSeconds,
        "    health-check:",
        "      enable: true",
        "      url: https://cp.cloudflare.com",
        "      interval: 300",
        "      timeout: 1000",
        "      tolerance: 100"
    )

    private fun findSectionEnd(lines: List<String>, from: Int): Int {
        for (index in from until lines.size) {
            val line = lines[index]
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            if (leadingSpaces(line) == 0) return index
        }
        return lines.size
    }

    private fun leadingSpaces(value: String): Int =
        value.takeWhile { it == ' ' }.length

    private fun yamlEscape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun unquote(value: String): String {
        if (value.length >= 2) {
            val first = value.first()
            val last = value.last()
            if ((first == '\"' && last == '\"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }
        }
        return value
    }

    private data class ProviderBlock(
        val start: Int,
        val endExclusive: Int,
        val subscription: MihomoSubscription
    )
}
