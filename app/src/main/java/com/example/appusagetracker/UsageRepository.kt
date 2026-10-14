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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.TimeUnit

/**
 * One row in the history: an app, how recently it was used, how long in total, and how
 * many times it was opened (consecutive opens of the same app are merged into one row).
 */
data class AppUsageRow(
    val packageName: String,
    val appLabel: String,
    val icon: ImageBitmap?,
    val lastOpened: Long,
    val totalDurationMillis: Long,
    val openCount: Int
)

/** An internal single foreground session before consecutive ones get merged. */
private data class Session(
    val packageName: String,
    val start: Long,
    val end: Long
)

/**
 * Reads the system's app-usage history via UsageStatsManager.
 *
 * Android records foreground transitions at the OS level, so this app does not need to run
 * in the background — it reads the history when opened. Requires the "Usage access"
 * permission, and QUERY_ALL_PACKAGES (declared in the manifest) to resolve other apps'
 * real names and icons.
 */
class UsageRepository(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /**
     * Returns the usage history, most recent first.
     *
     * Steps:
     *  1. Walk the event timeline and pair each "came to foreground" with the following
     *     "went to background" to form sessions with a real duration.
     *  2. Skip this app's own package so Cliqo Cheq doesn't list itself.
     *  3. Merge consecutive sessions of the SAME app (e.g. opened 5 times in a row) into
     *     one row, summing the time used and counting the opens.
     *  4. Attach each app's real name and icon.
     *
     * @param lookbackMillis how far back to look (default: last 24 hours)
     */
    fun getUsageRows(lookbackMillis: Long = TimeUnit.DAYS.toMillis(1)): List<AppUsageRow> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val start = now - lookbackMillis
        val ownPackage = context.packageName

        // --- 1. Build chronological foreground sessions ---
        val events = usm.queryEvents(start, now)
        val sessions = mutableListOf<Session>()
        val event = UsageEvents.Event()

        var openPkg: String? = null
        var openStart = 0L

        fun closeSession(endTime: Long) {
            val p = openPkg ?: return
            val safeEnd = endTime.coerceAtLeast(openStart)
            if (p != ownPackage) sessions.add(Session(p, openStart, safeEnd))
            openPkg = null
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> { // == ACTIVITY_RESUMED
                    if (openPkg != null && openPkg != pkg) closeSession(event.timeStamp)
                    if (openPkg == null) {
                        openPkg = pkg
                        openStart = event.timeStamp
                    }
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> { // == ACTIVITY_PAUSED
                    if (openPkg == pkg) closeSession(event.timeStamp)
                }
            }
        }
        // Anything still in the foreground at the end: close it at "now".
        closeSession(now)

        // --- 3. Merge consecutive same-app sessions ---
        data class Merged(var pkg: String, var last: Long, var total: Long, var count: Int)
        val merged = mutableListOf<Merged>()
        for (s in sessions) {
            val duration = s.end - s.start
            val lastIndex = merged.lastIndex
            if (lastIndex >= 0 && merged[lastIndex].pkg == s.packageName) {
                merged[lastIndex].total += duration
                merged[lastIndex].count += 1
                merged[lastIndex].last = maxOf(merged[lastIndex].last, s.start)
            } else {
                merged.add(Merged(s.packageName, s.start, duration, 1))
            }
        }

        // --- 4. Attach names + icons, newest first ---
        val labelCache = HashMap<String, String>()
        val iconCache = HashMap<String, ImageBitmap?>()
        return merged
            .map { m ->
                AppUsageRow(
                    packageName = m.pkg,
                    appLabel = labelCache.getOrPut(m.pkg) { resolveLabel(m.pkg) },
                    icon = iconCache.getOrPut(m.pkg) { resolveIcon(m.pkg) },
                    lastOpened = m.last,
                    totalDurationMillis = m.total,
                    openCount = m.count
                )
            }
            .sortedByDescending { it.lastOpened }
    }

    private fun resolveLabel(packageName: String): String {
        val pm = context.packageManager
        return try {
            val info: ApplicationInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun resolveIcon(packageName: String): ImageBitmap? {
        val pm = context.packageManager
        return try {
            pm.getApplicationIcon(packageName).toBitmap(width = 128, height = 128).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}
