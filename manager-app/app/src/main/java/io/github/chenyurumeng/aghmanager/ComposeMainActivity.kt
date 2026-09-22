package io.github.chenyurumeng.aghmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import io.github.chenyurumeng.aghmanager.data.StatusRepository
import io.github.chenyurumeng.aghmanager.domain.SystemOrchestrator
import io.github.chenyurumeng.aghmanager.ui.navigation.AppNavigation
import io.github.chenyurumeng.aghmanager.ui.theme.ManagerTheme

class ComposeMainActivity : ComponentActivity() {
    private val statusRepository = StatusRepository()
    private val orchestrator = SystemOrchestrator(statusRepository)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ManagerTheme {
                AppNavigation(
                    statusRepository = statusRepository,
                    orchestrator = orchestrator,
                    refreshIntervalProvider = {
                        getSharedPreferences("agh_manager", MODE_PRIVATE)
                            .getLong("refresh_ms", 5_000L)
                    },
                    onOpenLegacyManager = {
                        startActivity(Intent(this, MainActivity::class.java))
                    }
                )
            }
        }
    }
}
