package io.github.chenyurumeng.aghmanager.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.chenyurumeng.aghmanager.data.*
import io.github.chenyurumeng.aghmanager.domain.*
import io.github.chenyurumeng.aghmanager.ui.agh.*
import io.github.chenyurumeng.aghmanager.ui.box.*
import io.github.chenyurumeng.aghmanager.ui.home.*
import io.github.chenyurumeng.aghmanager.ui.logs.*
import io.github.chenyurumeng.aghmanager.ui.settings.*

private data class Destination(val route: String, val label: String, val icon: ImageVector)

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
    settingsRepository: SettingsRepository,
    logRepository: LogRepository,
    diagnosticRepository: DiagnosticRepository,
    orchestrator: SystemOrchestrator,
    boxController: BoxController,
    aghController: AghController,
    onOpenMihomoDashboard: (String) -> Unit,
    onOpenAghWeb: (String, String) -> Unit,
    onCopyText: (String, String) -> Unit
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
        factory = HomeViewModel.Factory(statusRepository, orchestrator, settingsRepository)
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
                                    "Box & AGH Manager · v0.5.0-rc1",
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
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                HomeScreen(viewModel = homeViewModel, contentPadding = innerPadding)
            }
            composable("box") {
                val vm: BoxViewModel = viewModel(factory = BoxViewModel.Factory(statusRepository, boxController))
                LaunchedEffect(vm) { vm.messages.collect { snackbarHostState.showSnackbar(it) } }
                BoxScreen(
                    viewModel = vm,
                    contentPadding = innerPadding,
                    onManageApps = { navController.navigate("appRouting") },
                    onOpenDashboard = onOpenMihomoDashboard
                )
            }
            composable("appRouting") {
                val vm: AppRoutingViewModel = viewModel(
                    factory = AppRoutingViewModel.Factory(appRepository, statusRepository)
                )
                LaunchedEffect(vm) { vm.messages.collect { snackbarHostState.showSnackbar(it) } }
                AppRoutingScreen(viewModel = vm, contentPadding = innerPadding, onBack = { navController.popBackStack() })
            }
            composable("agh") {
                val vm: AghViewModel = viewModel(factory = AghViewModel.Factory(statusRepository, aghController))
                LaunchedEffect(vm) { vm.messages.collect { snackbarHostState.showSnackbar(it) } }
                AghScreen(viewModel = vm, contentPadding = innerPadding, onOpenWeb = onOpenAghWeb)
            }
            composable("logs") {
                val vm: LogsViewModel = viewModel(factory = LogsViewModel.Factory(logRepository))
                LaunchedEffect(vm) {
                    vm.copyEvents.collect {
                        onCopyText("Box & AGH Logs", it)
                        snackbarHostState.showSnackbar("日志已复制")
                    }
                }
                LogsScreen(viewModel = vm, contentPadding = innerPadding)
            }
            composable("settings") {
                val vm: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(settingsRepository, diagnosticRepository, statusRepository)
                )
                LaunchedEffect(vm) { vm.messages.collect { snackbarHostState.showSnackbar(it) } }
                LaunchedEffect(vm, onCopyText) {
                    vm.copyEvents.collect { onCopyText("Box & AGH Manager 诊断报告", it) }
                }
                SettingsScreen(viewModel = vm, contentPadding = innerPadding)
            }
        }
    }
}
