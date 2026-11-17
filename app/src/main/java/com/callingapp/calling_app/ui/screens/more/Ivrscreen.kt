package com.callingapp.calling_app.ui.screens.more

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callingapp.calling_app.api.IvrMenu
import com.callingapp.calling_app.api.IvrAction
import com.callingapp.calling_app.api.VoicePrompt
import com.callingapp.calling_app.api.apiService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IvrScreen(
    extension: String,
    paddingValues: PaddingValues, // 👈 ✅ ADD THIS LAYOUT ACCEPTER PARAMETER
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var menus by remember { mutableStateOf<List<IvrMenu>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // --- In-App Audio State ---
    var playingUrl by remember { mutableStateOf<String?>(null) }
    val mediaPlayer = remember { MediaPlayer() }

    // Function to handle internal playback
    fun togglePlayback(url: String) {
        try {
            if (playingUrl == url) {
                mediaPlayer.stop()
                mediaPlayer.reset()
                playingUrl = null
            } else {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(url)
                mediaPlayer.prepareAsync()
                mediaPlayer.setOnPreparedListener {
                    it.start()
                }
                mediaPlayer.setOnCompletionListener {
                    playingUrl = null
                }
                playingUrl = url
            }
        } catch (e: Exception) {
            Log.e("IVR", "Playback error: ${e.message}")
            playingUrl = null
        }
    }

    // Release player resources when screen is closed
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    fun load() {
        scope.launch {
            isLoading = true; errorMsg = null
            try {
                val res = apiService.getIvrMenus(extension)
                if (res.isSuccessful && res.body()?.status == true) {
                    val data = res.body()?.data ?: emptyList()
                    if (data.isEmpty()) errorMsg = "IVR Menu Unavailable"
                    else menus = data
                } else {
                    errorMsg = "IVR Menu Unavailable"
                }
            } catch (e: Exception) {
                errorMsg = "IVR Menu Unavailable"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues) // 👈 ✅ APPLY THE INSETS PADDING BOUNDS HERE
            .background(Color.White)
    ) {
        // High positioning setup with window insets zeroed out
        TopAppBar(
            title = {
                Text(
                    text = "IVR / Auto-Attendant",
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF0F172A)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            windowInsets = WindowInsets(0.dp) // ✅ Removes upper layout spacer bars
        )

        // Count / Refresh Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${menus.size} IVR menus", color = Color(0xFF0F172A).copy(0.35f), fontSize = 12.sp)
            IconButton(onClick = { load() }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = Color(0xFFF59E0B))
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFF59E0B))
            }
        } else if (errorMsg != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorMsg!!, color = Color(0xFF0F172A).copy(0.4f), fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(menus, key = { it.id }) { menu ->
                    IvrMenuCard(
                        menu = menu,
                        playingUrl = playingUrl,
                        onPlayToggle = { togglePlayback(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun IvrMenuCard(
    menu: IvrMenu,
    playingUrl: String?,
    onPlayToggle: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = Color(0xFFF59E0B)

    val activeActions = menu.actions.filter { it.value.type != null && it.value.type != "none" }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF8FAFC)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = accent.copy(0.15f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SettingsPhone, null, tint = accent)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(menu.name, color = Color(0xFF0F172A), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Extension: ${menu.number} · ${activeActions.size} active actions",
                        color = Color(0xFF0F172A).copy(0.35f), fontSize = 12.sp)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color(0xFF0F172A).copy(0.3f))
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    HorizontalDivider(color = Color(0xFF0F172A).copy(alpha = 0.08f))

                    Text("Voice Prompts", color = Color(0xFF0F172A).copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        menu.prompts?.welcome?.let {
                            PromptItem("Welcome", it, accent, playingUrl == it.audioUrl, onPlayToggle)
                        }
                        menu.prompts?.repeat?.let {
                            PromptItem("Repeat", it, accent, playingUrl == it.audioUrl, onPlayToggle)
                        }
                    }

                    HorizontalDivider(color = Color(0xFF0F172A).copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))

                    Text("Key Actions", color = Color(0xFF0F172A).copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))

                    activeActions.forEach { (key, action) ->
                        IvrKeyRow(key, action, accent)
                    }

                    Surface(
                        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.03f)
                    ) {
                        Text(
                            "Digit Timeout: ${menu.settings?.digitTimeout}s · Direct Extension Dial: ${if(menu.settings?.directDial == true) "Yes" else "No"}",
                            modifier = Modifier.padding(8.dp),
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptItem(
    label: String,
    prompt: VoicePrompt,
    accent: Color,
    isCurrentlyPlaying: Boolean,
    onPlayToggle: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.05f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = Color(0xFF0F172A).copy(alpha = 0.4f), fontSize = 10.sp)
                Text(prompt.name, color = Color(0xFF0F172A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (prompt.audioUrl.isNotEmpty()) {
                IconButton(onClick = { onPlayToggle(prompt.audioUrl) }) {
                    Icon(
                        if (isCurrentlyPlaying) Icons.Default.Stop else Icons.Default.PlayCircle,
                        null,
                        tint = accent
                    )
                }
            }
        }
    }
}

@Composable
private fun IvrKeyRow(key: String, action: IvrAction, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(8.dp), color = accent.copy(0.2f)) {
            Box(contentAlignment = Alignment.Center) {
                Text(key, color = Color(0xFF0F172A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color(0xFF0F172A).copy(0.2f), modifier = Modifier.padding(horizontal = 12.dp))
        Column {
            val title = if (action.displayName == "None" || action.displayName.isNullOrBlank()) {
                action.targetId ?: "Action"
            } else {
                action.displayName
            }
            Text(title, color = Color(0xFF0F172A), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("Type: ${action.type} ${action.details?.extension?.let { "· Ext: $it" } ?: ""}",
                color = Color(0xFF0F172A).copy(0.4f), fontSize = 11.sp)
        }
    }
}