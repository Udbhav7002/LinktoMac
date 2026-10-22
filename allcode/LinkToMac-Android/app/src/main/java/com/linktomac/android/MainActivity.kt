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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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

        enableEdgeToEdge()

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
        var tokenInput by remember { mutableStateOf(prefs.getString("lastToken", "") ?: "") }
        var statusText by remember { mutableStateOf(LinkConnectionService.statusMessage) }
        var discoveredHost by remember { mutableStateOf(LinkConnectionService.discoveredMacHost) }
        var permissionTick by remember { mutableStateOf(0) }
        var showRestrictedGuide by remember { mutableStateOf(false) }

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
                val n = json.optString("macName")
                if (h.isNotEmpty()) {
                    tokenInput = t
                    triggerConnect(h, p, t, n)
                }
            } catch (_: Exception) {}
        }

        val perms = remember(permissionTick) { permissionStates() }
        val connected = LinkConnectionService.isConnectedState

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LS.xxl),
            verticalArrangement = Arrangement.spacedBy(LS.l)
        ) {
            Spacer(Modifier.height(LS.xl))

            BrandHeader()

            StatusCard(
                statusText = statusText,
                discoveredHost = discoveredHost,
                connected = connected,
                onAutoConnect = {
                    triggerConnect(discoveredHost, LinkConnectionService.discoveredMacPort, tokenInput)
                },
                onDisconnect = {
                    startService(Intent(this@MainActivity, LinkConnectionService::class.java).apply {
                        action = "DISCONNECT"
                    })
                }
            )

            if (!connected) {
                PairingCard(
                    tokenInput = tokenInput,
                    onScanQR = {
                        val options = ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("Scan the LinkToMac QR Code on your Mac")
                            setBeepEnabled(false)
                            setOrientationLocked(true)
                        }
                        qrLauncher.launch(options)
                    },
                    onConnectUSB = { triggerConnect("127.0.0.1", 54921, tokenInput) }
                )
            }

            // ---- Permissions checklist (only what is still missing)
            if (!perms.allGranted) {
                PermissionsCard(
                    states = perms,
                    onRequestRuntime = { permLauncher.launch(runtimePermissionBundle()) },
                    onOpenNotificationSettings = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            showRestrictedGuide = true
                        } else {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    },
                    onOpenStorageSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO))
                        } else {
                            permLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
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
                        if (Build.VERSION.SDK_INT >= 33) {
                            showRestrictedGuide = true
                        } else {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    }
                )
            }

            if (showRestrictedGuide) {
                RestrictedSettingsUnlockDialog(
                    onDismiss = { showRestrictedGuide = false },
                    onOpenAppDetails = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    },
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }

            Spacer(Modifier.height(LS.xxl))
        }
    }

    // ---------------------------------------------------- composable pieces

    @Composable
    private fun BrandHeader() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LS.m)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.primary, LR.control)
                    .padding(5.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_link),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column {
                Text(
                    "LinkToMac",
                    fontSize = LT.hero,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Local & private",
                    fontSize = LT.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun AppCard(content: @Composable ColumnScope.() -> Unit) {
        val status = linkStatusColors()
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = LR.card,
            border = BorderStroke(1.dp, status.hairline),
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
    private fun StatusDot(connected: Boolean) {
        val status = linkStatusColors()
        Box(
            Modifier
                .size(8.dp)
                .background(
                    if (connected) status.success else status.warning,
                    CircleShape
                )
        )
    }

    @Composable
    private fun QuietDangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
        val status = linkStatusColors()
        FilledTonalButton(
            onClick = onClick,
            shape = LR.control,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = status.dangerQuiet,
                contentColor = status.danger
            ),
            modifier = modifier
        ) {
            Text(text)
        }
    }

    private fun friendlyStatus(raw: String): String = when {
        raw.startsWith("Connection failed") || raw.contains("ECONNREFUSED") ->
            "Couldn't reach your Mac. Keep LinkToMac open on the Mac, then try USB or QR again."
        raw.startsWith("Connected") ->
            if (raw.contains("USB")) "Connected over USB" else "Connected over Wi-Fi"
        raw.startsWith("Connecting to") -> "Connecting to your Mac…"
        raw.startsWith("Reconnecting to") -> "Reconnecting…"
        raw.startsWith("Waiting for Mac") -> "Waiting for your Mac…"
        raw == "Disconnected" -> "Not connected"
        else -> raw
    }

    @Composable
    private fun StatusCard(
        statusText: String,
        discoveredHost: String,
        connected: Boolean,
        onAutoConnect: () -> Unit,
        onDisconnect: () -> Unit
    ) {
        AppCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LS.s)
            ) {
                StatusDot(connected)
                Text(
                    if (connected) {
                        val macName = LinkConnectionService.connectedMacName
                        if (macName.isNotBlank()) "Connected to $macName" else "Connected to Mac"
                    } else {
                        "Not connected"
                    },
                    fontSize = LT.section,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                friendlyStatus(statusText),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (discoveredHost.isNotEmpty() && !connected) {
                FilledTonalButton(onClick = onAutoConnect, shape = LR.control, modifier = Modifier.fillMaxWidth()) {
                    Text("Connect to discovered Mac")
                }
            }
            if (connected) {
                QuietDangerButton("Disconnect", onClick = onDisconnect, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    @Composable
    private fun PairingCard(
        tokenInput: String,
        onScanQR: () -> Unit,
        onConnectUSB: () -> Unit
    ) {
        AppCard {
            Button(onClick = onScanQR, shape = LR.control, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(R.drawable.ic_qr),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(LS.s))
                Text("Scan Mac QR code")
            }
            FilledTonalButton(onClick = onConnectUSB, shape = LR.control, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(R.drawable.ic_usb),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(LS.s))
                Text("Connect via USB")
            }
        }
    }

    @Composable
    private fun HairlineDivider() {
        val status = linkStatusColors()
        HorizontalDivider(color = status.hairline)
    }

    @Composable
    private fun PermissionTile(iconRes: Int) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, LR.control)
                .padding(7.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize()
            )
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
        data class Row(
            val icon: Int,
            val title: String,
            val subtitle: String,
            val granted: Boolean,
            val onFix: () -> Unit
        )

        val missing = listOf(
            Row(
                icon = R.drawable.ic_link,
                title = "Camera & Microphone",
                subtitle = "Virtual webcam and audio streaming for your Mac",
                granted = states.runtime,
                onFix = onRequestRuntime
            ),
            Row(
                icon = R.drawable.ic_bell,
                title = "Notification mirroring",
                subtitle = "Read and reply to phone notifications from your Mac",
                granted = states.notificationListener,
                onFix = onOpenNotificationSettings
            ),
            Row(
                icon = R.drawable.ic_folder,
                title = "File access",
                subtitle = "Browse and transfer files & photos",
                granted = states.storage,
                onFix = onOpenStorageSettings
            ),
            Row(
                icon = R.drawable.ic_battery,
                title = "Unrestricted battery",
                subtitle = "Keep the connection alive in the background",
                granted = states.battery,
                onFix = onOpenBatterySettings
            ),
            Row(
                icon = R.drawable.ic_accessibility,
                title = "Accessibility service",
                subtitle = "Wireless touch, mirroring and background clipboard",
                granted = states.accessibility,
                onFix = onOpenAccessibilitySettings
            )
        ).filter { !it.granted }

        if (missing.isEmpty()) return

        AppCard {
            Text(
                "Permissions needed",
                fontSize = LT.section,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            missing.forEachIndexed { index, row ->
                if (index > 0) HairlineDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LS.m),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PermissionTile(row.icon)
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.title,
                            fontSize = LT.body,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            row.subtitle,
                            fontSize = LT.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(onClick = row.onFix, shape = LR.control) { Text("Fix") }
                }
            }
        }
    }

    @Composable
    private fun RestrictedSettingsUnlockDialog(
        onDismiss: () -> Unit,
        onOpenAppDetails: () -> Unit,
        onOpenAccessibility: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "Unlock Remote Control (Android 13+)",
                    fontSize = LT.section,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(LS.m)) {
                    Text(
                        text = "Android protects sideloaded apps by restricting accessibility and notifications by default. Follow these quick steps to unlock:",
                        fontSize = LT.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(LS.s),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = LR.card)
                            .padding(LS.m)
                    ) {
                        Text(
                            text = "1. Tap '1. Open App Settings' below.",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = LT.body,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "2. Tap the three dots (⋮) in the top-right corner and select 'Allow restricted settings' (confirm with PIN/fingerprint).",
                            fontSize = LT.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "3. Tap '2. Enable Accessibility' below and toggle LinkToMac ON.",
                            fontSize = LT.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onOpenAppDetails,
                    shape = LR.control
                ) {
                    Text("1. Open App Settings")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onOpenAccessibility,
                    shape = LR.control
                ) {
                    Text("2. Enable Accessibility")
                }
            }
        )
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

        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

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

    private fun triggerConnect(host: String, port: Int, token: String, macName: String = "") {
        startService(Intent(this, LinkConnectionService::class.java).apply {
            action = "CONNECT_HOST"
            putExtra("host", host)
            putExtra("port", port)
            putExtra("token", token)
            putExtra("macName", macName)
        })
    }
}
