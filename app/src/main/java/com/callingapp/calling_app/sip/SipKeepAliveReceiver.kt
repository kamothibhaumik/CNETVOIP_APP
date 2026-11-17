package com.callingapp.calling_app.sip

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

class SipKeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("OfficeAppPrefs", Context.MODE_PRIVATE)
        val ext = prefs.getString("ext", "") ?: ""
        val pwd = prefs.getString("pwd", "") ?: ""

        // Logged out → stop the alarm chain (before it kept firing forever)
        if (ext.isEmpty() || pwd.isEmpty()) {
            Log.d("SipKeepAlive", "Logged out – keep-alive alarm stopped")
            return
        }

        val mgr = SipModule.udpManager
        if (mgr != null) {
            // Only re-REGISTERs when needed (not every 25 s any more)
            mgr.forceReregister()
        } else {
            Log.w("SipKeepAlive", "SIP manager not running – restarting service for $ext")
            val serviceIntent = Intent(context, SipForegroundService::class.java).apply {
                action = SipForegroundService.ACTION_START
                putExtra("EXTENSION", ext)
                putExtra("PASSWORD", pwd)
                putExtra("SERVER", "core.cnetvoip.cloud")
            }
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                Log.e("SipKeepAlive", "Could not restart service: ${e.message}")
            }
        }

        // Re-arm the alarm for the next 25-second tick (also works in Doze)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val keepAlivePi = PendingIntent.getBroadcast(
            context, 9999, Intent(context, SipKeepAliveReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 25_000,
                    keepAlivePi
                )
            } catch (e: SecurityException) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 25_000,
                    keepAlivePi
                )
            }
        }
    }
}
