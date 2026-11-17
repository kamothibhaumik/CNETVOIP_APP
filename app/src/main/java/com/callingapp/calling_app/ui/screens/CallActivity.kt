package com.callingapp.calling_app.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.callingapp.calling_app.utils.CallEntry
import com.callingapp.calling_app.utils.CallLog
import com.callingapp.calling_app.utils.CallType
import com.callingapp.calling_app.sip.SipModule
import com.callingapp.calling_app.sip.UdpSipManager
import com.callingapp.calling_app.ui.theme.Calling_appTheme
import kotlinx.coroutines.delay
import android.os.PowerManager

class CallActivity : ComponentActivity() {

    private var proximityWakeLock: android.os.PowerManager.WakeLock? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _isConnected  = mutableStateOf(false)
    private val _isRinging    = mutableStateOf(false)
    private val _shouldFinish = mutableStateOf(false)
    private val _isBusy       = mutableStateOf(false)

    private var number     = ""
    private var callerName = ""
    private var incoming   = false

    private var callConnectedAt     = 0L
    private var callWasAnswered     = false
    private var hangupInitiatedByUs = false

    private val dynamicConnectedAt = androidx.compose.runtime.mutableLongStateOf(0L)

    // ✅ Prevents stale ACTION_HANGUP_UI broadcasts from firing after activity already closing
    private var isAlreadyClosing = false

    private val signalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isAlreadyClosing) return

            when (intent?.action) {
                // ✅ 1. Outgoing call remote ringing event:
                "ACTION_REMOTE_RINGING" -> {
                    android.util.Log.d("CallActivity", "Remote ringing received: Updating UI to Ringing")
                    _isRinging.value = true
                }

                // ✅ 2. Call Connected / Picked Up:
                "ACTION_CALL_CONNECTED" -> {
                    com.callingapp.calling_app.sip.SipForegroundService.cancelIncomingCallNotification(this@CallActivity)
                    val stopRing = android.content.Intent(this@CallActivity, com.callingapp.calling_app.sip.SipForegroundService::class.java).apply {
                        action = "STOP_RINGTONE"
                    }
                    startService(stopRing)

                    // 🛑 STRICT EARPIECE ROUTING: Ensure bottom speaker is OFF
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = false

                    _isRinging.value   = false
                    _isConnected.value = true
                    callConnectedAt    = System.currentTimeMillis()
                    callWasAnswered    = true

                    // Activate ear proximity sensor (turns screen off on ear)
                    acquireProximityWakeLock()
                }

                "ACTION_CALL_BUSY" -> {
                    _isBusy.value = true
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        triggerClose()
                    }, 2000)
                }

                "ACTION_HANGUP_UI" -> {
                    val mgr = com.callingapp.calling_app.sip.SipModule.udpManager
                    if (mgr != null && mgr.callWaitingActive) {
                        return
                    }

                    com.callingapp.calling_app.sip.SipForegroundService.cancelIncomingCallNotification(this@CallActivity)
                    val stopRing = android.content.Intent(this@CallActivity, com.callingapp.calling_app.sip.SipForegroundService::class.java).apply {
                        action = "STOP_RINGTONE"
                    }
                    startService(stopRing)
                    _shouldFinish.value = true
                    triggerClose()
                }

                "ACTION_CALL_WAITING_ANSWERED" -> {
                    _isRinging.value   = false
                    _isConnected.value = true
                    callWasAnswered    = true
                    val refreshIntent = Intent("ACTION_FORCE_RECOMPOSE").apply { setPackage(packageName) }
                    sendBroadcast(refreshIntent)
                }

                "ACTION_SWITCH_CALL_CONTEXT" -> {
                    val restoredNumber = intent?.getStringExtra("NUMBER") ?: "Unknown"
                    val restoredName   = intent.getStringExtra("CALLER_NAME") ?: ""
                    val restoredTime   = intent.getLongExtra("CONNECTED_TIMESTAMP", System.currentTimeMillis())

                    number             = restoredNumber
                    callerName         = restoredName
                    callConnectedAt    = restoredTime

                    _isRinging.value   = false
                    _isConnected.value = true
                    _isBusy.value      = false
                    callWasAnswered    = true

                    getIntent().putExtra("NUMBER", restoredNumber)
                    getIntent().putExtra("CALLER_NAME", restoredName)
                    dynamicConnectedAt.value = restoredTime
                }

                "ACTION_SECONDARY_HANGUP" -> {
                    val mgr = com.callingapp.calling_app.sip.SipModule.udpManager
                    if (mgr != null && mgr.isCallActive) {
                        number     = mgr.lastTarget.ifBlank { number }
                        callerName = mgr.lastCallerName.ifBlank { callerName }
                        callConnectedAt    = mgr.callConnectedTimestamp
                        callWasAnswered    = true
                        _isConnected.value = true
                        _isRinging.value   = false
                        dynamicConnectedAt.value = mgr.callConnectedTimestamp
                        getIntent().putExtra("NUMBER", number)
                        getIntent().putExtra("CALLER_NAME", callerName)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        requestAudioFocus()

        // Inside CallActivity.kt -> Inside onCreate() tracking block:

        number     = intent.getStringExtra("NUMBER")      ?: "Unknown"
        callerName = intent.getStringExtra("CALLER_NAME") ?: ""
        incoming   = intent.getBooleanExtra("IS_INCOMING", false)
        val autoAnswer   = intent.getBooleanExtra("AUTO_ANSWER", false)
        val restoreCall  = intent.getBooleanExtra("RESTORE_CALL", false)

        if (!incoming && !restoreCall) {
            _isRinging.value = true
        }

        // ── 🚀 ✅ THE ROTATION SAFEGUARD ──
        // If the activity ever reloads while the background service is actively in a call,
        // force the UI variables to remain locked in Connected mode instantly.
        val activeUdpManager = SipModule.udpManager
        if (activeUdpManager != null && activeUdpManager.callIsActive) {
            _isConnected.value = true
            _isRinging.value   = false
            callWasAnswered    = true
            if (callConnectedAt == 0L) {
                // Keep the original countdown timer ticking accurately
                callConnectedAt = activeUdpManager.callConnectedTimestamp
            }
            audioManager.mode  = AudioManager.MODE_IN_COMMUNICATION
        }

        val displayName = callerName.ifBlank { number }

        val activeExtension = SipModule.udpManager?.myExtension ?: "unknown"
        CallLog.init(this@CallActivity, activeExtension)

        val filter = IntentFilter().apply {
            addAction("ACTION_REMOTE_RINGING")
            addAction("ACTION_CALL_CONNECTED")
            addAction("ACTION_HANGUP_UI")
            addAction("ACTION_CALL_BUSY")
            addAction("ACTION_SWITCH_CALL_CONTEXT")      // Phase 4: restore 501
            addAction("ACTION_CALL_WAITING_ANSWERED")     // Phase 3: switch to 701
            addAction("ACTION_SECONDARY_HANGUP")         // Phase 4: sync when secondary closes
        }
        ContextCompat.registerReceiver(this, signalReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // RESTORE_CALL: user closed app during active call and tapped the "Call in progress" notification.
        // Immediately restore connected UI — no need to re-answer, audio is already flowing.
        if (restoreCall) {
            val mgr = SipModule.udpManager
            if (mgr != null && mgr.callIsActive) {
                Log.d("CallActivity", "RESTORE_CALL — restoring active call UI")
                _isRinging.value   = false
                _isConnected.value = true
                callWasAnswered    = true
                if (callConnectedAt == 0L) callConnectedAt = System.currentTimeMillis()
                audioManager.mode  = AudioManager.MODE_IN_COMMUNICATION
            } else {
                Log.d("CallActivity", "RESTORE_CALL — no active call, finishing")
                finish()
                return
            }
        }

        // AUTO_ANSWER: opened from notification Answer button while app was closed.
        if (autoAnswer && incoming) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                Log.d("CallActivity", "AUTO_ANSWER mode — answering call")
                SipModule.udpManager?.answerCall()
            }, 500)
        }



        // Outgoing call log saved once on hangup in onHangUp lambda.

        setContent {
            Calling_appTheme {
                val shouldFinish by _shouldFinish

                LaunchedEffect(shouldFinish) {
                    if (shouldFinish) {
                        releaseAudioFocus()
                        finishAndRemoveTask()
                    }
                }

                val isConnected  by _isConnected
                val isRinging    by _isRinging
                val isBusy       by _isBusy

                var isMuted       by remember { mutableStateOf(false) }
                var isSpeakerOn   by remember { mutableStateOf(false) }
                var isHeld        by remember { mutableStateOf(false) }
                var isDialpadOpen by remember { mutableStateOf(false) }

                CallScreen(
                    displayName   = displayName,
                    rawNumber     = number,
                    isIncoming    = incoming,
                    isConnected   = isConnected,
                    isRinging     = isRinging,
                    isBusy        = isBusy,
                    isMuted       = isMuted,
                    isSpeakerOn   = isSpeakerOn,
                    isHeld        = isHeld,
                    isDialpadOpen = isDialpadOpen,
                    callConnectedAt    = callConnectedAt,
                    dynamicConnectedAt = dynamicConnectedAt.value,

                    // Inside CallActivity.kt -> Locate your onHangUp lambda block and apply this update:

                    // Inside CallActivity.kt -> onHangUp assignment block:

                    onHangUp = {
                        if (hangupInitiatedByUs || isAlreadyClosing) return@CallScreen
                        hangupInitiatedByUs = true

                        // ✅ THE TIME LOCK FIX: Inform UdpSipManager of the exact timestamp of this hangup
                        // This engages the 2000ms guard window to block any racing packets
                        UdpSipManager.hangupTimestamp = System.currentTimeMillis()

                        // Clear the system notification bar immediately
                        com.callingapp.calling_app.sip.SipForegroundService.cancelIncomingCallNotification(this@CallActivity)

                        // Write call log entries BEFORE calling hangUp()
                        when {
                            // You cancelled an outgoing call before remote answered
                            !incoming && !callWasAnswered -> CallLog.save(
                                CallEntry(
                                    number    = number.trim(),
                                    name      = callerName.ifBlank { number.trim() },
                                    type      = CallType.OUTGOING,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                            // You declined = INCOMING (you actively acknowledged and rejected).
                            incoming && !callWasAnswered -> {
                                // ── 📊 LOCK REJECT STATE TO BLOCK DUPLICATE CANCEL ENTRIES ──
                                SipModule.udpManager?.receiverDeclined = true // 👈 ADD THIS LINE HERE

                                CallLog.save(
                                    CallEntry(
                                        number    = number.trim(),
                                        name      = callerName.ifBlank { number.trim() },
                                        type      = CallType.INCOMING,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            }
                            // Call was answered and talked — save with duration
                            // ── Locate inside onHangUp lambda inside CallActivity.kt ──
                            // Call was answered and talked — save with duration
                            // Inside onHangUp block in CallActivity.kt
                            callWasAnswered && callConnectedAt > 0 -> {
                                // ✅ TRUE VOIP DELTA: Measures absolute runtime distance from start to final finish
                                val durationSeconds = ((System.currentTimeMillis() - callConnectedAt) / 1000).toInt()
                                val callType = if (incoming) CallType.INCOMING else CallType.OUTGOING
                                CallLog.save(
                                    CallEntry(
                                        number          = number.trim(),
                                        name            = callerName.ifBlank { number.trim() },
                                        type            = callType,
                                        timestamp       = callConnectedAt,
                                        durationSeconds = durationSeconds // Saves complete cumulative sum (e.g. 280 seconds = 4m 40s)
                                    )
                                )
                            }
                        }

                        // Signals the background worker to transmit 487 Request Terminated/BYE signals
                        SipModule.udpManager?.hangUp()
                    },

                    onAnswer = {
                        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                        audioManager.isSpeakerphoneOn = false
                        acquireProximityWakeLock()
                        SipModule.udpManager?.answerCall()
                    },

                    onMuteClick = {
                        isMuted = !isMuted
                        SipModule.rtpEngine?.setMuted(isMuted)
                        audioManager.isMicrophoneMute = isMuted
                    },
                    onSpeakerClick = {
                        isSpeakerOn = !isSpeakerOn
                        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = isSpeakerOn

                        // ✅ Toggle proximity sensor: Disable on speaker, Enable on earpiece
                        if (isSpeakerOn) {
                            releaseProximityWakeLock()
                        } else if (isConnected) {
                            acquireProximityWakeLock()
                        }
                    },
                    onHoldClick = {
                        isHeld = !isHeld
                        // ✅ HOLD FIX: Both steps required:
                        // 1. toggleHold() sends re-INVITE with a=sendonly/sendrecv to JiPBX
                        // 2. setOutputPaused() stops playing incoming audio (so we hear silence on hold)
                        // 3. setMuted() stops sending our mic audio to remote
                        SipModule.udpManager?.toggleHold()
                        SipModule.rtpEngine?.setOutputPaused(isHeld)
                        SipModule.rtpEngine?.setMuted(isHeld)
                    },
                    onDialpadToggle = { isDialpadOpen = !isDialpadOpen }
                )
            }
        }
    }

    // Inside CallActivity.kt -> Add this method below onCreate():

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val autoAnswer = intent.getBooleanExtra("AUTO_ANSWER", false)
        val isIncomingNew = intent.getBooleanExtra("IS_INCOMING", false)
        number     = intent.getStringExtra("NUMBER")      ?: number
        callerName = intent.getStringExtra("CALLER_NAME") ?: callerName

        if (autoAnswer && isIncomingNew) {
            // Activity already exists (app was backgrounded) - answer now
            com.callingapp.calling_app.sip.SipModule.udpManager?.answerCall()
            _isConnected.value = true
            _isRinging.value   = false
            callWasAnswered    = true
            callConnectedAt    = System.currentTimeMillis()
            audioManager.mode  = AudioManager.MODE_IN_COMMUNICATION
            com.callingapp.calling_app.sip.SipForegroundService.cancelIncomingCallNotification(this)
            Log.d("CallActivity", "onNewIntent AUTO_ANSWER: answered call for $number")
        }
    }

    // ✅ ONE single place that decides to close this activity.
    //    Called from: signalReceiver ACTION_HANGUP_UI
    private fun triggerClose() {
        if (isAlreadyClosing) return
        isAlreadyClosing = true
        releaseProximityWakeLock()

        // Write remote-hangup call log only if WE didn't initiate
        if (!hangupInitiatedByUs) {
            when {
                // Remote cancelled/rejected our outgoing call before answering
                !incoming && !callWasAnswered -> CallLog.save(
                    CallEntry(
                        number    = number.trim(),
                        name      = callerName.ifBlank { number.trim() },
                        type      = CallType.OUTGOING,
                        timestamp = System.currentTimeMillis()
                    )
                )
                // Incoming call cancelled by remote before we answered.
                // MISSED already saved in UdpSipManager CANCEL handler.
                // Do not save again here to avoid duplicates.
                incoming && !callWasAnswered -> { /* saved in UdpSipManager as MISSED */ }
                // Call was answered — save with duration
                callWasAnswered && callConnectedAt > 0 -> {
                    val dur = ((System.currentTimeMillis() - callConnectedAt) / 1000).toInt()
                    val callType = if (incoming) CallType.INCOMING else CallType.OUTGOING
                    CallLog.save(
                        CallEntry(
                            number          = number.trim(),
                            name            = callerName.ifBlank { number.trim() },
                            type            = callType,
                            timestamp       = callConnectedAt,
                            durationSeconds = dur
                        )
                    )
                }
            }
        }

        _shouldFinish.value = true
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false // 👈 Default strictly to earpiece
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
    }

    override fun onDestroy() {
        releaseProximityWakeLock()
        super.onDestroy()
        // ✅ Clear the system bar notification context on destruction as well
        com.callingapp.calling_app.sip.SipForegroundService.cancelIncomingCallNotification(this)

        UdpSipManager.isCallScreenOpen     = false
        UdpSipManager.activeIncomingCallId = null
        // NOTE: Do NOT clear hangupTimestamp here.
        // The 2000ms cooldown in UdpSipManager blocks PBX INVITE retransmissions
        // that arrive during the network round-trip after BYE/CANCEL.
        // Clearing it in onDestroy removes this protection too early.
        // hangupTimestamp expires naturally after 2000ms.
        try { unregisterReceiver(signalReceiver) } catch (_: Exception) {}
        releaseAudioFocus()
    }

    private fun acquireProximityWakeLock() {
        if (proximityWakeLock != null && proximityWakeLock?.isHeld == true) return
        try {
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            val wakeLockType = PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK

            proximityWakeLock = powerManager.newWakeLock(wakeLockType, "CNETVOIP::ProximityLock").apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d("CallActivity", "✅ Proximity sensor WakeLock actively ACQUIRED")
        } catch (e: Exception) {
            Log.e("CallActivity", "Failed to acquire proximity lock: ${e.message}")
        }
    }

    private fun releaseProximityWakeLock() {
        try {
            if (proximityWakeLock?.isHeld == true) {
                proximityWakeLock?.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
                Log.d("CallActivity", "Proximity sensor WakeLock RELEASED")
            }
        } catch (e: Exception) {
            Log.e("CallActivity", "Error releasing proximity lock: ${e.message}")
        }
        proximityWakeLock = null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CallScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CallScreen(
    displayName: String,
    rawNumber: String = displayName,
    isIncoming: Boolean,
    isConnected: Boolean,
    isRinging: Boolean,
    isBusy: Boolean = false,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isHeld: Boolean,
    isDialpadOpen: Boolean,
    callConnectedAt: Long,
    dynamicConnectedAt: Long,
    onHangUp: () -> Unit,
    onAnswer: () -> Unit,
    onMuteClick: () -> Unit,
    onSpeakerClick: () -> Unit,
    onHoldClick: () -> Unit,
    onDialpadToggle: () -> Unit
) {
    var ringSeconds      by remember { mutableIntStateOf(0) }
    var connectedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(isRinging) {
        if (isRinging) { ringSeconds = 0; while (true) {
            delay(1000); ringSeconds++ } }
    }
    // Find this block inside CallScreen and update it:
    LaunchedEffect(isConnected, dynamicConnectedAt) {
        if (isConnected) {
            while (true) {
                // ✅ Reads the long parameters passed into the function safely
                val anchorTime = if (dynamicConnectedAt > 0L) dynamicConnectedAt else callConnectedAt
                if (anchorTime > 0L) {
                    val elapsedMillis = System.currentTimeMillis() - anchorTime
                    connectedSeconds = (elapsedMillis / 1000).toInt()
                } else {
                    connectedSeconds++
                }
                delay(1000)
            }
        }
    }

    fun fmt(s: Int) = "%02d:%02d".format(s / 60, s % 60)

    var answeredLocally by remember { mutableStateOf(!isIncoming) }
    val showControls = answeredLocally || isConnected

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.12f, label = "ring",
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse)
    )
    val avatarScale = if (!isConnected) ringScale else 1f

    val statusColor by animateColorAsState(
        targetValue = when {
            isBusy      -> Color(0xFFEF4444) // Red for busy
            isHeld      -> Color(0xFFF59E0B) // Amber
            isConnected -> Color(0xFF14015E) // Green
            isRinging   -> Color(0xFF3B82F6) // Blue
            else        -> Color(0xFF0F172A) // Dark
        }, label = "status"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White) // ✅ Change to solid White
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                Box(contentAlignment = Alignment.Center) {
                    if (!isConnected) {
                        Surface(
                            modifier = Modifier.size(148.dp).scale(avatarScale),
                            shape    = CircleShape,
                            color    = Color(0xFF14015E).copy(alpha = 0.1f) // Light green pulse
                        ) {}
                    }
                    // ✅ Update Avatar Surface
                    Surface(modifier = Modifier.size(120.dp), shape = CircleShape, color = Color(0xFFF1F5F9)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = displayName.firstOrNull()?.toString()?.uppercase() ?: "?",
                                color = Color(0xFF0F172A), fontSize = 44.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                // ✅ Update Header Text Colors
                Text(displayName, fontSize = 28.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                if (displayName != rawNumber) {
                    Text("$rawNumber", fontSize = 14.sp, color = Color(0xFF0F172A).copy(alpha = 0.6f))
                }
                // Inside CallActivity.kt -> Verify the top status badge indicator design inside CallScreen:

                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = statusColor.copy(alpha = 0.1f)) {
                    Text(
                        text = when {
                            isBusy      -> "\uD83D\uDD34  Line Busy"
                            isHeld      -> "⏸  On Hold"
                            isConnected -> "●  Connected  ${fmt(connectedSeconds)}"
                            isRinging   -> "↗  Ringing…" // 👈 Shows clean Ringing with no seconds counter
                            isIncoming  -> "↙  Incoming call…"
                            else        -> "↗  Ringing…"
                        },
                        color = statusColor, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            if (showControls) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ControlButton(
                        icon     = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label    = if (isMuted) "Unmute" else "Mute",
                        isActive = isMuted, onClick = onMuteClick
                    )
                    ControlButton(
                        icon     = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                        label    = if (isSpeakerOn) "Speaker" else "Earpiece",
                        isActive = isSpeakerOn, onClick = onSpeakerClick
                    )
                    ControlButton(
                        icon        = Icons.Default.Pause,
                        label       = if (isHeld) "Resume" else "Hold",
                        isActive    = isHeld, activeColor = Color(0xFFF59E0B),
                        onClick     = onHoldClick
                    )
                    if (isConnected) {
                        ControlButton(
                            icon     = Icons.Default.Dialpad,
                            label    = "Keypad",
                            isActive = isDialpadOpen,
                            onClick  = onDialpadToggle
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = if (isIncoming && !answeredLocally && !isConnected)
                    Arrangement.SpaceEvenly else Arrangement.Center
            ) {
                if (isIncoming && !answeredLocally && !isConnected) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick  = { answeredLocally = true; onAnswer() },
                            modifier = Modifier.size(76.dp), shape = CircleShape,
                            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF14015E))
                        ) {
                            Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Answer", color = Color(0xFF14015E), fontSize = 13.sp)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick  = onHangUp,
                        modifier = Modifier.size(76.dp), shape = CircleShape,
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = if (isIncoming && !answeredLocally && !isConnected) "Decline" else "End Call",
                        color = Color(0xFFEF4444), fontSize = 13.sp
                    )
                }
            }
        }

        // ── DTMF Dialpad OVERLAY — shown on top of call screen when Keypad is tapped ──
        // Fixed: dialpad now fills the screen as an overlay so it's always visible
        // and includes its own hang-up button.
        if (isDialpadOpen && isConnected) {
            var dialpadInput by remember { mutableStateOf("") }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onDialpadToggle) {
                                Icon(Icons.Default.KeyboardArrowDown, "Close keypad",
                                    tint = Color(0xFF0F172A), modifier = Modifier.size(28.dp))
                            }
                            Text("Keypad", color = Color(0xFF0F172A), fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.size(48.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        // Digits typed display
                        Text(
                            text      = dialpadInput.ifEmpty { " " },
                            fontSize  = 36.sp,
                            color     = Color(0xFF0F172A),
                            fontWeight = FontWeight.Light,
                            letterSpacing = 4.sp
                        )
                    }

                    // Dial keys
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        listOf(
                            listOf("1","2","3"),
                            listOf("4","5","6"),
                            listOf("7","8","9"),
                            listOf("*","0","#")
                        ).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                row.forEach { key ->
                                    Surface(
                                        onClick = {
                                            dialpadInput += key
                                            // ✅ DTMF: send tone over SIP INFO
                                            SipModule.udpManager?.sendDtmf(key[0])
                                        },
                                        shape    = CircleShape,
                                        color    = Color(0xFFF1F5F9),
                                        modifier = Modifier.size(72.dp),
                                        border   = BorderStroke(1.dp, Color(0xFF0F172A).copy(alpha = 0.1f))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(key, color = Color(0xFF0F172A), fontSize = 28.sp,
                                                fontWeight = FontWeight.Light)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom row: Clear + Hang up
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Clear typed digits
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                onClick = { dialpadInput = "" },
                                shape   = CircleShape,
                                color   = Color(0xFFF1F5F9),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Backspace, "Clear",
                                        tint = Color(0xFF0F172A), modifier = Modifier.size(24.dp))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Clear", color = Color(0xFF0F172A).copy(alpha = 0.6f), fontSize = 11.sp)
                        }

                        // Hang up button inside dialpad
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(
                                onClick  = onHangUp,
                                modifier = Modifier.size(72.dp),
                                shape    = CircleShape,
                                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                            ) {
                                Icon(Icons.Default.CallEnd, null, tint = Color.White,
                                    modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("End Call", color = Color(0xFFEF4444), fontSize = 11.sp)
                        }

                        // Close keypad (go back to call screen)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                onClick  = onDialpadToggle,
                                shape    = CircleShape,
                                color    = Color(0xFFF1F5F9),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Phone, "Back",
                                        tint = Color(0xFF14015E), modifier = Modifier.size(24.dp))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Hide", color = Color(0xFF0F172A).copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color = Color(0xFF0F172A), // Default to dark when active
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) activeColor.copy(alpha = 0.1f) else Color(0xFFF1F5F9),
        label = "bg"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isActive) activeColor else Color(0xFF0F172A),
        label = "icon"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, shape = CircleShape, color = bgColor, modifier = Modifier.size(60.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color(0xFF0F172A).copy(alpha = 0.7f), fontSize = 11.sp)
    }
}
@Composable
fun IncomingCallScreen(extension: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White), // ✅ Change to White
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = extension,
            fontSize = 64.sp,
            color = Color(0xFF0F172A), // ✅ Change to Dark Primary
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Incoming Call",
            color = Color(0xFF0F172A).copy(alpha = 0.6f), // ✅ Change to Dark Secondary
            fontSize = 18.sp
        )
    }
}
