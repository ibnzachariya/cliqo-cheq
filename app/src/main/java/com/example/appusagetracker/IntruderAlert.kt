package com.example.appusagetracker

import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends an intruder alert (details + optional photo) to the owner's email by POSTing to a
 * Google Apps Script web app, which does the actual emailing. No app-side email credentials,
 * no server hosting — the script runs on Google's side and uses the owner's Gmail quota.
 *
 * Call this from a background thread (it does blocking network I/O).
 */
object IntruderAlert {

    /** Returns true if the alert was accepted. Never throws. */
    fun send(
        scriptUrl: String,
        secret: String,
        toEmail: String,
        appName: String,
        timeText: String,
        photoFile: File?
    ): Boolean {
        if (scriptUrl.isBlank() || toEmail.isBlank()) return false

        return try {
            val payload = JSONObject().apply {
                put("secret", secret)
                put("toEmail", toEmail)
                put("appName", appName)
                put("time", timeText)
                val bytes = photoFile?.takeIf { it.exists() }?.readBytes()
                if (bytes != null) {
                    put("image", Base64.encodeToString(bytes, Base64.NO_WRAP))
                }
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
            val code = conn.responseCode
            conn.disconnect()
            // Apps Script returns 200 (or a 302 to googleusercontent) once it has run.
            code in 200..399
        } catch (e: Exception) {
            false
        }
    }
}
