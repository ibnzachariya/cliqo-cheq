package com.example.appusagetracker

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import java.util.concurrent.TimeUnit

/**
 * One app-open event: which app, its label, and when it was brought to the foreground.
 */
data class AppOpenEvent(
    val packageName: String,
    val appLabel: String,
    val timestamp: Long
)

/**
 * Reads the system's app-usage history via UsageStatsManager.
 *
 * Key idea: Android records foreground transitions at the OS level all the time.
 * This app does NOT need to be running in the background to collect them — we just
 * query the history whenever the user opens this screen. The only requirement is the
 * PACKAGE_USAGE_STATS "Usage access" permission, which the user grants in Settings.
 */
class UsageRepository(private val context: Context) {

    /**
     * Returns true if the user has granted this app "Usage access".
     * Checked via AppOpsManager since PACKAGE_USAGE_STATS is a special permission
     * that checkSelfPermission() does not report reliably.
     */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Intent that opens the system "Usage access" settings screen so the user can grant it. */
    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /**
     * Returns the list of app-open events in the given window, most recent first.
     *
     * We use queryEvents() (not queryUsageStats) because it gives an ordered timeline
     * of individual foreground transitions — i.e. the actual "what was opened and when",
     * which is exactly what we want to show.
     *
     * @param lookbackMillis how far back to look (default: last 24 hours)
     */
    fun getAppOpenEvents(lookbackMillis: Long = TimeUnit.DAYS.toMillis(1)): List<AppOpenEvent> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - lookbackMillis

        val events = usm.queryEvents(start, end)
        val result = mutableListOf<AppOpenEvent>()
        val event = UsageEvents.Event()

        // Cache labels so we don't hit PackageManager repeatedly for the same package.
        val labelCache = HashMap<String, String>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            // ACTIVITY_RESUMED (API 29+) is the modern "app came to foreground" signal;
            // MOVE_TO_FOREGROUND is the older equivalent. We accept both for wide coverage.
            val isForeground = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND

            if (isForeground) {
                val pkg = event.packageName ?: continue
                val label = labelCache.getOrPut(pkg) { resolveLabel(pkg) }
                result.add(AppOpenEvent(pkg, label, event.timeStamp))
            }
        }

        // queryEvents returns chronological order; we want newest first for the UI.
        return result.sortedByDescending { it.timestamp }
    }

    /** Human-readable app name for a package, falling back to the package name itself. */
    private fun resolveLabel(packageName: String): String {
        val pm = context.packageManager
        return try {
            val info: ApplicationInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
