package com.callingapp.calling_app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callingapp.calling_app.api.ExtensionContact
import com.callingapp.calling_app.utils.CallEntry
import com.callingapp.calling_app.utils.CallLog
import com.callingapp.calling_app.utils.CallType
import android.content.Context
import android.content.Intent
@Composable
fun RecentCallsTab(
    myExtension: String,
    selectedTab: Int,
    allContacts: List<ExtensionContact> = emptyList(), // 👈 Added contacts list
    onCall: (String) -> Unit
) {
    val context = LocalContext.current

    // 0 = All Calls, 1 = Missed
    var filterType by remember { mutableIntStateOf(0) }

    var logsList by remember(myExtension) {
        CallLog.init(context, myExtension)
        mutableStateOf(CallLog.loadAll())
    }

    fun reloadLogs() {
        CallLog.init(context, myExtension)
        logsList = CallLog.loadAll()
    }

    // Listen for new incoming/missed call records in real-time while on this screen
    // Listen for new incoming/missed call records in real-time while on this screen
    DisposableEffect(myExtension) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                reloadLogs()
            }
        }
        val filter = android.content.IntentFilter("ACTION_MISSED_CALL_BADGE_UPDATE")
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    LaunchedEffect(selectedTab, myExtension) {
        reloadLogs()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reloadLogs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val filteredLogs = remember(logsList, filterType) {
        if (filterType == 1) {
            logsList.filter { it.type == CallType.MISSED }
        } else {
            logsList
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // ── Header & Filter Tabs ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Recents",
                color = Color(0xFF0F172A),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Your recent workspace audio logs",
                color = Color(0xFF0F172A).copy(alpha = 0.4f),
                fontSize = 12.sp
            )

            Spacer(Modifier.height(14.dp))

            // Clean Filter Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // All Calls Chip
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (filterType == 0) Color(0xFF14015E) else Color(0xFFF1F5F9),
                    modifier = Modifier.clickable { filterType = 0 }
                ) {
                    Text(
                        text = "All Calls",
                        color = if (filterType == 0) Color.White else Color(0xFF0F172A).copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = if (filterType == 0) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // Missed Chip
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (filterType == 1) Color(0xFFEF4444) else Color(0xFFF1F5F9),
                    modifier = Modifier.clickable { filterType = 1 }
                ) {
                    Text(
                        text = "Missed",
                        color = if (filterType == 1) Color.White else Color(0xFF0F172A).copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = if (filterType == 1) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (filterType == 1) "No missed calls found" else "No recent call activity found",
                    color = Color(0xFF0F172A).copy(alpha = 0.4f),
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredLogs) { logEntry ->
                    RecentCallCard(
                        entry = logEntry,
                        allContacts = allContacts,
                        onCardClick = { onCall(logEntry.number) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentCallCard(
    entry: CallEntry,
    allContacts: List<ExtensionContact>,
    onCardClick: () -> Unit
) {
    val displayExtensionNumber = remember(entry.number) {
        if (entry.number.startsWith("1001") && entry.number.length > 4) {
            entry.number.drop(4)
        } else {
            entry.number
        }
    }

    // ✅ RESOLVE CONTACT NAME: If entry.number matches a saved contact, show that Name!
    val displayCardName = remember(entry.name, entry.number, allContacts) {
        val matchedContact = allContacts.find { contact ->
            val cleanContactExt = if (contact.extension.startsWith("1001") && contact.extension.length > 4) {
                contact.extension.drop(4)
            } else {
                contact.extension
            }
            cleanContactExt == displayExtensionNumber
        }

        when {
            matchedContact != null && matchedContact.name.isNotBlank() -> matchedContact.name
            entry.name.isNotBlank() && entry.name != entry.number && !entry.name.contains("1001${entry.number}") -> entry.name
            else -> "Ext $displayExtensionNumber"
        }
    }

    val isMissed = entry.type == CallType.MISSED
    val statusColor = when (entry.type) {
        CallType.INCOMING -> Color(0xFF3B82F6)
        CallType.OUTGOING -> Color(0xFF14015E)
        CallType.MISSED   -> Color(0xFFEF4444)
    }

    val statusIcon = when (entry.type) {
        CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
        CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
        CallType.MISSED   -> Icons.Default.PhoneMissed
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isMissed) Color(0xFFEF4444).copy(alpha = 0.03f) else Color(0xFFF8FAFC),
        border = if (isMissed) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.08f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayCardName,
                    color = if (isMissed) Color(0xFFEF4444) else Color(0xFF0F172A),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Ext $displayExtensionNumber",
                        color = Color(0xFF0F172A).copy(alpha = 0.45f),
                        fontSize = 12.sp
                    )

                    if (entry.durationSeconds > 0 && !isMissed) {
                        Text(
                            text = "  ·  ${entry.formattedDuration()}",
                            color = Color(0xFF0F172A).copy(alpha = 0.35f),
                            fontSize = 12.sp
                        )
                    }

                    if (isMissed) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "MISSED",
                                color = Color(0xFFEF4444),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = entry.formattedTime(),
                color = Color(0xFF0F172A).copy(alpha = 0.3f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.width(8.dp))

            Surface(
                shape = CircleShape,
                color = Color(0xFF14015E).copy(alpha = 0.1f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Quick Call Back",
                        tint = Color(0xFF14015E),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}