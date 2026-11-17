package com.callingapp.calling_app.sip // Use your actual package name

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

enum class CallType { INCOMING, OUTGOING, MISSED }

data class CallEntry(
    val number: String,
    val name: String,
    val type: CallType,
    val timestamp: Long,
    val durationSeconds: Int = 0
)
object CallLog {
    private const val PREF_FILE = "call_log_prefs"
    private const val KEY_LOG = "call_log_json"
    private var appContext: Context? = null

    fun init(ctx: Context) {
        if (appContext == null) appContext = ctx.applicationContext
    }

    fun save(entry: CallEntry) {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_LOG, "[]"))
        val obj = JSONObject().apply {
            put("number", entry.number)
            put("name", entry.name)
            put("type", entry.type.name)
            put("timestamp", entry.timestamp)
            put("duration", entry.durationSeconds)
        }
        val newArr = JSONArray().apply { put(obj) }
        for (i in 0 until minOf(arr.length(), 99)) newArr.put(arr.get(i))
        prefs.edit().putString(KEY_LOG, newArr.toString()).apply()
    }
}