package com.callingapp.calling_app.ui.screens

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import com.callingapp.calling_app.utils.CallLog

// ── Cross-package imports ───────────────────────────────────────────────────
import com.callingapp.calling_app.api.ExtensionContact
import com.callingapp.calling_app.api.apiService
import com.callingapp.calling_app.sip.SipModule
import com.callingapp.calling_app.ui.screens.more.MoreMenuScreen

// ─────────────────────────────────────────────────────────────────────────────
// MainAppShell
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppShell(
    extension: String,
    password: String,
    tenantCode: String,
    onLogout: () -> Unit
) {
    val context     = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var missedCount by remember { mutableIntStateOf(CallLog.getUnreadMissedCount(context, extension)) }

    DisposableEffect(extension) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                missedCount = CallLog.getUnreadMissedCount(context, extension)
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

    var activeChatPartner by remember { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope       = rememberCoroutineScope()
    var trunkName   by remember { mutableStateOf("") }
    var callerId    by remember { mutableStateOf("") }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    var allContacts by remember { mutableStateOf<List<ExtensionContact>>(emptyList()) }

    // Load contacts once for name lookup in outgoing calls
    LaunchedEffect(extension) {
        if (extension.isBlank()) return@LaunchedEffect

        val cachedContactsJson = context.getSharedPreferences("OfficeAppPrefs", Context.MODE_PRIVATE)
            .getString("cached_contacts", null)
        if (!cachedContactsJson.isNullOrBlank()) {
            try {
                val listType = object : com.google.gson.reflect.TypeToken<List<ExtensionContact>>() {}.type
                allContacts = com.google.gson.Gson().fromJson(cachedContactsJson, listType)
            } catch (_: Exception) {}
        }

        try {
            val res = apiService.getContacts(extension)
            if (res.isSuccessful && res.body()?.status == true) {
                val rawList = res.body()?.data ?: emptyList()
                val cleanList = rawList.map { c ->
                    val cleanExt = if (c.extension.length > 4 && c.extension.startsWith("1001")) {
                        c.extension.drop(4)
                    } else c.extension
                    c.copy(extension = cleanExt)
                }
                allContacts = cleanList

                context.getSharedPreferences("OfficeAppPrefs", Context.MODE_PRIVATE).edit()
                    .putString("cached_contacts", com.google.gson.Gson().toJson(cleanList))
                    .apply()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainAppShell", "Contact sync failed: ${e.message}")
        }
    }

    BackHandler(enabled = activeChatPartner != null) {
        activeChatPartner = null
    }

    if (activeChatPartner != null) {
        ChatDetailScreen(
            extensionName = activeChatPartner!!,
            onBack = { activeChatPartner = null }
        )
    } else {
        androidx.compose.animation.AnimatedVisibility(
            visible = isVisible,
            enter = androidx.compose.animation.fadeIn(tween(100))
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            ModalDrawerSheet(
                                modifier = Modifier
                                    .width(320.dp)
                                    .fillMaxHeight(),
                                drawerContainerColor = Color(0xFFF8FAFC),
                                drawerShape = RoundedCornerShape(0.dp)
                            ) {
                                val BrandThemeColor = Color(0xFF14015E)

                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFEFF2F5))
                                        .padding(32.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            modifier = Modifier.size(64.dp),
                                            shape = CircleShape,
                                            color = BrandThemeColor
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = extension.firstOrNull()?.toString() ?: "E",
                                                    color = Color.White,
                                                    fontSize = 24.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Column(Modifier.padding(start = 16.dp)) {
                                            Text(
                                                text = extension,
                                                color = Color(0xFF0F172A),
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    Modifier
                                                        .size(8.dp)
                                                        .background(BrandThemeColor, CircleShape)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "Online • Registered",
                                                    color = BrandThemeColor,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))

                                DrawerItem("Logout", Icons.AutoMirrored.Filled.ExitToApp, isRed = true) {
                                    val fullExt = if (extension.length <= 4 && tenantCode.isNotBlank()) "$tenantCode$extension" else extension

                                    // Unregister TCP and clear audio
                                    SipModule.udpManager?.stopRegistration(fullExt)
                                    SipModule.stopAudio()
                                    SipModule.udpManager = null

                                    val stopIntent = Intent(context, com.callingapp.calling_app.sip.SipForegroundService::class.java).apply {
                                        action = com.callingapp.calling_app.sip.SipForegroundService.ACTION_STOP
                                        putExtra("EXTENSION", fullExt)
                                    }
                                    context.startService(stopIntent)

                                    context.getSharedPreferences("OfficeAppPrefs", Context.MODE_PRIVATE).edit().clear().apply()
                                    onLogout()
                                }
                            }
                        }
                    }
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        val BrandNavy = Color(0xFF14015E)

                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text("CNETVOIP", color = Color.White, fontWeight = FontWeight.Bold) },
                                    actions = {
                                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                            Icon(Icons.Default.Menu, null, tint = Color.White)
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandNavy)
                                )
                            },
                            bottomBar = {
                                NavigationBar(containerColor = BrandNavy) {
                                    val tabs = listOf("Dialer", "Recent", "Contacts", "More")
                                    val icons = listOf(
                                        Icons.Default.Dialpad,
                                        Icons.Default.History,
                                        Icons.Default.Person,
                                        Icons.Default.MoreHoriz
                                    )

                                    tabs.forEachIndexed { i, label ->
                                        NavigationBarItem(
                                            selected = selectedTab == i,
                                            onClick = {
                                                val previousTab = selectedTab
                                                selectedTab = i

                                                // ✅ If opening Recent OR leaving Recent, clear the badge!
                                                if (i == 1 || previousTab == 1) {
                                                    CallLog.clearUnreadMissed(context, extension)
                                                    missedCount = 0
                                                }
                                            },
                                            icon = {
                                                if (i == 1 && missedCount > 0) {
                                                    // 🔴 Red round badge on top-right of Recent icon
                                                    BadgedBox(
                                                        badge = {
                                                            Badge(
                                                                containerColor = Color(0xFFEF4444),
                                                                contentColor = Color.White
                                                            ) {
                                                                Text(
                                                                    text = if (missedCount > 99) "99+" else missedCount.toString(),
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = icons[i],
                                                            contentDescription = label,
                                                            tint = Color.White
                                                        )
                                                    }
                                                } else {
                                                    Icon(
                                                        imageVector = icons[i],
                                                        contentDescription = label,
                                                        tint = Color.White
                                                    )
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = label,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal,
                                                    color = Color.White
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                indicatorColor = Color.White.copy(alpha = 0.2f)
                                            )
                                        )
                                    }
                                }
                            }
                        ) { p ->
                            Box(Modifier.fillMaxSize()) {
                                when (selectedTab) {
                                    // 0: Dialer
                                    0 -> DialerUI(
                                        extension = extension,
                                        scaffoldPadding = p,
                                        allContacts = allContacts,
                                        onMakeCall = { target ->
                                            val cleanTarget = if (target.length > 4 && target.startsWith("1001")) target.drop(4) else target
                                            val contactName = allContacts.find {
                                                val ext = if (it.extension.startsWith("1001") && it.extension.length > 4) it.extension.drop(4) else it.extension
                                                ext == cleanTarget
                                            }?.name ?: ""

                                            SipModule.udpManager?.makeCall(cleanTarget)

                                            context.startActivity(
                                                Intent(context, CallActivity::class.java).apply {
                                                    putExtra("NUMBER", cleanTarget)
                                                    putExtra("CALLER_NAME", contactName)
                                                    putExtra("IS_INCOMING", false)
                                                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                                }
                                            )
                                        }
                                    )

                                    // 1: Recent Calls
                                    1 -> Box(Modifier.padding(p)) {
                                        RecentCallsTab(
                                            myExtension = extension,
                                            selectedTab = selectedTab,
                                            allContacts = allContacts,
                                            onCall = { target ->
                                                val cleanTarget = if (target.length > 4 && target.startsWith("1001")) target.drop(4) else target
                                                val contactName = allContacts.find {
                                                    val ext = if (it.extension.startsWith("1001") && it.extension.length > 4) it.extension.drop(4) else it.extension
                                                    ext == cleanTarget
                                                }?.name ?: ""

                                                // ✅ Trigger call via TCP
                                                SipModule.udpManager?.makeCall(cleanTarget)

                                                context.startActivity(Intent(context, CallActivity::class.java).apply {
                                                    putExtra("NUMBER", cleanTarget)
                                                    putExtra("CALLER_NAME", contactName)
                                                    putExtra("IS_INCOMING", false)
                                                })
                                            }
                                        )
                                    }

                                    // 2: Contacts
                                    2 -> Box(Modifier.padding(p)) {
                                        ContactsTab(
                                            myExtension = extension,
                                            onCall = { target ->
                                                val cleanTarget = if (target.length > 4 && target.startsWith("1001")) target.drop(4) else target
                                                val contactName = allContacts.find {
                                                    val ext = if (it.extension.startsWith("1001") && it.extension.length > 4) it.extension.drop(4) else it.extension
                                                    ext == cleanTarget
                                                }?.name ?: ""

                                                // ✅ Trigger call via TCP
                                                SipModule.udpManager?.makeCall(cleanTarget)

                                                context.startActivity(Intent(context, CallActivity::class.java).apply {
                                                    putExtra("NUMBER", cleanTarget)
                                                    putExtra("CALLER_NAME", contactName)
                                                    putExtra("IS_INCOMING", false)
                                                })
                                            }
                                        )
                                    }

                                    // 3: More Menu
                                    3 -> MoreMenuScreen(
                                        extension = extension,
                                        password = password,
                                        tenantCode = tenantCode,
                                        trunkName = trunkName,
                                        callerId = callerId,
                                        paddingValues = p
                                    )

                                    else -> Box(Modifier
                                        .fillMaxSize()
                                        .padding(p), contentAlignment = Alignment.Center) {
                                        Text("Module Coming Soon", color = Color(0xFF0F172A).copy(alpha = 0.4f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DialerUI
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DialerUI(
    extension: String,
    scaffoldPadding: PaddingValues,
    allContacts: List<ExtensionContact> = emptyList(),
    onMakeCall: (String) -> Unit
) {
    var digits    by remember { mutableStateOf("") }
    var cursorPos by remember { mutableIntStateOf(0) }
    val toneGen   = remember { ToneGenerator(AudioManager.STREAM_DTMF, 80) }
    val scope     = rememberCoroutineScope()

    val suggestedContacts = remember(digits, allContacts) {
        if (digits.isBlank()) emptyList()
        else {
            val cleanMyExtension = if (extension.length > 4 && extension.matches(Regex("^\\d{4}.*"))) {
                extension.drop(4)
            } else {
                extension
            }

            allContacts.filter { contact ->
                val displayExt = if (contact.extension.length > 4 && contact.extension.matches(Regex("^\\d{4}.*"))) {
                    contact.extension.drop(4)
                } else {
                    contact.extension
                }

                displayExt != cleanMyExtension && (
                        displayExt.contains(digits) ||
                                contact.name.contains(digits, ignoreCase = true)
                        )
            }.map { contact ->
                if (contact.extension.length > 4 && contact.extension.matches(Regex("^\\d{4}.*"))) {
                    contact.copy(extension = contact.extension.drop(4))
                } else {
                    contact
                }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue  = 1f, targetValue = 0f, label = "alpha",
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 530), repeatMode = RepeatMode.Reverse)
    )

    fun insertDigit(ch: String) {
        digits = digits.substring(0, cursorPos) + ch + digits.substring(cursorPos)
        cursorPos += ch.length
    }

    fun deleteOne() {
        if (digits.isNotEmpty() && cursorPos > 0) {
            digits = digits.removeRange(cursorPos - 1, cursorPos)
            cursorPos -= 1
        }
    }

    val fontSize = when {
        digits.length <= 8  -> 46
        digits.length <= 12 -> 36
        digits.length <= 16 -> 28
        else                -> 22
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFFFF))
            .padding(
                top = scaffoldPadding.calculateTopPadding() + 12.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + 8.dp
            )
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

        // Display Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    val y = size.height - strokeWidth / 2
                    drawLine(
                        color = Color(0xFF0F172A).copy(alpha = 0.06f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidth
                    )
                }
                .pointerInput(digits) {
                    detectTapGestures { offset ->
                        if (digits.isEmpty()) return@detectTapGestures
                        textLayoutResult?.let { layout ->
                            val index = layout.getOffsetForPosition(offset)
                            cursorPos = index
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (digits.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("Enter number", color = Color(0xFF0F172A).copy(alpha = 0.25f), fontSize = 24.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.width(2.dp))
                    Box(Modifier
                        .width(2.dp)
                        .height(30.dp)
                        .alpha(cursorAlpha)
                        .background(Color(0xFF14015E)))
                }
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = digits, fontSize = fontSize.sp, color = Color.Transparent, letterSpacing = 1.sp, maxLines = 1,
                        onTextLayout = { textLayoutResult = it }, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        val before = digits.substring(0, cursorPos)
                        val after = digits.substring(cursorPos)
                        Text(before, fontSize = fontSize.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Light, letterSpacing = 1.sp)
                        Box(Modifier
                            .width(2.5.dp)
                            .height((fontSize + 6).dp)
                            .alpha(cursorAlpha)
                            .background(Color(0xFF14015E)))
                        Text(after, fontSize = fontSize.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Light, letterSpacing = 1.sp)
                    }
                }
            }
        }

        // Suggestions Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .height(54.dp),
            contentAlignment = Alignment.Center
        ) {
            if (suggestedContacts.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(suggestedContacts) { contact ->
                        Surface(
                            modifier = Modifier.clickable {
                                digits = contact.extension
                                cursorPos = contact.extension.length
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Color(0xFF14015E).copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(modifier = Modifier.size(24.dp), shape = CircleShape, color = Color(0xFF14015E).copy(0.15f)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = contact.name.take(1).uppercase(), color = Color(0xFF14015E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(contact.name, color = Color(0xFF0F172A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text("Ext ${contact.extension}", color = Color(0xFF0F172A).copy(0.5f), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Keypad Panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val keys = listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("*","0","#"))
            keys.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { num ->
                        Surface(
                            onClick = {
                                insertDigit(num)
                                val tone = when (num) {
                                    "1" -> ToneGenerator.TONE_DTMF_1
                                    "2" -> ToneGenerator.TONE_DTMF_2
                                    "3" -> ToneGenerator.TONE_DTMF_3
                                    "4" -> ToneGenerator.TONE_DTMF_4
                                    "5" -> ToneGenerator.TONE_DTMF_5
                                    "6" -> ToneGenerator.TONE_DTMF_6
                                    "7" -> ToneGenerator.TONE_DTMF_7
                                    "8" -> ToneGenerator.TONE_DTMF_8
                                    "9" -> ToneGenerator.TONE_DTMF_9
                                    "0" -> ToneGenerator.TONE_DTMF_0
                                    "*" -> ToneGenerator.TONE_DTMF_S
                                    "#" -> ToneGenerator.TONE_DTMF_P
                                    else -> ToneGenerator.TONE_DTMF_0
                                }
                                toneGen.startTone(tone, 150)
                            },
                            shape = CircleShape,
                            color = Color(0xFF0F172A).copy(alpha = 0.04f),
                            modifier = Modifier.size(70.dp),
                            border = BorderStroke(1.dp, Color(0xFF0F172A).copy(alpha = 0.02f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(num, color = Color(0xFF0F172A), fontSize = 30.sp, fontWeight = FontWeight.Light)
                            }
                        }
                    }
                }
            }

            // Call + Delete Action Row
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(
                    onClick  = { if (digits.isNotEmpty()) onMakeCall(digits) },
                    modifier = Modifier.size(72.dp),
                    shape    = CircleShape,
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF14015E))
                ) {
                    Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .align(Alignment.CenterVertically)
                            .size(52.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { deleteOne() },
                                    onLongPress = {
                                        scope.launch {
                                            while (digits.isNotEmpty()) {
                                                deleteOne()
                                                delay(80L)
                                            }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, "Delete", tint = Color(0xFF0F172A).copy(alpha = 0.45f), modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawerItem
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DrawerItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isRed: Boolean = false,
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    val finalColor = if (isRed) Color.Red else textColor

    NavigationDrawerItem(
        label = { Text(label, color = finalColor) },
        selected = false,
        onClick = onClick,
        icon = { Icon(icon, null, tint = finalColor) },
        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
    )
}
