package com.callingapp.calling_app.sip

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import com.callingapp.calling_app.ui.screens.CallActivity
import com.callingapp.calling_app.ui.screens.SecondaryCallActivity
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * SIP over UDP.
 *
 * Main fixes compared to the old version:
 *  1. Contact = PUBLIC ip:port (learned from the PBX's received=/rport=), not 10.x.x.x.
 *     Before, the PBX stored <sip:1001501@10.5.5.45:54497> – a private address it cannot
 *     reach – so calls TO the app failed and the extension flickered offline.
 *  2. One socket, one listener, one set of timers. Opening the app no longer creates
 *     extra sockets/timers (that made the registration jump between ports).
 *  3. Every SIP message gets the correct answer / ACK (UDP needs this, TCP did not).
 *  4. Short numbers ("502") are sent as full extensions ("1001502").
 *  5. Logout sends a real un-REGISTER (Contact: *) and waits for the PBX to confirm.
 */
class UdpSipManager(
    context: Context,
    val serverIp: String,
    private val port: Int = 5060,
    private val password: String
) {
    private val T = "SIP"
    private val appContext = context.applicationContext
    @Volatile private var socket: DatagramSocket? = null
    private var regTimer: Timer? = null
    private var keepAliveTimer: Timer? = null

    // ── Registration state ────────────────────────────────────────────────────
    @Volatile private var myExt = ""
    private var registrationCallId = UUID.randomUUID().toString()
    private val regCSeq = AtomicInteger(1)
    private val stableRegTag = "reg${System.currentTimeMillis()}"
    @Volatile var isRegistered = false
        private set
    @Volatile private var isStopped = false
    @Volatile private var awaitingRegisterReply = false
    @Volatile private var lastRegisterSentAt = 0L
    @Volatile private var lastRegisterOkAt = 0L
    @Volatile private var lastRegisterAuthCSeq = -1
    @Volatile private var lastSentContact = ""
    @Volatile private var missedRegisterReplies = 0
    @Volatile private var lastTickAt = 0L
    @Volatile private var isUnregistering = false
    @Volatile private var unregAuthSent = false
    @Volatile private var unregLatch: CountDownLatch? = null
    private var unregExtension = ""

    // ── NAT: public address the PBX sees us on ────────────────────────────────
    @Volatile private var publicIp: String = ""
    @Volatile private var publicPort: Int = 0
    @Volatile private var serverAddr: InetAddress? = null

    // ── Call state ────────────────────────────────────────────────────────────
    var currentCallId: String? = null
        private set
    val hasPendingInvite: Boolean get() = lastInviteMessage != null
    private var remoteTag: String? = null
    private var localTag = "as${System.currentTimeMillis()}"
    private var lastInviteCSeq: Int = 1
    var isCallActive = false
    private var incomingRingStartTime = 0L
    private var isIncoming = false
    var lastTarget     = ""
    var lastCallerName = ""
    @Volatile var receiverDeclined = false
    val callIsActive: Boolean    get() = isCallActive
    val callIsIncoming: Boolean  get() = isIncoming
    val callConnectedTimestamp: Long get() = _callConnectedAt
    val myExtension: String      get() = myExt
    @Volatile private var isOutgoingCallPending = false

    // Short-number fallback: "501" fails on the PBX → retry as "1001501"
    @Volatile private var fullRetryDone = false
    @Volatile private var retryInProgress = false
    @Volatile private var gotProvisional = false

    // ── UDP reliability ───────────────────────────────────────────────────────
    @Volatile private var lastInviteResponse: String? = null   // last 180/200 we sent for an incoming INVITE
    @Volatile private var ackReceivedForCallId: String? = null
    @Volatile private var lastInviteAuthKey = ""                // "callId:cseq" of the 401 already answered
    @Volatile private var lastAcked2xxKey = ""                  // "callId:cseq" of the 200 OK already handled

    // ── Call Waiting ──────────────────────────────────────────────────────────
    @Volatile var callWaitingActive     = false
    var waitingCallerNumber             = ""
    var waitingCallerName               = ""
    var waitingCallId                   = ""
    private var waitingInviteMsg        = ""
    private var waitingLocalTag         = ""
    private var lastInviteMessage: String? = null
    private var isOnHold = false
    private var _callConnectedAt: Long = 0L
    private var heldCallId              = ""
    private var heldDialogFrom          = ""
    private var heldDialogTo            = ""
    private var heldContactUri          = ""
    private var heldRtpIp               = ""
    private var heldRtpPort             = 10000
    private var heldLocalPort           = 4000
    private var heldCSeq                = 1
    private var heldConnectedAt         = 0L
    private var heldHoldStartTime       = 0L

    // Single-thread executor for DTMF: digits arrive in order (needed for *3<ext>)
    private val dtmfExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var callWaitingBeepTimer: Timer? = null
    private var ringbackTone: android.media.ToneGenerator? = null

    // ── RTP coordinates ───────────────────────────────────────────────────────
    private var remoteRtpIp: String = serverIp
    private var remoteRtpPort: Int = 10000
    private var localRtpPort: Int = 4000

    private var currentInviteBranch: String = ""
    private var lastInviteVia: String = ""
    private var lastInviteFrom: String = ""
    private var lastInviteTo: String = ""
    private var lastInviteCSeqHeader: String = ""
    private var remoteContactUri: String = ""
    private var dialogFrom: String = ""
    private var dialogTo:   String = ""

    val deviceModel: String = run {
        val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = android.os.Build.MODEL
        if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model".trim()
    }

    companion object {
        @Volatile var activeIncomingCallId: String? = null
        @Volatile var hangupTimestamp: Long = 0L
        const val HANGUP_COOLDOWN_MS = 2000L
        @Volatile var isCallScreenOpen = false
        val terminatedCallIds: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf<String>())

        private const val REGISTER_EXPIRES_S = 60        // Wi-Fi off → PBX shows offline within 60 s
        private const val REREGISTER_EVERY_MS = 25_000L  // refresh 2× per minute → never expires while online
        private const val REGISTER_REPLY_TIMEOUT_MS = 10_000L
    }

    private fun broadcast(action: String) {
        appContext.sendBroadcast(Intent(action).setPackage(appContext.packageName))
    }

    private fun freshBranch() = "z9hG4bK${System.currentTimeMillis()}${(Math.random() * 9999).toInt()}"

    /** Header value by name (case-insensitive), first occurrence. */
    private fun hdr(msg: String, name: String): String {
        val head = msg.substringBefore("\r\n\r\n")
        for (line in head.split("\r\n").drop(1)) {
            val i = line.indexOf(':')
            if (i <= 0) continue
            if (line.substring(0, i).trim().equals(name, ignoreCase = true)) return line.substring(i + 1).trim()
        }
        return ""
    }

    private fun cseqNumber(msg: String): Int = hdr(msg, "CSeq").substringBefore(" ").trim().toIntOrNull() ?: 0

    // ─────────────────────────────────────────────────────────────────────────
    // Tones
    // ─────────────────────────────────────────────────────────────────────────
    private fun startRingbackTone() {
        stopRingbackTone()
        try {
            ringbackTone = android.media.ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70).apply {
                startTone(android.media.ToneGenerator.TONE_SUP_RINGTONE)
            }
        } catch (e: Exception) {
            Log.e(T, "Error starting ringback tone: ${e.message}")
        }
    }

    private fun stopRingbackTone() {
        try { ringbackTone?.stopTone(); ringbackTone?.release() } catch (_: Exception) {}
        ringbackTone = null
    }

    private fun startCallWaitingBeep() {
        stopCallWaitingBeep()
        callWaitingBeepTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        val toneGen = android.media.ToneGenerator(AudioManager.STREAM_VOICE_CALL, 60)
                        toneGen.startTone(android.media.ToneGenerator.TONE_SUP_CALL_WAITING, 150)
                    } catch (e: Exception) {
                        Log.e(T, "Error playing call waiting beep: ${e.message}")
                    }
                }
            }, 0, 3500)
        }
    }

    private fun stopCallWaitingBeep() {
        callWaitingBeepTimer?.cancel()
        callWaitingBeepTimer = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Extension number helpers ("502" ↔ "1001502")
    // ─────────────────────────────────────────────────────────────────────────
    private val tenantPrefix: String get() = if (myExt.length > 4) myExt.take(4) else ""

    fun toFullExt(target: String): String {
        val p = tenantPrefix
        val t = target.trim()
        if (p.isBlank() || t.isBlank()) return t
        if (t.length == 1 && t[0].isDigit()) return t                          // speed dial 0-9
        if (t.startsWith("*") || t.startsWith("#")) return t                   // feature codes
        if (t.startsWith("+") || t.startsWith("0") || t.length >= 10) return t // external numbers
        if (t.startsWith(p)) return t                                          // already 1001xxx
        if (t.length in 2..5 && t.all { it.isDigit() }) return "$p$t"          // 502 → 1001502
        return t
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Registration
    // ─────────────────────────────────────────────────────────────────────────
    @Synchronized
    fun startRegistration(extension: String) {
        val sock = socket
        if (extension == myExt && sock != null && !sock.isClosed && regTimer != null && !isStopped) {
            // ✅ App opened again → do NOT create a 2nd socket / 2nd timers
            Log.d(T, "startRegistration: already running for $extension → refresh only")
            sendRegister(extension)
            return
        }
        stopTimers()
        try { socket?.close() } catch (_: Exception) {}
        myExt = extension
        isStopped = false
        try {
            openSocket()
            regTimer = Timer("sip-reg").apply {
                schedule(object : TimerTask() {
                    override fun run() { sendRegister(myExt) }
                }, 0, REREGISTER_EVERY_MS)
            }
            keepAliveTimer = Timer("sip-keepalive").apply {
                schedule(object : TimerTask() {
                    override fun run() {
                        // This timer runs every 10 s. A bigger gap means Android/Vivo froze the app.
                        val now = System.currentTimeMillis()
                        if (lastTickAt > 0 && now - lastTickAt > 25_000) {
                            Log.e(T, "⚠ App was FROZEN by the phone for ${(now - lastTickAt) / 1000}s " +
                                "(screen off / battery saver) – PBX could not reach it")
                        }
                        lastTickAt = now
                        sendKeepAlive()
                        checkRegistrationHealth()
                    }
                }, 5_000, 10_000)
            }
            Log.i(T, "━━━ startRegistration $extension → $serverIp:$port (UDP) ━━━")
        } catch (e: Exception) {
            Log.e(T, "startRegistration failed: ${e.message}")
        }
    }

    private fun stopTimers() {
        regTimer?.cancel(); regTimer = null
        keepAliveTimer?.cancel(); keepAliveTimer = null
    }

    @Synchronized
    private fun openSocket() {
        val s = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress("0.0.0.0", 0))
        }
        socket = s
        publicIp = ""
        publicPort = 0
        listenLoop(s)
        Log.i(T, "UDP socket ready on local port ${s.localPort}")
    }

    @Synchronized
    private fun rebuildSocketAndRegister(reason: String) {
        if (isStopped || myExt.isBlank()) return
        if (isCallActive || currentCallId != null) {
            // Never swap the socket in the middle of a call – just retry REGISTER
            Log.w(T, "↻ $reason – call in progress, retrying REGISTER on same socket")
            sendRegister(myExt)
            return
        }
        Log.w(T, "↻ RECONNECT reason=$reason")
        try { socket?.close() } catch (_: Exception) {}
        serverAddr = null
        try {
            openSocket()
            sendRegister(myExt)
        } catch (e: Exception) {
            Log.e(T, "Socket rebuild failed (no network?): ${e.message}")
        }
    }

    /** Called by the 25-second keep-alive alarm (works in Doze). */
    fun forceReregister() {
        if (isStopped || myExt.isBlank()) return
        runOnThread {
            val s = socket
            if (s == null || s.isClosed) {
                rebuildSocketAndRegister("socket closed")
                return@runOnThread
            }
            sendKeepAlive()
            // Only re-REGISTER when needed (before: every 25 s → constant flapping)
            if (!isRegistered || System.currentTimeMillis() - lastRegisterOkAt > 30_000) {
                sendRegister(myExt)
            }
        }
    }

    fun forceRefresh() = forceReregister()

    /** Default network changed (Wi-Fi ⇄ mobile data, or network came back). */
    fun onNetworkChanged() {
        if (isStopped || myExt.isBlank()) return
        runOnThread { rebuildSocketAndRegister("network changed") }
    }

    /** No network at all (Wi-Fi off and data off). */
    fun onNetworkLost() {
        Log.w(T, "Network lost → extension OFFLINE (PBX marks it offline when its check fails / registration expires)")
        isRegistered = false
        awaitingRegisterReply = false
        SipModule.updateRegistrationState("Offline", "no network")
    }

    private fun checkRegistrationHealth() {
        if (isStopped || myExt.isBlank()) return
        val now = System.currentTimeMillis()
        if (awaitingRegisterReply && now - lastRegisterSentAt > REGISTER_REPLY_TIMEOUT_MS) {
            awaitingRegisterReply = false
            missedRegisterReplies++
            if (missedRegisterReplies < 3) {
                // One lost UDP packet is normal → resend on the SAME socket (same address for the PBX)
                Log.w(T, "No REGISTER reply ($missedRegisterReplies/3) → resending on same socket")
                sendRegister(myExt)
            } else {
                missedRegisterReplies = 0
                rebuildSocketAndRegister("PBX did not answer 3 REGISTERs in a row")
            }
        }
        if (isRegistered && lastRegisterOkAt > 0 && now - lastRegisterOkAt > REGISTER_EXPIRES_S * 1000L) {
            isRegistered = false
            SipModule.updateRegistrationState("Offline", "registration expired")
        }
    }

    // Contact host:port the PBX can actually reach (public ip:port when behind NAT)
    private fun contactHostPort(localIp: String): String {
        val ip = publicIp.ifBlank { localIp }
        val prt = if (publicPort > 0) publicPort else (socket?.localPort ?: 5060)
        return "$ip:$prt"
    }

    /** Learn our public address from the first Via of a response. Returns true if it changed. */
    private fun learnNat(msg: String): Boolean {
        val via = hdr(msg, "Via")
        val rcv = Regex("""received=([\d.]+)""").find(via)?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() && it != "0.0.0.0" } ?: return false
        val rp = Regex("""rport=(\d+)""").find(via)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
            ?: (socket?.localPort ?: 0)
        if (rcv == publicIp && rp == publicPort) return false
        publicIp = rcv
        publicPort = rp
        Log.i(T, "🌐 Public address learned: $rcv:$rp (local ${getLocalIp()}:${socket?.localPort})")
        return true
    }

    private fun sendKeepAlive() {
        runOnThread {
            try {
                val s = socket ?: return@runOnThread
                val addr = serverAddr ?: run { resolveServer(); serverAddr } ?: return@runOnThread
                val d = "\r\n\r\n".toByteArray()
                s.send(DatagramPacket(d, d.size, addr, port))
            } catch (_: Exception) {}
        }
    }

    private fun sendRegister(extension: String, authHeader: String = "") {
        if (extension.isBlank() || isStopped) return
        val cseq = regCSeq.getAndIncrement()
        if (authHeader.isNotEmpty()) lastRegisterAuthCSeq = cseq
        runOnThread {
            try {
                val sock = socket ?: return@runOnThread
                val localIp = getLocalIp()
                val uri = "sip:$extension@$serverIp"
                val contact = contactHostPort(localIp)
                lastSentContact = contact
                val msg = buildString {
                    append("REGISTER sip:$serverIp SIP/2.0\r\n")
                    append("Via: SIP/2.0/UDP $localIp:${sock.localPort};rport;branch=${freshBranch()}\r\n")
                    append("Max-Forwards: 70\r\n")
                    append("From: <$uri>;tag=$stableRegTag\r\n")
                    append("To: <$uri>\r\n")
                    append("Call-ID: $registrationCallId\r\n")
                    append("CSeq: $cseq REGISTER\r\n")
                    append("Contact: <sip:$extension@$contact;transport=udp>\r\n")
                    append("User-Agent: CNETVOIP 1.0 ($deviceModel)\r\n")
                    if (authHeader.isNotEmpty()) append(authHeader)
                    append("Expires: $REGISTER_EXPIRES_S\r\n")
                    append("Content-Length: 0\r\n\r\n")
                }
                awaitingRegisterReply = true
                lastRegisterSentAt = System.currentTimeMillis()
                sendRaw(msg)
            } catch (e: Exception) {
                Log.e(T, "sendRegister failed: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Outgoing call
    // ─────────────────────────────────────────────────────────────────────────
    fun makeCall(target: String, auth: String = "") {
        // "502" → "1001502": same PBX route as the full number (needed for *3 transfer too)
        // Send the number exactly as dialed (same as the old working version).
        // The PBX route for the short number ("502") has *3 transfer enabled.
        val dest = target.trim()
        lastTarget = dest
        isIncoming = false
        if (auth.isEmpty()) {
            isOutgoingCallPending = true
            if (!retryInProgress) fullRetryDone = false   // new call from the user
            retryInProgress = false
            gotProvisional = false
            startRingbackTone()
            broadcast("ACTION_REMOTE_RINGING")
            Log.i(T, "━━━ makeCall dialed='$target' sent='$dest' ━━━")

            // If the PBX doesn't start ringing within 6 s, retry with the full number
            Thread {
                Thread.sleep(400)                       // wait until currentCallId is set
                val thisCall = currentCallId
                try { Thread.sleep(5_600) } catch (_: Exception) { return@Thread }
                if (thisCall != null && currentCallId == thisCall && !gotProvisional && !isCallActive) {
                    retryWithFullNumber("no ringing within 6s")
                }
            }.apply { isDaemon = true }.start()
        }
        runOnThread {
            try {
                val cseq = if (auth.isEmpty()) 1 else 2
                lastInviteCSeq = cseq
                if (auth.isEmpty()) {
                    currentCallId = "${UUID.randomUUID()}@$serverIp"
                    localRtpPort = allocateFreeRtpPort()
                    localTag = "as${System.currentTimeMillis()}"
                }
                currentInviteBranch = freshBranch()
                val localIp = getLocalIp()
                val sdp = buildSdp(localRtpPort, localIp)
                val msg = buildString {
                    append("INVITE sip:$dest@$serverIp SIP/2.0\r\n")
                    append("Via: SIP/2.0/UDP $localIp:${socket?.localPort};rport;branch=$currentInviteBranch\r\n")
                    append("From: <sip:$myExt@$serverIp>;tag=$localTag\r\n")
                    append("To: <sip:$dest@$serverIp>\r\n")
                    append("Contact: <sip:$myExt@${contactHostPort(localIp)}>\r\n")
                    append("User-Agent: CNETVOIP 1.0 ($deviceModel)\r\n")
                    append("Call-ID: $currentCallId\r\n")
                    append("CSeq: $cseq INVITE\r\n")
                    append("Max-Forwards: 70\r\n")
                    if (auth.isNotEmpty()) append(auth)
                    append("Content-Type: application/sdp\r\n")
                    append("Content-Length: ${sdp.toByteArray().size}\r\n\r\n$sdp")
                }
                sendRaw(msg)
            } catch (e: Exception) {
                Log.e(T, "makeCall failed: ${e.message}")
            }
        }
    }

    /** Cancel the short-number INVITE and dial again with the tenant prefix ("501" → "1001501"). */
    private fun retryWithFullNumber(reason: String) {
        val full = toFullExt(lastTarget)
        if (fullRetryDone || full == lastTarget || isCallActive) return
        fullRetryDone = true
        Log.w(T, "↻ $reason for '$lastTarget' → retrying as '$full'")

        // CANCEL the first INVITE so the PBX doesn't keep it
        val oldCallId = currentCallId
        val oldBranch = currentInviteBranch
        val oldTag    = localTag
        val oldTarget = lastTarget
        val oldCSeq   = lastInviteCSeq
        if (oldCallId != null) {
            terminatedCallIds.add(oldCallId)
            val localIp = getLocalIp()
            sendRaw(buildString {
                append("CANCEL sip:$oldTarget@$serverIp SIP/2.0\r\n")
                append("Via: SIP/2.0/UDP $localIp:${socket?.localPort};rport;branch=$oldBranch\r\n")
                append("From: <sip:$myExt@$serverIp>;tag=$oldTag\r\n")
                append("To: <sip:$oldTarget@$serverIp>\r\n")
                append("Call-ID: $oldCallId\r\n")
                append("CSeq: $oldCSeq CANCEL\r\n")
                append("Max-Forwards: 70\r\n")
                append("Content-Length: 0\r\n\r\n")
            })
        }
        retryInProgress = true
        lastInviteAuthKey = ""
        makeCall(full)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Answer incoming call
    // ─────────────────────────────────────────────────────────────────────────
    fun answerCall() {
        runOnThread {
            try {
                val invite = lastInviteMessage ?: return@runOnThread
                isCallActive = true

                val via  = hdr(invite, "Via")
                val from = hdr(invite, "From")
                val to   = hdr(invite, "To").let { if (it.contains("tag=")) it else "$it;tag=$localTag" }
                val cseq = hdr(invite, "CSeq")

                dialogFrom = from
                dialogTo   = to
                localRtpPort = allocateFreeRtpPort()
                val localIp = getLocalIp()
                val sdp = buildSdp(localRtpPort, localIp)
                val ok = buildString {
                    append("SIP/2.0 200 OK\r\n")
                    append("Via: $via\r\n")
                    append("From: $from\r\n")
                    append("To: $to\r\n")
                    append("Call-ID: $currentCallId\r\n")
                    append("CSeq: $cseq\r\n")
                    append("Contact: <sip:$myExt@${contactHostPort(localIp)}>\r\n")
                    append("User-Agent: CNETVOIP 1.0 ($deviceModel)\r\n")
                    append("Content-Type: application/sdp\r\n")
                    append("Content-Length: ${sdp.toByteArray().size}\r\n\r\n$sdp")
                }
                ackReceivedForCallId = null
                sendRaw(ok)
                lastInviteResponse = ok
                startOkRetransmit(ok, currentCallId)   // UDP: repeat 200 OK until the PBX ACKs it

                _callConnectedAt = System.currentTimeMillis()
                val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                SipModule.startAudio(remoteRtpIp, remoteRtpPort, localRtpPort)
                broadcast("ACTION_CALL_CONNECTED")
                Log.i(T, "✓ Answered call $currentCallId")
            } catch (e: Exception) {
                Log.e(T, "answerCall failed: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hang up
    // ─────────────────────────────────────────────────────────────────────────
    fun hangUp() {
        SipModule.stopAudio()
        hangupTimestamp = System.currentTimeMillis()

        val savedCallId       = currentCallId
        val savedTarget       = lastTarget
        val savedRemoteTag    = remoteTag
        val savedLocalTag     = localTag
        val savedIsIncoming   = isIncoming
        val savedIsCallActive = isCallActive
        val savedInviteBranch = currentInviteBranch
        val savedVia          = lastInviteVia
        val savedFrom         = lastInviteFrom
        val savedTo           = lastInviteTo
        val savedCSeqHeader   = lastInviteCSeqHeader
        val savedContactUri   = remoteContactUri
        val savedDialogFrom   = dialogFrom
        val savedDialogTo     = dialogTo

        try { dtmfExecutor.submit {}.get() } catch (_: Exception) {}
        val savedCSeq = lastInviteCSeq

        if (!savedCallId.isNullOrBlank()) terminatedCallIds.add(savedCallId)

        try {
            SipForegroundService.cancelIncomingCallNotification(appContext)
            appContext.startService(Intent(appContext, SipForegroundService::class.java).apply { action = "STOP_RINGTONE" })
        } catch (e: Exception) {
            Log.e(T, "Stop ringtone failed: ${e.message}")
        }

        isCallActive = false

        if (heldCallId.isBlank()) {
            broadcast("ACTION_HANGUP_UI")
        } else {
            Log.d(T, "hangUp: Held call detected ($heldCallId). Resuming secondary.")
        }

        runOnThread {
            try {
                val localIp = getLocalIp()
                when {
                    savedIsIncoming && !savedIsCallActive -> {
                        sendRaw(buildString {
                            append("SIP/2.0 487 Request Terminated\r\n")
                            append("Via: $savedVia\r\n")
                            append("From: $savedFrom\r\n")
                            append("To: $savedTo;tag=$savedLocalTag\r\n")
                            append("Call-ID: $savedCallId\r\n")
                            append("CSeq: $savedCSeqHeader\r\n")
                            append("Content-Length: 0\r\n\r\n")
                        })
                        resetCall()
                    }
                    !savedIsCallActive -> {
                        sendRaw(buildString {
                            append("CANCEL sip:$savedTarget@$serverIp SIP/2.0\r\n")
                            append("Via: SIP/2.0/UDP $localIp:${socket?.localPort};rport;branch=$savedInviteBranch\r\n")
                            append("From: <sip:$myExt@$serverIp>;tag=$savedLocalTag\r\n")
                            append("To: <sip:$savedTarget@$serverIp>\r\n")
                            append("Call-ID: $savedCallId\r\n")
                            append("CSeq: $savedCSeq CANCEL\r\n")
                            append("Max-Forwards: 70\r\n")
                            append("Content-Length: 0\r\n\r\n")
                        })
                        resetCall()
                    }
                    else -> {
                        val isCurrentCallIncoming = if (heldCallId.isNotBlank()) true else savedIsIncoming
                        val byeFrom = if (isCurrentCallIncoming) {
                            savedDialogTo.ifBlank { "<sip:$myExt@$serverIp>;tag=$savedLocalTag" }
                        } else {
                            savedDialogFrom.ifBlank { "<sip:$myExt@$serverIp>;tag=$savedLocalTag" }
                        }
                        val fallbackTo = "<sip:$savedTarget@$serverIp>${if (savedRemoteTag != null) ";tag=$savedRemoteTag" else ""}"
                        val byeTo = if (isCurrentCallIncoming) savedDialogFrom.ifBlank { fallbackTo } else savedDialogTo.ifBlank { fallbackTo }
                        val requestUri = savedContactUri.ifBlank { "sip:$serverIp" }

                        sendRaw(buildString {
                            append("BYE $requestUri SIP/2.0\r\n")
                            append("Via: SIP/2.0/UDP $localIp:${socket?.localPort};rport;branch=${freshBranch()}\r\n")
                            append("From: $byeFrom\r\n")
                            append("To: $byeTo\r\n")
                            append("Call-ID: $savedCallId\r\n")
                            append("CSeq: ${savedCSeq + 1} BYE\r\n")
                            append("Max-Forwards: 70\r\n")
                            append("Content-Length: 0\r\n\r\n")
                        })

                        if (heldCallId.isNotBlank()) resumeHeldCall() else resetCall()
                    }
                }
            } catch (e: Exception) {
                Log.e(T, "hangUp SIP send failed: ${e.message}")
                resetCall()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hold / Resume
    // ─────────────────────────────────────────────────────────────────────────
    fun toggleHold() {
        if (!isCallActive) return
        isOnHold = !isOnHold
        if (isOnHold) {
            SipModule.rtpEngine?.setMuted(true)
            SipModule.rtpEngine?.setOutputPaused(true)
        }
        sendReInvite(onHold = isOnHold)
    }

    // ── Call Waiting: Answer (hold current, take new) ────────────────────────
    fun answerWaitingCall() {
        stopCallWaitingBeep()
        if (!callWaitingActive || waitingInviteMsg.isEmpty()) return

        com.callingapp.calling_app.utils.CallLog.init(appContext, myExt)
        com.callingapp.calling_app.utils.CallLog.save(
            com.callingapp.calling_app.utils.CallEntry(
                number    = waitingCallerNumber,
                name      = waitingCallerName.ifBlank { waitingCallerNumber },
                type      = com.callingapp.calling_app.utils.CallType.INCOMING,
                timestamp = System.currentTimeMillis()
            )
        )

        runOnThread {
            try {
                isOnHold = true
                SipModule.rtpEngine?.setMuted(true)
                SipModule.rtpEngine?.setOutputPaused(true)
                sendReInvite(onHold = true)
                Thread.sleep(300)

                val invite = waitingInviteMsg
                val via    = hdr(invite, "Via")
                val from   = hdr(invite, "From")
                val to     = hdr(invite, "To").let { if (it.contains("tag=")) it else "$it;tag=$waitingLocalTag" }
                val cseq   = hdr(invite, "CSeq")
                val localIp = getLocalIp()
                val newRtpPort = allocateFreeRtpPort()
                val sdp = buildSdp(newRtpPort, localIp)

                heldCallId     = currentCallId ?: ""
                heldDialogFrom = dialogFrom
                heldDialogTo   = dialogTo
                heldContactUri = remoteContactUri
                heldRtpIp      = remoteRtpIp
                heldRtpPort    = remoteRtpPort
                heldLocalPort  = localRtpPort
                heldCSeq       = lastInviteCSeq
                heldConnectedAt   = _callConnectedAt
                heldHoldStartTime = System.currentTimeMillis()

                currentCallId  = waitingCallId
                dialogFrom     = from
                dialogTo       = to
                localRtpPort   = newRtpPort
                lastInviteMessage = invite

                val ok = buildString {
                    append("SIP/2.0 200 OK\r\n")
                    append("Via: $via\r\n")
                    append("From: $from\r\n")
                    append("To: $to\r\n")
                    append("Call-ID: $waitingCallId\r\n")
                    append("CSeq: $cseq\r\n")
                    append("Contact: <sip:$myExt@${contactHostPort(localIp)}>\r\n")
                    append("Content-Type: application/sdp\r\n")
                    append("Content-Length: ${sdp.toByteArray().size}\r\n\r\n$sdp")
                }
                ackReceivedForCallId = null
                sendRaw(ok)
                lastInviteResponse = ok
                startOkRetransmit(ok, waitingCallId)

                parseSdp(invite, fromIp = serverIp)
                Thread.sleep(80)
                SipModule.stopAudio()
                val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                SipModule.startAudio(remoteRtpIp, remoteRtpPort, newRtpPort)

                lastTarget          = waitingCallerNumber
                lastCallerName      = waitingCallerName
                callWaitingActive   = false
                waitingInviteMsg    = ""
                SipForegroundService.cancelCallWaitingNotification(appContext)
                broadcast("ACTION_CALL_WAITING_ANSWERED")

                appContext.startActivity(Intent(appContext, SecondaryCallActivity::class.java).apply {
                    putExtra("NUMBER", waitingCallerNumber)
                    putExtra("CALLER_NAME", waitingCallerName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
                Log.d(T, "Call Waiting answered: $waitingCallerNumber")
            } catch (e: Exception) {
                Log.e(T, "answerWaitingCall failed: ${e.message}")
            }
        }
    }

    // ── Call Waiting: Resume Held Call ──
    fun resumeHeldCall() {
        if (heldCallId.isBlank()) {
            Log.w(T, "resumeHeldCall triggered but no background call is stored.")
            return
        }
        runOnThread {
            try {
                currentCallId       = heldCallId
                dialogFrom          = heldDialogFrom
                dialogTo            = heldDialogTo
                remoteContactUri    = heldContactUri
                remoteRtpIp         = heldRtpIp
                remoteRtpPort       = heldRtpPort
                localRtpPort        = heldLocalPort
                lastInviteCSeq      = heldCSeq

                isCallActive = true
                isOnHold     = false
                _callConnectedAt = heldConnectedAt

                heldCallId          = ""
                heldDialogFrom      = ""
                heldDialogTo        = ""
                heldContactUri      = ""
                heldRtpIp           = ""
                heldRtpPort         = 10000
                heldLocalPort       = 4000
                heldCSeq            = 1
                heldConnectedAt     = 0L
                heldHoldStartTime   = 0L

                val targetHeaderString = if (isIncoming) dialogFrom else dialogTo
                val restoredTarget = Regex("""sip:([^@>;]+)""").find(targetHeaderString)?.groupValues?.get(1)?.trim() ?: ""
                lastTarget     = if (restoredTarget.startsWith("1001") || restoredTarget.startsWith("1003")) restoredTarget.drop(4) else restoredTarget
                lastCallerName = lastTarget

                sendReInvite(onHold = false)

                appContext.sendBroadcast(Intent("ACTION_SWITCH_CALL_CONTEXT").apply {
                    setPackage(appContext.packageName)
                    putExtra("NUMBER",              lastTarget)
                    putExtra("CALLER_NAME",         lastCallerName)
                    putExtra("CONNECTED_TIMESTAMP", _callConnectedAt)
                })
                appContext.sendBroadcast(Intent("ACTION_SECONDARY_HANGUP").apply { setPackage(appContext.packageName) })
                Log.d(T, "Held call restored. Secondary closing.")
            } catch (e: Exception) {
                Log.e(T, "resumeHeldCall failed: ${e.message}")
            }
        }
    }

    // ── Call Waiting: Decline (keep current, reject new) ─────────────────────
    fun declineWaitingCall() {
        stopCallWaitingBeep()
        if (waitingCallerNumber.isNotBlank()) {
            com.callingapp.calling_app.utils.CallLog.init(appContext, myExt)
            com.callingapp.calling_app.utils.CallLog.save(
                com.callingapp.calling_app.utils.CallEntry(
                    number    = waitingCallerNumber,
                    name      = waitingCallerName.ifBlank { waitingCallerNumber },
                    type      = com.callingapp.calling_app.utils.CallType.MISSED,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        if (!callWaitingActive || waitingInviteMsg.isEmpty()) return
        runOnThread {
            try {
                sendRaw(simpleResponse(waitingInviteMsg, "486 Busy Here", waitingLocalTag))
                callWaitingActive   = false
                waitingInviteMsg    = ""
                waitingCallerNumber = ""
                waitingCallerName   = ""
                waitingCallId       = ""
                SipForegroundService.cancelCallWaitingNotification(appContext)
            } catch (e: Exception) {
                Log.e(T, "declineWaitingCall failed: ${e.message}")
            }
        }
    }

    private fun sendReInvite(onHold: Boolean) {
        if (!isCallActive) return
        runOnThread {
            try {
                lastInviteCSeq++
                val localIp = getLocalIp()
                val sdpDirection = if (onHold) "a=sendonly" else "a=sendrecv"
                val sdpIp = publicIp.ifBlank { localIp }
                val ts = System.currentTimeMillis()
                val sdp = buildString {
                    append("v=0\r\no=- $ts $ts IN IP4 $sdpIp\r\ns=Talk\r\nc=IN IP4 $sdpIp\r\nt=0 0\r\n")
                    append("m=audio $localRtpPort RTP/AVP 0 101\r\n")
                    append("a=rtpmap:0 PCMU/8000\r\na=rtpmap:101 telephone-event/8000\r\na=fmtp:101 0-15\r\n")
                    append("$sdpDirection\r\n")
                    append("a=ptime:20\r\n")
                }
                val sipFrom = if (isIncoming) dialogTo else dialogFrom
                val sipTo   = if (isIncoming) dialogFrom else dialogTo
                val requestUri = remoteContactUri.ifEmpty { "sip:$lastTarget@$serverIp" }

                sendRaw(buildString {
                    append("INVITE $requestUri SIP/2.0\r\n")
                    append("Via: SIP/2.0/UDP $localIp:${socket?.localPort};rport;branch=${freshBranch()}\r\n")
                    append("From: $sipFrom\r\n")
                    append("To: $sipTo\r\n")
                    append("Contact: <sip:$myExt@${contactHostPort(localIp)}>\r\n")
                    append("Call-ID: $currentCallId\r\n")
                    append("CSeq: $lastInviteCSeq INVITE\r\n")
                    append("Max-Forwards: 70\r\n")
                    append("Content-Type: application/sdp\r\n")
                    append("Content-Length: ${sdp.toByteArray().size}\r\n\r\n$sdp")
                })
                Log.d(T, "Re-INVITE sent (onHold=$onHold, isIncoming=$isIncoming)")
            } catch (e: Exception) {
                Log.e(T, "sendReInvite failed: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Receive loop – ONE thread per socket, exits when the socket is replaced
    // ─────────────────────────────────────────────────────────────────────────
    private fun listenLoop(sock: DatagramSocket) {
        Thread {
            val buf = ByteArray(8192)
            while (!sock.isClosed && sock === socket) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    sock.receive(pkt)
                    val msg = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                    if (msg.isBlank()) continue
                    Log.d("SIP_IN", msg.take(700))
                    try {
                        handleIncoming(msg, pkt)
                    } catch (e: Exception) {
                        Log.e(T, "handleIncoming crashed: ${e.message}", e)
                    }
                } catch (e: Exception) {
                    if (sock.isClosed || sock !== socket) break
                    try { Thread.sleep(200) } catch (_: Exception) {}
                }
            }
            Log.d(T, "Listener for local port ${sock.localPort} stopped")
        }.apply { name = "sip-listener"; isDaemon = true }.start()
    }

    private fun handleIncoming(msg: String, pkt: DatagramPacket) {
        val srcIp = pkt.address?.hostAddress ?: serverIp
        val callId = hdr(msg, "Call-ID")
        val cseq = hdr(msg, "CSeq")

        if (msg.startsWith("SIP/2.0")) {
            learnNat(msg)
            if (cseq.endsWith("REGISTER")) {
                awaitingRegisterReply = false
                missedRegisterReplies = 0
            }
        }

        when {
            msg.startsWith("OPTIONS") || msg.startsWith("NOTIFY") || msg.startsWith("INFO") -> {
                sendRaw(simpleResponse(msg, "200 OK"))
            }

            msg.startsWith("ACK") -> {
                ackReceivedForCallId = callId
            }

            msg.startsWith("INVITE") -> handleInvite(msg, srcIp, callId, cseq)

            msg.startsWith("BYE") -> {
                sendRaw(simpleResponse(msg, "200 OK"))
                terminatedCallIds.add(callId)
                if (callId == currentCallId?.trim()) {
                    Log.i(T, "◀ Remote hung up (BYE)")
                    SipModule.stopAudio()
                    if (heldCallId.isNotBlank()) {
                        resumeHeldCall()
                    } else {
                        broadcast("ACTION_HANGUP_UI")
                        resetCall()
                    }
                } else if (callId == heldCallId.trim()) {
                    Log.d(T, "The background held call was disconnected by the remote party.")
                    heldCallId = ""
                }
            }

            msg.startsWith("CANCEL") -> handleCancel(msg, callId)

            msg.startsWith("SIP/2.0 100") -> { /* Trying */ }

            msg.startsWith("SIP/2.0 180") || msg.startsWith("SIP/2.0 183") -> {
                if (callId == currentCallId && !isIncoming && !isCallActive) {
                    gotProvisional = true
                    startRingbackTone()
                    broadcast("ACTION_REMOTE_RINGING")
                }
            }

            msg.startsWith("SIP/2.0 401") || msg.startsWith("SIP/2.0 407") -> {
                if (cseq.endsWith("INVITE")) ackErrorResponse(msg)   // UDP: must ACK or PBX repeats the 401
                handleAuth(msg)
            }

            msg.startsWith("SIP/2.0 2") -> handle2xx(msg, srcIp, callId, cseq)

            msg.startsWith("SIP/2.0 3") || msg.startsWith("SIP/2.0 4") ||
                msg.startsWith("SIP/2.0 5") || msg.startsWith("SIP/2.0 6") -> handleError(msg, callId, cseq)

            !msg.startsWith("SIP/2.0") -> {
                // UPDATE / REFER / MESSAGE … – always answer, never stay silent
                sendRaw(simpleResponse(msg, "501 Not Implemented"))
            }
        }
    }

    private fun handle2xx(msg: String, srcIp: String, callId: String, cseq: String) {
        when {
            cseq.endsWith("REGISTER") -> {
                if (callId != registrationCallId) return
                if (isUnregistering) {
                    Log.i(T, "✓ Un-REGISTER confirmed by PBX – extension is OFFLINE")
                    unregLatch?.countDown()
                    return
                }
                isRegistered = true
                lastRegisterOkAt = System.currentTimeMillis()
                SipModule.updateRegistrationState("Online")
                Log.i(T, "✓ REGISTER OK – $myExt ONLINE, PBX will send calls to: ${hdr(msg, "Contact")}")
                // Public address became known/changed after this REGISTER → register the reachable Contact
                val nowContact = contactHostPort(getLocalIp())
                if (lastSentContact.isNotBlank() && lastSentContact != nowContact) {
                    Log.i(T, "↻ Contact changed $lastSentContact → $nowContact, re-registering")
                    sendRegister(myExt)
                }
            }
            cseq.endsWith("REFER") -> Log.i("DTMF", "✓ PBX accepted transfer – call is being handed over")
            cseq.endsWith("INFO") -> Log.i("DTMF", "✓ PBX accepted DTMF ($cseq)")

            cseq.endsWith("INVITE") -> {
                // 200 OK for a call we already ended/cancelled → ACK + BYE so the PBX doesn't keep it
                if (callId != currentCallId && callId != heldCallId) {
                    ack2xx(msg)
                    if (terminatedCallIds.contains(callId)) byeFromResponse(msg)
                    Log.d(T, "200 OK for old call $callId → ACKed and ignored")
                    return
                }

                stopRingbackTone()
                val toH = hdr(msg, "To")
                if (toH.contains("tag=")) {
                    remoteTag = toH.substringAfter("tag=").substringBefore(">").substringBefore(";").substringBefore(",").trim()
                }

                // ✅ ACK AFTER reading the To-tag (before, it went out without the tag → PBX ignored
                //    it, re-sent 200 OK, audio restarted and the call dropped after ~32s)
                ack2xx(msg)

                // Same 200 OK again (UDP retransmission) → ACK only, don't restart audio
                val key = "$callId:${cseq.substringBefore(" ").trim()}"
                if (key == lastAcked2xxKey) {
                    Log.d(T, "Retransmitted 200 OK ($key) → re-ACKed only")
                    return
                }
                lastAcked2xxKey = key

                val contactLine = hdr(msg, "Contact")
                if (contactLine.isNotBlank()) {
                    remoteContactUri = if (contactLine.contains("<")) {
                        contactLine.substringAfter("<").substringBefore(">")
                    } else {
                        contactLine.substringBefore(";").trim()
                    }
                }

                if (!isIncoming) {
                    dialogFrom = hdr(msg, "From")
                    dialogTo = toH
                }

                parseSdp(msg, fromIp = srcIp)

                if (!isCallActive) {
                    isCallActive = true
                    isOutgoingCallPending = false
                    _callConnectedAt = System.currentTimeMillis()
                    Log.i(T, "✓ Call connected rtp=$remoteRtpIp:$remoteRtpPort")
                    runOnThread {
                        Thread.sleep(80)
                        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        am.mode = AudioManager.MODE_IN_COMMUNICATION
                        SipModule.startAudio(remoteRtpIp, remoteRtpPort, localRtpPort)
                        broadcast("ACTION_CALL_CONNECTED")
                    }
                } else {
                    Log.d(T, "Re-INVITE confirmed by PBX. isOnHold=$isOnHold remoteRtp=$remoteRtpIp:$remoteRtpPort")
                    if (!isOnHold) {
                        SipModule.stopAudio()
                        runOnThread {
                            Thread.sleep(80)
                            SipModule.startAudio(remoteRtpIp, remoteRtpPort, localRtpPort)
                            SipModule.rtpEngine?.setMuted(false)
                            SipModule.rtpEngine?.setOutputPaused(false)
                        }
                    }
                }
            }
        }
    }

    private fun handleError(msg: String, callId: String, cseq: String) {
        val statusLine = msg.substringBefore("\r\n")
        val statusCode = msg.substringAfter("SIP/2.0 ").substringBefore(" ").trim().toIntOrNull() ?: 0

        // UDP: every final response to an INVITE MUST be ACKed, or the PBX repeats it every 0.5–4 s
        if (cseq.endsWith("INVITE")) ackErrorResponse(msg)

        if (cseq.endsWith("REGISTER")) {
            if (isUnregistering) { unregLatch?.countDown(); return }
            Log.e(T, "✗ REGISTER rejected: $statusLine")
            isRegistered = false
            SipModule.updateRegistrationState("Offline", statusLine)
            return
        }
        if (cseq.endsWith("REFER")) {
            Log.e("DTMF", "✗ PBX rejected transfer: $statusLine")
            return
        }
        if (cseq.endsWith("INFO")) {
            Log.e("DTMF", "✗ PBX REJECTED DTMF: $statusLine")
            return
        }
        if (!cseq.endsWith("INVITE")) {
            Log.w(T, "Error for $cseq ignored: $statusLine")
            return
        }
        if (isCallActive) {
            Log.d(T, "Re-INVITE rejected during active call: $statusLine")
            return
        }
        if (callId != currentCallId) {
            Log.d(T, "Error for old call $callId ignored: $statusLine")
            return
        }

        Log.w(T, "✗ Call to $lastTarget failed: $statusLine")

        // Short number not routable on the PBX → try the full extension once
        // Short number rejected by the PBX → try the full extension once.
        // 603/403 only count if nothing was ringing yet (a real "Decline" by the person comes after ringing).
        val shortNumberRejected = statusCode in listOf(404, 480, 484, 408, 503, 604) ||
            (!gotProvisional && statusCode in listOf(603, 403))
        if (shortNumberRejected && !fullRetryDone && toFullExt(lastTarget) != lastTarget) {
            retryWithFullNumber("PBX answered $statusCode")
            return
        }
        SipModule.stopAudio()
        if (statusCode == 486 || statusCode == 600) broadcast("ACTION_CALL_BUSY")   // screen shows "Busy", closes after 2s
        else broadcast("ACTION_HANGUP_UI")
        resetCall()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Incoming INVITE
    // ─────────────────────────────────────────────────────────────────────────
    private fun handleInvite(msg: String, srcIp: String, callId: String, cseq: String) {
        val toHdr = hdr(msg, "To")

        // 1. Re-INVITE inside an existing call (other side Hold/Resume, PBX session refresh)
        if (toHdr.contains("tag=") && (callId == currentCallId || callId == heldCallId)) {
            answerReInvite(msg, srcIp)
            return
        }

        // 2. Same INVITE again (UDP retransmission) → repeat our last answer, don't start a 2nd call
        if (callId == currentCallId || (callId.isNotBlank() && callId == waitingCallId)) {
            if (callId == currentCallId) lastInviteResponse?.let { sendRaw(it) }
            Log.d(T, "Retransmitted INVITE $callId → repeated last response")
            return
        }

        // 3. Already ended
        if (terminatedCallIds.contains(callId)) {
            sendRaw(simpleResponse(msg, "487 Request Terminated"))
            return
        }

        // 4. Hangup cooldown – answer Busy instead of staying silent
        if (System.currentTimeMillis() - hangupTimestamp < HANGUP_COOLDOWN_MS) {
            Log.w(T, "INVITE during 2s hangup cooldown → 486 Busy")
            sendRaw(simpleResponse(msg, "486 Busy Here"))
            return
        }

        // 4b. Stale "in call" state: old call ended but its BYE was lost.
        //     A real call always has audio running; if not, clean up and ring normally.
        if (isCallActive && SipModule.rtpEngine == null &&
            _callConnectedAt > 0L && System.currentTimeMillis() - _callConnectedAt > 3_000) {
            Log.w(T, "isCallActive was stuck (no audio running) → resetting, showing new call")
            resetCall()
        }

        // 5. Call waiting
        if (isCallActive && !callWaitingActive) {
            val wFrom = hdr(msg, "From")
            val wNum  = Regex("""sip:([^@>;]+)""").find(wFrom)?.groupValues?.get(1)?.trim()
                ?.let { if (it.startsWith("1001") && it.length > 4) it.drop(4) else it } ?: "Unknown"
            val wName = when {
                wFrom.contains('"') -> wFrom.substringAfter('"').substringBefore('"').trim()
                wFrom.contains("<") && !wFrom.trimStart().startsWith("<") -> wFrom.substringBefore("<").trim()
                else -> ""
            }
            callWaitingActive   = true
            waitingInviteMsg    = msg
            waitingCallerNumber = wNum
            waitingCallerName   = wName
            waitingCallId       = callId
            waitingLocalTag     = "as${System.currentTimeMillis()}"

            startCallWaitingBeep()
            sendRaw(simpleResponse(msg, "180 Ringing", waitingLocalTag))
            Log.d(T, "Call Waiting: $wNum queued")
            SipForegroundService.showCallWaitingNotification(appContext, wNum, wName)
            return
        }

        if (isCallActive && callWaitingActive) {
            sendRaw(simpleResponse(msg, "486 Busy Here"))
            return
        }

        // 6. Another call is being set up → Busy (before: silently ignored → "No response")
        if (isOutgoingCallPending || (isCallScreenOpen && currentCallId != null)) {
            Log.w(T, "Another call is being set up → 486 Busy for $callId")
            sendRaw(simpleResponse(msg, "486 Busy Here"))
            return
        }
        if (isCallScreenOpen) {
            Log.w(T, "isCallScreenOpen was stuck – resetting and accepting the call")
            isCallScreenOpen = false
        }

        // 7. New incoming call
        val fromHeader = hdr(msg, "From")
        val callerNumber = Regex("""sip:([^@>;]+)""").find(fromHeader)?.groupValues?.get(1)?.trim() ?: "Unknown"
        Log.i(T, "━━━ Incoming call from $callerNumber (To=$toHdr) ━━━")

        isIncoming = true
        isCallScreenOpen = true
        activeIncomingCallId = callId
        incomingRingStartTime = System.currentTimeMillis()

        lastInviteMessage = msg
        currentCallId = callId
        localTag = "as${System.currentTimeMillis()}"   // fresh tag for every incoming call

        currentInviteBranch = hdr(msg, "Via").substringAfter("branch=", "").substringBefore(";").trim()
        remoteTag = fromHeader.substringAfter("tag=", "").substringBefore(";").trim().ifBlank { null }
        lastTarget = callerNumber

        lastInviteVia = hdr(msg, "Via")
        lastInviteFrom = fromHeader
        lastInviteTo = toHdr
        lastInviteCSeqHeader = cseq

        parseSdp(msg, fromIp = srcIp)

        val ringing = "SIP/2.0 180 Ringing\r\n" +
            "Via: $lastInviteVia\r\n" +
            "From: $lastInviteFrom\r\n" +
            "To: $lastInviteTo;tag=$localTag\r\n" +
            "Call-ID: $currentCallId\r\n" +
            "CSeq: $cseq\r\n" +
            "Contact: <sip:$myExt@${contactHostPort(getLocalIp())}>\r\n" +
            "Content-Length: 0\r\n\r\n"
        lastInviteResponse = ringing
        sendRaw(ringing)

        val cleanCallerExt = if (callerNumber.startsWith("1001") && callerNumber.length > 4) callerNumber.drop(4) else callerNumber

        val cachedName = try {
            val prefs = appContext.getSharedPreferences("OfficeAppPrefs", Context.MODE_PRIVATE)
            val json = prefs.getString("cached_contacts", null)
            if (!json.isNullOrBlank()) {
                val listType = object : com.google.gson.reflect.TypeToken<List<com.callingapp.calling_app.api.ExtensionContact>>() {}.type
                val list: List<com.callingapp.calling_app.api.ExtensionContact> = com.google.gson.Gson().fromJson(json, listType)
                list.find { it.extension == cleanCallerExt || it.extension == callerNumber }?.name
            } else null
        } catch (_: Exception) { null }

        val rawDisplayName = when {
            fromHeader.contains('"') -> fromHeader.substringAfter('"').substringBefore('"').trim()
            fromHeader.contains("<") && !fromHeader.trimStart().startsWith("<") -> fromHeader.substringBefore("<").trim()
            else -> ""
        }

        val callerDisplayName = cachedName?.takeIf { it.isNotBlank() }
            ?: rawDisplayName.takeIf { it.isNotBlank() && it != callerNumber }
            ?: "Ext $cleanCallerExt"
        lastCallerName = callerDisplayName

        SipForegroundService.showIncomingCallNotification(appContext, callerNumber, callerDisplayName)

        try {
            appContext.startService(Intent(appContext, SipForegroundService::class.java).apply { action = "START_RINGTONE" })
        } catch (e: Exception) {
            Log.e(T, "Could not start ringtone: ${e.message}")
        }

        try {
            appContext.startActivity(
                Intent(appContext, CallActivity::class.java).apply {
                    putExtra("NUMBER", callerNumber)
                    putExtra("CALLER_NAME", callerDisplayName)
                    putExtra("IS_INCOMING", true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                }
            )
        } catch (e: Exception) {
            Log.e(T, "Foreground full screen launch intercepted: ${e.message}")
        }
    }

    private fun handleCancel(msg: String, callId: String) {
        val tag = if (callId == waitingCallId) waitingLocalTag else localTag
        sendRaw(simpleResponse(msg, "200 OK", tag))
        terminatedCallIds.add(callId)

        when {
            // Waiting caller hung up → keep the active call
            callWaitingActive && callId == waitingCallId -> {
                if (waitingInviteMsg.isNotEmpty()) sendRaw(simpleResponse(waitingInviteMsg, "487 Request Terminated", waitingLocalTag))
                stopCallWaitingBeep()
                if (waitingCallerNumber.isNotBlank()) {
                    com.callingapp.calling_app.utils.CallLog.init(appContext, myExt)
                    com.callingapp.calling_app.utils.CallLog.save(
                        com.callingapp.calling_app.utils.CallEntry(
                            number    = waitingCallerNumber,
                            name      = waitingCallerName.ifBlank { waitingCallerNumber },
                            type      = com.callingapp.calling_app.utils.CallType.MISSED,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                SipForegroundService.cancelCallWaitingNotification(appContext)
                callWaitingActive = false
                waitingInviteMsg = ""
                waitingCallId = ""
            }

            // Caller hung up while our phone was ringing
            callId == currentCallId?.trim() && !isCallActive -> {
                lastInviteMessage?.let { sendRaw(simpleResponse(it, "487 Request Terminated", localTag)) }

                val callerNum = Regex("""sip:([^@>;]+)""").find(lastInviteFrom)?.groupValues?.get(1)?.trim()
                    ?.let { if (it.startsWith("1001") && it.length > 4) it.drop(4) else it } ?: lastTarget

                if (callerNum.isNotBlank() && !receiverDeclined) {
                    com.callingapp.calling_app.utils.CallLog.init(appContext, myExt)
                    com.callingapp.calling_app.utils.CallLog.save(
                        com.callingapp.calling_app.utils.CallEntry(
                            number    = callerNum,
                            name      = lastCallerName.ifBlank { callerNum },
                            type      = com.callingapp.calling_app.utils.CallType.MISSED,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    SipForegroundService.showMissedCallNotification(appContext, callerNum, lastCallerName.ifBlank { callerNum })
                }
                SipModule.stopAudio()
                SipForegroundService.cancelIncomingCallNotification(appContext)
                try {
                    appContext.startService(Intent(appContext, SipForegroundService::class.java).apply { action = "STOP_RINGTONE" })
                } catch (_: Exception) {}
                broadcast("ACTION_HANGUP_UI")
                resetCall()
            }

            else -> Log.d(T, "CANCEL for unknown call $callId — ignoring")
        }
    }

    private fun answerReInvite(msg: String, srcIp: String) {
        val cid = hdr(msg, "Call-ID")
        val isHeldLeg = cid == heldCallId
        val localIp = getLocalIp()
        val sdp = buildSdp(if (isHeldLeg) heldLocalPort else localRtpPort, localIp)
        sendRaw(buildString {
            append("SIP/2.0 200 OK\r\n")
            append("Via: ${hdr(msg, "Via")}\r\n")
            append("From: ${hdr(msg, "From")}\r\n")
            append("To: ${hdr(msg, "To")}\r\n")
            append("Call-ID: $cid\r\n")
            append("CSeq: ${hdr(msg, "CSeq")}\r\n")
            append("Contact: <sip:$myExt@${contactHostPort(localIp)}>\r\n")
            append("Content-Type: application/sdp\r\n")
            append("Content-Length: ${sdp.toByteArray().size}\r\n\r\n$sdp")
        })
        Log.d(T, "Re-INVITE for existing call $cid answered with 200 OK")
        if (!isHeldLeg && msg.contains("m=audio")) {
            val oldIp = remoteRtpIp
            val oldPort = remoteRtpPort
            parseSdp(msg, fromIp = srcIp)
            if ((oldIp != remoteRtpIp || oldPort != remoteRtpPort) && isCallActive && !isOnHold) {
                SipModule.stopAudio()
                runOnThread { Thread.sleep(80); SipModule.startAudio(remoteRtpIp, remoteRtpPort, localRtpPort) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Response / ACK helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun simpleResponse(req: String, status: String, toTag: String? = null): String {
        var to = hdr(req, "To")
        if (!to.contains("tag=")) to += ";tag=${toTag ?: "as${System.currentTimeMillis()}"}"
        return "SIP/2.0 $status\r\n" +
            "Via: ${hdr(req, "Via")}\r\n" +
            "From: ${hdr(req, "From")}\r\n" +
            "To: $to\r\n" +
            "Call-ID: ${hdr(req, "Call-ID")}\r\n" +
            "CSeq: ${hdr(req, "CSeq")}\r\n" +
            "User-Agent: CNETVOIP 1.0 ($deviceModel)\r\n" +
            "Content-Length: 0\r\n\r\n"
    }

    /** ACK for a 200 OK to our INVITE / re-INVITE, built from the response itself. */
    private fun ack2xx(resp: String) {
        val from    = hdr(resp, "From")
        val to      = hdr(resp, "To")
        val callId  = hdr(resp, "Call-ID")
        val cseqNo  = cseqNumber(resp)
        val contact = hdr(resp, "Contact")
        val ruri = when {
            contact.contains("<") -> contact.substringAfter("<").substringBefore(">")
            contact.isNotBlank()  -> contact.substringBefore(";").trim()
            else -> "sip:${Regex("""sip:([^@>;]+)""").find(to)?.groupValues?.get(1) ?: lastTarget}@$serverIp"
        }
        val localIp = getLocalIp()
        sendRaw(buildString {
            append("ACK $ruri SIP/2.0\r\n")
            append("Via: SIP/2.0/UDP $localIp:${socket?.localPort};rport;branch=${freshBranch()}\r\n")
            append("From: $from\r\n")
            append("To: $to\r\n")
            append("Call-ID: $callId\r\n")
            append("CSeq: $cseqNo ACK\r\n")
            append("Max-Forwards: 70\r\n")
            append("Content-Length: 0\r\n\r\n")
        })
    }

    /** ACK for a 3xx-6xx / 401 response to our INVITE (same branch as the INVITE). */
    private fun ackErrorResponse(resp: String) {
        val via    = hdr(resp, "Via")
        val branch = Regex("""branch=([^;\s]+)""").find(via)?.groupValues?.get(1) ?: return
        val to     = hdr(resp, "To")
        val toUser = Regex("""sip:([^@>;]+)""").find(to)?.groupValues?.get(1) ?: return
        val localIp = getLocalIp()
        sendRaw(buildString {
            append("ACK sip:$toUser@$serverIp SIP/2.0\r\n")
            append("Via: SIP/2.0/UDP $localIp:${socket?.localPort};rport;branch=$branch\r\n")
            append("From: ${hdr(resp, "From")}\r\n")
            append("To: $to\r\n")
            append("Call-ID: ${hdr(resp, "Call-ID")}\r\n")
            append("CSeq: ${cseqNumber(resp)} ACK\r\n")
            append("Max-Forwards: 70\r\n")
            append("Content-Length: 0\r\n\r\n")
        })
    }

    /** BYE for a call that got answered after we already hung up (race). */
    private fun byeFromResponse(resp: String) {
        val contact = hdr(resp, "Contact")
        val ruri = if (contact.contains("<")) contact.substringAfter("<").substringBefore(">") else "sip:$serverIp"
        val localIp = getLocalIp()
        sendRaw(buildString {
            append("BYE $ruri SIP/2.0\r\n")
            append("Via: SIP/2.0/UDP $localIp:${socket?.localPort};rport;branch=${freshBranch()}\r\n")
            append("From: ${hdr(resp, "From")}\r\n")
            append("To: ${hdr(resp, "To")}\r\n")
            append("Call-ID: ${hdr(resp, "Call-ID")}\r\n")
            append("CSeq: ${cseqNumber(resp) + 1} BYE\r\n")
            append("Max-Forwards: 70\r\n")
            append("Content-Length: 0\r\n\r\n")
        })
    }

    /** UDP: our 200 OK must be repeated until the PBX ACKs it (RFC 3261 §13.3.1.4). */
    private fun startOkRetransmit(ok: String, callId: String?) {
        if (callId == null) return
        Thread {
            for (wait in longArrayOf(500, 1000, 2000, 4000, 4000, 4000, 4000, 4000, 4000)) {
                try { Thread.sleep(wait) } catch (_: Exception) { return@Thread }
                if (ackReceivedForCallId == callId || currentCallId != callId) return@Thread
                Log.w(T, "No ACK for our 200 OK yet → resending")
                sendRaw(ok)
            }
            if (ackReceivedForCallId != callId && currentCallId == callId) {
                Log.e(T, "✗ PBX never ACKed our 200 OK – it cannot reach this phone")
            }
        }.apply { name = "sip-200-retx"; isDaemon = true }.start()
    }

    // ── *3<ext> transfer – done with SIP REFER so it works on EVERY call route ──
    private val xferLock = Any()


    // ─────────────────────────────────────────────────────────────────────────
    // DTMF – Dual Transmission (In-Band RFC 2833 RTP + SIP INFO fallback)
    // ─────────────────────────────────────────────────────────────────────────
    fun sendDtmf(digit: Char) {
        if (!isCallActive || currentCallId == null) return
        // ONE message per key (SIP INFO), exactly like the old working version.
        // Do NOT also send RTP DTMF – the PBX would count every key twice (*3 → **33).
        dtmfExecutor.submit {
            try {
                val localIp  = getLocalIp()
                val cseq     = ++lastInviteCSeq
                val infoFrom = if (isIncoming) dialogTo   else dialogFrom
                val infoTo   = if (isIncoming) dialogFrom else dialogTo
                val dtmfBody = "Signal=$digit\r\nDuration=160\r\n"
                sendRaw(buildString {
                    append("INFO sip:$serverIp SIP/2.0\r\n")
                    append("Via: SIP/2.0/UDP $localIp:${socket?.localPort};rport;branch=${freshBranch()}\r\n")
                    append("From: $infoFrom\r\n")
                    append("To: $infoTo\r\n")
                    append("Call-ID: $currentCallId\r\n")
                    append("CSeq: $cseq INFO\r\n")
                    append("Max-Forwards: 70\r\n")
                    append("Content-Type: application/dtmf-relay\r\n")
                    append("Content-Length: ${dtmfBody.toByteArray().size}\r\n\r\n")
                    append(dtmfBody)
                })
                Log.d("DTMF", "Sent '$digit' cseq=$cseq isIncoming=$isIncoming")
                Thread.sleep(120)
            } catch (e: Exception) {
                Log.e("DTMF", "sendDtmf('$digit') failed: ${e.message}")
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────────────
    // Auth
    // ─────────────────────────────────────────────────────────────────────────
    private fun handleAuth(msg: String) {
        val authHeader = hdr(msg, "WWW-Authenticate").ifBlank { hdr(msg, "Proxy-Authenticate") }
        val nonce = Regex("""nonce="(.*?)"""").find(authHeader)?.groupValues?.get(1) ?: return
        val realm = Regex("""realm="(.*?)"""").find(authHeader)?.groupValues?.get(1) ?: return

        val msgCallId = hdr(msg, "Call-ID")
        val isRegistration = (msgCallId == registrationCallId)
        val method = if (isRegistration) "REGISTER" else "INVITE"
        val user = if (isRegistration && isUnregistering && unregExtension.isNotBlank()) unregExtension else myExt

        val ha1 = "$user:$realm:$password".md5()
        val ha2 = "$method:sip:$serverIp".md5()
        val hash = "$ha1:$nonce:$ha2".md5()
        val headerName = if (msg.startsWith("SIP/2.0 407")) "Proxy-Authorization" else "Authorization"
        val authResponseHeader = "$headerName: Digest username=\"$user\", realm=\"$realm\", " +
            "nonce=\"$nonce\", uri=\"sip:$serverIp\", response=\"$hash\", algorithm=MD5\r\n"

        if (isRegistration) {
            if (isUnregistering) {
                if (unregAuthSent) return
                unregAuthSent = true
                sendUnregister(unregExtension, authResponseHeader)
                return
            }
            // Our AUTHENTICATED REGISTER was rejected again → wrong password (avoid an endless loop)
            if (cseqNumber(msg) == lastRegisterAuthCSeq && !authHeader.contains("stale=true", ignoreCase = true)) {
                Log.e(T, "✗ REGISTER password rejected for $user – check the extension password")
                isRegistered = false
                SipModule.updateRegistrationState("Offline", "wrong password")
                return
            }
            sendRegister(user, authResponseHeader)
        } else {
            val cseqNo = cseqNumber(msg)
            val key = "$msgCallId:$cseqNo"
            if (key == lastInviteAuthKey) {
                Log.d(T, "handleAuth: same 401 repeated (UDP) – already answered, ignoring")
                return
            }
            if (cseqNo >= 2) {
                Log.e(T, "✗ INVITE password rejected by PBX")
                SipModule.stopAudio()
                broadcast("ACTION_HANGUP_UI")
                resetCall()
                return
            }
            if (isOutgoingCallPending && msgCallId == currentCallId) {
                lastInviteAuthKey = key
                makeCall(lastTarget, authResponseHeader)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SDP helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildSdp(rtpPort: Int, localIp: String): String {
        val sdpIp = publicIp.takeIf { it.isNotBlank() } ?: localIp
        val ts = System.currentTimeMillis()
        return buildString {
            append("v=0\r\no=- $ts $ts IN IP4 $sdpIp\r\ns=Talk\r\nc=IN IP4 $sdpIp\r\nt=0 0\r\n")
            append("m=audio $rtpPort RTP/AVP 0 101\r\n")
            append("a=rtpmap:0 PCMU/8000\r\na=rtpmap:101 telephone-event/8000\r\na=fmtp:101 0-15\r\n")
            append("a=sendrecv\r\n")
        }
    }

    private fun parseSdp(message: String, fromIp: String) {
        val body = message.substringAfter("\r\n\r\n", "")
        remoteRtpIp = fromIp.takeIf { it.isNotBlank() && it != "0.0.0.0" }
            ?: Regex("""c=IN IP4 ([\d.]+)""").find(body)?.groupValues?.get(1)
                ?.takeIf { it.isNotBlank() && it != "0.0.0.0" } ?: serverIp
        remoteRtpPort = Regex("""m=audio (\d+)""").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 10000
    }

    private var pendingRtpSocket: DatagramSocket? = null
    private fun allocateFreeRtpPort(): Int {
        try { pendingRtpSocket?.close() } catch (_: Exception) {}
        return try {
            val sock = DatagramSocket(null)
            sock.reuseAddress = true
            sock.bind(InetSocketAddress("0.0.0.0", 0))
            pendingRtpSocket = sock
            sock.localPort
        } catch (e: Exception) {
            20000 + (System.currentTimeMillis() % 10000).toInt()
        }
    }

    fun takePendingRtpSocket(): DatagramSocket? {
        val sock = pendingRtpSocket
        pendingRtpSocket = null
        return sock
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Network helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun resolveServer() {
        serverAddr = try {
            val all = InetAddress.getAllByName(serverIp)
            all.firstOrNull { it is Inet4Address } ?: all.first()
        } catch (e: Exception) {
            Log.e(T, "DNS resolve failed for $serverIp: ${e.message}")
            null
        }
    }

    /** Always an IPv4 literal (an IPv6 address in Via/Contact/SDP breaks the PBX). */
    fun getLocalIp(): String {
        try {
            DatagramSocket().use { s ->
                s.connect(serverAddr ?: run { resolveServer(); serverAddr } ?: InetAddress.getByName(serverIp), port)
                val a = s.localAddress
                if (a is Inet4Address && !a.isAnyLocalAddress) a.hostAddress?.let { return it }
            }
        } catch (_: Exception) {}
        try {
            val ifaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (ifaces != null && ifaces.hasMoreElements()) {
                val ni = ifaces.nextElement()
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.inetAddresses) {
                    if (ia is Inet4Address && !ia.isLoopbackAddress) ia.hostAddress?.let { return it }
                }
            }
        } catch (_: Exception) {}
        return publicIp.ifBlank { "0.0.0.0" }
    }

    private fun sendRaw(msg: String) {
        try {
            val s = socket ?: run { Log.w(T, "sendRaw: no socket (no network?)"); return }
            val addr = serverAddr ?: run { resolveServer(); serverAddr } ?: return
            val bytes = msg.toByteArray(Charsets.UTF_8)
            Log.d("SIP_OUT", msg.take(400))
            s.send(DatagramPacket(bytes, bytes.size, addr, port))
        } catch (e: Exception) {
            Log.e(T, "sendRaw failed: ${e.message}")
            serverAddr = null
        }
    }

    private fun runOnThread(block: () -> Unit) { Thread(block).start() }

    private fun resetCall() {
        stopCallWaitingBeep()
        stopRingbackTone()
        isOutgoingCallPending = false
        _callConnectedAt = 0L
        receiverDeclined    = false
        callWaitingActive   = false
        waitingInviteMsg    = ""
        waitingCallerNumber = ""
        waitingCallerName   = ""
        waitingCallId       = ""
        waitingLocalTag     = ""
        heldCallId          = ""
        heldDialogFrom      = ""
        heldDialogTo        = ""
        heldContactUri      = ""
        heldRtpIp           = ""
        heldRtpPort         = 10000
        heldLocalPort       = 4000
        heldCSeq            = 1
        try { pendingRtpSocket?.close() } catch (_: Exception) {}
        pendingRtpSocket     = null
        isCallScreenOpen     = false
        activeIncomingCallId = null
        currentCallId        = null
        remoteTag            = null
        remoteContactUri     = ""
        dialogFrom           = ""
        dialogTo             = ""
        isCallActive         = false
        isIncoming           = false
        lastInviteMessage    = null
        lastInviteResponse   = null
        lastInviteAuthKey    = ""
        currentInviteBranch  = ""
        lastInviteCSeq       = 1
        isOnHold             = false
        lastInviteVia        = ""
        lastInviteFrom       = ""
        lastInviteTo         = ""
        lastInviteCSeqHeader = ""
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logout → real un-REGISTER, extension goes OFFLINE immediately
    // ─────────────────────────────────────────────────────────────────────────
    fun stopRegistration(extension: String) {
        if (isStopped) return            // called twice (MainAppShell + service) → only once
        isStopped = true                 // blocks timers / alarm from registering again
        stopTimers()
        val ext = myExt.ifBlank { extension }
        Thread {
            try {
                isUnregistering = true
                unregExtension = ext
                unregAuthSent = false
                val latch = CountDownLatch(1)
                unregLatch = latch
                sendUnregister(ext, "")
                val confirmed = latch.await(4, TimeUnit.SECONDS)
                if (confirmed) Log.i(T, "✓ $ext logged out – PBX confirmed OFFLINE")
                else Log.w(T, "⚠ $ext un-REGISTER not confirmed – PBX drops it within $REGISTER_EXPIRES_S s")
            } catch (e: Exception) {
                Log.e(T, "stopRegistration failed: ${e.message}")
            } finally {
                isUnregistering = false
                isRegistered = false
                SipModule.updateRegistrationState("Offline", "logged out")
                try { socket?.close() } catch (_: Exception) {}
                socket = null
            }
        }.apply { name = "sip-unregister" }.start()
    }

    private fun sendUnregister(extension: String, authHeader: String) {
        runOnThread {
            try {
                val sock = socket ?: return@runOnThread
                val localIp = getLocalIp()
                val cseq = regCSeq.getAndIncrement()
                sendRaw(buildString {
                    append("REGISTER sip:$serverIp SIP/2.0\r\n")
                    append("Via: SIP/2.0/UDP $localIp:${sock.localPort};rport;branch=${freshBranch()}\r\n")
                    append("Max-Forwards: 70\r\n")
                    append("From: <sip:$extension@$serverIp>;tag=$stableRegTag\r\n")
                    append("To: <sip:$extension@$serverIp>\r\n")
                    append("Call-ID: $registrationCallId\r\n")
                    append("CSeq: $cseq REGISTER\r\n")
                    append("Contact: *\r\n")                     // remove ALL contacts of this extension
                    append("User-Agent: CNETVOIP 1.0 ($deviceModel)\r\n")
                    if (authHeader.isNotEmpty()) append(authHeader)
                    append("Expires: 0\r\n")
                    append("Content-Length: 0\r\n\r\n")
                })
            } catch (e: Exception) {
                Log.e(T, "sendUnregister failed: ${e.message}")
            }
        }
    }
}

fun String.md5(): String =
    MessageDigest.getInstance("MD5").digest(this.toByteArray())
        .joinToString("") { "%02x".format(it) }
