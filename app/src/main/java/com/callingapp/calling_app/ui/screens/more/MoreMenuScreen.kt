package com.callingapp.calling_app.ui.screens.more

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// Which sub-screen is open inside More
// ─────────────────────────────────────────────────────────────────────────────

enum class MoreDestination {
    NONE, ALL_EXTENSION_CDRS, DND, EMERGENCY, FAX, VOICEMAIL, WAKE_UP
}

// ─────────────────────────────────────────────────────────────────────────────
// MoreMenuScreen — hub that routes to sub-screens
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MoreMenuScreen(
    extension: String,
    password: String,
    tenantCode: String,
    trunkName: String,
    callerId: String,
    paddingValues: PaddingValues
) {
    var destination by remember { mutableStateOf(MoreDestination.NONE) }
    val scrollState = rememberScrollState()

    // Intercept the system back button
    BackHandler(enabled = destination != MoreDestination.NONE) {
        destination = MoreDestination.NONE
    }

    when (destination) {
        MoreDestination.NONE -> {
            MoreHub(
                paddingValues = paddingValues,
                scrollState = scrollState,
                onNavigate = { chosenDestination ->
                    destination = chosenDestination
                }
            )
        }

        MoreDestination.ALL_EXTENSION_CDRS -> TenantCallHistoryScreen(
            extension = extension,
            onBack = { destination = MoreDestination.NONE }
        )

        MoreDestination.DND -> DndScreen(
            tenantCode = tenantCode,
            extension = extension,
            password = password,
            paddingValues = paddingValues,
            onBack = { destination = MoreDestination.NONE }
        )

        MoreDestination.EMERGENCY -> EmergencyScreen(
            extension = extension,
            password = password,
            tenantCode = tenantCode,
            paddingValues = paddingValues,
            onBack = { destination = MoreDestination.NONE }
        )

        MoreDestination.FAX -> FaxScreen(
            extension = extension,
            password = password,
            tenantCode = tenantCode,
            trunkName = trunkName,
            callerId = callerId,
            paddingValues = paddingValues,
            onBack = { destination = MoreDestination.NONE }
        )

        MoreDestination.VOICEMAIL -> VoicemailScreen(
            extension = extension,
            password = password,
            tenantCode = tenantCode,
            paddingValues = paddingValues,
            onBack = { destination = MoreDestination.NONE }
        )

        MoreDestination.WAKE_UP -> WakeUpScreen(
            extension = extension,
            paddingValues = paddingValues,
            onBack = { destination = MoreDestination.NONE }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hub grid — Alphabetical Feature dashboard cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MoreHub(
    paddingValues: PaddingValues,
    scrollState: ScrollState,
    onNavigate: (MoreDestination) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(Color(0xFFFFFFFF))
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text("More", color = Color(0xFF0F172A), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Office Features", color = Color(0xFF0F172A).copy(0.4f), fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))

        // 1. All Extension Call History (A)
        MoreCard(
            icon        = Icons.Default.Assessment,
            iconBg      = Color(0xFF001170),
            title       = "Call History",
            description = "Live PBX workspace call tracking and CDR logs",
            badge       = "Call History",
            badgeColor  = Color(0xFF001170),
            onClick     = { onNavigate(MoreDestination.ALL_EXTENSION_CDRS) }
        )
        Spacer(Modifier.height(14.dp))

        // 2. Do Not Disturb (D)
        MoreCard(
            icon        = Icons.Default.DoNotDisturbOn,
            iconBg      = Color(0xFF0F172A),
            title       = "Do Not Disturb (DND)",
            description = "Mute inbound routing instantly across configurations",
            badge       = "Routing Guard",
            badgeColor  = Color(0xFF0F172A),
            onClick     = { onNavigate(MoreDestination.DND) }
        )
        Spacer(Modifier.height(14.dp))

        // 3. Emergency Call Report (E)
        MoreCard(
            icon        = Icons.Default.Emergency,
            iconBg      = Color(0xFFEF4444),
            title       = "Emergency Call Report",
            description = "View 911 and 933 emergency calls made by your tenant",
            badge       = "911 / 933",
            badgeColor  = Color(0xFFEF4444),
            onClick     = { onNavigate(MoreDestination.EMERGENCY) }
        )
        Spacer(Modifier.height(14.dp))

        // 4. Fax (F)
        MoreCard(
            icon        = Icons.Default.Fax,
            iconBg      = Color(0xFF6366F1),
            title       = "Fax",
            description = "Send faxes and view your fax history",
            badge       = "Send & Receive",
            badgeColor  = Color(0xFF6366F1),
            onClick     = { onNavigate(MoreDestination.FAX) }
        )
        Spacer(Modifier.height(14.dp))

        // 5. Voicemail (V)
        MoreCard(
            icon        = Icons.Default.Voicemail,
            iconBg      = Color(0xFF10B981),
            title       = "Voicemail",
            description = "Listen to recorded voicemail messages",
            badge       = "Recordings",
            badgeColor  = Color(0xFF10B981),
            onClick     = { onNavigate(MoreDestination.VOICEMAIL) }
        )
        Spacer(Modifier.height(14.dp))

        // 6. Wake up call (W)
        MoreCard(
            icon        = Icons.Default.Alarm,
            iconBg      = Color(0xFFEC4899),
            title       = "Wake up call",
            description = "Schedule automated reminder calls",
            badge       = "Scheduler",
            badgeColor  = Color(0xFFEC4899),
            onClick     = { onNavigate(MoreDestination.WAKE_UP) }
        )
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun MoreCard(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    description: String,
    badge: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier  = Modifier.fillMaxWidth().clickable { onClick() },
        shape     = RoundedCornerShape(20.dp),
        color     = Color(0xFFF8FAFC),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape    = CircleShape,
                color    = iconBg.copy(alpha = 0.18f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = iconBg, modifier = Modifier.size(28.dp))
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color(0xFF0F172A), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(description, color = Color(0xFF0F172A).copy(alpha = 0.45f), fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        badge,
                        color    = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                null,
                tint     = Color(0xFF0F172A).copy(alpha = 0.25f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared top bar for sub-screens
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreenTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, color = Color(0xFF0F172A), fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFF0F172A))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8FAFC))
    )
}