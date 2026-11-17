package com.callingapp.calling_app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callingapp.calling_app.api.ExtensionContact
import com.callingapp.calling_app.api.apiService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun ContactsTab(
    myExtension: String,
    onCall: (String) -> Unit
) {
    var contacts   by remember { mutableStateOf<List<ExtensionContact>>(emptyList()) }
    var isLoading  by remember { mutableStateOf(true) }
    var errorMsg   by remember { mutableStateOf<String?>(null) }
    var searchText by remember { mutableStateOf("") }
    val scope      = rememberCoroutineScope()

    // Live status: initial load shows spinner, then polls every 3s in background
    // No delay before first poll - status updates happen behind the scenes without UI disruption
    LaunchedEffect(myExtension) {
        // First load: show spinner until we have data
        fetchContacts(myExtension,
            onResult = { contacts = it; isLoading = false },
            onError  = { errorMsg = it; isLoading = false }
        )
        // Background poll every 3s: only update status fields, no list rebuild, no flicker
        while (isActive) {
            delay(3_000L)
            fetchContactsStatusOnly(myExtension) { updatedList ->
                // Merge: keep existing contacts order/names, only patch the status field
                contacts = contacts.map { existing ->
                    updatedList.find { it.extension == existing.extension }
                        ?.let { existing.copy(deviceStatus = it.deviceStatus) }
                        ?: existing
                }
            }
        }
    }

    // Filter logic: Search + Exclude Self
    // Inside ContactsTab.kt -> Replace your existing filtered block with this:

    // Filter logic: Search + Exclude Self dynamically across all tenant IDs
    val filtered = remember(contacts, searchText) {
        // ✅ FIXED: Slices off any 4-digit tenant prefix dynamically (like 1001, 1003, etc.)
        val cleanMyExtension = if (myExtension.length > 4 && myExtension.matches(Regex("^\\d{4}.*"))) {
            myExtension.drop(4)
        } else {
            myExtension
        }

        contacts.filter { contact ->
            // Now "contact.extension" (which is already short like "701")
            // will match your "cleanMyExtension" ("701") perfectly!
            contact.extension != cleanMyExtension &&
                    (searchText.isBlank() ||
                            contact.name.contains(searchText, ignoreCase = true) ||
                            contact.extension.contains(searchText))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Search Bar
        OutlinedTextField(
            value         = searchText,
            onValueChange = { searchText = it },
            placeholder   = { Text("Search name or extension…", color = Color(0xFF0F172A).copy(alpha = 0.35f), fontSize = 14.sp) },
            leadingIcon   = { Icon(Icons.Default.Search, null, tint = Color(0xFF0F172A).copy(alpha = 0.35f)) },
            trailingIcon  = {
                IconButton(onClick = {
                    scope.launch {
                        isLoading = true; errorMsg = null
                        fetchContacts(myExtension,
                            onResult = { contacts = it; isLoading = false },
                            onError  = { errorMsg = it; isLoading = false }
                        )
                    }
                }) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = Color(0xFF14015E))
                }
            },
            singleLine = true,
            modifier   = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            shape      = RoundedCornerShape(14.dp),
            colors     = OutlinedTextFieldDefaults.colors(
                focusedTextColor     = Color(0xFF0F172A),
                unfocusedTextColor   = Color(0xFF0F172A),
                focusedBorderColor   = Color(0xFF14015E),
                unfocusedBorderColor = Color(0xFF0F172A).copy(alpha = 0.2f),
                cursorColor          = Color(0xFF14015E)
            )
        )

        // UI State Handling
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF14015E))
            }
            errorMsg != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorMsg!!, color = Color.Red, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        scope.launch {
                            isLoading = true; errorMsg = null
                            fetchContacts(myExtension,
                                onResult = { contacts = it; isLoading = false },
                                onError  = { errorMsg = it; isLoading = false }
                            )
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14015E))) {
                        Text("Retry")
                    }
                }
            }
            else -> {
                Text(
                    "${filtered.size} other extensions",
                    color    = Color(0xFF0F172A).copy(alpha = 0.35f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { it.extension }) { contact ->
                        ContactRow(contact, onCall)
                    }
                }
            }
        }
    }
}

// Inside ContactsTab.kt -> Replace the top section of ContactRow with this update:

@Composable
fun ContactRow(contact: ExtensionContact, onCall: (String) -> Unit) {
    // ✅ IMPROVED STATUS MAPPING: Captures variants from your cloud server metrics
    val statusTextLower = contact.deviceStatus.lowercase().trim()

    // \u2705 Matches actual API values from device_status field: "online" | "offline"
    val isOnline = statusTextLower == "online" ||
            statusTextLower == "available" ||
            statusTextLower == "registered" ||
            statusTextLower == "active"

    val isDnd = statusTextLower == "dnd" ||
            statusTextLower == "do not disturb" ||
            statusTextLower == "busy" ||
            statusTextLower == "on call"

    val statusColor = when {
        isOnline -> Color(0xFF14015E) // ✅ Signature Green (e.g., for 4007 and 503)
        isDnd    -> Color(0xFFF59E0B) // ✅ Amber/Yellow for Do Not Disturb
        else     -> Color(0xFF94A3B8) // ✅ Slate Gray for Offline
    }

    val displayStatusText = when {
        isOnline -> "ONLINE"
        isDnd    -> "BUSY (DND)"
        else     -> "OFFLINE"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCall(contact.extension) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar + Status Badge Dot
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                shape = CircleShape,
                color = statusColor.copy(alpha = 0.08f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = contact.name.take(1).uppercase(),
                        color = Color(0xFF0F172A),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Status Dot Indicator
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .background(Color.White, CircleShape) // Padding ring
                    .padding(1.5.dp)
                    .background(statusColor, CircleShape)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name, color = Color(0xFF0F172A), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ext ${contact.extension}", color = Color(0xFF0F172A).copy(alpha = 0.45f), fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "• $displayStatusText",
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        IconButton(onClick = { onCall(contact.extension) }) {
            Icon(Icons.Default.Call, null, tint = Color(0xFF14015E))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 68.dp), thickness = 0.5.dp, color = Color(0xFF0F172A).copy(alpha = 0.07f))
}

private suspend fun fetchContacts(
    extension: String,
    onResult: (List<ExtensionContact>) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val res = apiService.getContacts(extension)
        if (res.isSuccessful) {
            val body = res.body()
            if (body != null && body.status) {
                val processedContacts = body.data.map { contact ->
                    if (contact.extension.length > 4) {
                        contact.copy(extension = contact.extension.drop(4))
                    } else {
                        contact
                    }
                }.sortedBy { it.name }

                onResult(processedContacts)
            } else onError("No data found")
        } else onError("Server error: ${res.code()}")
    } catch (e: Exception) {
        onError("Network error")
    }
}

// Lightweight status-only poll: same endpoint, but only patches status fields.
// Called every 3s in background - never triggers isLoading, never rebuilds the list.
private suspend fun fetchContactsStatusOnly(
    extension: String,
    onResult: (List<ExtensionContact>) -> Unit
) {
    try {
        val res = apiService.getContacts(extension)
        if (res.isSuccessful) {
            val body = res.body()
            if (body != null && body.status) {
                val processed = body.data.map { contact ->
                    if (contact.extension.length > 4) contact.copy(extension = contact.extension.drop(4))
                    else contact
                }
                onResult(processed)
            }
        }
    } catch (_: Exception) {
        // Silently ignore - stale status is better than a crash or error flash
    }
}