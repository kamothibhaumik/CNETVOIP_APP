package com.callingapp.calling_app.sip

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.callingapp.calling_app.MainActivity
import com.callingapp.calling_app.ui.screens.CallActivity
import android.app.Notification

class SipForegroundService : Service() {

    companion object {
        const val CHANNEL_SIP   = "SIP_SERVICE_CHANNEL"
        const val CHANNEL_CALL  = "INCOMING_CALL_CHANNEL"
        const val NOTIF_SIP_ID  = 1
        const val NOTIF_CALL_ID = 2
        const val NOTIF_ACTIVE_CALL_ID = 3

        const val ACTION_START  = "ACTION_START"
        const val ACTION_STOP   = "ACTION_STOP"
        const val NOTIF_MISSED_CALL_ID = 5
        const val ACTION_NOTIF_HANGUP = "com.callingapp.calling_app.ACTION_NOTIF_HANGUP"

        fun showMissedCallNotification(context: Context, callerNumber: String, callerName: String) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    "MISSED_CALL_CHANNEL", "Missed Calls",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                nm.createNotificationChannel(ch)
            }

            val displayName = callerName.ifBlank { callerNumber }
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPi = PendingIntent.getActivity(
                context, 505, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, "MISSED_CALL_CHANNEL")
                .setSmallIcon(android.R.drawable.sym_call_missed)
                .setContentTitle("Missed Call")
                .setContentText("Extension $displayName")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(openPi)
                .setAutoCancel(true)
                .build()

            nm.notify(callerNumber.trim(), NOTIF_MISSED_CALL_ID, notification)
        }

        fun showIncomingCallNotification(context: Context, callerNumber: String, callerName: String) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            createCallChannel(nm)

            val callIntent = Intent(context, CallActivity::class.java).apply {
                putExtra("NUMBER", callerNumber)
                putExtra("CALLER_NAME", callerName)
                putExtra("IS_INCOMING", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val fullScreenPi = PendingIntent.getActivity(
                context, 100, callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val incomingHangupIntent = Intent(context, SipForegroundService::class.java).apply {
                action = ACTION_NOTIF_HANGUP
            }
            val incomingHangupPi = PendingIntent.getService(
                context, 401, incomingHangupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val displayName = callerName.ifBlank { callerNumber }
            val notification = NotificationCompat.Builder(context, CHANNEL_CALL)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle("Incoming Call")
                .setContentText("Extension $displayName")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(fullScreenPi)
                .setFullScreenIntent(fullScreenPi, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Hang Up",
                    incomingHangupPi
                )
                .build()

            nm.notify(NOTIF_CALL_ID, notification)
        }

        fun cancelIncomingCallNotification(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIF_CALL_ID)
        }

        fun showActiveCallNotification(
            context: Context,
            number: String,
            callerName: String,
            isIncoming: Boolean,
            connectedTimestamp: Long
        ) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    "ACTIVE_CALL_CHANNEL", "Active Call",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setSound(null, null) }
                nm.createNotificationChannel(ch)
            }

            val displayName = callerName.ifBlank { number }
            val label = if (isIncoming) "Incoming call with" else "Outgoing call with"

            val returnIntent = Intent(context, CallActivity::class.java).apply {
                putExtra("NUMBER", number)
                putExtra("CALLER_NAME", callerName)
                putExtra("IS_INCOMING", isIncoming)
                putExtra("RESTORE_CALL", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val returnPi = PendingIntent.getActivity(
                context, 300, returnIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val hangupIntent = Intent(context, SipForegroundService::class.java).apply {
                action = ACTION_NOTIF_HANGUP
            }
            val hangupPi = PendingIntent.getService(
                context, 400, hangupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationBuilder = NotificationCompat.Builder(context, "ACTIVE_CALL_CHANNEL")
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle("Call in progress")
                .setContentText("$label $displayName")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(returnPi)
                .setUsesChronometer(true)
                .setWhen(connectedTimestamp)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Hang Up",
                    hangupPi
                )

            nm.notify(NOTIF_ACTIVE_CALL_ID, notificationBuilder.build())
        }

        fun cancelActiveCallNotification(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIF_ACTIVE_CALL_ID)
        }

        const val NOTIF_CALL_WAITING_ID = 4
        const val ACTION_ANSWER_WAITING  = "ACTION_ANSWER_WAITING"
        const val ACTION_DECLINE_WAITING = "ACTION_DECLINE_WAITING"

        fun showCallWaitingNotification(context: Context, callerNumber: String, callerName: String) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    "CALL_WAITING_CHANNEL",
                    "Call Waiting",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(null, null)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(ch)
            }

            val displayName = callerName.ifBlank { callerNumber }
            val fullScreenIntent = Intent(context, context::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val fullScreenPi = PendingIntent.getActivity(
                context, 503, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val answerIntent = Intent(context, SipForegroundService::class.java).apply {
                action = ACTION_ANSWER_WAITING
                putExtra("NUMBER", callerNumber)
                putExtra("CALLER_NAME", callerName)
            }
            val answerPi = PendingIntent.getService(context, 501, answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val declineIntent = Intent(context, SipForegroundService::class.java).apply {
                action = ACTION_DECLINE_WAITING
                putExtra("NUMBER", callerNumber)
                putExtra("CALLER_NAME", callerName)
            }
            val declinePi = PendingIntent.getService(context, 502, declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val notification = NotificationCompat.Builder(context, "CALL_WAITING_CHANNEL")
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle("Call Waiting")
                .setContentText("$displayName is calling...")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(fullScreenPi, true)
                .addAction(android.R.drawable.ic_menu_call, "Answer", answerPi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePi)
                .build()

            nm.notify(NOTIF_CALL_WAITING_ID, notification)
        }

        fun cancelCallWaitingNotification(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIF_CALL_WAITING_ID)
        }

        private fun createCallChannel(nm: NotificationManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    CHANNEL_CALL, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Incoming VoIP call alerts"
                    setSound(null, null)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private var udpManager: UdpSipManager? = null
    private var mediaPlayer: MediaPlayer?   = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock?  = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var alarmManager: AlarmManager? = null
    private var keepAlivePi: PendingIntent? = null

    private var extension = ""
    private var password  = ""
    private var serverIp  = "core.cnetvoip.cloud"

    override fun onCreate() {
        super.onCreate()
        createSipChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_SIP_ID, buildSipNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_SIP_ID, buildSipNotification())
        }

        // 1. Acquire WakeLock to keep CPU executing keepalive tasks
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        // ✅ Held until logout (was 24h → after a day the phone could sleep and go offline)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CNETVOIP::SipWakeLock")
            .also { it.setReferenceCounted(false); it.acquire() }

        // 2. Acquire High-Perf WifiLock so Wi-Fi stays awake when locked
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "CNETVOIP::WifiLock")
            .also { it.acquire() }

        // 3. Register Network Monitor for seamless reconnects
        registerNetworkMonitor()

        Log.d("SipService", "Foreground Service Started with WakeLock & WifiLock")
    }

    private fun registerNetworkMonitor() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // ✅ Watch only the DEFAULT network. Before, every network (Wi-Fi AND mobile data)
        //    fired onAvailable → re-register again and again → extension flickered.
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            private var activeHandle = -1L
            private var firstCallback = true

            override fun onAvailable(network: Network) {
                val handle = network.networkHandle
                if (firstCallback) {
                    // Android calls this immediately on registration – not a real change
                    firstCallback = false
                    activeHandle = handle
                    return
                }
                if (handle != activeHandle) {
                    activeHandle = handle
                    Log.d("SipService", "Default network changed to $network → re-registering")
                    if (extension.isNotBlank()) SipModule.udpManager?.onNetworkChanged()
                }
            }

            override fun onLost(network: Network) {
                if (network.networkHandle == activeHandle) {
                    activeHandle = -1L
                    Log.d("SipService", "No network (Wi-Fi and data off) → extension offline")
                    SipModule.udpManager?.onNetworkLost()
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager?.registerDefaultNetworkCallback(networkCallback!!)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager?.registerNetworkCallback(request, networkCallback!!)
            }
        } catch (e: Exception) {
            Log.e("SipService", "Network callback registration failed: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android restarted the service after killing the app → log in again from saved login
        if (intent == null || intent.action == null) {
            val prefs = getSharedPreferences("OfficeAppPrefs", Context.MODE_PRIVATE)
            val ext = prefs.getString("ext", "") ?: ""
            val pwd = prefs.getString("pwd", "") ?: ""
            if (ext.isBlank() || pwd.isBlank()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            Log.w("SipService", "↻ Service restarted by Android – restoring login for $ext")
            startSip(ext, pwd, prefs.getString("tenant_code", "") ?: "", serverIp)
            return START_STICKY
        }

        when (intent.action) {
            "START_RINGTONE" -> startRingtone()
            "STOP_RINGTONE"  -> stopRingtone()

            ACTION_NOTIF_HANGUP -> {
                val mgr = SipModule.udpManager
                if (mgr != null && !mgr.callIsActive && mgr.callIsIncoming) {
                    mgr.receiverDeclined = true
                    try {
                        val rawNumber = mgr.lastTarget.trim()
                        val cleanDisplayNumber = if (rawNumber.startsWith("1001") && rawNumber.length > 4) {
                            rawNumber.drop(4)
                        } else {
                            rawNumber
                        }

                        com.callingapp.calling_app.utils.CallLog.init(applicationContext, mgr.myExtension)
                        com.callingapp.calling_app.utils.CallLog.save(
                            com.callingapp.calling_app.utils.CallEntry(
                                number    = cleanDisplayNumber,
                                name      = mgr.lastCallerName.ifBlank { "$cleanDisplayNumber" },
                                type      = com.callingapp.calling_app.utils.CallType.INCOMING,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("SipService", "Failed to write call log: ${e.message}")
                    }
                }
                SipModule.udpManager?.hangUp()
            }

            ACTION_ANSWER_WAITING -> {
                cancelCallWaitingNotification(applicationContext)
                SipModule.udpManager?.answerWaitingCall()
            }

            ACTION_DECLINE_WAITING -> {
                cancelCallWaitingNotification(applicationContext)
                SipModule.udpManager?.declineWaitingCall()
            }

            ACTION_STOP -> {
                cancelKeepAliveAlarm()
                stopRingtone()
                // Logout: un-REGISTER (no-op if MainAppShell already did it)
                (udpManager ?: SipModule.udpManager)?.stopRegistration(extension)
                udpManager = null
                SipModule.udpManager = null
                extension = ""
                password = ""
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                val ext = intent.getStringExtra("EXTENSION") ?: return START_STICKY
                val pwd = intent.getStringExtra("PASSWORD") ?: return START_STICKY
                val tenant = intent.getStringExtra("TENANT_CODE")
                    ?: getSharedPreferences("OfficeAppPrefs", Context.MODE_PRIVATE).getString("tenant_code", "") ?: ""
                startSip(ext, pwd, tenant, intent.getStringExtra("SERVER") ?: "core.cnetvoip.cloud")
            }
        }
        return START_STICKY
    }

    private fun startSip(rawExt: String, pwd: String, tenantCode: String, server: String) {
        // "501" → "1001501" (PBX endpoint name)
        val fullExt = if (rawExt.length <= 4 && tenantCode.isNotBlank() && !rawExt.startsWith(tenantCode)) "$tenantCode$rawExt" else rawExt
        extension = fullExt
        password  = pwd
        serverIp  = server
        SipModule.appContext = applicationContext

        val existing = SipModule.udpManager
        if (existing != null && existing.myExtension == fullExt) {
            // ✅ App opened again → just refresh. Before: startRegistration() again
            //    → a 2nd socket + 2nd timers every time → registration jumped ports → flicker
            Log.d("SipService", "Already running for $fullExt → refresh only")
            existing.forceReregister()
            udpManager = existing
            scheduleKeepAliveAlarm()
            return
        }
        if (existing != null) {
            Log.d("SipService", "Extension changed ${existing.myExtension} → $fullExt")
            existing.stopRegistration(existing.myExtension)
        }

        udpManager = UdpSipManager(this, serverIp, 5060, password).also {
            SipModule.udpManager = it
            it.startRegistration(fullExt)
        }
        scheduleKeepAliveAlarm()
    }

    private fun scheduleKeepAliveAlarm() {
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, SipKeepAliveReceiver::class.java)
        keepAlivePi = PendingIntent.getBroadcast(
            this, 9999, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                alarmManager?.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 25_000,
                    keepAlivePi!!
                )
            } catch (e: SecurityException) {
                // Android 12+ without "Alarms & reminders" permission → inexact alarm
                alarmManager?.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 25_000,
                    keepAlivePi!!
                )
            }
        } else {
            alarmManager?.setRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 25_000,
                25_000,
                keepAlivePi!!
            )
        }
        Log.d("SipService", "Keep-alive alarm scheduled (Doze-proof)")
    }

    private fun cancelKeepAliveAlarm() {
        keepAlivePi?.let { alarmManager?.cancel(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelKeepAliveAlarm()
        stopRingtone()
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        super.onDestroy()
        Log.d("SipService", "Foreground Service Destroyed")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (extension.isBlank() || password.isBlank()) return   // logged out – don't restart
        Log.d("SipService", "App removed from recent apps — restarting foreground service")
        val restartIntent = Intent(applicationContext, SipForegroundService::class.java).apply {
            action = ACTION_START
            putExtra("EXTENSION", extension)
            putExtra("PASSWORD", password)
            putExtra("SERVER", serverIp)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }

    fun startRingtone() {
        if (mediaPlayer?.isPlaying == true) return
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_SILENT,
                AudioManager.RINGER_MODE_VIBRATE -> return
            }

            audioManager.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true

            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setLegacyStreamType(AudioManager.STREAM_RING)
                        .build()
                )
                setDataSource(applicationContext, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("SipService", "Ringtone failed: ${e.message}")
        }
    }

    fun stopRingtone() {
        try { mediaPlayer?.apply { if (isPlaying) stop(); reset(); release() } } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun buildSipNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_SIP)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("CNETVOIP")
            .setContentText("Registered • Ready for calls")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun createSipChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_SIP, "SIP Connection", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(ch)
        }
    }
}
