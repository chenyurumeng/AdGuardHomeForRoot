package io.github.chenyurumeng.aghmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.chenyurumeng.aghmanager.data.*
import io.github.chenyurumeng.aghmanager.domain.*
import io.github.chenyurumeng.aghmanager.ui.navigation.AppNavigation
import io.github.chenyurumeng.aghmanager.ui.theme.ManagerTheme

class ComposeMainActivity : ComponentActivity() {
    private val statusRepository = StatusRepository()
    private val appRepository by lazy { AppRepository(applicationContext) }
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val boxSettingsRepository = BoxSettingsRepository()
    private val mihomoApiRepository = MihomoApiRepository()
    private val mihomoPreferencesRepository by lazy { MihomoPreferencesRepository(applicationContext) }
    private val mihomoSubscriptionRepository = MihomoSubscriptionRepository()
    private val logRepository = LogRepository()
    private val diagnosticRepository = DiagnosticRepository()
    private val aghConfigRepository = AghConfigRepository()
    private val aghCredentialStore by lazy { AghCredentialStore(applicationContext) }
    private val aghApiRepository by lazy {
        AghApiRepository(aghConfigRepository, aghCredentialStore)
    }
    private val aghFilteringRepository by lazy {
        AghFilteringRepository(aghApiRepository)
    }
    private val aghQueryLogRepository by lazy {
        AghQueryLogRepository(aghApiRepository)
    }
    private val aghClientsRepository by lazy {
        AghClientsRepository(aghApiRepository)
    }
    private val aghRewriteRepository by lazy {
        AghRewriteRepository(aghApiRepository)
    }
    private val aghAccessRepository by lazy {
        AghAccessRepository(aghApiRepository)
    }
    private val aghBlockedServicesRepository by lazy {
        AghBlockedServicesRepository(aghApiRepository)
    }
    private val aghStatisticsRepository by lazy {
        AghStatisticsRepository(aghApiRepository)
    }
    private val aghProtectionRepository by lazy {
        AghProtectionRepository(aghApiRepository)
    }
    private val orchestrator = SystemOrchestrator(statusRepository)
    private val boxController = BoxController(statusRepository)
    private val aghController = AghController(statusRepository)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ManagerTheme {
                AppNavigation(
                    statusRepository = statusRepository,
                    appRepository = appRepository,
                    settingsRepository = settingsRepository,
                    boxSettingsRepository = boxSettingsRepository,
                    mihomoApiRepository = mihomoApiRepository,
                    mihomoPreferencesRepository = mihomoPreferencesRepository,
                    mihomoSubscriptionRepository = mihomoSubscriptionRepository,
                    logRepository = logRepository,
                    diagnosticRepository = diagnosticRepository,
                    aghConfigRepository = aghConfigRepository,
                    aghCredentialStore = aghCredentialStore,
                    aghApiRepository = aghApiRepository,
                    aghFilteringRepository = aghFilteringRepository,
                    aghQueryLogRepository = aghQueryLogRepository,
                    aghClientsRepository = aghClientsRepository,
                    aghRewriteRepository = aghRewriteRepository,
                    aghAccessRepository = aghAccessRepository,
                    aghBlockedServicesRepository = aghBlockedServicesRepository,
                    aghStatisticsRepository = aghStatisticsRepository,
                    aghProtectionRepository = aghProtectionRepository,
                    orchestrator = orchestrator,
                    boxController = boxController,
                    aghController = aghController,
                    onOpenMihomoDashboard = { url ->
                        openWeb(MihomoWebViewActivity::class.java, "Mihomo Dashboard", url)
                    },
                    onOpenAghWeb = { title, url ->
                        val target = if (title.contains("Foreign", ignoreCase = true)) {
                            ForeignWebViewActivity::class.java
                        } else {
                            DomesticWebViewActivity::class.java
                        }
                        openWeb(target, title, url)
                    },
                    onCopyText = ::copyText
                )
            }
        }
    }

    private fun openWeb(target: Class<out android.app.Activity>, title: String, url: String) {
        startActivity(Intent(this, target).putExtra("title", title).putExtra("url", url))
    }

    private fun copyText(label: String, value: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    }
}
