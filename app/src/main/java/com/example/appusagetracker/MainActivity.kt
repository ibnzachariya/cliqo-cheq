package com.example.appusagetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: UsageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppUsageTrackerTheme {
                UsageScreen(
                    vm = viewModel,
                    onOpenSettings = { startActivity(viewModel.settingsIntent()) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh every time the screen comes back to the foreground — this picks up
        // the permission the user may have just granted in Settings, and reloads events.
        viewModel.refresh()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(
    vm: UsageViewModel,
    onOpenSettings: () -> Unit
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cliqo Cheq") },
                actions = {
                    if (state.hasPermission) {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            !state.hasPermission -> PermissionPrompt(
                modifier = Modifier.fillMaxSize().padding(padding),
                onOpenSettings = onOpenSettings
            )
            state.loading && state.events.isEmpty() -> LoadingState(
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            state.events.isEmpty() -> EmptyState(
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            else -> EventList(
                modifier = Modifier.fillMaxSize().padding(padding),
                events = state.events
            )
        }
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
        Text(
            text = "Usage access needed",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = "To show which apps have been opened on this phone, grant " +
                "\"Usage access\" to this app. You can turn it off again any time.",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text("Open Settings")
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No app opens recorded in the last 24 hours.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EventList(modifier: Modifier, events: List<AppOpenEvent>) {
    val formatter = remember { SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault()) }
    LazyColumn(modifier = modifier) {
        items(events) { e ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(text = e.appLabel, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = formatter.format(Date(e.timestamp)),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = e.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
        }
    }
}
