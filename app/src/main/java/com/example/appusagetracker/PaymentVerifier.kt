package com.example.appusagetracker

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Confirms a Paystack payment is real by asking the Google Apps Script to verify the
 * transaction reference. The script checks the reference against Paystack using the SECRET
 * key (which lives only on the script, never in the app), so a payment can't be faked.
 *
 * Call from a background thread. Returns true only when the script replies "ok".
 */
object PaymentVerifier {

    fun verify(scriptUrl: String, secret: String, reference: String): Boolean {
        if (scriptUrl.isBlank() || reference.isBlank()) return false
        return try {
            val payload = JSONObject().apply {
                put("action", "verify")
                put("secret", secret)
                put("reference", reference)
            }.toString()

            val conn = (URL(scriptUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(payload.toByteArray()) }
            val body = conn.inputStream.bufferedReader().use { it.readText() }.trim()
            conn.disconnect()
            body.equals("ok", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
}
