package com.callingapp.calling_app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callingapp.calling_app.api.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(extensionName: String, onBack: () -> Unit) {
    var messageText by remember { mutableStateOf("") }

    // Mocking real-time extension messaging
    val messages = remember {
        mutableStateListOf(
            ChatMessage("1", "Hello! Is the SIP server updated?", false, "10:30 AM"),
            ChatMessage("2", "Yes, I just finished the configuration.", true, "10:31 AM"),
            ChatMessage("3", "Great, testing the ringtone now.", false, "10:32 AM")
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom),
        topBar = {
            // ✅ Navy Blue Header
            TopAppBar(
                title = {
                    Column {
                        Text(extensionName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Online", fontSize = 12.sp, color = Color(0xFF10B981))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            // ✅ UNIQUE: Floating Bottom Bar Design
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFF0F172A).copy(alpha = 0.08f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.Add, null, tint = Color(0xFF0F172A).copy(alpha = 0.4f))
                        }

                        TextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("Write a message...") },
                            modifier = Modifier.weight(1f),
                            // ✅ FIX: Set text color to Black
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )

                        Button(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    messages.add(ChatMessage("4", messageText, true, "Now"))
                                    messageText = ""
                                }
                            },
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8FAFC)), // Soft SaaS Background
            reverseLayout = false,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { msg ->
                MessageBubble(msg)
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isMe = message.isFromMe

    // ✅ UNIQUE UI: Threaded Card Design
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        if (!isMe) {
            // Extension Avatar
            Surface(
                modifier = Modifier.size(32.dp).padding(top = 4.dp),
                shape = CircleShape,
                color = Color(0xFF0F172A).copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "E",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            color = if (isMe) Color(0xFF0F172A) else Color.White,
            shape = RoundedCornerShape(
                topStart = if (isMe) 20.dp else 4.dp,
                topEnd = if (isMe) 4.dp else 20.dp,
                bottomStart = 20.dp,
                bottomEnd = 20.dp
            ),
            // ✅ Always Visible Border
            border = BorderStroke(1.dp, Color(0xFF0F172A).copy(alpha = 0.15f)),
            shadowElevation = 3.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isMe) {
                    Text(
                        "Office Extension",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                    Spacer(Modifier.height(2.dp))
                }

                Text(
                    text = message.text,
                    color = if (isMe) Color.White else Color(0xFF0F172A),
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )

                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.time,
                        color = (if (isMe) Color.White else Color(0xFF0F172A)).copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                    if (isMe) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF10B981) // Green read receipt
                        )
                    }
                }
            }
        }
    }
}