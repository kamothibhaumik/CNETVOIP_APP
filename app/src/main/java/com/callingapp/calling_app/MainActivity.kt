package com.callingapp.calling_app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.callingapp.calling_app.ui.screens.LoginScreen
import com.callingapp.calling_app.ui.screens.MainAppShell
import com.callingapp.calling_app.ui.screens.SplashScreen
import com.callingapp.calling_app.ui.theme.Calling_appTheme
import com.callingapp.calling_app.utils.CallLog

class MainActivity : ComponentActivity() {

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Permissions handled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createCallNotificationChannel(this)
        setTheme(R.style.Theme_Calling_app)
        enableEdgeToEdge()

        val sharedPref     = getSharedPreferences("OfficeAppPrefs", Context.MODE_PRIVATE)
        val savedExtension = sharedPref.getString("ext",         "") ?: ""
        val savedPassword  = sharedPref.getString("pwd",         "") ?: ""
        val savedTenant    = sharedPref.getString("tenant_code", "") ?: ""

        CallLog.init(this, savedExtension.ifBlank { "default" })

        if (savedExtension.isNotEmpty() && savedPassword.isNotEmpty()) {
            val intent = android.content.Intent(
                this,
                com.callingapp.calling_app.sip.SipForegroundService::class.java
            ).apply {
                action = com.callingapp.calling_app.sip.SipForegroundService.ACTION_START
                putExtra("EXTENSION", savedExtension)
                putExtra("PASSWORD", savedPassword)
                putExtra("SERVER", "core.cnetvoip.cloud")
            }
            androidx.core.content.ContextCompat.startForegroundService(this, intent)
        }

        val activeMgr = com.callingapp.calling_app.sip.SipModule.udpManager
        if (activeMgr != null && activeMgr.callIsActive) {
            android.util.Log.d("MainActivity", "Active call on app open — restoring call screen")
            startActivity(
                android.content.Intent(this, com.callingapp.calling_app.ui.screens.CallActivity::class.java).apply {
                    putExtra("NUMBER",       activeMgr.lastTarget)
                    putExtra("CALLER_NAME",  activeMgr.lastCallerName)
                    putExtra("IS_INCOMING",  activeMgr.callIsIncoming)
                    putExtra("RESTORE_CALL", true)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            )
            finish()
            return
        }

        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionsLauncher.launch(permissions.toTypedArray())

        // ✅ Check on normal app launch if the user is already logged in
        checkAndRequestBatteryExemption(this)

        setContent {
            Calling_appTheme {
                var showSplash by remember { mutableStateOf(true) }
                var isLoggedIn by remember {
                    mutableStateOf(savedExtension.isNotEmpty() && savedPassword.isNotEmpty())
                }
                var userExtension by remember { mutableStateOf(savedExtension) }
                var userPassword  by remember { mutableStateOf(savedPassword)  }
                var tenantCode    by remember { mutableStateOf(savedTenant)    }

                if (showSplash) {
                    SplashScreen(onFinished = { showSplash = false })
                } else {
                    if (!isLoggedIn) {
                        LoginScreen { ext, pwd, tCode ->
                            sharedPref.edit {
                                putString("ext", ext)
                                putString("pwd", pwd)
                                putString("tenant_code", tCode)
                            }

                            CallLog.init(this@MainActivity, ext)

                            val serviceIntent = android.content.Intent(
                                this@MainActivity,
                                com.callingapp.calling_app.sip.SipForegroundService::class.java
                            ).apply {
                                action = com.callingapp.calling_app.sip.SipForegroundService.ACTION_START
                                putExtra("EXTENSION", ext)
                                putExtra("PASSWORD", pwd)
                                putExtra("SERVER", "core.cnetvoip.cloud")
                            }

                            androidx.core.content.ContextCompat.startForegroundService(
                                this@MainActivity,
                                serviceIntent
                            )

                            // ✅ Check immediately after the user logs in for the first time
                            checkAndRequestBatteryExemption(this@MainActivity)

                            userExtension = ext
                            userPassword = pwd
                            tenantCode = tCode
                            isLoggedIn = true
                        }
                    } else {
                        MainAppShell(
                            extension = userExtension,
                            password = userPassword,
                            tenantCode = tenantCode,
                            onLogout = {
                                sharedPref.edit { clear() }
                                isLoggedIn = false
                            }
                        )
                    }
                }
            }
        }
    }

    // ✅ ADDED EXTRA CLEAN METHOD OUTSIDE ONCREATE FOR BETTER SYSTEM ARCHITECTURE
    private fun checkAndRequestBatteryExemption(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Battery optimization prompt failed: ${e.message}")
                }
            }
        }
    }

    private fun createCallNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "INCOMING_CALL_CHANNEL",
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "CNETVOIP call alerts"
                setSound(null, null)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
