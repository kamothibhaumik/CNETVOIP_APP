package com.callingapp.calling_app.sip

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

class RtpAudioEngine(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE        = 8000
        private const val FRAME_MS           = 20
        private const val SAMPLES_PER_FRAME  = SAMPLE_RATE * FRAME_MS / 1000   // 160
        private const val RTP_HEADER_SIZE    = 12
        private const val PAYLOAD_TYPE_PCMU  = 0
        private const val PAYLOAD_TYPE_DTMF  = 101
        private const val MAX_RTP_PACKET     = 172
    }

    private var rtpSocket: DatagramSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack:  AudioTrack?  = null

    @Volatile private var remoteRtpIp: String = ""
    @Volatile private var remoteRtpPort: Int = 10000

    private var sendThread: Thread? = null
    private var recvThread: Thread? = null

    @Volatile private var running          = false
    @Volatile private var muted            = false
    @Volatile private var outputPaused     = false
    @Volatile private var isSendingDtmf    = false // Pauses mic packets during DTMF transmission
    @Volatile private var sequenceNumber   = (Math.random() * 65535).toInt()
    @Volatile private var timestamp        = (Math.random() * 0xFFFFFFFFL).toLong()
    private          val ssrc              = (Math.random() * 0x7FFFFFFF).toInt()

    fun start(remoteIp: String, remotePort: Int, localPort: Int = 4000,
              prebound: DatagramSocket? = null) {
        if (running) stop()
        running = true

        remoteRtpIp = remoteIp
        remoteRtpPort = remotePort

        try {
            val sock = if (prebound != null && !prebound.isClosed) {
                Log.d("RTP", "Reusing pre-bound socket on port ${prebound.localPort}")
                prebound
            } else {
                Log.d("RTP", "Binding fresh socket on port $localPort")
                DatagramSocket(null).also {
                    it.reuseAddress = true
                    it.bind(InetSocketAddress("0.0.0.0", localPort))
                }
            }
            rtpSocket = sock

            Log.d("RTP", "RTP socket ready: local=:${sock.localPort} → remote=$remoteIp:$remotePort")

            startSender(remoteIp, remotePort)
            startReceiver()
        } catch (e: Exception) {
            Log.e("RTP", "Failed to start RTP engine: ${e.message}")
            running = false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RFC 2833 / RFC 4733 In-Band RTP Telephone Event (Payload 101)
    // ─────────────────────────────────────────────────────────────────────────
    fun sendRtpDtmf(digit: Char) {
        val eventCode: Byte = when (digit) {
            in '0'..'9' -> (digit - '0').toByte()
            '*' -> 10
            '#' -> 11
            'A', 'a' -> 12
            'B', 'b' -> 13
            'C', 'c' -> 14
            'D', 'd' -> 15
            else -> return
        }

        Thread {
            try {
                val sock = rtpSocket ?: return@Thread
                if (remoteRtpIp.isBlank() || remoteRtpPort <= 0) return@Thread

                val targetAddr = java.net.InetAddress.getByName(remoteRtpIp)
                val targetPort = remoteRtpPort
                val eventTimestamp = timestamp

                // RFC 4733: Send 3 continuation packets sharing the same event timestamp
                for (i in 1..3) {
                    val packet = ByteArray(16)
                    packet[0] = 0x80.toByte()
                    // Marker bit M=1 ONLY on the first packet
                    packet[1] = if (i == 1) (101 or 0x80).toByte() else 101.toByte()
                    packet[2] = ((sequenceNumber shr 8) and 0xFF).toByte()
                    packet[3] = (sequenceNumber and 0xFF).toByte()
                    packet[4] = ((eventTimestamp shr 24) and 0xFF).toByte()
                    packet[5] = ((eventTimestamp shr 16) and 0xFF).toByte()
                    packet[6] = ((eventTimestamp shr 8) and 0xFF).toByte()
                    packet[7] = (eventTimestamp and 0xFF).toByte()
                    packet[8] = ((ssrc shr 24) and 0xFF).toByte()
                    packet[9] = ((ssrc shr 16) and 0xFF).toByte()
                    packet[10] = ((ssrc shr 8) and 0xFF).toByte()
                    packet[11] = (ssrc and 0xFF).toByte()

                    packet[12] = eventCode
                    packet[13] = 0x0A.toByte() // Volume 10 dBm, End bit 0

                    val dur = i * 320
                    packet[14] = ((dur shr 8) and 0xFF).toByte()
                    packet[15] = (dur and 0xFF).toByte()

                    sequenceNumber = (sequenceNumber + 1) and 0xFFFF
                    sock.send(java.net.DatagramPacket(packet, packet.size, targetAddr, targetPort))
                    Thread.sleep(20)
                }

                // Send 3 End packets (End bit E=1 set on bit 7)
                val finalDuration = 1280
                for (i in 1..3) {
                    val packet = ByteArray(16)
                    packet[0] = 0x80.toByte()
                    packet[1] = 101.toByte()
                    packet[2] = ((sequenceNumber shr 8) and 0xFF).toByte()
                    packet[3] = (sequenceNumber and 0xFF).toByte()
                    packet[4] = ((eventTimestamp shr 24) and 0xFF).toByte()
                    packet[5] = ((eventTimestamp shr 16) and 0xFF).toByte()
                    packet[6] = ((eventTimestamp shr 8) and 0xFF).toByte()
                    packet[7] = (eventTimestamp and 0xFF).toByte()
                    packet[8] = ((ssrc shr 24) and 0xFF).toByte()
                    packet[9] = ((ssrc shr 16) and 0xFF).toByte()
                    packet[10] = ((ssrc shr 8) and 0xFF).toByte()
                    packet[11] = (ssrc and 0xFF).toByte()

                    packet[12] = eventCode
                    packet[13] = 0x8A.toByte() // E=1 (bit 7 set)
                    packet[14] = ((finalDuration shr 8) and 0xFF).toByte()
                    packet[15] = (finalDuration and 0xFF).toByte()

                    sequenceNumber = (sequenceNumber + 1) and 0xFFFF
                    sock.send(java.net.DatagramPacket(packet, packet.size, targetAddr, targetPort))
                    Thread.sleep(10)
                }

                timestamp = (timestamp + finalDuration) and 0xFFFFFFFFL
            } catch (e: Exception) {
                android.util.Log.e("RTP_DTMF", "sendRtpDtmf error: ${e.message}")
            }
        }.start()
    }

    private fun startSender(remoteIp: String, remotePort: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("RTP", "RECORD_AUDIO not granted — mic disabled")
            return
        }

        val minBuf  = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, SAMPLES_PER_FRAME * 2 * 4)

        audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            )
        } catch (e: SecurityException) {
            Log.e("RTP", "SecurityException opening AudioRecord: ${e.message}")
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("RTP", "AudioRecord failed to initialize")
            return
        }

        audioRecord?.startRecording()

        sendThread = Thread {
            val pcmBuf    = ShortArray(SAMPLES_PER_FRAME)
            val rtpPacket = ByteArray(RTP_HEADER_SIZE + SAMPLES_PER_FRAME)

            while (running) {
                try {
                    // Skip sending voice frames while DTMF tone is actively transmitting
                    if (isSendingDtmf) {
                        Thread.sleep(10)
                        continue
                    }

                    val read = audioRecord?.read(pcmBuf, 0, SAMPLES_PER_FRAME) ?: break
                    if (read <= 0) continue

                    val remoteAddr = InetAddress.getByName(remoteRtpIp)

                    rtpPacket[0] = 0x80.toByte()
                    rtpPacket[1] = PAYLOAD_TYPE_PCMU.toByte()
                    rtpPacket[2] = ((sequenceNumber shr 8) and 0xFF).toByte()
                    rtpPacket[3] = (sequenceNumber and 0xFF).toByte()
                    rtpPacket[4] = ((timestamp shr 24) and 0xFF).toByte()
                    rtpPacket[5] = ((timestamp shr 16) and 0xFF).toByte()
                    rtpPacket[6] = ((timestamp shr 8) and 0xFF).toByte()
                    rtpPacket[7] = (timestamp and 0xFF).toByte()
                    rtpPacket[8] = ((ssrc shr 24) and 0xFF).toByte()
                    rtpPacket[9] = ((ssrc shr 16) and 0xFF).toByte()
                    rtpPacket[10] = ((ssrc shr 8) and 0xFF).toByte()
                    rtpPacket[11] = (ssrc and 0xFF).toByte()

                    for (i in 0 until read) {
                        rtpPacket[RTP_HEADER_SIZE + i] =
                            if (muted) encodeMulaw(0) else encodeMulaw(pcmBuf[i].toInt())
                    }

                    val payloadLen = RTP_HEADER_SIZE + read
                    rtpSocket?.send(DatagramPacket(rtpPacket, payloadLen, remoteAddr, remoteRtpPort))

                    sequenceNumber = (sequenceNumber + 1) and 0xFFFF
                    timestamp = (timestamp + SAMPLES_PER_FRAME) and 0xFFFFFFFFL

                } catch (e: Exception) {
                    if (running) Log.w("RTP_SEND", "Send error: ${e.message}")
                }
            }
        }.also {
            it.name     = "rtp-sender"
            it.priority = Thread.MAX_PRIORITY
            it.start()
        }
    }

    private fun startReceiver() {
        val minBuf  = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, SAMPLES_PER_FRAME * 2 * 8)

        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            bufSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            Log.e("RTP", "AudioTrack failed to initialize")
            return
        }

        audioTrack?.play()

        recvThread = Thread {
            val rawBuf = ByteArray(MAX_RTP_PACKET + 64)

            while (running) {
                try {
                    val pkt = DatagramPacket(rawBuf, rawBuf.size)
                    rtpSocket?.receive(pkt) ?: break

                    // Symmetric RTP: automatically learn Asterisk's actual remote media port
                    val senderIp = pkt.address?.hostAddress
                    if (!senderIp.isNullOrBlank() && (senderIp != remoteRtpIp || pkt.port != remoteRtpPort)) {
                        remoteRtpIp = senderIp
                        remoteRtpPort = pkt.port
                    }

                    val data = pkt.data
                    val len  = pkt.length
                    if (len < RTP_HEADER_SIZE) continue

                    val version = (data[0].toInt() and 0xC0) shr 6
                    if (version != 2) continue

                    val payloadType = data[1].toInt() and 0x7F
                    if (payloadType != PAYLOAD_TYPE_PCMU) continue

                    val cc        = data[0].toInt() and 0x0F
                    val hasExt    = (data[0].toInt() and 0x10) != 0
                    var offset    = RTP_HEADER_SIZE + cc * 4
                    if (hasExt && offset + 4 <= len) {
                        val extWords = ((data[offset + 2].toInt() and 0xFF) shl 8) or
                            (data[offset + 3].toInt() and 0xFF)
                        offset += 4 + extWords * 4
                    }
                    if (offset >= len) continue

                    val payloadLen = len - offset
                    val pcmOut    = ShortArray(payloadLen)
                    for (i in 0 until payloadLen) {
                        pcmOut[i] = decodeMulaw(data[offset + i])
                    }

                    if (!outputPaused) {
                        audioTrack?.write(pcmOut, 0, payloadLen)
                    }
                } catch (e: Exception) {
                    if (running) Log.w("RTP_RECV", "Recv error: ${e.message}")
                }
            }
        }.also {
            it.name     = "rtp-receiver"
            it.priority = Thread.MAX_PRIORITY
            it.start()
        }
    }

    fun setMuted(isMuted: Boolean) {
        muted = isMuted
    }

    fun setOutputPaused(paused: Boolean) {
        outputPaused = paused
        Log.d("RTP", "OutputPaused = $paused")
    }

    fun stop() {
        running = false
        try { rtpSocket?.close() } catch (_: Exception) {}
        rtpSocket = null
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        try { audioTrack?.stop();  audioTrack?.release()  } catch (_: Exception) {}
        audioRecord = null
        audioTrack  = null
        sendThread?.interrupt(); sendThread = null
        recvThread?.interrupt(); recvThread = null
    }

    private fun encodeMulaw(sample: Int): Byte {
        val BIAS = 0x84
        val MAX  = 32767
        var s    = sample.coerceIn(-MAX, MAX)
        val sign = if (s < 0) { s = -s; 0x80 } else 0
        s += BIAS
        val exp = when {
            s >= 32768 -> 7; s >= 16384 -> 6; s >= 8192 -> 5; s >= 4096 -> 4
            s >= 2048  -> 3; s >= 1024  -> 2; s >= 512  -> 1; else -> 0
        }
        val mantissa = (s shr (exp + 3)) and 0x0F
        return (sign or (exp shl 4) or mantissa).inv().toByte()
    }

    private fun decodeMulaw(byte: Byte): Short {
        val b        = byte.toInt().inv() and 0xFF
        val sign     = b and 0x80
        val exp      = (b shr 4) and 0x07
        val mantissa = b and 0x0F
        var linear   = ((mantissa shl 3) + 0x84) shl exp
        linear      -= 0x84
        return if (sign != 0) (-linear).toShort() else linear.toShort()
    }
}
