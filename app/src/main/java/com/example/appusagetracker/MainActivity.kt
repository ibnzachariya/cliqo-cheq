package com.example.appusagetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val viewModel: UsageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppUsageTrackerTheme {
                RootScreen(
                    vm = viewModel,
                    onOpenSettings = { startActivity(viewModel.settingsIntent()) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

private enum class Tab(val label: String) { USAGE("Usage"), LOCK("App Lock"), INTRUDERS("Intruders") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScreen(vm: UsageViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { LockPrefs(context) }
    val intruderStore = remember { IntruderStore(context) }

    var tab by remember { mutableIntStateOf(0) }
    var premium by remember { mutableStateOf(prefs.premiumUnlocked) }
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cliqo Cheq") },
                actions = {
                    if (tab == 0 && state.hasPermission) {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.FormatListBulleted, contentDescription = null) },
                    label = { Text(Tab.USAGE.label) }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1; premium = prefs.premiumUnlocked },
                    icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    label = { Text(Tab.LOCK.label) }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                    label = { Text(Tab.INTRUDERS.label) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> UsageTab(state = state, onOpenSettings = onOpenSettings, onRefresh = { vm.refresh() })
                1 -> if (premium) {
                    AppLockScreen(context = context, prefs = prefs)
                } else {
                    PremiumScreen(prefs = prefs, onUnlocked = { premium = true })
                }
                2 -> IntruderLogScreen(store = intruderStore, prefs = prefs)
            }
        }
    }
}

@Composable
private fun UsageTab(
    state: UsageUiState,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    when {
        !state.hasPermission -> PermissionPrompt(Modifier.fillMaxSize(), onOpenSettings)
        state.loading && state.rows.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        state.rows.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
            Text("No app opens recorded in the last 24 hours.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        }
        else -> UsageList(Modifier.fillMaxSize(), state.rows)
    }
}

@Composable
private fun PermissionPrompt(modifier: Modifier, onOpenSettings: () -> Unit) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "Cliqo Cheq logo",
            modifier = Modifier.size(96.dp).padding(bottom = 16.dp)
        )
        Text("Usage access needed", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            "To show which apps have been opened on this phone, grant \"Usage access\" to this app. You can turn it off again any time.",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 24.dp)) { Text("Open Settings") }
    }
}

@Composable
private fun UsageList(modifier: Modifier, rows: List<AppUsageRow>) {
    val formatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(modifier = modifier) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(rows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (row.icon != null) {
                        Image(row.icon, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                    } else {
                        Icon(Icons.Filled.Android, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(row.appLabel, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Last opened ${formatter.format(Date(row.lastOpened))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            usageSubtitle(row.totalDurationMillis, row.openCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
            }
        }

        if (rows.size > 8) {
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SmallFloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Scroll to top")
                }
                SmallFloatingActionButton(onClick = {
                    scope.launch { listState.animateScrollToItem((rows.size - 1).coerceAtLeast(0)) }
                }) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Scroll to bottom")
                }
            }
        }
    }
}

private fun usageSubtitle(durationMillis: Long, openCount: Int): String {
    val used = "Used ${formatDuration(durationMillis)}"
    return if (openCount > 1) "$used · opened $openCount times" else used
}

private fun formatDuration(millis: Long): String {
    if (millis < 1000) return "<1s"
    val totalSeconds = millis / 1000
    val hours = TimeUnit.SECONDS.toHours(totalSeconds)
    val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
