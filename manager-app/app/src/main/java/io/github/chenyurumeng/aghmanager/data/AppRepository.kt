package io.github.chenyurumeng.aghmanager.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.github.chenyurumeng.aghmanager.model.AppEntry
import io.github.chenyurumeng.aghmanager.model.AppRoutingCache
import io.github.chenyurumeng.aghmanager.model.AppSyncResult
import io.github.chenyurumeng.aghmanager.model.RoutingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class AppRepository(context: Context) {
    companion object {
        private const val BOX_SERVICE = "/data/adb/box/scripts/box.service"
        private const val BOX_SETTINGS = "/data/adb/box/settings.ini"
        private const val BOX_PACKAGE_LIST = "/data/adb/box/package.list.cfg"

        private const val CACHE_PREFS = "app_routing_cache_v2"
        private const val KEY_CACHE_APPS = "apps"
        private const val KEY_CACHE_MODE = "mode"
        private const val KEY_CACHE_SELECTED = "selected"
    }

    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val cachePrefs = appContext.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)

    suspend fun loadCache(): AppRoutingCache = withContext(Dispatchers.Default) {
        val apps = readCachedApps()
        val liveKeys = apps.asSequence().map { it.key }.toSet()
        AppRoutingCache(
            apps = apps,
            mode = RoutingMode.fromRaw(cachePrefs.getString(KEY_CACHE_MODE, "whitelist")),
            selected = decodeSelected(cachePrefs.getString(KEY_CACHE_SELECTED, ""))
                .filterTo(linkedSetOf()) { it in liveKeys }
        )
    }

    suspend fun syncIncrementally(): AppSyncResult = withContext(Dispatchers.IO) {
        val result = RootShell.exec(buildStateCommand(), 45)
        if (!result.ok) {
            return@withContext AppSyncResult(
                ok = false,
                error = if (result.timedOut) "后台同步超时" else "后台同步失败"
            )
        }

        val parsed = parseState(result.stdout)
        val cached = readCachedApps()
        val merge = mergeApps(cached, parsed)
        val liveKeys = merge.apps.asSequence().map { it.key }.toSet()
        val cacheSelected = parsed.selected.filterTo(linkedSetOf()) { it in liveKeys }
        persistCache(merge.apps, parsed.mode, cacheSelected)

        AppSyncResult(
            ok = true,
            apps = merge.apps,
            mode = parsed.mode,
            selected = parsed.selected,
            added = merge.added,
            removed = merge.removed
        )
    }

    suspend fun applyRouting(
        mode: RoutingMode,
        selected: Set<String>,
        apps: List<AppEntry>
    ): ShellResult = withContext(Dispatchers.IO) {
        val liveKeys = apps.asSequence().map { it.key }.toSet()
        val validEntries = selected
            .filter { it in liveKeys && it.matches(Regex("[0-9]+:[A-Za-z0-9._]+")) }
            .sorted()

        val body = buildString {
            validEntries.forEach {
                append(it)
                append('\n')
            }
        }

        val sh = '$'
        val command = buildString {
            append("set -e; tmp=")
            append(BOX_PACKAGE_LIST)
            append(".manager.tmp; ")
            append("cat > \"")
            append(sh)
            append("tmp\" <<'__BOX_APPS_EOF__'\n")
            append(body)
            append("__BOX_APPS_EOF__\n")
            append("chmod 0644 \"")
            append(sh)
            append("tmp\"; mv \"")
            append(sh)
            append("tmp\" ")
            append(BOX_PACKAGE_LIST)
            append("; ")
            append("sed -i 's/^proxy_mode=.*/proxy_mode=\"")
            append(mode.raw)
            append("\"/' ")
            append(BOX_SETTINGS)
            append("; ")
            append(BOX_SERVICE)
            append(" apply-apps")
        }

        val result = RootShell.exec(command, 120)
        if (result.ok) {
            persistCache(apps, mode, validEntries.toSet())
        }
        result
    }

    private fun buildStateCommand(): String {
        val sh = '$'
        return listOf(
            "echo '__MODE__'",
            "awk -F'\"' '/^proxy_mode=/{print " + sh + "2; exit}' " + BOX_SETTINGS + " 2>/dev/null",
            "echo '__SELECTED__'",
            "cat " + BOX_PACKAGE_LIST + " 2>/dev/null || true",
            "echo '__USERS__'",
            "pm list users 2>/dev/null || true",
            "echo '__PACKAGES__'",
            "for u in " + sh + "(pm list users 2>/dev/null | sed -n 's/.*UserInfo{\\([0-9][0-9]*\\):.*/\\1/p'); do " +
                "pm list packages --user \"" + sh + "u\" 2>/dev/null | sed \"s/^package:/" + sh + "u|/\"; done",
            "echo '__END__'"
        ).joinToString("; ")
    }

    private fun parseState(output: String): ParsedState {
        val state = ParsedState()
        var section = ""
        val userPattern = Regex("UserInfo\\{(\\d+):([^:}]+)")

        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("__") && line.endsWith("__")) {
                section = line
                return@forEach
            }
            if (line.isEmpty()) return@forEach

            when (section) {
                "__MODE__" -> {
                    if (state.modeRaw.isEmpty()) state.modeRaw = line
                }

                "__SELECTED__" -> {
                    if (line.startsWith("#")) return@forEach
                    when {
                        line.matches(Regex("[0-9]+:[A-Za-z0-9._]+")) ->
                            state.selected.add(line)
                        line.matches(Regex("[A-Za-z0-9._]+")) ->
                            state.selected.add("0:" + line)
                    }
                }

                "__USERS__" -> {
                    val match = userPattern.find(line) ?: return@forEach
                    val userId = match.groupValues[1].toIntOrNull() ?: return@forEach
                    state.userNames[userId] = match.groupValues[2]
                }

                "__PACKAGES__" -> {
                    val sep = line.indexOf('|')
                    if (sep <= 0 || sep >= line.length - 1) return@forEach
                    val userId = line.substring(0, sep).toIntOrNull() ?: return@forEach
                    val pkg = line.substring(sep + 1)
                    if (pkg.matches(Regex("[A-Za-z0-9._]+"))) {
                        state.packages.add(PackageKey(userId, pkg))
                    }
                }
            }
        }

        return state
    }

    private fun mergeApps(cached: List<AppEntry>, parsed: ParsedState): MergeResult {
        val old = cached.associateBy { it.key }
        val merged = mutableListOf<AppEntry>()
        val live = linkedSetOf<String>()
        var added = 0

        parsed.packages.forEach { packageKey ->
            val unique = packageKey.userId.toString() + ":" + packageKey.packageName
            if (!live.add(unique)) return@forEach

            val userName = parsed.userNames[packageKey.userId]
                ?.takeIf { it.isNotBlank() }
                ?: if (packageKey.userId == 0) "Owner" else "User"

            val previous = old[unique]
            if (previous != null) {
                merged.add(previous.copy(userName = userName))
            } else {
                merged.add(resolveNewEntry(packageKey.userId, packageKey.packageName, userName))
                added++
            }
        }

        val removed = old.keys.count { it !in live }
        return MergeResult(sortApps(merged), added, removed)
    }

    private fun resolveNewEntry(userId: Int, packageName: String, userName: String): AppEntry {
        var label = packageName
        var system = false
        try {
            val info = packageManager.getApplicationInfo(
                packageName,
                PackageManager.MATCH_UNINSTALLED_PACKAGES
            )
            val value = packageManager.getApplicationLabel(info)
            if (!value.isNullOrEmpty()) label = value.toString()
            system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
        } catch (_: Exception) {
        }

        return AppEntry(
            userId = userId,
            packageName = packageName,
            label = label,
            userName = userName,
            system = system
        )
    }

    private fun readCachedApps(): List<AppEntry> {
        val result = mutableListOf<AppEntry>()
        val raw = cachePrefs.getString(KEY_CACHE_APPS, "[]") ?: "[]"

        try {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val userId = item.optInt("userId", 0)
                val packageName = item.optString("pkg", "")
                if (!packageName.matches(Regex("[A-Za-z0-9._]+"))) continue

                result.add(
                    AppEntry(
                        userId = userId,
                        packageName = packageName,
                        label = item.optString("label", packageName),
                        userName = item.optString(
                            "userName",
                            if (userId == 0) "Owner" else "User"
                        ),
                        system = item.optBoolean("system", false)
                    )
                )
            }
        } catch (_: Exception) {
        }

        return sortApps(result)
    }

    private fun persistCache(
        apps: List<AppEntry>,
        mode: RoutingMode,
        selected: Set<String>
    ) {
        val array = JSONArray()
        apps.forEach { app ->
            val item = JSONObject()
            item.put("userId", app.userId)
            item.put("pkg", app.packageName)
            item.put("label", app.label)
            item.put("userName", app.userName)
            item.put("system", app.system)
            array.put(item)
        }

        cachePrefs.edit()
            .putString(KEY_CACHE_APPS, array.toString())
            .putString(KEY_CACHE_MODE, mode.raw)
            .putString(KEY_CACHE_SELECTED, encodeSelected(selected))
            .apply()
    }

    private fun encodeSelected(values: Set<String>): String =
        values
            .filter { it.matches(Regex("[0-9]+:[A-Za-z0-9._]+")) }
            .sorted()
            .joinToString(separator = "\n", postfix = if (values.isEmpty()) "" else "\n")

    private fun decodeSelected(raw: String?): Set<String> =
        (raw ?: "")
            .lineSequence()
            .map { it.trim() }
            .filter { it.matches(Regex("[0-9]+:[A-Za-z0-9._]+")) }
            .toSet()

    private fun sortApps(apps: List<AppEntry>): List<AppEntry> =
        apps.sortedWith(
            compareBy<AppEntry> { it.label.lowercase(Locale.ROOT) }
                .thenBy { it.userId }
                .thenBy { it.packageName }
        )

    private data class PackageKey(
        val userId: Int,
        val packageName: String
    )

    private data class ParsedState(
        var modeRaw: String = "",
        val selected: MutableSet<String> = linkedSetOf(),
        val userNames: MutableMap<Int, String> = linkedMapOf(),
        val packages: MutableList<PackageKey> = mutableListOf()
    ) {
        val mode: RoutingMode
            get() = RoutingMode.fromRaw(modeRaw)
    }

    private data class MergeResult(
        val apps: List<AppEntry>,
        val added: Int,
        val removed: Int
    )
}
