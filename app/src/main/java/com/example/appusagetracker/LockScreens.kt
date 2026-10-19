package com.example.appusagetracker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Indigo = Color(0xFF3C56D6)
private val IndigoDark = Color(0xFF26317F)
private val Green = Color(0xFF26D07C)

// ---------------------------------------------------------------------------
// Shared PIN widgets (used by the lock screen and PIN setup)
// ---------------------------------------------------------------------------

@Composable
fun PinDots(filled: Int, total: Int, color: Color = Color.White) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (i < filled) color else color.copy(alpha = 0.25f))
            )
        }
    }
}

@Composable
fun PinPad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    keyColor: Color = Color.White
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "<")
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier.size(70.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (key) {
                            "" -> {}
                            "<" -> Icon(
                                Icons.Filled.Backspace,
                                contentDescription = "Delete",
                                tint = keyColor,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { onBackspace() }
                            )
                            else -> Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(keyColor.copy(alpha = 0.12f))
                                    .clickable { onDigit(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(key, color = keyColor, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Dialog that asks the user to enter a 4-digit PIN twice to set/change it. */
@Composable
fun PinSetupDialog(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var entered by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Choose a 4-digit PIN") }
    val pinLength = 4

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Set PIN") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                PinDots(filled = entered.length, total = pinLength, color = Indigo)
                Spacer(Modifier.height(16.dp))
                PinPad(
                    keyColor = Indigo,
                    onDigit = { d ->
                        if (entered.length < pinLength) {
                            entered += d
                            if (entered.length == pinLength) {
                                if (first.isEmpty()) {
                                    first = entered
                                    entered = ""
                                    message = "Re-enter to confirm"
                                } else if (first == entered) {
                                    onDone(entered)
                                } else {
                                    first = ""
                                    entered = ""
                                    message = "PINs didn't match. Try again."
                                }
                            }
                        }
                    },
                    onBackspace = { if (entered.isNotEmpty()) entered = entered.dropLast(1) }
                )
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Premium unlock screen
// ---------------------------------------------------------------------------

@Composable
fun PremiumScreen(prefs: LockPrefs, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showCode by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }

    val payLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Remember the payment email so intruder alerts go to this buyer.
            if (email.contains("@")) prefs.ownerEmail = email.trim()
            val reference = result.data?.getStringExtra(PaymentActivity.EXTRA_REFERENCE).orEmpty()

            if (!PremiumManager.isAlertConfigured()) {
                // No verification server set up — trust the Paystack callback.
                prefs.premiumUnlocked = true
                onUnlocked()
            } else {
                // Confirm the payment is real before unlocking.
                verifying = true
                error = null
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        PaymentVerifier.verify(PremiumManager.ALERT_SCRIPT_URL, PremiumManager.ALERT_SECRET, reference)
                    }
                    verifying = false
                    if (ok) {
                        prefs.premiumUnlocked = true
                        onUnlocked()
                    } else {
                        error = "We couldn't confirm your payment yet. If you were charged, tap Pay again, or contact support."
                    }
                }
            }
        } else {
            error = "Payment was not completed."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Indigo, IndigoDark)))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("App Lock & Intruder Selfie", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "Lock any app with a PIN. If someone enters the wrong PIN, CliqoCheck quietly takes their photo and logs it.",
            color = Color(0xFFD6DBFF),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("One-time payment", color = Color.Gray)
                Text("₦1,000", color = Indigo, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Pay once with card, transfer or USSD. Unlocks this phone instantly.", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; error = null },
                    label = { Text("Your email (for the receipt)") },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    enabled = !verifying,
                    onClick = {
                        when {
                            !email.contains("@") || !email.contains(".") ->
                                error = "Please enter a valid email."
                            !PremiumManager.isPaystackConfigured() ->
                                error = "Payment isn't set up yet. Please try again later."
                            else -> {
                                error = null
                                val i = Intent(context, PaymentActivity::class.java)
                                    .putExtra(PaymentActivity.EXTRA_EMAIL, email.trim())
                                payLauncher.launch(i)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (verifying) "Verifying payment…" else "Pay ₦1,000 with Paystack") }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { showCode = !showCode },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.18f),
                contentColor = Color.White
            )
        ) {
            Text(if (showCode) "Hide code entry" else "Have an unlock code?  (admin)")
        }

        if (showCode) {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it; codeError = null },
                        label = { Text("Enter unlock code") },
                        singleLine = true,
                        isError = codeError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (codeError != null) {
                        Text(codeError!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (PremiumManager.isValidCode(code)) {
                                prefs.premiumUnlocked = true
                                onUnlocked()
                            } else {
                                codeError = "That code isn't valid."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Unlock with code") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// App Lock setup screen
// ---------------------------------------------------------------------------

@Composable
fun AppLockScreen(context: Context, prefs: LockPrefs) {
    var refresh by remember { mutableStateOf(0) }
    var hasPin by remember { mutableStateOf(false) }
    var canOverlay by remember { mutableStateOf(false) }
    var hasCamera by remember { mutableStateOf(false) }
    var protectionOn by remember { mutableStateOf(prefs.protectionEnabled) }
    var showPinDialog by remember { mutableStateOf(false) }
    var lockedSet by remember { mutableStateOf(prefs.lockedPackages()) }
    var alertEmail by remember { mutableStateOf(prefs.ownerEmail) }
    var showEmailDialog by remember { mutableStateOf(false) }

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var loadingApps by remember { mutableStateOf(true) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    LaunchedEffect(refresh) {
        hasPin = prefs.hasPin()
        canOverlay = canDrawOverlays(context)
        hasCamera = CameraCapture.hasPermission(context)
        protectionOn = prefs.protectionEnabled
        lockedSet = prefs.lockedPackages()
    }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { installedLaunchableApps(context) }
        loadingApps = false
    }

    if (showPinDialog) {
        PinSetupDialog(
            onDone = { pin -> prefs.setPin(pin); showPinDialog = false; refresh++ },
            onDismiss = { showPinDialog = false }
        )
    }

    if (showEmailDialog) {
        var draft by remember { mutableStateOf(alertEmail) }
        AlertDialog(
            onDismissRequest = { showEmailDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    prefs.ownerEmail = draft.trim()
                    alertEmail = draft.trim()
                    showEmailDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEmailDialog = false }) { Text("Cancel") } },
            title = { Text("Alert email") },
            text = {
                Column {
                    Text("Intruder alerts (photo + details) are emailed here.")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("Email address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
    ) {
        // Setup checklist card
        item {
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Setup", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    SetupRow(
                        done = hasPin,
                        label = if (hasPin) "PIN is set" else "Set a 4-digit PIN",
                        actionText = if (hasPin) "Change" else "Set PIN",
                        onAction = { showPinDialog = true }
                    )
                    SetupRow(
                        done = canOverlay,
                        label = "Allow display over other apps",
                        actionText = "Allow",
                        onAction = { overlayLauncher.launch(overlayIntent(context)) }
                    )
                    SetupRow(
                        done = hasCamera,
                        label = "Allow camera (for intruder selfie)",
                        actionText = "Allow",
                        onAction = { cameraLauncher.launch(android.Manifest.permission.CAMERA) }
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        SetupRow(
                            done = false,
                            label = "Allow notifications (for the protection badge)",
                            actionText = "Allow",
                            onAction = { notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                        )
                    }
                }
            }
        }

        // Protection switch
        item {
            Spacer(Modifier.height(12.dp))
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Protection", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            if (protectionOn) "On — locked apps ask for your PIN" else "Off",
                            color = Color.Gray, fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = protectionOn,
                        onCheckedChange = { want ->
                            if (want) {
                                when {
                                    !hasPin -> showPinDialog = true
                                    !canOverlay -> overlayLauncher.launch(overlayIntent(context))
                                    else -> {
                                        prefs.protectionEnabled = true
                                        protectionOn = true
                                        LockMonitorService.start(context)
                                    }
                                }
                            } else {
                                prefs.protectionEnabled = false
                                protectionOn = false
                                LockMonitorService.stop(context)
                            }
                        }
                    )
                }
            }
        }

        // Intruder alert email
        item {
            Spacer(Modifier.height(12.dp))
            Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Intruder alerts", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            if (alertEmail.isBlank()) "No email set — alerts won't be sent"
                            else "Emailed to: $alertEmail",
                            color = Color.Gray, fontSize = 13.sp
                        )
                    }
                    TextButton(onClick = { showEmailDialog = true }) {
                        Text(if (alertEmail.isBlank()) "Set" else "Change")
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Text("Choose apps to lock", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(vertical = 8.dp))
        }

        if (loadingApps) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            items(apps) { app ->
                val locked = app.packageName in lockedSet
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            prefs.setLocked(app.packageName, !locked)
                            lockedSet = prefs.lockedPackages()
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (app.icon != null) {
                        Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)))
                    } else {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).background(Indigo.copy(alpha = 0.15f)))
                    }
                    Text(app.label, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Switch(
                        checked = locked,
                        onCheckedChange = {
                            prefs.setLocked(app.packageName, it)
                            lockedSet = prefs.lockedPackages()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupRow(done: Boolean, label: String, actionText: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) Green else Color.Gray,
            modifier = Modifier.size(22.dp)
        )
        Text(label, modifier = Modifier.weight(1f).padding(start = 10.dp))
        if (!done) {
            TextButton(onClick = onAction) { Text(actionText) }
        } else if (actionText == "Change") {
            TextButton(onClick = onAction) { Text(actionText) }
        }
    }
}

// ---------------------------------------------------------------------------
// Intruder log screen
// ---------------------------------------------------------------------------

@Composable
fun IntruderLogScreen(store: IntruderStore, prefs: LockPrefs) {
    // Require the PIN before showing captured photos (if a PIN has been set).
    var unlocked by remember { mutableStateOf(!prefs.hasPin()) }
    var entered by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var records by remember { mutableStateOf<List<IntruderRecord>>(emptyList()) }
    var showClearAll by remember { mutableStateOf(false) }
    val formatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    fun reload() { records = store.all() }
    LaunchedEffect(unlocked) { if (unlocked) reload() }

    if (!unlocked) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Indigo, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(if (pinError) "Wrong PIN, try again" else "Enter PIN to view intruders",
                color = if (pinError) MaterialTheme.colorScheme.error else Color.Gray)
            Spacer(Modifier.height(20.dp))
            PinDots(filled = entered.length, total = 4, color = Indigo)
            Spacer(Modifier.height(20.dp))
            PinPad(
                keyColor = Indigo,
                onDigit = { d ->
                    if (entered.length < 4) {
                        pinError = false
                        entered += d
                        if (entered.length == 4) {
                            if (prefs.checkPin(entered)) { unlocked = true } else { pinError = true }
                            entered = ""
                        }
                    }
                },
                onBackspace = { if (entered.isNotEmpty()) entered = entered.dropLast(1) }
            )
        }
        return
    }

    if (showClearAll) {
        AlertDialog(
            onDismissRequest = { showClearAll = false },
            confirmButton = {
                TextButton(onClick = { store.clearAll(); reload(); showClearAll = false }) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { showClearAll = false }) { Text("Cancel") } },
            title = { Text("Delete all intruders?") },
            text = { Text("This permanently removes every saved photo and record.") }
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (records.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${records.size} captured", Modifier.weight(1f), color = Color.Gray, fontSize = 13.sp)
                TextButton(onClick = { showClearAll = true }) { Text("Delete all") }
            }
        }

        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No intruders yet. Failed PIN attempts will show up here with a photo.", textAlign = TextAlign.Center, color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(records) { r ->
                    val photo: ImageBitmap? = remember(r.photoPath) { IntruderStore.loadPhoto(r.photoPath) }
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column {
                            Box(
                                Modifier.fillMaxWidth().height(160.dp).background(Color(0xFFEDEDED)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (photo != null) {
                                    Image(photo, contentDescription = null, modifier = Modifier.fillMaxSize())
                                } else {
                                    Text("No photo", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(start = 10.dp, top = 8.dp, bottom = 8.dp)) {
                                    Text(r.appLabel, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(formatter.format(Date(r.timestamp)), color = Color.Gray, fontSize = 12.sp)
                                }
                                IconButton(onClick = { store.delete(r.timestamp); reload() }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Permission helpers
// ---------------------------------------------------------------------------

private fun canDrawOverlays(context: Context): Boolean =
    Settings.canDrawOverlays(context)

private fun overlayIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
