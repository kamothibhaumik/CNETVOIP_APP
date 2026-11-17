package com.callingapp.calling_app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable // ✅ Required import
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ✅ Model definition
data class ChatConversation(
    val id: String,
    val name: String,
    val lastMessage: String,
    val timestamp: String,
    val unreadCount: Int = 0
)

@Composable
fun SmsTab(onChatClicked: (String) -> Unit) { // ✅ FIX: Added this parameter
    val chats = remember {
        mutableStateListOf<ChatConversation>(
            ChatConversation("1", "Anand Office", "The SIP server is ready.", "10:45 AM", 2),
            ChatConversation("2", "Bhaumik Kamothi", "Logo design looks great!", "Yesterday", 0)
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Text(
            "Messages",
            modifier = Modifier.padding(16.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )

        LazyColumn {
            items(chats) { chat ->
                ConversationRow(
                    chat = chat,
                    onClick = { onChatClicked(chat.name) } // ✅ Now this reference is valid
                )
            }
        }
    }
}

@Composable
fun ConversationRow(chat: ChatConversation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() } // ✅ FIX: Removed the 'androidx...' prefix
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = Color(0xFFF1F5F9)) {
            Box(contentAlignment = Alignment.Center) {
                Text(chat.name.take(1), fontWeight = FontWeight.Bold)
            }
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(chat.name, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Text(chat.lastMessage, color = Color.Gray, fontSize = 14.sp, maxLines = 1)
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(chat.timestamp, fontSize = 12.sp, color = Color.Gray)
            if (chat.unreadCount > 0) {
                Badge(containerColor = Color(0xFF10B981)) {
                    Text("${chat.unreadCount}", color = Color.White)
                }
            }
        }
    }
}