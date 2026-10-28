package de.jdf.dienstleistung

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.jdf.dienstleistung.ui.AppTheme
import de.jdf.dienstleistung.ui.CustomerDetailScreen
import de.jdf.dienstleistung.ui.CustomerEditScreen
import de.jdf.dienstleistung.ui.CustomerListScreen
import de.jdf.dienstleistung.ui.JobDetailScreen
import de.jdf.dienstleistung.ui.JobEditScreen
import de.jdf.dienstleistung.ui.JobListScreen
import de.jdf.dienstleistung.ui.MiniTimerBar
import de.jdf.dienstleistung.ui.SettingsScreen
import de.jdf.dienstleistung.ui.TodayScreen
import de.jdf.dienstleistung.ui.rememberTimerActions
import de.jdf.dienstleistung.ui.runningTimer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { AppNav() } }
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Heute", Icons.Default.Home),
    JOBS("jobs", "Aufträge", Icons.Default.Assignment),
    CUSTOMERS("customers", "Kunden", Icons.Default.People),
    SETTINGS("settings", "Backup", Icons.Default.CloudUpload),
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppNav() {
    val app = LocalApp()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = Tab.entries.any { it.route == route }
    val timer = runningTimer()
    val timerActions = rememberTimerActions()

    // Benachrichtigung „Timer läuft“ braucht ab Android 13 eine Erlaubnis
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) app.timer.restoreNotification()
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else app.timer.restoreNotification()
    }

    Scaffold(
        bottomBar = {
            if (showBar) Column {
                if (timer != null && route != Tab.TODAY.route) {
                    MiniTimerBar(timer, onOpen = { nav.switchTab(Tab.TODAY.route) }, onStop = timerActions.stop)
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(tab.icon, null, Modifier.size(26.dp)) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        val idArg = listOf(navArgument("id") { type = NavType.LongType })
        NavHost(nav, startDestination = Tab.TODAY.route, modifier = Modifier
            .padding(bottom = pad.calculateBottomPadding())
            .consumeWindowInsets(PaddingValues(bottom = pad.calculateBottomPadding()))) {
            composable(Tab.TODAY.route) {
                TodayScreen(
                    onOpenJob = { nav.navigate("job/$it") },
                    onShowJobs = { nav.switchTab(Tab.JOBS.route) },
                    onNewJob = { nav.navigate("jobEdit/0?customerId=0") },
                )
            }
            composable(Tab.JOBS.route) {
                JobListScreen(
                    onOpen = { nav.navigate("job/$it") },
                    onNew = { nav.navigate("jobEdit/0?customerId=0") },
                )
            }
            composable(Tab.CUSTOMERS.route) {
                CustomerListScreen(
                    onOpen = { nav.navigate("customer/$it") },
                    onNew = { nav.navigate("customerEdit/0") },
                )
            }
            composable(Tab.SETTINGS.route) { SettingsScreen() }

            composable("job/{id}", idArg) { e ->
                JobDetailScreen(
                    jobId = e.arguments!!.getLong("id"),
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("jobEdit/$it?customerId=0") },
                    onOpenCustomer = { nav.navigate("customer/$it") },
                )
            }
            composable(
                "jobEdit/{id}?customerId={customerId}",
                idArg + navArgument("customerId") { type = NavType.LongType; defaultValue = 0L },
            ) { e ->
                JobEditScreen(
                    jobId = e.arguments!!.getLong("id"),
                    presetCustomerId = e.arguments!!.getLong("customerId"),
                    onDone = { savedId, isNew ->
                        nav.popBackStack()
                        if (isNew) nav.navigate("job/$savedId")
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("customer/{id}", idArg) { e ->
                CustomerDetailScreen(
                    customerId = e.arguments!!.getLong("id"),
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("customerEdit/$it") },
                    onOpenJob = { nav.navigate("job/$it") },
                    onNewJob = { nav.navigate("jobEdit/0?customerId=$it") },
                )
            }
            composable("customerEdit/{id}", idArg) { e ->
                CustomerEditScreen(
                    customerId = e.arguments!!.getLong("id"),
                    onDone = { nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun LocalApp(): App = de.jdf.dienstleistung.ui.app()
