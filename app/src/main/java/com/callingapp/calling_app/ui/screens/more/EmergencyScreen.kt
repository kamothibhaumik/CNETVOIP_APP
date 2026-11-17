package com.callingapp.calling_app.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callingapp.calling_app.api.EmergencyCall
import com.callingapp.calling_app.api.apiService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyScreen(
    extension: String,
    password: String,
    tenantCode: String,
    paddingValues: PaddingValues, // 👈 ✅ ADD THIS LAYOUT ACCEPTER PARAMETER
    onBack: () -> Unit
) {
    val scope     = rememberCoroutineScope()
    var calls     by remember { mutableStateOf<List<EmergencyCall>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg  by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                val res = apiService.getEmergencyCalls(extension)
                if (res.isSuccessful && res.body()?.status == true) {
                    calls = res.body()?.data ?: emptyList()
                    if (calls.isEmpty()) errorMsg = "Emergency Report Unavailable"
                } else {
                    errorMsg = "Emergency Report Unavailable"
                }
            } catch (e: Exception) {
                errorMsg = "Emergency Report Unavailable"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(Color(0xFFFFFFFF))
    ) {
        // ✅ HIGH POSITIONING FIX: Customized TopAppBar bounds to pull your menu name higher up the screen!
        TopAppBar(
            title = {
                Text(
                    text = "Emergency Call Report",
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp // Clean, tight typography profile
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
            windowInsets = WindowInsets(0.dp) // ✅ Removes any leftover default status bar padding windows pushing text down
        )

        // Info banner (Shifted up with tight vertical margins)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp), // Reduced from 8.dp
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFEF4444).copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier.padding(10.dp), // Compact internal padding
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Emergency, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Showing 911 & 933 emergency calls for your tenant",
                    color    = Color(0xFFEF4444).copy(alpha = 0.9f),
                    fontSize = 12.sp
                )
            }
        }

        // Refresh row (Shifted up with tight vertical margins)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp), // Reduced from 4.dp
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${calls.size} record${if (calls.size != 1) "s" else ""}",
                color = Color(0xFF0F172A).copy(alpha = 0.35f), fontSize = 12.sp
            )
            IconButton(onClick = { load() }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = Color(0xFFEF4444))
            }
        }

        when {
            isLoading -> LoadingBox()
            errorMsg != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(errorMsg!!, color = Color(0xFF0F172A).copy(0.4f), fontSize = 14.sp)
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(calls, key = { (it.id.toString()).let { id -> if (id.isBlank()) it.date + it.extension else id } }) { call ->
                    EmergencyCallRow(call)
                }
            }
        }
    }
}

@Composable
private fun EmergencyCallRow(call: EmergencyCall) {
    val displayNum = call.number.substringAfterLast("-")

    // ✅ FIXED: Removes the first 4 digits if the string length is greater than 4,
    // otherwise shows the full string as fallback (showing all remaining digits).
    val rawExt = try {
        call.dcontext.substringAfter("-").substringBefore("-")
    } catch (e: Exception) {
        call.extension
    }

    val displayExt = if (rawExt.length > 4) rawExt.drop(4) else rawExt

    val displayName = call.callerId.substringBefore(" <").replace("\"", "")
    val color = Color(0xFFEF4444)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(52.dp), shape = CircleShape, color = color.copy(0.12f)) {
            Box(contentAlignment = Alignment.Center) {
                Text(displayNum, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayName.ifBlank { "Extension $displayExt" },
            color = Color(0xFF0F172A), fontSize = 15.sp, fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Extension $displayExt · ", color = Color(0xFF0F172A).copy(0.35f), fontSize = 12.sp)

                val statusColor = if (call.status == "ANSWERED") Color(0xFF10B981) else color
                Surface(shape = RoundedCornerShape(4.dp), color = statusColor.copy(0.15f)) {
                    Text(
                        call.status, color = statusColor, fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(" · ${formatDuration(call.duration)}", color = Color(0xFF0F172A).copy(0.35f), fontSize = 12.sp)
            }
            Text(formatDate(call.date), color = Color(0xFF0F172A).copy(0.3f), fontSize = 11.sp)
        }
    }
    HorizontalDivider(color = Color(0xFF0F172A).copy(0.06f), modifier = Modifier.padding(start = 78.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared helpers
// ─────────────────────────────────────────────────────────────────────────────

fun formatDuration(seconds: Int): String {
    val m = seconds / 60; val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

fun formatDate(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = sdf.parse(dateStr) ?: return dateStr
        val out = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
        out.format(date)
    } catch (_: Exception) { dateStr }
}

@Composable
fun LoadingBox(label: String = "Loading…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFFEF4444))
            Spacer(Modifier.height(12.dp))
            Text(label, color = Color(0xFF0F172A).copy(alpha = 0.5f), fontSize = 13.sp)
        }
    }
}