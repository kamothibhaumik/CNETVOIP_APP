package com.callingapp.calling_app.ui.screens.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.PhoneMissed
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callingapp.calling_app.api.CallRecordItem
import com.callingapp.calling_app.api.apiService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val LogoNavy = Color(0xFF001170)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantCallHistoryScreen(
    extension: String,
    onBack: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayStr = remember { dateFormatter.format(Date()) }

    // Always holds a specific date; defaults strictly to today's date
    var selectedDate by remember { mutableStateOf(todayStr) }
    var showDatePicker by remember { mutableStateOf(false) }

    var callList by remember { mutableStateOf<List<CallRecordItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var currentPage by remember { mutableIntStateOf(1) }
    var lastPage by remember { mutableIntStateOf(1) }
    var totalRecords by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun fetchCallPage(pageToFetch: Int) {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val res = apiService.getCallHistory(
                    extension = extension,
                    page = pageToFetch,
                    fromDate = selectedDate,
                    toDate = selectedDate
                )
                if (res.isSuccessful && res.body()?.status == true) {
                    val body = res.body()
                    val incomingCalls = body?.calls ?: emptyList()

                    // Ensure local filter only reflects the active selected date
                    val filteredByDate = incomingCalls.filter { it.start.startsWith(selectedDate) }

                    callList = filteredByDate
                    currentPage = body?.pagination?.currentPage ?: pageToFetch
                    lastPage = body?.pagination?.lastPage ?: 1
                    totalRecords = body?.pagination?.total ?: callList.size
                } else {
                    errorMessage = "Failed to load history: ${res.message()}"
                }
            } catch (e: Exception) {
                errorMessage = "Network error: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    // Re-fetch when extension or date changes
    LaunchedEffect(extension, selectedDate) {
        currentPage = 1
        fetchCallPage(1)
    }

    val filteredList = remember(callList, searchQuery) {
        if (searchQuery.isBlank()) callList
        else {
            callList.filter {
                it.source.contains(searchQuery, ignoreCase = true) ||
                        it.destination.contains(searchQuery, ignoreCase = true) ||
                        it.callerId.contains(searchQuery, ignoreCase = true) ||
                        it.disposition.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // ── Material 3 Date Picker Dialog ──
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val utcFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                            selectedDate = utcFormatter.format(Date(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", color = LogoNavy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = LogoNavy,
                    todayDateBorderColor = LogoNavy
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("All Extension History", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            text = if (selectedDate == todayStr) "Today ($todayStr)" else selectedDate,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LogoNavy)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(padding)
        ) {
            // Search and Date Picker Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search number/status...",
                            fontSize = 14.sp,
                            color = Color(0xFF64748B).copy(alpha = 0.6f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    // ── Crisp, dark, bold typography ──
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF0F172A),
                        unfocusedTextColor = Color(0xFF0F172A),
                        focusedBorderColor = LogoNavy,
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        cursorColor = LogoNavy
                    )
                )

                Spacer(Modifier.width(8.dp))

                // ── Styled Calendar Action Button with Border ──
                Surface(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    shadowElevation = 0.5.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Pick Date",
                            tint = LogoNavy,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Quick Date Selectors: Only "Today" & the custom picked date
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedDate == todayStr,
                    onClick = { selectedDate = todayStr },
                    label = { Text("Today", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LogoNavy,
                        selectedLabelColor = Color.White
                    )
                )

                if (selectedDate != todayStr) {
                    FilterChip(
                        selected = true,
                        onClick = { showDatePicker = true },
                        label = { Text(selectedDate, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LogoNavy,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = LogoNavy)
                        }
                    }
                    errorMessage != null -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(errorMessage ?: "", color = Color(0xFFEF4444), fontSize = 14.sp)
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { fetchCallPage(currentPage) },
                                    colors = ButtonDefaults.buttonColors(containerColor = LogoNavy)
                                ) {
                                    Text("Retry", color = Color.White)
                                }
                            }
                        }
                    }
                    filteredList.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No calls found for $selectedDate",
                                color = Color(0xFF64748B),
                                fontSize = 14.sp
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredList, key = { it.id }) { call ->
                                CallItemCard(call)
                            }
                        }
                    }
                }
            }

            // ── Pagination Bar Elevated Above Bottom Nav ──
            Surface(
                color = Color.White,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 80.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { if (currentPage > 1) fetchCallPage(currentPage - 1) },
                        enabled = currentPage > 1 && !isLoading,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LogoNavy,
                            disabledContainerColor = LogoNavy.copy(alpha = 0.2f)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Prev", fontSize = 13.sp, color = Color.White)
                    }

                    Text(
                        text = "Page $currentPage of $lastPage",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )

                    Button(
                        onClick = { if (currentPage < lastPage) fetchCallPage(currentPage + 1) },
                        enabled = currentPage < lastPage && !isLoading,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LogoNavy,
                            disabledContainerColor = LogoNavy.copy(alpha = 0.2f)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Next", fontSize = 13.sp, color = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallItemCard(call: CallRecordItem) {
    val isAnswered = call.disposition.equals("ANSWERED", ignoreCase = true)
    val statusColor = when (call.disposition.uppercase()) {
        "ANSWERED" -> Color(0xFF10B981)
        "BUSY"     -> Color(0xFFF59E0B)
        else       -> Color(0xFFEF4444)
    }

    val icon = if (isAnswered) Icons.AutoMirrored.Filled.CallMade else Icons.AutoMirrored.Filled.PhoneMissed

    fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = call.source,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("➔", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = call.destination,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = call.start,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = call.disposition,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                if (isAnswered && call.billableSeconds > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatDuration(call.billableSeconds),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0F172A).copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}