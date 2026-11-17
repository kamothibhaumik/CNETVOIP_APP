package com.callingapp.calling_app.ui.screens.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callingapp.calling_app.api.ExtensionContact
import com.callingapp.calling_app.api.apiService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DndScreen(
    tenantCode: String,
    extension: String,
    password: String,
    paddingValues: PaddingValues,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var extensionsList by remember { mutableStateOf<List<ExtensionContact>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Keyed by extension number → current DND bool
    val dndStates = remember { mutableStateMapOf<String, Boolean>() }

    fun load() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = apiService.getContacts(extension)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.status) {
                        extensionsList = body.data
                        body.data.forEach { contact ->
                            dndStates[contact.extension] = contact.dndStatus
                        }
                    } else {
                        errorMessage = "No data returned from server."
                    }
                } else {
                    errorMessage = "Server error: ${response.code()}"
                }
            } catch (e: Exception) {
                errorMessage = "Network error: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(Color.White)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Do Not Disturb",
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
            actions = {
                IconButton(onClick = { load() }) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = Color(0xFF0F172A))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            windowInsets = WindowInsets(0.dp)
        )

        // Only show DND count chip (Removes "Extensions" and "Available" chips)
        if (extensionsList.isNotEmpty()) {
            val dndCount = dndStates.values.count { it }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                StatChip(
                    label = "$dndCount DND Active",
                    color = if (dndCount > 0) Color(0xFFEF4444) else Color(0xFF64748B)
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.06f)
        )

        Spacer(Modifier.height(4.dp))

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF0F172A))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Loading extensions...",
                            color = Color(0xFF0F172A).copy(alpha = 0.4f),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            errorMessage != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorMessage ?: "Unknown error",
                            color = Color(0xFFEF4444),
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { load() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                        ) {
                            Text("Retry", color = Color.White)
                        }
                    }
                }
            }

            extensionsList.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No extensions found.",
                        color = Color(0xFF0F172A).copy(alpha = 0.4f),
                        fontSize = 14.sp
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(extensionsList, key = { it.extension }) { contact ->
                        val isDnd = dndStates[contact.extension] ?: contact.dndStatus
                        DndExtensionCard(
                            contact = contact,
                            isDnd = isDnd
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Extension card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DndExtensionCard(
    contact: ExtensionContact,
    isDnd: Boolean,
) {
    // Device status dot color
    val dotColor = when (contact.deviceStatus.lowercase()) {
        "online"  -> Color(0xFF0F172A)
        "offline" -> Color(0xFF94A3B8)
        "dnd"     -> Color(0xFFEF4444)
        "busy"    -> Color(0xFFF59E0B)
        else      -> Color(0xFF94A3B8)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDnd) Color(0xFFFFF1F2) else Color(0xFFF8FAFC)
        ),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (isDnd) Color(0xFFEF4444).copy(alpha = 0.2f)
            else Color(0xFF0F172A).copy(alpha = 0.04f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Avatar + info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Avatar with device status ring
                Box(contentAlignment = Alignment.BottomEnd) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = CircleShape,
                        color = if (isDnd)
                            Color(0xFFEF4444).copy(alpha = 0.12f)
                        else
                            Color(0xFF0F172A).copy(alpha = 0.06f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = contact.name.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = if (isDnd) Color(0xFFEF4444) else Color(0xFF0F172A),
                                fontSize = 17.sp
                            )
                        }
                    }
                    // Device status dot
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(1.5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        text = contact.name,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A),
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Ext ${contact.extension}",
                        fontSize = 11.sp,
                        color = Color(0xFF0F172A).copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(3.dp))
                    // Device status badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = dotColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = contact.deviceStatus.replaceFirstChar { it.uppercase() },
                            color = dotColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // DND Badge - Only show when DND is active (LIVE badge removed)
            if (isDnd) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "DND ON",
                        color = Color(0xFFEF4444),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stats chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.10f)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
