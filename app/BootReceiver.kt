package com.example.appusagetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the lock monitor after the phone reboots, if protection was on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = LockPrefs(context)
        if (prefs.protectionEnabled && prefs.premiumUnlocked) {
            LockMonitorService.start(context)
        }
    }
}
