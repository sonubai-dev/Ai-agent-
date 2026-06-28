package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.*
import com.example.ui.theme.*

enum class Screen {
    Dashboard,
    Agents,
    Contacts,
    Campaigns,
    CallLogs,
    Analytics,
    Settings
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Dynamic runtime permission requests for outbound calling campaigns
        val permissions = mutableListOf(
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions(permissions.toTypedArray(), 101)

        setContent {
            MyApplicationTheme {
                val viewModel: CallAgentViewModel = viewModel()
                val jwtToken by viewModel.jwtToken.collectAsState()

                if (jwtToken.isEmpty()) {
                    AuthScreen(viewModel = viewModel)
                } else {
                    AppMainLayout(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MainHeader() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondary)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = "CALLAGENT AI",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF22C55E))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SYSTEM ONLINE",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
    }
}

@Composable
fun AppMainLayout(viewModel: CallAgentViewModel) {
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }

    val navigationItems = listOf(
        NavigationItem(Screen.Dashboard, "Dashboard", Icons.Default.Dashboard, "dashboard_tab"),
        NavigationItem(Screen.Campaigns, "Campaigns", Icons.Default.Campaign, "campaigns_tab"),
        NavigationItem(Screen.Settings, "Profile", Icons.Default.Person, "settings_tab")
    )

    // Responsive Adaptive Layout: Bottom Navigation for Mobile, Navigation Rail for Tablet/Wide Screen
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        if (isWideScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.testTag("nav_rail")
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    navigationItems.forEach { item ->
                        NavigationRailItem(
                            selected = currentScreen == item.screen,
                            onClick = { currentScreen = item.screen },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                    // Content area
                ) {
                    Scaffold(
                        topBar = { MainHeader() },
                        contentWindowInsets = WindowInsets.safeDrawing
                    ) { innerPadding ->
                        ScreenContent(
                            screen = currentScreen,
                            viewModel = viewModel,
                            onNavigateToCampaigns = { currentScreen = Screen.Campaigns },
                            onNavigate = { currentScreen = it },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        } else {
            Scaffold(
                topBar = { MainHeader() },
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier.testTag("bottom_navigation")
                    ) {
                        // Display primary tabs in mobile layout, wrapping or sliding if needed
                        navigationItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentScreen == item.screen,
                                onClick = { currentScreen = item.screen },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, maxLines = 1) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                },
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                ScreenContent(
                    screen = currentScreen,
                    viewModel = viewModel,
                    onNavigateToCampaigns = { currentScreen = Screen.Campaigns },
                    onNavigate = { currentScreen = it },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
fun ScreenContent(
    screen: Screen,
    viewModel: CallAgentViewModel,
    onNavigateToCampaigns: () -> Unit,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    when (screen) {
        Screen.Dashboard -> DashboardScreen(
            viewModel = viewModel,
            onNavigateToCampaigns = onNavigateToCampaigns,
            modifier = modifier
        )
        Screen.Agents -> AgentManagementScreen(
            viewModel = viewModel,
            modifier = modifier
        )
        Screen.Contacts -> ContactManagementScreen(
            viewModel = viewModel,
            modifier = modifier
        )
        Screen.Campaigns -> CampaignManagementScreen(
            viewModel = viewModel,
            modifier = modifier
        )
        Screen.CallLogs -> CallLogsScreen(
            viewModel = viewModel,
            modifier = modifier
        )
        Screen.Analytics -> AnalyticsScreen(
            viewModel = viewModel,
            modifier = modifier
        )
        Screen.Settings -> SettingsScreen(
            viewModel = viewModel,
            onNavigate = onNavigate,
            modifier = modifier
        )
    }
}

data class NavigationItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)
