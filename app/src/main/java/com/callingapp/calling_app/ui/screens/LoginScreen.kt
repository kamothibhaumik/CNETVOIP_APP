package com.callingapp.calling_app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callingapp.calling_app.R
import com.callingapp.calling_app.api.apiService
import kotlinx.coroutines.launch

// Primary deep navy theme sampled from the CnetGeek circuit logo
private val LogoThemeColor = Color(0xFF001170)

@Composable
fun LoginScreen(onLoginSuccess: (String, String, String) -> Unit) {
    val context         = LocalContext.current
    var extension       by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isAccepted      by remember { mutableStateOf(false) }
    var isLoading       by remember { mutableStateOf(false) }
    var errorMessage    by remember { mutableStateOf<String?>(null) }
    val scope           = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // ── CENTER CONTENT (LOGO + FORM CARD) ──
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Logo placed above the form card
                Image(
                    painter = painterResource(id = R.drawable.cnetgeek_logo),
                    contentDescription = "CnetGeek Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .padding(bottom = 16.dp)
                )

                // Sign In Form Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Extension Sign In",
                            color = Color(0xFF0F172A),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(24.dp))

                        if (errorMessage != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = errorMessage ?: "",
                                    color = Color(0xFFEF4444),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(10.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        // ── EXTENSION FIELD ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Extension no",
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        OutlinedTextField(
                            value = extension,
                            onValueChange = { extension = it },
                            placeholder = { Text("Extension Number", color = Color(0xFF0F172A).copy(alpha = 0.3f), fontSize = 14.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF0F172A),
                                unfocusedTextColor = Color(0xFF0F172A),
                                focusedBorderColor = LogoThemeColor,
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            )
                        )

                        Spacer(Modifier.height(18.dp))

                        // ── PASSWORD FIELD ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Password",
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = { Text("••••••••", color = Color(0xFF0F172A).copy(alpha = 0.3f)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = Color(0xFF0F172A).copy(alpha = 0.4f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF0F172A),
                                unfocusedTextColor = Color(0xFF0F172A),
                                focusedBorderColor = LogoThemeColor,
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            )
                        )

                        Spacer(Modifier.height(14.dp))

                        // ── PRIVACY POLICY CHECKBOX ROW ──
                        val policyText = buildAnnotatedString {
                            append("I accept CNETVOIP's ")
                            pushStringAnnotation(tag = "URL", annotation = "https://cnetgeek.com/privacy-policy/")
                            withStyle(
                                style = SpanStyle(
                                    color = LogoThemeColor,
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = TextDecoration.Underline
                                )
                            ) {
                                append("privacy policy")
                            }
                            pop()
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isAccepted,
                                onCheckedChange = { isAccepted = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = LogoThemeColor,
                                    uncheckedColor = Color(0xFF94A3B8),
                                    checkmarkColor = Color.White
                                ),
                                modifier = Modifier.size(28.dp)
                            )

                            Spacer(Modifier.width(8.dp))

                            ClickableText(
                                text = policyText,
                                style = LocalTextStyle.current.copy(
                                    fontSize = 12.sp,
                                    color = Color(0xFF0F172A).copy(alpha = 0.75f)
                                ),
                                onClick = { offset ->
                                    policyText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                        .firstOrNull()?.let { annotation ->
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                errorMessage = "Cannot open browser: ${e.message}"
                                            }
                                        }
                                }
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        // ── SUBMIT BUTTON (LOCKED UNLESS ACCEPTED) ──
                        val isSubmitEnabled = extension.isNotEmpty() && password.isNotEmpty() && isAccepted && !isLoading

                        Button(
                            onClick = {
                                if (isSubmitEnabled) {
                                    scope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        try {
                                            val res = apiService.login(mapOf("extension" to extension, "password" to password))
                                            if (res.isSuccessful && res.body()?.status == true) {
                                                val body = res.body()
                                                onLoginSuccess(extension, password, body?.tenantCode ?: "")
                                            } else {
                                                errorMessage = "Login Failed: Invalid credentials"
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Server Error: ${e.localizedMessage}"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            },
                            enabled = isSubmitEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LogoThemeColor,
                                disabledContainerColor = LogoThemeColor.copy(alpha = 0.4f)
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Submit",
                                        color = if (isSubmitEnabled) Color.White else Color.White.copy(alpha = 0.7f),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = if (isSubmitEnabled) Color.White else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── BOTTOM FOOTER ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFF0F172A).copy(alpha = 0.05f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Version 1.0.24",
                    color = Color(0xFF0F172A).copy(alpha = 0.45f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}