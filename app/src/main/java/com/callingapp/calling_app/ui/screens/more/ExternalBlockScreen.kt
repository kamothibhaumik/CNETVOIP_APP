package com.callingapp.calling_app.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalBlockScreen(
    extension: String,
    paddingValues: PaddingValues, // 👈 ✅ ADD THIS LAYOUT ACCEPTER PARAMETER
    onBack: () -> Unit
) {
    var blockInputText by remember { mutableStateOf("") }
    val blockList = remember { mutableStateListOf("9876543210", "18005550199") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues) // 👈 ✅ APPLY THE SYSTEM INSETS PADDING HERE TO PROTECT CONTENT
            .background(Color.White)
    ) {
        // High positioning setup with completely zeroed baseline window paddings
        TopAppBar(
            title = {
                Text(
                    text = "External Number Block",
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
            windowInsets = WindowInsets(0.dp) // ✅ Pulls the menu title higher up the screen
        )

        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = blockInputText,
                    onValueChange = { blockInputText = it },
                    placeholder = { Text("Enter target phone number") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedBorderColor = Color(0xFFDC2626),
                        unfocusedBorderColor = Color(0xFF0F172A).copy(alpha = 0.15f),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (blockInputText.isNotBlank()) {
                            blockList.add(blockInputText.trim())
                            blockInputText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Block")
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Blocked Numbers Engine Log", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Spacer(Modifier.height(6.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(blockList) { num ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(num, color = Color(0xFF0F172A), fontWeight = FontWeight.Medium)
                            IconButton(onClick = { blockList.remove(num) }) {
                                Icon(Icons.Default.Delete, "Remove Blacklist Entry", tint = Color(0xFFDC2626))
                            }
                        }
                    }
                }
            }
        }
    }
}