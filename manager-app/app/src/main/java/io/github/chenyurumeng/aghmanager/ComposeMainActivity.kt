package io.github.chenyurumeng.aghmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.chenyurumeng.aghmanager.data.AppRepository
import io.github.chenyurumeng.aghmanager.data.StatusRepository
import io.github.chenyurumeng.aghmanager.domain.BoxController
import io.github.chenyurumeng.aghmanager.domain.SystemOrchestrator
import io.github.chenyurumeng.aghmanager.ui.navigation.AppNavigation
import io.github.chenyurumeng.aghmanager.ui.theme.ManagerTheme

class ComposeMainActivity : ComponentActivity() {
    private val statusRepository = StatusRepository()
    private val appRepository by lazy { AppRepository(applicationContext) }
    private val orchestrator = SystemOrchestrator(statusRepository)
    private val boxController = BoxController(statusRepository)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ManagerTheme {
                AppNavigation(
                    statusRepository = statusRepository,
                    appRepository = appRepository,
                    orchestrator = orchestrator,
                    boxController = boxController,
                    refreshIntervalProvider = {
                        getSharedPreferences("agh_manager", MODE_PRIVATE)
                            .getLong("refresh_ms", 5_000L)
                    },
                    onOpenLegacyManager = {
                        startActivity(Intent(this, MainActivity::class.java))
                    },
                    onOpenMihomoDashboard = { url ->
                        startActivity(
                            Intent(this, MihomoWebViewActivity::class.java)
                                .putExtra("title", "Mihomo Dashboard")
                                .putExtra("url", url)
                        )
                    }
                )
            }
        }
    }
}
