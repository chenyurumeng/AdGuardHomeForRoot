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
    private val logRepository = LogRepository()
    private val diagnosticRepository = DiagnosticRepository()
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
                    logRepository = logRepository,
                    diagnosticRepository = diagnosticRepository,
                    orchestrator = orchestrator,
                    boxController = boxController,
                    aghController = aghController,
                    onOpenMihomoDashboard = { url ->
                        openWeb(MihomoWebViewActivity::class.java, "Mihomo Dashboard", url)
                    },
                    onOpenAghWeb = { title, url ->
                        val target = if (url.contains(":3001")) {
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
