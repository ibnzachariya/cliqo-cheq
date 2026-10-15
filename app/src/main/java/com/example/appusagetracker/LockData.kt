package com.example.appusagetracker

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.security.MessageDigest
import java.util.Collections

/**
 * Simple SharedPreferences-backed settings for the App Lock feature:
 * the PIN (stored only as a salted hash), which apps are locked, whether protection
 * is on, and whether the premium feature has been unlocked.
 */
class LockPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("cliqo_lock", Context.MODE_PRIVATE)

    // ---- PIN ----
    fun hasPin(): Boolean = prefs.getString(KEY_PIN_HASH, null) != null

    fun setPin(pin: String) {
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: randomSalt().also {
            prefs.edit().putString(KEY_PIN_SALT, it).apply()
        }
        prefs.edit().putString(KEY_PIN_HASH, hash(pin, salt)).apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return hash(pin, salt) == stored
    }

    // ---- Locked apps ----
    fun lockedPackages(): Set<String> =
        prefs.getStringSet(KEY_LOCKED, emptySet())?.toSet() ?: emptySet()

    fun setLocked(pkg: String, locked: Boolean) {
        val current = lockedPackages().toMutableSet()
        if (locked) current.add(pkg) else current.remove(pkg)
        prefs.edit().putStringSet(KEY_LOCKED, current).apply()
    }

    // ---- Protection on/off ----
    var protectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROTECT, false)
        set(value) = prefs.edit().putBoolean(KEY_PROTECT, value).apply()

    // ---- Premium ----
    var premiumUnlocked: Boolean
        get() = prefs.getBoolean(KEY_PREMIUM, false)
        set(value) = prefs.edit().putBoolean(KEY_PREMIUM, value).apply()

    // ---- Alert email (where intruder alerts are sent; defaults to the payment email) ----
    var ownerEmail: String
        get() = prefs.getString(KEY_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    private fun hash(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest("$salt::$pin".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun randomSalt(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars.random() }.joinToString("")
    }

    companion object {
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_LOCKED = "locked_pkgs"
        private const val KEY_PROTECT = "protection_on"
        private const val KEY_PREMIUM = "premium_on"
        private const val KEY_EMAIL = "owner_email"
    }
}

/**
 * Validates premium unlock codes entirely offline — no server needed.
 *
 * A code is accepted if it is the MASTER_CODE (for the admin / people the admin chooses),
 * or a valid voucher: "CQ" + a 6-hex serial + a 2-hex check, where the check is derived
 * from the serial and a secret SALT. The admin can mint unlimited voucher codes with the
 * same formula (see the separate code-generator page) without any backend.
 *
 * NOTE: because the SALT lives inside the app, a determined person could extract it and
 * generate codes. That's an acceptable trade-off at small scale; moving validation to a
 * tiny server would close it if the app grows.
 */
object PremiumManager {

    // ---- Paystack (in-app payment) ----
    // Paste your Paystack PUBLIC key here. Use the TEST key (pk_test_...) while trying it
    // out — you can pay with Paystack's test cards and no real money moves. Switch to your
    // LIVE key (pk_live_...) when you're ready to take real payments.
    // NOTE: only the PUBLIC key goes in the app. NEVER put your secret key (sk_...) here.
    const val PAYSTACK_PUBLIC_KEY = "pk_live_c7c5cea1e623acb95fea1cc6fbb28c5a13782f52"

    // ₦1,000 expressed in kobo (Paystack charges in kobo: 1,000 × 100).
    const val PRICE_KOBO = 100_000L
    const val CURRENCY = "NGN"
    // Prefix so you can tell this app's payments apart from your other apps in Paystack.
    const val REF_PREFIX = "CQCHEQ"
    const val APP_NAME = "Cliqo Cheq"

    fun isPaystackConfigured(): Boolean =
        PAYSTACK_PUBLIC_KEY.startsWith("pk_") && !PAYSTACK_PUBLIC_KEY.contains("REPLACE")

    // ---- Intruder email alerts (free Google Apps Script sender) ----
    // Paste the Web-app URL of your deployed Apps Script here (it looks like
    // https://script.google.com/macros/s/XXXX/exec). Leave blank to disable email alerts.
    const val ALERT_SCRIPT_URL = "https://script.google.com/macros/s/AKfycbxHB0GkqXRpZZtaKBHnDqtFeELmaDJT4bKhF_SJeIi5aM2wJYEvgUyHY4pYNM-dhRTlCw/exec"
    // Must EXACTLY match the SECRET in the Apps Script. Change both to your own value.
    const val ALERT_SECRET = "mycliqomyapp"

    fun isAlertConfigured(): Boolean = ALERT_SCRIPT_URL.startsWith("https://")

    // ---- Free unlock codes (admin) ----
    // CHANGE THESE two values to your own secrets. If you change SALT, regenerate codes
    // with the generator page using the same new SALT.
    private const val MASTER_CODE = "CLIQO-ADMIN-2026"
    private const val SALT = "Cl1qoCheq!Secret#Salt"

    fun isValidCode(raw: String): Boolean {
        val code = raw.trim().uppercase().replace("-", "").replace(" ", "")
        if (code.isEmpty()) return false
        if (code == MASTER_CODE.uppercase().replace("-", "")) return true

        // Voucher: CQ + 6 hex serial + 2 hex check
        if (code.length != 10) return false
        if (!code.startsWith("CQ")) return false
        val serial = code.substring(2, 8)
        val check = code.substring(8, 10)
        if (!serial.all { it in "0123456789ABCDEF" }) return false
        return check == expectedCheck(serial)
    }

    /** The 2-char check for a serial — same formula the generator page uses. */
    private fun expectedCheck(serial: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hex = md.digest("$SALT|$serial".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return hex.substring(0, 2).uppercase()
    }
}

/** An installed app the user can choose to lock. */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?
)

/** Lists user-launchable installed apps (excluding this app itself), sorted by name. */
fun installedLaunchableApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val own = context.packageName
    return pm.queryIntentActivities(intent, 0)
        .mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == own) return@mapNotNull null
            val label = ri.loadLabel(pm).toString()
            val icon = try {
                ri.activityInfo.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
            } catch (e: Exception) {
                null
            }
            AppInfo(pkg, label, icon)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/**
 * Tracks which locked apps are "unlocked for now" (correct PIN was entered and the app
 * is still in the foreground). Shared between the lock screen and the monitor service.
 */
object LockState {
    private val unlocked = Collections.synchronizedSet(mutableSetOf<String>())

    fun markUnlocked(pkg: String) { unlocked.add(pkg) }
    fun isUnlocked(pkg: String): Boolean = unlocked.contains(pkg)
    fun clear(pkg: String) { unlocked.remove(pkg) }
    fun clearAll() { unlocked.clear() }
}
