package com.callingapp.calling_app.ui.screens.more

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callingapp.calling_app.api.apiService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────
// LOCAL HISTORY MODEL & STORE
// ─────────────────────────────────────────────────────────────
data class LocalFaxRecord(
    val toNumber: String,
    val status: String,
    val fileName: String,
    val time: String
)

object FaxHistoryStore {
    val history = mutableStateListOf<LocalFaxRecord>()
}

// ─────────────────────────────────────────────────────────────
// MAIN FAX SCREEN
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaxScreen(
    extension: String,
    password: String,
    tenantCode: String,
    trunkName: String,
    callerId: String,
    paddingValues: PaddingValues,
    onBack: () -> Unit
) {
    var showSendSheet by remember { mutableStateOf(false) }
    val faxList = FaxHistoryStore.history

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(Color.White)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Fax",
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF0F172A)
                    )
                }
            },
            actions = {
                Button(
                    onClick = { showSendSheet = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6366F1)
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Send Fax", fontSize = 13.sp)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White
            ),
            windowInsets = WindowInsets(0.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${faxList.size} faxes",
                color = Color(0xFF0F172A).copy(alpha = 0.35f),
                fontSize = 12.sp
            )
        }

        if (faxList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Fax History Not Found",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(faxList.reversed()) { fax ->
                    FaxRow(fax)
                }
            }
        }
    }

    if (showSendSheet) {
        SendFaxSheet(
            tenantCode = tenantCode,
            extension = extension,
            password = password,
            trunkName = trunkName,
            callerId = callerId,
            onDismiss = { showSendSheet = false },
            onSent = { showSendSheet = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// HISTORY ROW ITEM
// ─────────────────────────────────────────────────────────────
@Composable
private fun FaxRow(fax: LocalFaxRecord) {
    val statusColor = when (fax.status.lowercase()) {
        "sent", "success" -> Color(0xFF10B981)
        "failed", "error" -> Color(0xFFEF4444)
        else              -> Color(0xFFF59E0B)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = Color(0xFF6366F1).copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Fax,
                    contentDescription = null,
                    tint = Color(0xFF6366F1)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "To: ${fax.toNumber}",
                color = Color(0xFF0F172A),
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(statusColor, CircleShape))
                Spacer(Modifier.width(5.dp))
                Text(
                    text = fax.status,
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = fax.fileName,
                color = Color.Gray,
                fontSize = 11.sp
            )
        }

        Text(
            text = fax.time,
            color = Color.Gray,
            fontSize = 11.sp
        )
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.05f)
    )
}

// ─────────────────────────────────────────────────────────────
// HELPER: PHONE NUMBER SANITIZATION & NORMALIZATION
// ─────────────────────────────────────────────────────────────
private fun sanitizeFaxNumber(input: String): String {
    val digitsOnly = input.replace(Regex("[^0-9]"), "")
    return when {
        digitsOnly.length == 10 -> "1$digitsOnly"
        else -> digitsOnly
    }
}

// ─────────────────────────────────────────────────────────────
// SEND FAX BOTTOM SHEET
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendFaxSheet(
    tenantCode: String,
    extension: String,
    password: String,
    trunkName: String,
    callerId: String,
    onDismiss: () -> Unit,
    onSent: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var toNumber by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("No file selected") }
    var isSending by remember { mutableStateOf(false) }
    var resultMsg by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedUri = it
            selectedName = it.lastPathSegment?.substringAfterLast("/") ?: "document.pdf"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF8FAFC)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Send Fax",
                color = Color(0xFF0F172A),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = toNumber,
                onValueChange = { toNumber = it },
                label = {
                    Text(
                        text = "Fax Number",
                        color = Color(0xFF0F172A).copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                },
                placeholder = {
                    Text(
                        text = "e.g. 9843450555 or 19843450555",
                        color = Color(0xFF0F172A).copy(alpha = 0.35f),
                        fontSize = 15.sp
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone
                ),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A)
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF0F172A),
                    unfocusedTextColor = Color(0xFF0F172A),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = Color(0xFFCBD5E1),
                    cursorColor = Color(0xFF6366F1)
                )
            )

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { filePicker.launch("application/pdf") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(text = selectedName, maxLines = 1)
            }

            resultMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    color = Color(0xFFEF4444),
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (toNumber.isBlank()) {
                        resultMsg = "Please enter a fax number"
                        return@Button
                    }
                    if (selectedUri == null) {
                        resultMsg = "Please select a PDF file"
                        return@Button
                    }

                    val cleanNumber = sanitizeFaxNumber(toNumber)
                    if (cleanNumber.length < 3) {
                        resultMsg = "Please enter a valid fax number or extension"
                        return@Button
                    }

                    scope.launch {
                        isSending = true
                        resultMsg = null

                        var tempFile: File? = null
                        try {
                            tempFile = withContext(Dispatchers.IO) {
                                val input = context.contentResolver.openInputStream(selectedUri!!)
                                    ?: throw java.io.IOException("Could not open attachment stream")
                                val file = File(context.cacheDir, "fax_upload_${System.currentTimeMillis()}.pdf")
                                FileOutputStream(file).use { out -> input.copyTo(out) }
                                file
                            }

                            var sentSuccessfully = false
                            var lastError: String? = null

                            // Primary Attempt: Backend REST API over HTTPS
                            try {
                                val tenantPart = tenantCode.toRequestBody("text/plain".toMediaTypeOrNull())
                                val extPart = extension.toRequestBody("text/plain".toMediaTypeOrNull())
                                val passPart = password.toRequestBody("text/plain".toMediaTypeOrNull())
                                val toPart = cleanNumber.toRequestBody("text/plain".toMediaTypeOrNull())

                                val requestFile = tempFile.asRequestBody("application/pdf".toMediaTypeOrNull())
                                val filePart = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)

                                val apiRes = withContext(Dispatchers.IO) {
                                    apiService.sendFax(tenantPart, extPart, passPart, toPart, filePart)
                                }

                                if (apiRes.isSuccessful && (apiRes.body()?.status == true || !apiRes.body()?.faxId.isNullOrEmpty())) {
                                    sentSuccessfully = true
                                } else {
                                    val err = apiRes.errorBody()?.string() ?: "API returned HTTP ${apiRes.code()}"
                                    lastError = "API Error: $err"
                                    android.util.Log.w("FAX_API", "REST API non-success ($err), trying SSH fallback")
                                }
                            } catch (e: Exception) {
                                lastError = "API Error: ${e.localizedMessage}"
                                android.util.Log.e("FAX_API_ERR", "REST API error, trying SSH: ${e.message}", e)
                            }

                            // Secondary Attempt: Direct SSH transmission fallback
                            if (!sentSuccessfully) {
                                val (sshOk, sshErr) = sendFaxViaSsh(
                                    localPdf = tempFile,
                                    faxNumber = cleanNumber,
                                    tenantCode = tenantCode,
                                    trunkName = trunkName.ifBlank { "CNETGEEK2" },
                                    callerId = callerId
                                )
                                sentSuccessfully = sshOk
                                if (!sshOk && sshErr != null) {
                                    lastError = sshErr
                                }
                            }

                            FaxHistoryStore.history.add(
                                LocalFaxRecord(
                                    toNumber = cleanNumber,
                                    status = if (sentSuccessfully) "Sent" else "Failed",
                                    fileName = selectedName,
                                    time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                                )
                            )

                            if (sentSuccessfully) {
                                resultMsg = "Fax sent successfully"
                                onSent()
                            } else {
                                resultMsg = lastError ?: "Fax send failed. Check server connection or dialplan."
                            }

                        } catch (e: Exception) {
                            resultMsg = e.localizedMessage ?: "Fax send failed"
                        } finally {
                            tempFile?.delete()
                            isSending = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isSending,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Send Fax", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// SSH FAX TRANSMITTER (BACKGROUND DISPATCHER FALLBACK)
// ─────────────────────────────────────────────────────────────
private suspend fun sendFaxViaSsh(
    localPdf: File,
    faxNumber: String,
    tenantCode: String,
    trunkName: String,
    callerId: String
): Pair<Boolean, String?> = withContext(Dispatchers.IO) {

    return@withContext try {

        val jsch = JSch()

        val session = jsch.getSession(
            "cnetvoip",
            "core.cnetvoip.cloud",
            2830
        )

        session.setPassword("Cnetgeek*#2011")
        session.setConfig("StrictHostKeyChecking", "no")
        session.setConfig("PreferredAuthentications", "password,keyboard-interactive")

        val uiHandler = object : com.jcraft.jsch.UserInfo, com.jcraft.jsch.UIKeyboardInteractive {
            override fun getPassphrase(): String? = null
            override fun getPassword(): String = "Cnetgeek*#2011"
            override fun promptPassword(message: String?): Boolean = true
            override fun promptPassphrase(message: String?): Boolean = true
            override fun promptYesNo(message: String?): Boolean = true
            override fun showMessage(message: String?) {}
            override fun promptKeyboardInteractive(
                destination: String?,
                name: String?,
                instruction: String?,
                prompt: Array<out String>?,
                echo: BooleanArray?
            ): Array<String> {
                return arrayOf("Cnetgeek*#2011")
            }
        }
        session.setUserInfo(uiHandler)
        session.connect(10000)

        // Upload PDF to /tmp directory where user cnetvoip has write permissions
        val timestamp = System.currentTimeMillis()
        val remotePdfPath = "/tmp/fax_$timestamp.pdf"
        val remoteTiffPath = "/tmp/fax_$timestamp.tif"

        val sftp = session.openChannel("sftp") as ChannelSftp
        sftp.connect()

        FileInputStream(localPdf).use { input ->
            sftp.put(input, remotePdfPath)
        }

        sftp.disconnect()

        // Convert PDF to TIFF G4 format (required by Asterisk SendFAX / ReceiveFax)
        val convertCmd = "gs -q -dNOPAUSE -dBATCH -sDEVICE=tiffg4 -sOutputFile=$remoteTiffPath $remotePdfPath || cp $remotePdfPath $remoteTiffPath"
        val convertExec = session.openChannel("exec") as ChannelExec
        convertExec.setCommand(convertCmd)
        convertExec.connect()

        while (!convertExec.isClosed) {
            delay(200)
        }
        convertExec.disconnect()

        val activeCallerId = callerId.ifBlank { "19842590345" }
        val activeTrunk = trunkName.ifBlank { "CNETGEEK2" }
        val cleanFaxTarget = faxNumber.replace(Regex("[^0-9]"), "")
        val outboundContext = if (tenantCode.isNotBlank()) "from-outbound-$tenantCode" else "from-outbound-1003"

        // Primary: Originate via local outbound dialplan route [from-outbound-1003]
        val command =
            "asterisk -rx \"channel originate Local/$cleanFaxTarget@$outboundContext extension s@send-fax setvar __FAXFILE=$remoteTiffPath setvar __FAXCALLERID=$activeCallerId setvar FAXFILE=$remoteTiffPath setvar FAXCALLERID=$activeCallerId\""

        var exec = session.openChannel("exec") as ChannelExec
        exec.setCommand(command)
        exec.connect()

        while (!exec.isClosed) {
            delay(200)
        }

        var success = exec.exitStatus == 0
        exec.disconnect()

        // Fallback: If Local originate fails, try direct PJSIP trunk originate
        if (!success) {
            val fallbackCommand =
                "asterisk -rx \"channel originate PJSIP/$cleanFaxTarget@$activeTrunk extension s@send-fax setvar __FAXFILE=$remoteTiffPath setvar __FAXCALLERID=$activeCallerId setvar FAXFILE=$remoteTiffPath setvar FAXCALLERID=$activeCallerId\""

            exec = session.openChannel("exec") as ChannelExec
            exec.setCommand(fallbackCommand)
            exec.connect()

            while (!exec.isClosed) {
                delay(200)
            }

            success = exec.exitStatus == 0
            exec.disconnect()
        }

        session.disconnect()

        if (success) {
            Pair(true, null)
        } else {
            Pair(false, "Asterisk originate command failed. Check dialplan [from-outbound-$tenantCode] or [send-fax].")
        }

    } catch (e: Exception) {
        android.util.Log.e("FAX_SSH_ERROR", e.message ?: "Unknown", e)
        val msg = when {
            e is com.jcraft.jsch.JSchException && e.message?.contains("Auth fail", ignoreCase = true) == true ->
                "SSH Auth fail: Invalid SSH user/password for core.cnetvoip.cloud"
            e is com.jcraft.jsch.SftpException ->
                "SFTP Upload Error: ${e.message} (Permission denied on server)"
            else -> e.localizedMessage ?: "SSH Connection error"
        }
        Pair(false, msg)
    }
}