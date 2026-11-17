package com.callingapp.calling_app.sip

import android.content.Context
import android.util.Log
import com.callingapp.calling_app.sip.RtpAudioEngine

object SipModule {
    var udpManager: UdpSipManager? = null
    var rtpEngine: RtpAudioEngine? = null

    // Current SIP registration state ("Online" / "Offline") + reason, for logs and UI
    @Volatile
    var registrationState: String = "Offline"
        private set

    @Volatile
    var registrationError: String = ""
        private set

    fun updateRegistrationState(state: String, error: String = "") {
        registrationState = state
        registrationError = error
        android.util.Log.d(
            "SipModule",
            "Registration state: $state${if (error.isNotBlank()) " - $error" else ""}"
        )
    }
    var appContext: Context? = null

    fun startAudio(remoteRtpIp: String, remoteRtpPort: Int, localRtpPort: Int) {
        rtpEngine?.stop()
        val ctx = appContext ?: return
        Log.d("SipModule", "startAudio remote=$remoteRtpIp:$remoteRtpPort  local=:$localRtpPort")

        val prebound = udpManager?.takePendingRtpSocket()
        rtpEngine = RtpAudioEngine(ctx)
        rtpEngine?.start(
            remoteIp = remoteRtpIp,
            remotePort = remoteRtpPort,
            localPort = localRtpPort,
            prebound = prebound
        )
    }

    fun stopAudio() {
        rtpEngine?.stop()
        rtpEngine = null
    }
}
