package com.example.appusagetracker

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A recorded failed unlock attempt (an "intruder"). */
data class IntruderRecord(
    val packageName: String,
    val appLabel: String,
    val timestamp: Long,
    val photoPath: String?
)

/**
 * Stores intruder records (as JSON in SharedPreferences) and their photos (as JPEG files
 * in the app's private storage, so nothing shows up in the phone's gallery).
 */
class IntruderStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("cliqo_intruders", Context.MODE_PRIVATE)

    /** Directory for captured photos, inside the app's private files. */
    fun photoDir(): File = File(appContext.filesDir, "intruders").apply { mkdirs() }

    fun add(packageName: String, appLabel: String, timestamp: Long, photoPath: String?) {
        val arr = readArray()
        val obj = JSONObject().apply {
            put("pkg", packageName)
            put("label", appLabel)
            put("time", timestamp)
            put("photo", photoPath ?: JSONObject.NULL)
        }
        arr.put(obj)
        // Keep only the most recent 200 to bound storage.
        val trimmed = if (arr.length() > 200) {
            JSONArray().also { out -> for (i in arr.length() - 200 until arr.length()) out.put(arr.get(i)) }
        } else arr
        prefs.edit().putString(KEY, trimmed.toString()).apply()
    }

    fun all(): List<IntruderRecord> {
        val arr = readArray()
        val list = ArrayList<IntruderRecord>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val photo = if (o.isNull("photo")) null else o.optString("photo", null)
            list.add(
                IntruderRecord(
                    packageName = o.optString("pkg"),
                    appLabel = o.optString("label"),
                    timestamp = o.optLong("time"),
                    photoPath = photo
                )
            )
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun clearAll() {
        photoDir().listFiles()?.forEach { it.delete() }
        prefs.edit().remove(KEY).apply()
    }

    /** Deletes a single record (and its photo) by its timestamp. */
    fun delete(timestamp: Long) {
        val arr = readArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optLong("time") == timestamp) {
                val photo = if (o.isNull("photo")) null else o.optString("photo", null)
                if (!photo.isNullOrEmpty()) File(photo).delete()
            } else {
                out.put(o)
            }
        }
        prefs.edit().putString(KEY, out.toString()).apply()
    }

    private fun readArray(): JSONArray =
        try {
            JSONArray(prefs.getString(KEY, "[]"))
        } catch (e: Exception) {
            JSONArray()
        }

    companion object {
        private const val KEY = "records"

        /** Loads a saved photo file into an ImageBitmap for display, or null if missing. */
        fun loadPhoto(path: String?): ImageBitmap? {
            if (path.isNullOrEmpty()) return null
            val f = File(path)
            if (!f.exists()) return null
            return try {
                BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
}
