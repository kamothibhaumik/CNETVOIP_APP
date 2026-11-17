package com.callingapp.calling_app.utils

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

enum class CallType { INCOMING, OUTGOING, MISSED }

data class CallEntry(
    val number:          String,
    val name:            String,
    val type:            CallType,
    val timestamp:       Long,
    val durationSeconds: Int = 0
) {
    fun formattedTime(): String {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000      -> "Just now"
            diff < 3_600_000   -> "${diff / 60_000}m ago"
            diff < 86_400_000  -> "${diff / 3_600_000}h ago"
            diff < 172_800_000 -> "Yesterday"
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
        }
    }

    fun formattedDuration(): String {
        if (durationSeconds <= 0) return ""
        val m = durationSeconds / 60; val s = durationSeconds % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }
}

object CallLog {
    private const val PREFS_NAME = "CallLogsPrefs"

    // ✅ Keep track of the currently logged-in extension box dynamically
    private var currentExtension: String = "default"

    // ✅ DYNAMIC BUCKET KEY: Changes dynamically for each extension account automatically
    private val KEY_LOG get() = "call_log_json_${currentExtension}"

    private lateinit var context: Context

    // Always store using the short extension (e.g. "503") regardless of whether
    // the caller passes "503" or "1001503" — ensures consistent key across all callers.
    fun init(ctx: Context, extension: String = "") {
        context = ctx.applicationContext
        if (extension.isNotBlank()) {
            currentExtension = normalizeExt(extension)
        }
        // If extension is blank, keep using whatever currentExtension was already set
    }

    private fun normalizeExt(ext: String): String {
        return if (ext.length > 4 && ext.startsWith("1001"))
            ext.drop(4) else ext
    }

    fun save(entry: CallEntry) {
        if (!::context.isInitialized) return
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentLogs = loadAll().toMutableList()
        currentLogs.add(0, entry) // Insert at the top of the timeline sheet

        val gson = Gson()
        val json = gson.toJson(currentLogs)
        sharedPref.edit().putString(KEY_LOG, json).commit()  // sync write - prevents race with ON_RESUME read
    }

    fun loadAll(): List<CallEntry> {
        if (!::context.isInitialized) return emptyList()
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sharedPref.getString(KEY_LOG, null) ?: return emptyList()

        return try {
            val type = object : TypeToken<List<CallEntry>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun incrementUnreadMissed(context: Context, extension: String) {
        val prefs = context.getSharedPreferences("CallLogPrefs", Context.MODE_PRIVATE)
        val current = prefs.getInt("unread_missed_count_$extension", 0)
        prefs.edit().putInt("unread_missed_count_$extension", current + 1).apply()
    }

    fun getUnreadMissedCount(context: Context, extension: String): Int {
        val prefs = context.getSharedPreferences("CallLogPrefs", Context.MODE_PRIVATE)
        return prefs.getInt("unread_missed_count_$extension", 0)
    }

    fun clearUnreadMissed(context: Context, extension: String) {
        val prefs = context.getSharedPreferences("CallLogPrefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("unread_missed_count_$extension", 0).apply()
    }

    fun clear() {
        if (!::context.isInitialized) return
        val sharedPref = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Only clears the data row bucket for the active account
        sharedPref.edit().remove(KEY_LOG).commit()
    }
}