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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

        // Start foreground companion service immediately (unchanged)
        startForegroundService(Intent(this, LinkConnectionService::class.java))

        handleIncomingShareIntent(intent)

        setContent {
            LinkToMacTheme {
                LinkToMacAndroidScreen()
            }
        }
    }

    // ---------------------------------------------------------------- UI

    @Composable
    fun LinkToMacAndroidScreen() {
        val prefs = getSharedPreferences("linktomac", MODE_PRIVATE)
        var hostInput by remember { mutableStateOf(prefs.getString("lastHost", "192.168.1.100") ?: "") }
        var portInput by remember { mutableStateOf(prefs.getInt("lastPort", 54921).toString()) }
        var tokenInput by remember { mutableStateOf(prefs.getString("lastToken", "") ?: "") }
        var statusText by remember { mutableStateOf(LinkConnectionService.statusMessage) }
        var discoveredHost by remember { mutableStateOf(LinkConnectionService.discoveredMacHost) }
        var permissionTick by remember { mutableStateOf(0) }

        // Existing 1s status poll — now also refreshes permission checklist states
        LaunchedEffect(Unit) {
            while (true) {
                statusText = LinkConnectionService.statusMessage
                discoveredHost = LinkConnectionService.discoveredMacHost
                permissionTick++
                delay(1000)
            }
        }

        // Runtime permissions launcher (unchanged bundle)
        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> permissionTick++ }

        // QR scanner launcher (unchanged parsing)
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

        val perms = remember(permissionTick) { permissionStates() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = LS.l)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LS.l)
        ) {
            Spacer(Modifier.height(LS.xl))

            // ---- Brand + status
            Text(
                "LinkToMac",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            StatusCard(
                statusText = statusText,
                discoveredHost = discoveredHost,
                onAutoConnect = {
                    hostInput = discoveredHost
                    triggerConnect(discoveredHost, LinkConnectionService.discoveredMacPort, tokenInput)
                }
            )

            // ---- Pairing
            PairingCard(
                hostInput = hostInput,
                portInput = portInput,
                tokenInput = tokenInput,
                onHostChange = { hostInput = it },
                onPortChange = { portInput = it },
                onTokenChange = { tokenInput = it },
                onScanQR = {
                    val options = ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt("Scan the LinkToMac QR Code on your Mac")
                        setBeepEnabled(false)
                    }
                    qrLauncher.launch(options)
                },
                onConnectUSB = { triggerConnect("127.0.0.1", 54921, tokenInput) },
                onConnectWiFi = {
                    triggerConnect(hostInput.trim(), portInput.toIntOrNull() ?: 54921, tokenInput.trim())
                },
                onDisconnect = {
                    startService(Intent(this@MainActivity, LinkConnectionService::class.java).apply {
                        action = "DISCONNECT"
                    })
                }
            )

            // ---- Quick actions
            QuickActionsCard(
                onPushClipboard = { ClipboardManagerBridge.sendCurrentClipboardToMac(this@MainActivity) },
                onSyncSms = { SmsContactsCallBridge.syncSmsAndContactsToMac(this@MainActivity) }
            )

            // ---- Permissions checklist (stateful)
            PermissionsCard(
                states = perms,
                onRequestRuntime = { permLauncher.launch(runtimePermissionBundle()) },
                onOpenNotificationSettings = {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                onOpenStorageSettings = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            android.net.Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    }
                },
                onOpenBatterySettings = {
                    val pm = getSystemService(POWER_SERVICE) as PowerManager
                    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    }
                },
                onOpenAccessibilitySettings = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )

            Spacer(Modifier.height(LS.xxl))
        }
    }

    // ---------------------------------------------------- composable pieces

    @Composable
    private fun AppCard(content: @Composable ColumnScope.() -> Unit) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = LR.card,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(LS.l),
                verticalArrangement = Arrangement.spacedBy(LS.m),
                content = content
            )
        }
    }

    @Composable
    private fun CardTitle(text: String) {
        Text(
            text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    @Composable
    private fun StatusCard(
        statusText: String,
        discoveredHost: String,
        onAutoConnect: () -> Unit
    ) {
        val connected = LinkConnectionService.isConnectedState
        val status = linkStatusColors()
        AppCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            when {
                                connected -> status.success
                                else -> status.warning
                            },
                            CircleShape
                        )
                )
                Text(
                    if (connected) "Connected to Mac" else "Not connected",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                statusText,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (discoveredHost.isNotEmpty() && !connected) {
                FilledTonalButton(onClick = onAutoConnect, modifier = Modifier.fillMaxWidth()) {
                    Text("Connect to discovered Mac")
                }
            }
        }
    }

    @Composable
    private fun PairingCard(
        hostInput: String, portInput: String, tokenInput: String,
        onHostChange: (String) -> Unit,
        onPortChange: (String) -> Unit,
        onTokenChange: (String) -> Unit,
        onScanQR: () -> Unit,
        onConnectUSB: () -> Unit,
        onConnectWiFi: () -> Unit,
        onDisconnect: () -> Unit
    ) {
        AppCard {
            CardTitle("Pair with your Mac")

            Button(onClick = onScanQR, modifier = Modifier.fillMaxWidth()) {
                Text("Scan Mac QR code", fontWeight = FontWeight.SemiBold)
            }
            FilledTonalButton(onClick = onConnectUSB, modifier = Modifier.fillMaxWidth()) {
                Text("Connect via USB")
            }

            OutlinedTextField(
                value = hostInput,
                onValueChange = onHostChange,
                label = { Text("Mac local Wi-Fi address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(LS.s)) {
                OutlinedTextField(
                    value = portInput,
                    onValueChange = onPortChange,
                    label = { Text("Port") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = onTokenChange,
                    label = { Text("Pairing token") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(LS.s)) {
                Button(onClick = onConnectWiFi, modifier = Modifier.weight(1f)) {
                    Text("Connect")
                }
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.weight(1f)) {
                    Text("Disconnect")
                }
            }
        }
    }

    @Composable
    private fun QuickActionsCard(onPushClipboard: () -> Unit, onSyncSms: () -> Unit) {
        AppCard {
            CardTitle("Quick actions")
            Row(horizontalArrangement = Arrangement.spacedBy(LS.s)) {
                FilledTonalButton(onClick = onPushClipboard, modifier = Modifier.weight(1f)) {
                    Text("Push clipboard")
                }
                FilledTonalButton(onClick = onSyncSms, modifier = Modifier.weight(1f)) {
                    Text("Sync SMS & contacts")
                }
            }
        }
    }

    @Composable
    private fun PermissionsCard(
        states: PermissionStates,
        onRequestRuntime: () -> Unit,
        onOpenNotificationSettings: () -> Unit,
        onOpenStorageSettings: () -> Unit,
        onOpenBatterySettings: () -> Unit,
        onOpenAccessibilitySettings: () -> Unit
    ) {
        val status = linkStatusColors()
        AppCard {
            CardTitle("Companion permissions")

            PermissionRow(
                title = "Messages, calls & camera",
                subtitle = "SMS, contacts, phone state, camera and microphone",
                granted = states.runtime,
                onFix = onRequestRuntime
            )
            PermissionRow(
                title = "Notification mirroring",
                subtitle = "Read and reply to phone notifications from your Mac",
                granted = states.notificationListener,
                onFix = onOpenNotificationSettings
            )
            PermissionRow(
                title = "File access",
                subtitle = "Browse and transfer files & photos",
                granted = states.storage,
                onFix = onOpenStorageSettings
            )
            PermissionRow(
                title = "Unrestricted battery",
                subtitle = "Keep the connection alive in the background",
                granted = states.battery,
                onFix = onOpenBatterySettings
            )
            PermissionRow(
                title = "Accessibility service",
                subtitle = "Wireless touch, mirroring and background clipboard",
                granted = states.accessibility,
                onFix = onOpenAccessibilitySettings
            )

            if (states.allGranted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = status.success
                    )
                    Text(
                        "Everything is set up — LinkToMac is ready.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    @Composable
    private fun PermissionRow(
        title: String,
        subtitle: String,
        granted: Boolean,
        onFix: () -> Unit
    ) {
        val status = linkStatusColors()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LS.m),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = if (granted) "Granted" else null,
                tint = if (granted) status.success else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!granted) {
                FilledTonalButton(onClick = onFix) { Text("Fix") }
            }
        }
    }

    // ---------------------------------------------------- permission state

    private data class PermissionStates(
        val runtime: Boolean,
        val notificationListener: Boolean,
        val storage: Boolean,
        val battery: Boolean,
        val accessibility: Boolean
    ) {
        val allGranted: Boolean
            get() = runtime && notificationListener && storage && battery && accessibility
    }

    private fun runtimePermissionBundle(): Array<String> {
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
        return perms.toTypedArray()
    }

    private fun permissionStates(): PermissionStates {
        val runtimeGranted = runtimePermissionBundle().all {
            checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        val listenerEnabled = try {
            Settings.Secure.getString(
                contentResolver, "enabled_notification_listeners"
            )?.contains(packageName) == true
        } catch (_: Exception) { false }

        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true

        val batteryGranted = try {
            (getSystemService(POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(packageName)
        } catch (_: Exception) { false }

        return PermissionStates(
            runtime = runtimeGranted,
            notificationListener = listenerEnabled,
            storage = storageGranted,
            battery = batteryGranted,
            accessibility = LinkTouchAccessibilityService.instance != null
        )
    }

    // ---------------------------------------------------- non-UI (unchanged)

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
