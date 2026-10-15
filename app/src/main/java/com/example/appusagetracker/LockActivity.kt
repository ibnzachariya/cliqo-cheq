package com.example.appusagetracker

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen PIN prompt shown over a locked app. Correct PIN lets the app through;
 * a wrong PIN silently snaps a front-camera photo and logs the attempt.
 */
class LockActivity : ComponentActivity() {

    private lateinit var prefs: LockPrefs
    private lateinit var intruderStore: IntruderStore
    private var pkg: String = ""
    private var label: String = ""
    private var appIcon: ImageBitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LockPrefs(this)
        intruderStore = IntruderStore(this)

        pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        resolveApp()

        setContent {
            AppUsageTrackerTheme {
                LockUi(
                    appLabel = label,
                    icon = appIcon,
                    onPinEntered = ::onPinEntered
                )
            }
        }
    }

    private fun resolveApp() {
        val pm = packageManager
        label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
        appIcon = try {
            pm.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    /** Returns true if the PIN was correct (so the UI can clear/close). */
    private fun onPinEntered(pin: String): Boolean {
        return if (prefs.checkPin(pin)) {
            LockState.markUnlocked(pkg)
            finish()
            true
        } else {
            captureIntruder()
            false
        }
    }

    private fun captureIntruder() {
        val now = System.currentTimeMillis()
        lifecycleScope.launch {
            val file = File(intruderStore.photoDir(), "$now.jpg")
            val ok = try {
                CameraCapture.capture(this@LockActivity, this@LockActivity, file)
            } catch (e: Exception) {
                false
            }
            intruderStore.add(pkg, label, now, if (ok) file.absolutePath else null)

            // Email the alert to the owner, if configured. Runs on IO; failures are ignored.
            val toEmail = prefs.ownerEmail
            if (PremiumManager.isAlertConfigured() && toEmail.isNotBlank()) {
                val timeText = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(now))
                withContext(Dispatchers.IO) {
                    IntruderAlert.send(
                        scriptUrl = PremiumManager.ALERT_SCRIPT_URL,
                        secret = PremiumManager.ALERT_SECRET,
                        toEmail = toEmail,
                        appName = label,
                        timeText = timeText,
                        photoFile = if (ok) file else null
                    )
                }
            }
        }
    }

    // Pressing back should NOT reveal the locked app — send the user to the home screen.
    override fun onBackPressed() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
    }
}

@androidx.compose.runtime.Composable
private fun LockUi(
    appLabel: String,
    icon: ImageBitmap?,
    onPinEntered: (String) -> Boolean
) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val pinLength = 4

    val gradient = Brush.verticalGradient(
        listOf(Color(0xFF3C56D6), Color(0xFF26317F))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
            }
            Text(
                text = appLabel,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = if (error) "Wrong PIN, try again" else "Enter PIN to open",
                color = if (error) Color(0xFFFFCDD2) else Color(0xFFD6DBFF),
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp)
            )

            PinDots(filled = entered.length, total = pinLength)

            Box(modifier = Modifier.padding(top = 36.dp)) {
                PinPad(
                    onDigit = { d ->
                        if (entered.length < pinLength) {
                            error = false
                            entered += d
                            if (entered.length == pinLength) {
                                val ok = onPinEntered(entered)
                                if (!ok) {
                                    error = true
                                    entered = ""
                                }
                            }
                        }
                    },
                    onBackspace = { if (entered.isNotEmpty()) entered = entered.dropLast(1) }
                )
            }
        }
    }
}
