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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callingapp.calling_app.api.WakeUpCall
import com.callingapp.calling_app.api.apiService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.callingapp.calling_app.api.ExtensionContact

@Composable
fun inputFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFF0F172A),
    unfocusedTextColor = Color(0xFF0F172A),
    focusedLabelColor = Color(0xFFEC4899),
    unfocusedLabelColor = Color(0xFF0F172A).copy(0.6f),
    focusedBorderColor = Color(0xFFEC4899),
    unfocusedBorderColor = Color(0xFF0F172A).copy(0.2f),
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = Color.White
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeUpScreen(
    extension: String,
    paddingValues: PaddingValues, // 👈 ✅ ADD THIS LAYOUT ACCEPTER PARAMETER
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var calls by remember { mutableStateOf<List<WakeUpCall>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showSchedule by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                val res = apiService.getWakeUpCalls(extension)
                if (res.isSuccessful) {
                    val data = res.body()?.data ?: emptyList()
                    if (data.isEmpty()) errorMsg = "Wake Up Call Not Found"
                    else calls = data
                } else {
                    errorMsg = "Wake Up Call Not Found"
                }
            } catch (e: Exception) {
                errorMsg = "Wake Up Call Not Found"
            }
            isLoading = false
        }
    }

    fun cancelCall(targetExt: String) {
        scope.launch {
            try {
                val body = mapOf(
                    "mobile_extension" to extension,
                    "target_extension" to targetExt
                )
                val res = apiService.cancelWakeUp(body)
                if (res.isSuccessful && res.body()?.status == true) {
                    load()
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues) // 👈 ✅ APPLY THE INSETS PADDING HERE TO SECURE THE CANVAS
            .background(Color.White)
    ) {
        // High positioning layout adjustments with zeroed window bounds
        TopAppBar(
            title = {
                Text(
                    text = "Wake Up Call",
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
                Button(
                    onClick = { showSchedule = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Schedule", fontSize = 13.sp)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            windowInsets = WindowInsets(0.dp) // ✅ Pulls code header higher up the status area
        )

        // Metrics counter alignment description row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${calls.size} Scheduled Calls", color = Color(0xFF0F172A).copy(0.45f), fontSize = 13.sp)
            IconButton(onClick = { load() }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = Color(0xFFEC4899))
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFEC4899))
            }
        } else if (errorMsg != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorMsg!!, color = Color(0xFF0F172A).copy(0.4f), fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(calls) { call ->
                    WakeUpCallRow(call, onCancel = { cancelCall(call.extension) })
                }
            }
        }
    }

    if (showSchedule) {
        ScheduleWakeUpSheet(
            myExtension = extension,
            onDismiss = { showSchedule = false },
            onScheduled = {
                showSchedule = false
                load()
            }
        )
    }
}

@Composable
private fun WakeUpCallRow(call: WakeUpCall, onCancel: () -> Unit) {
    val accent = Color(0xFFEC4899)

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(52.dp), shape = CircleShape, color = accent.copy(0.12f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Alarm, null, tint = accent)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(call.formattedDate, color = Color(0xFF0F172A), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Room: ${call.roomNumber} · Guest: ${call.guestName}", color = Color(0xFF0F172A).copy(0.5f), fontSize = 12.sp)
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.DeleteOutline, "Cancel", tint = Color.Red.copy(0.7f))
        }
    }
    HorizontalDivider(color = Color(0xFF0F172A).copy(0.06f), modifier = Modifier.padding(start = 78.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleWakeUpSheet(
    myExtension: String,
    onDismiss: () -> Unit,
    onScheduled: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var targetExtension by remember { mutableStateOf("") }
    var timeHour        by remember { mutableStateOf("07") }
    var timeMinute      by remember { mutableStateOf("00") }
    var extensions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingExtensions by remember { mutableStateOf(false) }
    var expandedDropdown by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var isSending       by remember { mutableStateOf(false) }
    var resultMsg       by remember { mutableStateOf<String?>(null) }
    var isSuccess       by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scope.launch {
            isLoadingExtensions = true
            try {
                val res = apiService.getContacts(myExtension)
                if (res.isSuccessful) {
                    val contactList: List<ExtensionContact> = res.body()?.data ?: emptyList()
                    extensions = contactList.map { it.extension }
                }
            } catch (e: Exception) {
                android.util.Log.e("WakeUp", "Failed to load extensions: ${e.message}")
            }
            isLoadingExtensions = false
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = formatter.format(Date(millis))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Color(0xFFF8FAFC)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
            Text("Schedule Wake-Up Call", color = Color(0xFF0F172A), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            val fieldColors = inputFieldColors()

            ExposedDropdownMenuBox(
                expanded = expandedDropdown,
                onExpandedChange = { expandedDropdown = !expandedDropdown }
            ) {
                OutlinedTextField(
                    value = targetExtension,
                    onValueChange = { targetExtension = it },
                    label = { Text("Target Extension") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    trailingIcon = {
                        if (isLoadingExtensions) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        else ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown)
                    }
                )
                val filtered = extensions.filter { it.contains(targetExtension) }
                if (filtered.isNotEmpty()) {
                    ExposedDropdownMenu(expanded = expandedDropdown, onDismissRequest = { expandedDropdown = false }) {
                        filtered.forEach { ext ->
                            DropdownMenuItem(text = { Text(ext) }, onClick = { targetExtension = ext; expandedDropdown = false })
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = selectedDate.ifBlank { "Select Date" },
                onValueChange = {},
                readOnly = true,
                label = { Text("Wake-up Date") },
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                enabled = false,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = Color(0xFF0F172A),
                    disabledBorderColor = Color(0xFF0F172A).copy(0.2f),
                    disabledLabelColor = Color(0xFF0F172A).copy(0.6f),
                    disabledContainerColor = Color.White,
                    disabledTrailingIconColor = Color(0xFF0F172A).copy(0.6f)
                ),
                trailingIcon = { Icon(Icons.Default.CalendarMonth, null) }
            )

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = timeHour,
                    onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 2) timeHour = it },
                    label = { Text("Hour (HH)") },
                    placeholder = { Text("07") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = timeMinute,
                    onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 2) timeMinute = it },
                    label = { Text("Minute (MM)") },
                    placeholder = { Text("00") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        isSending = true
                        try {
                            val datePart = selectedDate.ifBlank { formatter.format(Date()) }
                            val body = mapOf(
                                "mobile_extension" to myExtension,
                                "target_extension" to targetExtension,
                                "datetime"         to "$datePart $timeHour:$timeMinute:00"
                            )
                            val res = apiService.scheduleWakeUp(body)
                            if (res.isSuccessful && res.body()?.status == true) {
                                isSuccess = true
                                resultMsg = "Scheduled!"
                                delay(800)
                                onScheduled()
                            } else {
                                isSuccess = false
                                resultMsg = res.body()?.message ?: "Error"
                            }
                        } catch (e: Exception) {
                            isSuccess = false
                            resultMsg = e.message
                        }
                        isSending = false
                    }
                },
                enabled = !isSending && targetExtension.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899))
            ) {
                if (isSending) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                else Text("Schedule Now", fontWeight = FontWeight.Bold)
            }
        }
    }
}