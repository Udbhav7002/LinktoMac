package com.linktomac.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start foreground companion service immediately
        startForegroundService(Intent(this, LinkConnectionService::class.java))

        handleIncomingShareIntent(intent)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                LinkToMacAndroidScreen()
            }
        }
    }

    @Composable
    fun LinkToMacAndroidScreen() {
        val prefs = getSharedPreferences("linktomac", MODE_PRIVATE)
        var hostInput by remember { mutableStateOf(prefs.getString("lastHost", "192.168.1.100") ?: "") }
        var portInput by remember { mutableStateOf(prefs.getInt("lastPort", 54921).toString()) }
        var tokenInput by remember { mutableStateOf(prefs.getString("lastToken", "") ?: "") }
        var statusText by remember { mutableStateOf(LinkConnectionService.statusMessage) }
        var discoveredHost by remember { mutableStateOf(LinkConnectionService.discoveredMacHost) }

        // Poll connection status every second for UI
        LaunchedEffect(Unit) {
            while (true) {
                statusText = LinkConnectionService.statusMessage
                discoveredHost = LinkConnectionService.discoveredMacHost
                delay(1000)
            }
        }

        // Runtime Permissions Launcher
        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> }

        // QR Code Scanner Launcher
        val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
            val contents = result.contents ?: return@rememberLauncherForActivityResult
            try {
                val json = JSONObject(contents)
                val h = json.optString("host")
                val p = json.optInt("port", 54921)
                val t = json.optString("token")
                if (h.isNotEmpty()) {
                    hostInput = h
                    portInput = p.toString()
                    tokenInput = t
                    triggerConnect(h, p, t)
                }
            } catch (_: Exception) {}
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E1116))
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "LinkToMac",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4ADE80)
            )
            Text(
                text = "Make Android feel at home on your Mac • v1.61",
                fontSize = 14.sp,
                color = Color.LightGray
            )

            // Status Banner
            Surface(
                color = Color(0xFF1A202C),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (LinkConnectionService.isConnectedState) "● Connected to Mac" else "○ Not Connected",
                        color = if (LinkConnectionService.isConnectedState) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(text = statusText, color = Color.White, fontSize = 13.sp)

                    if (discoveredHost.isNotEmpty() && !LinkConnectionService.isConnectedState) {
                        Button(
                            onClick = {
                                hostInput = discoveredHost
                                triggerConnect(discoveredHost, LinkConnectionService.discoveredMacPort, tokenInput)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4ADE80))
                        ) {
                            Text("Auto-Connect Discovered Mac ($discoveredHost)", color = Color.Black)
                        }
                    }
                }
            }

            // Step 1: Connect via QR, USB, or Wi-Fi
            Surface(
                color = Color(0xFF1A202C),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("01 • Pair or Connect", fontWeight = FontWeight.Bold, color = Color.White)

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val options = ScanOptions().apply {
                                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    setPrompt("Scan the LinkToMac QR Code on your Mac")
                                    setBeepEnabled(false)
                                }
                                qrLauncher.launch(options)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4ADE80)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Scan Mac QR Code", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                // USB ADB reverse maps Mac port 54921 to phone's 127.0.0.1:54921
                                triggerConnect("127.0.0.1", 54921, tokenInput)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Connect via USB")
                        }
                    }

                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        label = { Text("Mac Local Wi-Fi IP") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it },
                            label = { Text("Port") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text("6-Digit Token") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                triggerConnect(hostInput.trim(), portInput.toIntOrNull() ?: 54921, tokenInput.trim())
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Connect Wi-Fi")
                        }
                        OutlinedButton(
                            onClick = {
                                startService(Intent(this@MainActivity, LinkConnectionService::class.java).apply {
                                    action = "DISCONNECT"
                                })
                            }
                        ) {
                            Text("Disconnect")
                        }
                    }
                }
            }

            // Step 2: Quick Actions (Send Clipboard & Sync)
            Surface(
                color = Color(0xFF1A202C),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("02 • Instant Desk Actions", fontWeight = FontWeight.Bold, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { ClipboardManagerBridge.sendCurrentClipboardToMac(this@MainActivity) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Push Clipboard to Mac")
                        }
                        Button(
                            onClick = { SmsContactsCallBridge.syncSmsAndContactsToMac(this@MainActivity) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sync SMS & Contacts")
                        }
                    }
                }
            }

            // Step 3: 1-Tap Permission Checklist
            Surface(
                color = Color(0xFF1A202C),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("03 • One-Time Companion Permissions", fontWeight = FontWeight.Bold, color = Color.White)

                    Button(
                        onClick = {
                            val perms = mutableListOf(
                                Manifest.permission.READ_SMS,
                                Manifest.permission.SEND_SMS,
                                Manifest.permission.RECEIVE_SMS,
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.READ_PHONE_STATE,
                                Manifest.permission.READ_CALL_LOG,
                                Manifest.permission.ANSWER_PHONE_CALLS,
                                Manifest.permission.CALL_PHONE,
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO
                            )
                            if (Build.VERSION.SDK_INT >= 33) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                perms.add(Manifest.permission.READ_MEDIA_IMAGES)
                                perms.add(Manifest.permission.READ_MEDIA_VIDEO)
                            } else {
                                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            permLauncher.launch(perms.toTypedArray())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1. Grant SMS, Call, Contacts, Camera & Mic")
                    }

                    Button(
                        onClick = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("2. Enable Notification Mirroring & Replies")
                    }

                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    android.net.Uri.parse("package:$packageName")
                                )
                                startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("3. Enable Full Storage Access (Files Transfer)")
                    }

                    Button(
                        onClick = {
                            val pm = getSystemService(POWER_SERVICE) as PowerManager
                            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                                val intent = Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    android.net.Uri.parse("package:$packageName")
                                )
                                startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("4. Allow Unrestricted Battery (Stay Connected)")
                    }

                    Button(
                        onClick = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("5. Enable Accessibility (Wireless Touch, Mirror & Background Clipboard)")
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return

        when (action) {
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrBlank()) {
                    ClipboardManagerBridge.sendClipboardToMac(sharedText)
                    val trimmed = sharedText.trim()
                    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                        val frame = WireProtocol.packJson(
                            type = "URL_HANDOFF",
                            payloadMap = mapOf("url" to trimmed)
                        )
                        LinkConnectionService.instance?.sendRawFrame(frame)
                    }
                }

                val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                }

                if (streamUri != null) {
                    shareUriToMac(streamUri)
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                }

                if (!streamUris.isNullOrEmpty()) {
                    for (uri in streamUris) {
                        shareUriToMac(uri)
                    }
                }
            }
        }
    }

    private fun shareUriToMac(uri: android.net.Uri) {
        Thread {
            try {
                var fileName = "shared_${System.currentTimeMillis()}"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) {
                        cursor.getString(nameIdx)?.let { fileName = it }
                    }
                }
                val tempFile = java.io.File(cacheDir, fileName)
                contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                FilePhotosBridge.streamFileToMac(tempFile.absolutePath, fileName, "true")
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Failed to share URI to Mac: ${e.message}")
            }
        }.start()
    }

    private fun triggerConnect(host: String, port: Int, token: String) {
        startService(Intent(this, LinkConnectionService::class.java).apply {
            action = "CONNECT_HOST"
            putExtra("host", host)
            putExtra("port", port)
            putExtra("token", token)
        })
    }
}







