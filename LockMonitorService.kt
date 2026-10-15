package com.example.appusagetracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that watches which app is in front (using the same usage-access data
 * the history screen reads) and, when a LOCKED app appears, pops up the PIN lock screen.
 *
 * It needs the "Display over other apps" permission — that both lets the lock screen show
 * on top and lets this service launch it from the background (Android's background-activity
 * rules make an exception for apps holding that permission).
 */
class LockMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var prefs: LockPrefs
    private var lastForeground: String? = null

    override fun onCreate() {
        super.onCreate()
        prefs = LockPrefs(this)
        startForeground(NOTIF_ID, buildNotification())
        loop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun loop() {
        scope.launch {
            while (isActive) {
                try {
                    tick()
                } catch (_: Exception) {
                    // Never let one bad read kill the monitor.
                }
                delay(POLL_MS)
            }
        }
    }

    private fun tick() {
        if (!prefs.protectionEnabled || !prefs.premiumUnlocked) return

        val current = currentForegroundApp() ?: return

        // When the foreground app changes, the previously-unlocked app should re-lock next time.
        if (current != lastForeground) {
            lastForeground?.let { LockState.clear(it) }
            lastForeground = current
        }

        if (current == packageName) return                   // our own app / lock screen
        if (current !in prefs.lockedPackages()) return        // not a protected app
        if (LockState.isUnlocked(current)) return             // already unlocked this session

        // Prompt for the PIN.
        val i = Intent(this, LockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(LockActivity.EXTRA_PACKAGE, current)
        }
        startActivity(i)
    }

    /** The package currently in the foreground, from recent usage events. */
    private fun currentForegroundApp(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 5_000, now)
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                last = e.packageName
            }
        }
        return last
    }

    private fun buildNotification(): Notification {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Lock protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps your locked apps protected." }
            mgr.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cliqo Cheq is protecting your apps")
            .setContentText("Locked apps will ask for your PIN.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val POLL_MS = 700L
        private const val NOTIF_ID = 42
        private const val CHANNEL_ID = "cliqo_lock_channel"

        fun start(context: Context) {
            val i = Intent(context, LockMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LockMonitorService::class.java))
            LockState.clearAll()
        }
    }
}
