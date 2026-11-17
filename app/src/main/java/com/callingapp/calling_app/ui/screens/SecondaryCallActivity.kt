package com.callingapp.calling_app.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.callingapp.calling_app.sip.SipModule
import com.callingapp.calling_app.sip.UdpSipManager
import com.callingapp.calling_app.ui.theme.Calling_appTheme
import kotlinx.coroutines.delay
import com.callingapp.calling_app.utils.CallEntry
import com.callingapp.calling_app.utils.CallLog
import com.callingapp.calling_app.utils.CallType

class SecondaryCallActivity : ComponentActivity() {

    private var number = "701"
    private var callerName = ""
    private var secondaryConnectTime = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "ACTION_SECONDARY_HANGUP" -> {
                    Log.d("SecondaryCall", "ACTION_SECONDARY_HANGUP: closing, 501 restored.")
                    finishAndRemoveTask()
                }
                "ACTION_HANGUP_UI" -> {
                    Log.d("SecondaryCall", "ACTION_HANGUP_UI: 701 disconnected.")
                    finishAndRemoveTask()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        number = intent.getStringExtra("NUMBER") ?: "701"
        callerName = intent.getStringExtra("CALLER_NAME") ?: ""

        val filter = IntentFilter().apply {
            addAction("ACTION_HANGUP_UI")
            addAction("ACTION_CALL_BUSY")
            addAction("ACTION_SECONDARY_HANGUP")
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent {
            Calling_appTheme {
                var isMuted     by remember { mutableStateOf(false) }
                var isSpeakerOn by remember { mutableStateOf(false) }

                if (secondaryConnectTime == 0L) {
                    secondaryConnectTime = System.currentTimeMillis()
                }
                var secondaryConnectedSeconds by remember { mutableIntStateOf(0) }

                LaunchedEffect(Unit) {
                    while (true) {
                        val elapsedSeconds = (System.currentTimeMillis() - secondaryConnectTime) / 1000
                        secondaryConnectedSeconds = elapsedSeconds.toInt()
                        delay(1000)
                    }
                }

                CallScreen(
                    displayName   = callerName.ifBlank { "$number" },
                    rawNumber     = number,
                    isIncoming    = true,
                    isConnected   = true,
                    isRinging     = false,
                    isMuted       = isMuted,
                    isSpeakerOn   = isSpeakerOn,
                    isHeld        = false,
                    isDialpadOpen = false,
                    callConnectedAt    = secondaryConnectTime,
                    dynamicConnectedAt = secondaryConnectTime,
                    onHangUp = {
                        UdpSipManager.hangupTimestamp = System.currentTimeMillis()
                        // ✅ SIMPLIFIED: Let onDestroy handle database storage safely for both paths
                        SipModule.udpManager?.hangUp()
                    },
                    onAnswer = {},
                    onMuteClick = {
                        isMuted = !isMuted
                        SipModule.rtpEngine?.setMuted(isMuted)
                    },
                    onSpeakerClick = {
                        isSpeakerOn = !isSpeakerOn
                        val am = getSystemService(AUDIO_SERVICE) as AudioManager
                        am.isSpeakerphoneOn = isSpeakerOn
                    },
                    onHoldClick = {},
                    onDialpadToggle = {}
                )
            }
        }
    }

    override fun onDestroy() {
        // ── 📊 ✅ DUAL-SIDE GUARANTEED LOGGING MATRIX ──
        // Executed immediately whenever the window context clears, regardless of hangup path
        try {
            if (secondaryConnectTime > 0L) {
                val totalTalkedSeconds = ((System.currentTimeMillis() - secondaryConnectTime) / 1000).toInt()

                // Fetch dynamic extensions safely. Fall back to current cached memory context if trunk was reset
                val activeExtension = SipModule.udpManager?.myExtension
                    ?: intent.getStringExtra("MY_EXTENSION")
                    ?: "unknown"

                if (totalTalkedSeconds > 0) {
                    CallLog.init(this@SecondaryCallActivity, activeExtension)
                    CallLog.save(
                        CallEntry(
                            number          = number.trim(),
                            name            = callerName.ifBlank { "Ext ${number.trim()}" },
                            type            = CallType.INCOMING,
                            timestamp       = secondaryConnectTime,
                            durationSeconds = totalTalkedSeconds
                        )
                    )
                    Log.d("SecondaryCall", "History recorded dynamically: $totalTalkedSeconds seconds.")
                }
            }
        } catch (e: Exception) {
            Log.e("SecondaryCall", "Background log compiling failed: ${e.message}")
        }

        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
    }
}