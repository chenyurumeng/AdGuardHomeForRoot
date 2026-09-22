package io.github.chenyurumeng.aghmanager.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.chenyurumeng.aghmanager.data.AppRepository
import io.github.chenyurumeng.aghmanager.data.StatusRepository
import io.github.chenyurumeng.aghmanager.domain.BoxController
import io.github.chenyurumeng.aghmanager.domain.SystemOrchestrator
import io.github.chenyurumeng.aghmanager.ui.box.AppRoutingScreen
import io.github.chenyurumeng.aghmanager.ui.box.AppRoutingViewModel
import io.github.chenyurumeng.aghmanager.ui.box.BoxScreen
import io.github.chenyurumeng.aghmanager.ui.box.BoxViewModel
import io.github.chenyurumeng.aghmanager.ui.home.HomeScreen
import io.github.chenyurumeng.aghmanager.ui.home.HomeViewModel

private data class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val destinations = listOf(
    Destination("home", "首页", Icons.Default.Home),
    Destination("box", "Box", Icons.Default.Build),
    Destination("agh", "AGH", Icons.Default.Security),
    Destination("logs", "日志", Icons.Default.List),
    Destination("settings", "设置", Icons.Default.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    statusRepository: StatusRepository,
    appRepository: AppRepository,
    orchestrator: SystemOrchestrator,
    boxController: BoxController,
    refreshIntervalProvider: () -> Long,
    onOpenLegacyManager: () -> Unit,
    onOpenMihomoDashboard: (String) -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "home"
    val isAppRouting = currentRoute == "appRouting"
    val current = destinations.firstOrNull { it.route == currentRoute }
        ?: destinations.firstOrNull { it.route == "box" }
        ?: destinations.first()
    val snackbarHostState = remember { SnackbarHostState() }

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(
            statusRepository,
            orchestrator,
            refreshIntervalProvider
        )
    )

    LaunchedEffect(homeViewModel) {
        homeViewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            if (!isAppRouting) {
                TopAppBar(
                    title = {
                        Column {
                            Text(current.label)
                            if (currentRoute == "home") {
                                Text(
                                    "Box & AGH Manager · v0.5.0-compose-alpha2",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    },
                    actions = {
                        if (currentRoute == "home") {
                            IconButton(onClick = homeViewModel::refresh) {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新状态")
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!isAppRouting) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(destination.icon, contentDescription = destination.label)
                            },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home"
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = homeViewModel,
                    contentPadding = innerPadding,
                    onOpenLegacyManager = onOpenLegacyManager
                )
            }

            composable("box") {
                val boxViewModel: BoxViewModel = viewModel(
                    factory = BoxViewModel.Factory(statusRepository, boxController)
                )
                LaunchedEffect(boxViewModel) {
                    boxViewModel.messages.collect { snackbarHostState.showSnackbar(it) }
                }
                BoxScreen(
                    viewModel = boxViewModel,
                    contentPadding = innerPadding,
                    onManageApps = { navController.navigate("appRouting") },
                    onOpenDashboard = onOpenMihomoDashboard
                )
            }

            composable("appRouting") {
                val appRoutingViewModel: AppRoutingViewModel = viewModel(
                    factory = AppRoutingViewModel.Factory(
                        appRepository,
                        statusRepository
                    )
                )
                LaunchedEffect(appRoutingViewModel) {
                    appRoutingViewModel.messages.collect { snackbarHostState.showSnackbar(it) }
                }
                AppRoutingScreen(
                    viewModel = appRoutingViewModel,
                    contentPadding = innerPadding,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("agh") {
                LegacyPlaceholder(
                    title = "AdGuard Home",
                    detail = "Compose AGH 页面将在 alpha3 迁移；现阶段不改动双实例后端。",
                    contentPadding = innerPadding,
                    onOpenLegacyManager = onOpenLegacyManager
                )
            }
            composable("logs") {
                LegacyPlaceholder(
                    title = "日志",
                    detail = "Compose 日志页将在 alpha3 迁移，日志源与现有路径保持不变。",
                    contentPadding = innerPadding,
                    onOpenLegacyManager = onOpenLegacyManager
                )
            }
            composable("settings") {
                LegacyPlaceholder(
                    title = "设置",
                    detail = "自动刷新仍读取 v0.4.x 的 3/5/10 秒或关闭设置；完整 Compose 设置页将在 alpha3 迁移。",
                    contentPadding = innerPadding,
                    onOpenLegacyManager = onOpenLegacyManager
                )
            }
        }
    }
}

@Composable
private fun LegacyPlaceholder(
    title: String,
    detail: String,
    contentPadding: PaddingValues,
    onOpenLegacyManager: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp)
    ) {
        Text(
            title,
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
        )
        Text(
            detail,
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onOpenLegacyManager) {
            Text("打开旧版控制台")
        }
    }
}
