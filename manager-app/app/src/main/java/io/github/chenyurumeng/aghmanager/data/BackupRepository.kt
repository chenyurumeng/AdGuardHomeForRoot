package io.github.chenyurumeng.aghmanager.data

import android.content.Context
import io.github.chenyurumeng.aghmanager.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

class BackupRepository(
    context: Context,
    private val boxSettingsRepository: BoxSettingsRepository,
    private val appRepository: AppRepository,
    private val mihomoSubscriptionRepository: MihomoSubscriptionRepository,
    private val aghConfigRepository: AghConfigRepository,
    private val aghApiRepository: AghApiRepository,
    private val aghFilteringRepository: AghFilteringRepository,
    private val aghRewriteRepository: AghRewriteRepository,
    private val aghAccessRepository: AghAccessRepository,
    private val aghBlockedServicesRepository: AghBlockedServicesRepository,
    private val aghStatisticsRepository: AghStatisticsRepository
) {
    companion object {
        const val FORMAT = "BoxAghBackup"
        const val VERSION = 1
        private const val MAX_IMPORT_BYTES = 2 * 1024 * 1024
    }

    private val appContext = context.applicationContext
    private val snapshotDir = File(appContext.filesDir, "manager-backups")

    suspend fun createBackup(): Result<String> =
        try {
            Result.success(buildSnapshot().toString(2))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "创建备份失败"))
        }

    suspend fun preview(raw: String): Result<BackupPreview> =
        try {
            require(raw.toByteArray(Charsets.UTF_8).size <= MAX_IMPORT_BYTES) {
                "备份文件不能超过 2 MiB"
            }
            val imported = parseBackup(raw)
            val current = buildSnapshot()
            val modules = moduleDefinitions().map { def ->
                val source = def.read(imported)
                val now = def.read(current)
                BackupModulePreview(
                    key = def.key,
                    title = def.title,
                    changed = source.toString() != now.toString(),
                    selected = source.toString() != now.toString(),
                    note = def.note
                )
            }
            Result.success(
                BackupPreview(
                    createdAt = imported.optString("created_at", ""),
                    modules = modules
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException(e.message ?: "备份文件无法解析"))
        }

    suspend fun restore(raw: String, selectedKeys: Set<String>): Result<BackupRestoreResult> {
        if (selectedKeys.isEmpty()) {
            return Result.failure(IllegalArgumentException("至少选择一个需要恢复的模块"))
        }

        val target = try {
            parseBackup(raw)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException(e.message ?: "备份文件无法解析"))
        }

        val before = try {
            buildSnapshot()
        } catch (e: Exception) {
            return Result.failure(
                IllegalStateException("恢复前快照创建失败，已拒绝开始恢复：" + (e.message ?: "未知错误"))
            )
        }

        val snapshotPath = try {
            savePreRestoreSnapshot(before)
        } catch (e: Exception) {
            return Result.failure(
                IllegalStateException("恢复前快照写入失败，已拒绝开始恢复：" + (e.message ?: "未知错误"))
            )
        }

        val warnings = mutableListOf<String>()
        val applied = mutableListOf<String>()
        for (key in moduleOrder().filter { it in selectedKeys }) {
            val result = restoreModule(target, key, warnings)
            if (result.isSuccess) {
                applied += key
                continue
            }

            val rollbackErrors = mutableListOf<String>()
            for (rollbackKey in applied.asReversed()) {
                val rollback = restoreModule(before, rollbackKey, mutableListOf())
                if (rollback.isFailure) {
                    rollbackErrors += rollbackKey
                }
            }
            val detail = result.exceptionOrNull()?.message ?: "未知错误"
            val rollbackText = if (rollbackErrors.isEmpty()) {
                "已回滚此前成功写入的模块"
            } else {
                "部分回滚失败：" + rollbackErrors.joinToString()
            }
            return Result.failure(
                IllegalStateException(
                    "恢复在 " + titleFor(key) + " 失败：" + detail + "；" + rollbackText +
                        "。恢复前快照：" + snapshotPath
                )
            )
        }

        return Result.success(
            BackupRestoreResult(
                preRestoreSnapshotPath = snapshotPath,
                warnings = warnings.distinct()
            )
        )
    }

    private suspend fun buildSnapshot(): JSONObject {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("created_at", Instant.now().toString())
            .put("manager_version", "0.5.0-rc17")
            .put(
                "security",
                JSONObject()
                    .put("agh_plaintext_password_included", false)
                    .put("android_keystore_included", false)
                    .put("tls_private_key_included", false)
                    .put("mihomo_subscription_urls_included", false)
            )

        val boxConfig = boxSettingsRepository.load().getOrThrow()
        val apps = appRepository.syncIncrementally()
        if (!apps.ok) throw IllegalStateException(apps.error.ifBlank { "读取应用路由失败" })
        val subscriptions = mihomoSubscriptionRepository.load().getOrThrow()

        root.put(
            "box",
            JSONObject()
                .put("settings", boxConfigJson(boxConfig))
                .put("apps", JSONArray(apps.selected.sorted()))
                .put(
                    "subscriptions",
                    JSONArray().apply {
                        subscriptions.sortedBy { it.name }.forEach { sub ->
                            put(
                                JSONObject()
                                    .put("name", sub.name)
                                    .put("interval_seconds", sub.intervalSeconds)
                                    .put("enabled", sub.enabled)
                                    .put("editable", sub.editable)
                            )
                        }
                    }
                )
        )

        val agh = JSONObject()
        for (instance in listOf(AghInstance.DOMESTIC, AghInstance.FOREIGN)) {
            agh.put(instance.key, buildAghSnapshot(instance))
        }
        root.put("agh", agh)
        return root
    }

    private suspend fun buildAghSnapshot(instance: AghInstance): JSONObject {
        val structural = aghConfigRepository.load(instance).getOrThrow()
        val filtering = aghFilteringRepository.load(instance).getOrThrow()
        val clients = aghApiRepository.control(
            instance = instance,
            method = "GET",
            path = "/control/clients"
        ).getOrThrow()
        val rewrites = aghRewriteRepository.load(instance).getOrThrow()
        val access = aghAccessRepository.load(instance).getOrThrow()
        val blocked = aghBlockedServicesRepository.load(instance).getOrThrow()
        val stats = aghStatisticsRepository.loadConfig(instance).getOrThrow()

        return JSONObject()
            .put("structural", structuralJson(structural))
            .put("filtering", filteringJson(filtering))
            .put("clients", sanitizeClients(JSONObject(clients).optJSONArray("clients")))
            .put("rewrites", rewriteJson(rewrites.first, rewrites.second))
            .put("access", accessJson(access))
            .put("blocked_services", blockedJson(blocked))
            .put("statistics", statsJson(stats))
    }

    private suspend fun restoreModule(
        root: JSONObject,
        key: String,
        warnings: MutableList<String>
    ): Result<Unit> = try {
        when (key) {
            BackupModuleKeys.BOX_SETTINGS -> restoreBoxSettings(
                root.getJSONObject("box").getJSONObject("settings")
            )
            BackupModuleKeys.BOX_APPS -> restoreBoxApps(
                root.getJSONObject("box").getJSONArray("apps")
            )
            BackupModuleKeys.BOX_SUBSCRIPTIONS -> restoreSubscriptions(
                root.getJSONObject("box").getJSONArray("subscriptions"),
                warnings
            )
            else -> {
                val parts = key.split('.')
                require(parts.size == 3 && parts[0] == "agh") { "未知备份模块：$key" }
                val instance = AghInstance.fromKey(parts[1])
                val section = root.getJSONObject("agh")
                    .getJSONObject(instance.key)
                    .get(parts[2])
                restoreAghModule(instance, parts[2], section, warnings)
            }
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.message ?: "模块恢复失败"))
    }

    private suspend fun restoreBoxSettings(json: JSONObject) {
        val current = boxSettingsRepository.load().getOrThrow()
        val target = BoxConfig(
            proxyMode = RoutingMode.fromRaw(json.optString("proxy_mode")),
            networkMode = NetworkMode.fromRaw(json.optString("network_mode")),
            dnsHijackMode = DnsHijackMode.fromRaw(json.optString("dns_hijack_mode")),
            ipv6 = json.optBoolean("ipv6", true),
            proxyTcp = json.optBoolean("proxy_tcp", true),
            proxyUdp = json.optBoolean("proxy_udp", true),
            dnsHijackTcp = json.optBoolean("dns_hijack_tcp", true),
            dnsHijackUdp = json.optBoolean("dns_hijack_udp", true),
            quic = json.optBoolean("quic", true),
            mihomoDnsForward = json.optBoolean("mihomo_dns_forward", true)
        )
        val running = RootShell.exec(
            "p=\$(cat /data/adb/box/run/box.pid 2>/dev/null); [ -n \"\$p\" ] && kill -0 \"\$p\" 2>/dev/null",
            10
        ).ok
        val result = boxSettingsRepository.apply(current, target, running)
        if (!result.ok) {
            throw IllegalStateException(
                result.stdout.ifBlank { "Box settings 恢复失败（exit=" + result.exitCode + "）" }
            )
        }
    }

    private suspend fun restoreBoxApps(array: JSONArray) {
        val target = array.stringSet()
        val sync = appRepository.syncIncrementally()
        if (!sync.ok) throw IllegalStateException(sync.error.ifBlank { "应用列表读取失败" })
        val currentConfig = boxSettingsRepository.load().getOrThrow()
        val result = appRepository.applyRouting(currentConfig.proxyMode, target, sync.apps)
        if (!result.ok) {
            throw IllegalStateException(
                result.stdout.ifBlank { "应用路由恢复失败（exit=" + result.exitCode + "）" }
            )
        }
    }

    private suspend fun restoreSubscriptions(array: JSONArray, warnings: MutableList<String>) {
        val current = mihomoSubscriptionRepository.load().getOrThrow().associateBy { it.name }
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name", "")
            if (name.isBlank()) continue
            val existing = current[name]
            if (existing == null) {
                warnings += "订阅 $name 在当前设备不存在；普通备份不含 URL，因此未重建"
                continue
            }
            val interval = item.optInt("interval_seconds", existing.intervalSeconds)
            if (existing.editable && interval != existing.intervalSeconds) {
                mihomoSubscriptionRepository.save(
                    originalName = existing.name,
                    name = existing.name,
                    url = existing.url,
                    intervalSeconds = interval
                ).getOrThrow()
            } else if (!existing.editable && interval != existing.intervalSeconds) {
                warnings += "订阅 $name 不可编辑，未恢复更新周期"
            }
            val enabled = item.optBoolean("enabled", existing.enabled)
            if (enabled != existing.enabled) {
                mihomoSubscriptionRepository.setEnabled(name, enabled).getOrThrow()
            }
        }
    }

    private suspend fun restoreAghModule(
        instance: AghInstance,
        module: String,
        value: Any,
        warnings: MutableList<String>
    ) {
        when (module) {
            BackupModuleKeys.STRUCTURAL -> {
                val current = aghConfigRepository.load(instance).getOrThrow()
                val json = value as JSONObject
                val target = AghStructuralConfig(
                    webHost = json.getString("web_host"),
                    webPort = json.getInt("web_port"),
                    dnsBindHosts = json.getJSONArray("dns_bind_hosts").stringList(),
                    dnsPort = json.getInt("dns_port"),
                    username = json.getString("username"),
                    hasUser = json.optBoolean("has_user", true)
                )
                aghConfigRepository.apply(instance, current, target, passwordHash = null).getOrThrow()
            }
            BackupModuleKeys.FILTERING -> restoreFiltering(instance, value as JSONObject)
            BackupModuleKeys.CLIENTS -> restoreClients(instance, value as JSONArray)
            BackupModuleKeys.REWRITES -> restoreRewrites(instance, value as JSONObject)
            BackupModuleKeys.ACCESS -> {
                val current = aghAccessRepository.load(instance).getOrThrow()
                val json = value as JSONObject
                val target = AghAccessList(
                    allowedClients = json.getJSONArray("allowed_clients").stringList(),
                    disallowedClients = json.getJSONArray("disallowed_clients").stringList(),
                    blockedHosts = json.getJSONArray("blocked_hosts").stringList()
                )
                aghAccessRepository.update(instance, current, target).getOrThrow()
            }
            BackupModuleKeys.BLOCKED -> {
                val current = aghBlockedServicesRepository.load(instance).getOrThrow()
                val json = value as JSONObject
                val targetSchedule = parseSchedule(json.getJSONObject("schedule"))
                aghBlockedServicesRepository.update(
                    instance = instance,
                    baselineIds = current.selectedIds,
                    baselineSchedule = current.schedule,
                    selectedIds = json.getJSONArray("ids").stringSet(),
                    pendingSchedule = targetSchedule.toDraft()
                ).getOrThrow()
            }
            BackupModuleKeys.STATISTICS -> {
                val current = aghStatisticsRepository.loadConfig(instance).getOrThrow()
                val json = value as JSONObject
                val target = AghStatsConfig(
                    enabled = json.optBoolean("enabled", true),
                    intervalMs = json.optLong("interval_ms", 86_400_000L),
                    ignored = json.getJSONArray("ignored").stringList(),
                    ignoredEnabled = json.optBoolean("ignored_enabled", false)
                )
                aghStatisticsRepository.updateConfig(instance, current, target).getOrThrow()
            }
            else -> warnings += "未识别 AGH 模块：$module"
        }
    }

    private suspend fun restoreFiltering(instance: AghInstance, json: JSONObject) {
        val current = aghFilteringRepository.load(instance).getOrThrow()
        aghFilteringRepository.updateSettings(
            instance,
            json.optBoolean("enabled", true),
            json.optInt("interval_hours", 72)
        ).getOrThrow()

        val targetFilters = parseFilterArray(json.getJSONArray("filters"), false) +
            parseFilterArray(json.getJSONArray("whitelist_filters"), true)
        val currentByKey = current.allFilters.associateBy { filterKey(it.whitelist, it.url) }
        val targetByKey = targetFilters.associateBy { filterKey(it.whitelist, it.url) }

        currentByKey.filterKeys { it !in targetByKey }.values.forEach {
            aghFilteringRepository.remove(instance, it).getOrThrow()
        }
        targetByKey.filterKeys { it !in currentByKey }.values.forEach {
            aghFilteringRepository.add(instance, it.name, it.url, it.whitelist).getOrThrow()
            val latest = aghFilteringRepository.load(instance).getOrThrow().allFilters
                .firstOrNull { f -> filterKey(f.whitelist, f.url) == filterKey(it.whitelist, it.url) }
            if (latest != null && latest.enabled != it.enabled) {
                aghFilteringRepository.setEnabled(instance, latest, it.enabled).getOrThrow()
            }
        }
        targetByKey.filterKeys { it in currentByKey }.forEach { (key, target) ->
            val existing = currentByKey.getValue(key)
            if (existing.name != target.name || existing.enabled != target.enabled) {
                aghFilteringRepository.update(
                    instance,
                    existing,
                    target.name,
                    target.url,
                    target.enabled
                ).getOrThrow()
            }
        }

        val latest = aghFilteringRepository.load(instance).getOrThrow()
        val rules = json.getJSONArray("user_rules").stringList()
        if (latest.userRules != rules) {
            aghFilteringRepository.saveUserRules(instance, latest.userRules, rules).getOrThrow()
        }
    }

    private suspend fun restoreClients(instance: AghInstance, target: JSONArray) {
        val currentRaw = JSONObject(
            aghApiRepository.control(instance, "GET", "/control/clients").getOrThrow()
        ).optJSONArray("clients") ?: JSONArray()
        val current = mutableMapOf<String, JSONObject>()
        for (i in 0 until currentRaw.length()) {
            val item = currentRaw.optJSONObject(i) ?: continue
            val name = item.optString("name", "")
            if (name.isNotBlank()) current[name] = item
        }

        val wanted = mutableMapOf<String, JSONObject>()
        for (i in 0 until target.length()) {
            val item = target.optJSONObject(i) ?: continue
            val name = item.optString("name", "")
            if (name.isNotBlank()) wanted[name] = item
        }

        for (name in current.keys - wanted.keys) {
            aghApiRepository.control(
                instance,
                "POST",
                "/control/clients/delete",
                JSONObject().put("name", name).toString()
            ).getOrThrow()
        }
        for ((name, item) in wanted) {
            if (name in current) {
                aghApiRepository.control(
                    instance,
                    "POST",
                    "/control/clients/update",
                    JSONObject().put("name", name).put("data", item).toString()
                ).getOrThrow()
            } else {
                aghApiRepository.control(
                    instance,
                    "POST",
                    "/control/clients/add",
                    item.toString()
                ).getOrThrow()
            }
        }
    }

    private suspend fun restoreRewrites(instance: AghInstance, json: JSONObject) {
        val (currentEnabled, currentRules) = aghRewriteRepository.load(instance).getOrThrow()
        val targetEnabled = json.optBoolean("enabled", true)
        if (currentEnabled != targetEnabled) {
            aghRewriteRepository.setEnabled(instance, targetEnabled).getOrThrow()
        }

        val targetRules = mutableListOf<AghRewriteRule>()
        val array = json.getJSONArray("rules")
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            targetRules += AghRewriteRule(
                domain = item.optString("domain", ""),
                answer = item.optString("answer", ""),
                enabled = item.optBoolean("enabled", true)
            )
        }
        val currentByKey = currentRules.associateBy { it.key }
        val targetByKey = targetRules.associateBy { it.key }
        currentByKey.filterKeys { it !in targetByKey }.values.forEach {
            aghRewriteRepository.delete(instance, it).getOrThrow()
        }
        targetByKey.filterKeys { it !in currentByKey }.values.forEach {
            aghRewriteRepository.add(instance, it).getOrThrow()
        }
        targetByKey.filterKeys { it in currentByKey }.forEach { (key, targetRule) ->
            val old = currentByKey.getValue(key)
            if (old != targetRule) {
                aghRewriteRepository.update(instance, old, targetRule).getOrThrow()
            }
        }
    }

    private fun parseBackup(raw: String): JSONObject {
        val json = JSONObject(raw)
        require(json.optString("format") == FORMAT) { "不是 BoxAghBackup 文件" }
        require(json.optInt("version", -1) == VERSION) {
            "不支持的备份版本：" + json.optInt("version", -1)
        }
        require(json.has("box") && json.has("agh")) { "备份缺少 Box 或 AGH 数据" }
        return json
    }

    private fun savePreRestoreSnapshot(root: JSONObject): String {
        if (!snapshotDir.exists() && !snapshotDir.mkdirs()) {
            throw IllegalStateException("无法创建内部快照目录")
        }
        val file = File(snapshotDir, "pre-restore-" + System.currentTimeMillis() + ".json")
        file.writeText(root.toString(2), Charsets.UTF_8)
        return file.absolutePath
    }

    private fun moduleDefinitions(): List<ModuleDefinition> = buildList {
        add(ModuleDefinition(BackupModuleKeys.BOX_SETTINGS, "Box · Settings", "受管网络字段"))
        add(ModuleDefinition(BackupModuleKeys.BOX_APPS, "Box · 应用路由", "package list"))
        add(
            ModuleDefinition(
                BackupModuleKeys.BOX_SUBSCRIPTIONS,
                "Mihomo · 订阅元数据",
                "普通备份不含订阅 URL；只能恢复当前设备已存在的同名 Provider"
            )
        )
        for (instance in listOf(AghInstance.DOMESTIC, AghInstance.FOREIGN)) {
            add(ModuleDefinition(BackupModuleKeys.agh(instance, BackupModuleKeys.STRUCTURAL), instance.label + " · 监听/端口/用户", "不含密码"))
            add(ModuleDefinition(BackupModuleKeys.agh(instance, BackupModuleKeys.FILTERING), instance.label + " · Filters / User Rules"))
            add(ModuleDefinition(BackupModuleKeys.agh(instance, BackupModuleKeys.CLIENTS), instance.label + " · Clients"))
            add(ModuleDefinition(BackupModuleKeys.agh(instance, BackupModuleKeys.REWRITES), instance.label + " · DNS Rewrites"))
            add(ModuleDefinition(BackupModuleKeys.agh(instance, BackupModuleKeys.ACCESS), instance.label + " · Access Control"))
            add(ModuleDefinition(BackupModuleKeys.agh(instance, BackupModuleKeys.BLOCKED), instance.label + " · Blocked Services"))
            add(ModuleDefinition(BackupModuleKeys.agh(instance, BackupModuleKeys.STATISTICS), instance.label + " · Statistics"))
        }
    }

    private fun moduleOrder(): List<String> = moduleDefinitions().map { it.key }

    private fun titleFor(key: String): String =
        moduleDefinitions().firstOrNull { it.key == key }?.title ?: key

    private data class ModuleDefinition(
        val key: String,
        val title: String,
        val note: String = ""
    ) {
        fun read(root: JSONObject): Any {
            if (key.startsWith("box.")) {
                return root.getJSONObject("box").get(key.substringAfter("box."))
            }
            val parts = key.split('.')
            return root.getJSONObject("agh").getJSONObject(parts[1]).get(parts[2])
        }
    }

    private fun boxConfigJson(config: BoxConfig) = JSONObject()
        .put("proxy_mode", config.proxyMode.raw)
        .put("network_mode", config.networkMode.raw)
        .put("dns_hijack_mode", config.dnsHijackMode.raw)
        .put("ipv6", config.ipv6)
        .put("proxy_tcp", config.proxyTcp)
        .put("proxy_udp", config.proxyUdp)
        .put("dns_hijack_tcp", config.dnsHijackTcp)
        .put("dns_hijack_udp", config.dnsHijackUdp)
        .put("quic", config.quic)
        .put("mihomo_dns_forward", config.mihomoDnsForward)

    private fun structuralJson(config: AghStructuralConfig) = JSONObject()
        .put("web_host", config.webHost)
        .put("web_port", config.webPort)
        .put("dns_bind_hosts", JSONArray(config.dnsBindHosts))
        .put("dns_port", config.dnsPort)
        .put("username", config.username)
        .put("has_user", config.hasUser)

    private fun filteringJson(status: AghFilteringStatus) = JSONObject()
        .put("enabled", status.enabled)
        .put("interval_hours", status.intervalHours)
        .put("filters", filterArray(status.filters))
        .put("whitelist_filters", filterArray(status.whitelistFilters))
        .put("user_rules", JSONArray(status.userRules))

    private fun filterArray(filters: List<AghFilterSubscription>) =
        JSONArray().apply {
            filters.sortedBy { it.url }.forEach {
                put(
                    JSONObject()
                        .put("name", it.name)
                        .put("url", it.url)
                        .put("enabled", it.enabled)
                )
            }
        }

    private fun parseFilterArray(array: JSONArray, whitelist: Boolean): List<AghFilterSubscription> =
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    AghFilterSubscription(
                        id = 0L,
                        enabled = item.optBoolean("enabled", true),
                        name = item.optString("name", ""),
                        url = item.optString("url", ""),
                        rulesCount = 0,
                        lastUpdated = "",
                        whitelist = whitelist
                    )
                )
            }
        }

    private fun filterKey(whitelist: Boolean, url: String) =
        (if (whitelist) "W|" else "B|") + url

    private fun sanitizeClients(array: JSONArray?): JSONArray {
        val allowed = setOf(
            "name", "ids", "tags", "use_global_settings", "filtering_enabled",
            "parental_enabled", "safebrowsing_enabled", "safe_search",
            "use_global_blocked_services", "blocked_services", "blocked_services_schedule",
            "upstreams", "upstreams_cache_enabled", "upstreams_cache_size",
            "ignore_querylog", "ignore_statistics"
        )
        val result = JSONArray()
        if (array == null) return result
        for (i in 0 until array.length()) {
            val source = array.optJSONObject(i) ?: continue
            val item = JSONObject()
            allowed.forEach { key -> if (source.has(key)) item.put(key, source.get(key)) }
            if (item.optString("name").isNotBlank()) result.put(item)
        }
        return result
    }

    private fun rewriteJson(enabled: Boolean, rules: List<AghRewriteRule>) =
        JSONObject()
            .put("enabled", enabled)
            .put(
                "rules",
                JSONArray().apply {
                    rules.forEach {
                        put(
                            JSONObject()
                                .put("domain", it.domain)
                                .put("answer", it.answer)
                                .put("enabled", it.enabled)
                        )
                    }
                }
            )

    private fun accessJson(list: AghAccessList) = JSONObject()
        .put("allowed_clients", JSONArray(list.allowedClients))
        .put("disallowed_clients", JSONArray(list.disallowedClients))
        .put("blocked_hosts", JSONArray(list.blockedHosts))

    private fun blockedJson(snapshot: AghBlockedServicesSnapshot) = JSONObject()
        .put("ids", JSONArray(snapshot.selectedIds.sorted()))
        .put("schedule", scheduleJson(snapshot.schedule))

    private fun scheduleJson(schedule: AghBlockedServicesSchedule): JSONObject {
        val json = JSONObject().put("time_zone", schedule.timeZone)
        AGH_SCHEDULE_DAY_KEYS.forEach { key ->
            schedule.days[key]?.let { day ->
                json.put(
                    key,
                    JSONObject().put("start", day.startMs).put("end", day.endMs)
                )
            }
        }
        return json
    }

    private fun parseSchedule(json: JSONObject): AghBlockedServicesSchedule =
        AghBlockedServicesSchedule(
            timeZone = json.optString("time_zone", "Local").ifBlank { "Local" },
            days = buildMap {
                AGH_SCHEDULE_DAY_KEYS.forEach { key ->
                    val day = json.optJSONObject(key) ?: return@forEach
                    put(
                        key,
                        AghBlockedScheduleDay(
                            startMs = day.optLong("start", 0L),
                            endMs = day.optLong("end", 0L)
                        )
                    )
                }
            }
        )

    private fun statsJson(config: AghStatsConfig) = JSONObject()
        .put("enabled", config.enabled)
        .put("interval_ms", config.intervalMs)
        .put("ignored", JSONArray(config.ignored))
        .put("ignored_enabled", config.ignoredEnabled)

    private fun JSONArray.stringList(): List<String> =
        buildList {
            for (i in 0 until length()) {
                val value = optString(i, "").trim()
                if (value.isNotBlank()) add(value)
            }
        }

    private fun JSONArray.stringSet(): Set<String> = stringList().toSet()
}
