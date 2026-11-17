package com.callingapp.calling_app.ui.screens.more

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callingapp.calling_app.api.RingGroup
import com.callingapp.calling_app.api.apiService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingGroupScreen(
    extension: String,
    password: String,
    tenantCode: String,
    paddingValues: PaddingValues, // 👈 ✅ ADD THIS LAYOUT ACCEPTER PARAMETER
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<RingGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            isLoading = true; errorMsg = null
            try {
                val res = apiService.getRingGroups(extension)
                if (res.isSuccessful && res.body()?.status == true) {
                    groups = res.body()?.data ?: emptyList()
                    if (groups.isEmpty()) errorMsg = "Ring Groups Unavailable"
                } else {
                    errorMsg = "Ring Groups Unavailable"
                }
            } catch (e: Exception) {
                errorMsg = "Ring Groups Unavailable"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        load()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues) // 👈 ✅ APPLY THE SYSTEM INSETS PADDING HERE
            .background(Color(0xFFFFFFFF))
    ) {
        // High positioning setup with completely zeroed baseline window paddings
        TopAppBar(
            title = {
                Text(
                    text = "Ring Groups",
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
            windowInsets = WindowInsets(0.dp) // ✅ Pulls text up underneath top action boundary
        )

        // Metrics data row lines
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${groups.size} groups", color = Color(0xFF0F172A).copy(alpha = 0.35f), fontSize = 12.sp)
            IconButton(onClick = { load() }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = Color(0xFF8B5CF6))
            }
        }

        if (isLoading) {
            LoadingBox("Loading ring groups…")
        } else if (errorMsg != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorMsg!!, color = Color(0xFF0F172A).copy(0.4f), fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(groups, key = { it.id }) { group ->
                    RingGroupCard(group)
                }
            }
        }
    }
}

@Composable
private fun RingGroupCard(group: RingGroup) {
    var expanded by remember { mutableStateOf(false) }
    val accent = Color(0xFF8B5CF6)

    val memberList = remember(group.membersRaw) {
        try {
            group.membersRaw
                .removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().replace("\"", "") }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList<String>()
        }
    }

    val strategyLabel = when (group.strategy.lowercase()) {
        "simultaneous", "ringall" -> "Ring All"
        "sequential", "linear"   -> "Sequential"
        else                     -> group.strategy.replaceFirstChar { it.uppercase() }
    }

    val strategyColor = if (strategyLabel == "Ring All") Color(0xFF10B981) else Color(0xFF60A5FA)

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
                Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = accent.copy(alpha = 0.15f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Groups, null, tint = accent, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.name.ifBlank { "Group ${group.number}" }, color = Color(0xFF0F172A), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ext ${group.number} · ", color = Color(0xFF0F172A).copy(alpha = 0.35f), fontSize = 12.sp)
                        Surface(shape = RoundedCornerShape(4.dp), color = strategyColor.copy(alpha = 0.15f)) {
                            Text(strategyLabel, color = strategyColor, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color(0xFF0F172A).copy(alpha = 0.3f))
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    HorizontalDivider(color = Color(0xFF0F172A).copy(alpha = 0.08f))

                    Text(
                        "Members (${memberList.size})",
                        color = Color(0xFF0F172A).copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    memberList.forEachIndexed { idx, extNum ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(Modifier.size(24.dp), shape = CircleShape, color = accent.copy(0.12f)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("${idx + 1}", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("Extension $extNum", color = Color(0xFF0F172A), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}