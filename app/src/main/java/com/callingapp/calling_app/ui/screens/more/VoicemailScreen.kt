package com.callingapp.calling_app.ui.screens.more

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.font.FontStyle
import com.callingapp.calling_app.api.VoicemailRecord
import com.callingapp.calling_app.api.apiService

// ─────────────────────────────────────────────────────────────────────────────
// VoicemailScreen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicemailScreen(
    extension: String,
    password: String,
    tenantCode: String,
    paddingValues: PaddingValues, // 👈 ✅ ADD THIS LAYOUT ACCEPTER PARAMETER
    onBack: () -> Unit
) {
    val scope     = rememberCoroutineScope()
    var messages  by remember { mutableStateOf<List<VoicemailRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg  by remember { mutableStateOf<String?>(null) }

    // Which voicemail is currently playing (by id)
    var playingId by remember { mutableStateOf<String?>(null) }
    // MediaPlayer instance — one at a time
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopPlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        playingId   = null
    }

    // Clean up on leave
    DisposableEffect(Unit) {
        onDispose { stopPlayer() }
    }

    fun load() {
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                val res = apiService.getVoicemail(extension, tenantCode, password)
                if (res.isSuccessful) {
                    messages = res.body()?.data ?: emptyList()
                    if (messages.isEmpty()) errorMsg = "Voicemail Not Found"
                } else {
                    errorMsg = "Voicemail Not Found"
                }
            } catch (e: Exception) {
                errorMsg = "Voicemail Not Found"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues) // 👈 ✅ APPLY THE INSETS PADDING HERE
            .background(Color(0xFFFFFFFF))
    ) {
        // ✅ HIGH POSITIONING FIX: White layout canvas alignment with completely stripped window block insets
        TopAppBar(
            title = {
                Text(
                    text = "Voicemail",
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
            },
            navigationIcon = {
                IconButton(onClick = { stopPlayer(); onBack() }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack, // ✅ Core matching standard icon
                        contentDescription = "Back",
                        tint = Color(0xFF0F172A)
                    )
                }
            },
            actions = {
                // Unread count badge
                val unread = messages.count { !it.isRead }
                if (unread > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            "$unread new",
                            color    = Color(0xFF10B981),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                IconButton(onClick = { stopPlayer(); load() }) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = Color(0xFF10B981))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White), // ✅ Reset color block tracking
            windowInsets = WindowInsets(0.dp) // ✅ Pulls layout higher upward
        )

        // Count / Refresh metrics layout line (shifted up)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp), // ✅ Balanced compact padding
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${messages.size} message${if (messages.size != 1) "s" else ""}", color = Color(0xFF0F172A).copy(0.35f), fontSize = 12.sp)
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color(0xFF10B981),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Loading voicemail…",
                            color = Color(0xFF10B981),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            errorMsg != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = errorMsg!!,
                        color = Color(0xFF0F172A).copy(0.45f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 3. No Data Found State
            messages.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Voicemail Not Found",
                        color = Color(0xFF0F172A).copy(0.45f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(messages, key = { it.id.let { id -> if (id.isBlank()) it.date + it.fromNumber else id } }) { vm ->
                    VoicemailRow(
                        vm        = vm,
                        isPlaying = playingId == vm.id,
                        onPlay    = {
                            if (playingId == vm.id) {
                                stopPlayer()
                            } else {
                                stopPlayer()
                                if (vm.audioUrl.isNotBlank()) {
                                    playingId = vm.id
                                    scope.launch {
                                        try {
                                            val mp = MediaPlayer().apply {
                                                setDataSource(vm.audioUrl)
                                                prepareAsync()
                                                setOnPreparedListener { start() }
                                                setOnCompletionListener { stopPlayer() }
                                                setOnErrorListener { _, _, _ -> stopPlayer(); true }
                                            }
                                            mediaPlayer = mp
                                            try {
                                                apiService.markVoicemailRead(
                                                    mapOf("id" to vm.id, "extension" to extension, "password" to password)
                                                )
                                                messages = messages.map {
                                                    if (it.id == vm.id) it.copy(isRead = true) else it
                                                }
                                            } catch (_: Exception) {}
                                        } catch (e: Exception) {
                                            stopPlayer()
                                            Log.e("Voicemail", "Play error: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

// Inside VoicemailScreen.kt -> Add this at the very bottom of your file

@Composable
private fun PlayingWaveform() {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val heights = listOf(
        infiniteTransition.animateFloat(
            initialValue = 4f, targetValue = 14f, label = "b1",
            animationSpec = infiniteRepeatable(
                tween(400), RepeatMode.Reverse
            )
        ),
        infiniteTransition.animateFloat(
            initialValue = 10f, targetValue = 4f, label = "b2",
            animationSpec = infiniteRepeatable(
                tween(300, delayMillis = 100),
                RepeatMode.Reverse
            )
        ),
        infiniteTransition.animateFloat(
            initialValue = 6f, targetValue = 16f, label = "b3",
            animationSpec = infiniteRepeatable(
                tween(500, delayMillis = 50),
                RepeatMode.Reverse
            )
        ),
        infiniteTransition.animateFloat(
            initialValue = 14f, targetValue = 6f, label = "b4",
            animationSpec = infiniteRepeatable(
                tween(350, delayMillis = 150),
                RepeatMode.Reverse
            )
        ),
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(20.dp)
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h.value.dp)
                    .background(Color(0xFF10B981), RoundedCornerShape(2.dp))
            )
        }
        Spacer(Modifier.width(6.dp))
        Text("Playing…", color = Color(0xFF10B981), fontSize = 11.sp)
    }
}

@Composable
private fun VoicemailRow(
    vm: VoicemailRecord,
    isPlaying: Boolean,
    onPlay: () -> Unit
) {
    val accentColor = Color(0xFF10B981)
    val isUnread    = !vm.isRead

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isUnread) Color(0xFF0F172A).copy(alpha = 0.025f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Surface(
                modifier = Modifier.size(52.dp),
                shape    = CircleShape,
                color    = accentColor.copy(alpha = if (isPlaying) 0.25f else 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        (vm.fromName.firstOrNull() ?: vm.fromNumber.firstOrNull() ?: 'V')
                            .toString().uppercase(),
                        color      = accentColor,
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (isUnread) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(0xFFEF4444), CircleShape)
                        .align(Alignment.TopEnd)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    vm.fromName.ifBlank { vm.fromNumber },
                    color      = Color(0xFF0F172A),
                    fontSize   = 15.sp,
                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f)
                )
                if (isUnread) {
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFEF4444).copy(alpha = 0.15f)) {
                        Text("New", color = Color(0xFFEF4444), fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.fromName.isNotBlank() && vm.fromNumber.isNotBlank()) {
                    Text("${vm.fromNumber} · ", color = Color(0xFF0F172A).copy(alpha = 0.35f), fontSize = 12.sp)
                }
                Text(
                    text = vm.durationStr,
                    color = Color(0xFF0F172A).copy(alpha = 0.45f),
                    fontSize = 12.sp
                )
            }

            if (vm.transcript.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    vm.transcript,
                    color    = Color(0xFF0F172A).copy(alpha = 0.35f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontStyle = FontStyle.Italic
                )
            }

            Text(formatDate(vm.date), color = Color(0xFF0F172A).copy(alpha = 0.3f), fontSize = 11.sp)

            if (isPlaying) {
                Spacer(Modifier.height(6.dp))
                PlayingWaveform()
            }
        }

        Spacer(Modifier.width(8.dp))

        Surface(
            onClick  = onPlay,
            shape    = CircleShape,
            color    = if (isPlaying) accentColor.copy(alpha = 0.3f) else accentColor.copy(alpha = 0.15f),
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    tint     = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    HorizontalDivider(color = Color(0xFF0F172A).copy(alpha = 0.06f), modifier = Modifier.padding(start = 78.dp))
}