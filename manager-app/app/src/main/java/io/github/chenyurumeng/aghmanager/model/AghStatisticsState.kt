package io.github.chenyurumeng.aghmanager.model

data class AghStatsTopEntry(
    val key: String,
    val value: Double
)

data class AghStatistics(
    val timeUnits: String = "hours",
    val totalQueries: Long = 0L,
    val blockedFiltering: Long = 0L,
    val safeBrowsingBlocked: Long = 0L,
    val safeSearchReplaced: Long = 0L,
    val parentalBlocked: Long = 0L,
    val averageProcessingSeconds: Double = 0.0,
    val topQueriedDomains: List<AghStatsTopEntry> = emptyList(),
    val topClients: List<AghStatsTopEntry> = emptyList(),
    val topBlockedDomains: List<AghStatsTopEntry> = emptyList(),
    val topUpstreamResponses: List<AghStatsTopEntry> = emptyList(),
    val topUpstreamAverageSeconds: List<AghStatsTopEntry> = emptyList(),
    val dnsQueriesSeries: List<Long> = emptyList(),
    val blockedSeries: List<Long> = emptyList(),
    val safeBrowsingSeries: List<Long> = emptyList(),
    val parentalSeries: List<Long> = emptyList()
) {
    val totalBlocked: Long
        get() = blockedFiltering + safeBrowsingBlocked + parentalBlocked

    val blockedPercent: Double
        get() = if (totalQueries <= 0L) 0.0
        else totalBlocked.toDouble() * 100.0 / totalQueries.toDouble()
}

data class AghStatsConfig(
    val enabled: Boolean = true,
    val intervalMs: Long = 86_400_000L,
    val ignored: List<String> = emptyList(),
    val ignoredEnabled: Boolean = false
) {
    fun normalized(): AghStatsConfig = copy(
        intervalMs = intervalMs.coerceAtLeast(0L),
        ignored = ignored.map(String::trim).filter(String::isNotBlank).distinct()
    )
}

data class AghStatsPeriod(
    val label: String,
    val milliseconds: Long
)

data class AghStatisticsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val savingConfig: Boolean = false,
    val resetting: Boolean = false,
    val stats: AghStatistics? = null,
    val config: AghStatsConfig? = null,
    val pendingConfig: AghStatsConfig? = null,
    val recentMs: Long = 0L,
    val error: String = ""
) {
    val configDirty: Boolean
        get() = config != null && pendingConfig != null &&
            config.normalized() != pendingConfig.normalized()
}
