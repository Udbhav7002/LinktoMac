# LinkToMac — Complete Android Source Code (`allcode/LinkToMac-Android`)

Every file below belongs inside the codebase. Run `python3 stitch_allcode.py` from `LINKTOMAC_MASTER_PLAN.md` to extract them automatically.

---

### FILE: allcode/LinkToMac-Android/settings.gradle.kts
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "LinkToMacAndroid"
include(":app")






```

---

### FILE: allcode/LinkToMac-Android/gradle.properties
```properties
android.useAndroidX=true
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.builtInKotlin=false
android.newDsl=false
kotlin.compiler.execution.strategy=in-process






```

---

### FILE: allcode/LinkToMac-Android/gradle/wrapper/gradle-wrapper.properties
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
networkTimeout=10000
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists






```

---

### FILE: allcode/LinkToMac-Android/build.gradle.kts
```kotlin
plugins {
    id("com.android.application") version "8.3.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}






```

---

### FILE: allcode/LinkToMac-Android/app/build.gradle.kts
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.linktomac.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.linktomac.android"
        minSdk = 29
        targetSdk = 34
        versionCode = 161
        versionName = "1.61"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // ZXing QR Scanner for 1-tap QR code pairing
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}






```

---

### FILE: allcode/LinkToMac-Android/app/src/main/AndroidManifest.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Local Wi-Fi & Foreground Service -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <!-- SMS, Contacts & Call Controls -->
    <uses-permission android:name="android.permission.READ_SMS" />
    <uses-permission android:name="android.permission.SEND_SMS" />
    <uses-permission android:name="android.permission.RECEIVE_SMS" />
    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.READ_CALL_LOG" />
    <uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
    <uses-permission android:name="android.permission.CALL_PHONE" />

    <!-- Files, Photos, Camera & Microphone -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" tools:ignore="ScopedStorage" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <application
        android:allowBackup="true"
        android:label="LinkToMac"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <!-- Share Sheet Target: "Send to Mac (LinkToMac)" -->
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="*/*" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.SEND_MULTIPLE" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="*/*" />
            </intent-filter>
        </activity>

        <service
            android:name=".LinkConnectionService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="connectedDevice|camera|microphone|mediaProjection" />

        <service
            android:name=".LinkNotificationService"
            android:label="LinkToMac Notification &amp; Media Bridge"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

        <service
            android:name=".LinkTouchAccessibilityService"
            android:label="LinkToMac Wireless Touch &amp; Mirror Bridge"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/link_accessibility_config" />
        </service>
    </application>
</manifest>






```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/values/strings.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">LinkToMac</string>
    <string name="accessibility_service_desc">Enables remote wireless touch input, navigation gestures, and screen capture streaming from your Mac.</string>
</resources>






```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/xml/accessibility_service_config.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_desc"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="50"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:canTakeScreenshot="true" />






```

---

### FILE: allcode/LinkToMac-Android/app/src/main/res/xml/link_accessibility_config.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_desc"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:canPerformGestures="true"
    android:canTakeScreenshot="true"
    android:accessibilityFlags="flagDefault|flagIncludeNotImportantViews|flagReportViewIds|flagRetrieveInteractiveWindows" />





```

---

### FILE: allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/ProtocolModels.kt
```kotlin
package com.linktomac.android

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WireProtocol {
    const val FRAME_JSON: Byte       = 0x01
    const val FRAME_FILE_CHUNK: Byte = 0x02
    const val FRAME_VIDEO_JPEG: Byte = 0x03
    const val FRAME_AUDIO_PCM: Byte  = 0x04

    fun packFrame(frameType: Byte, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(5 + payload.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(payload.size)
        buffer.put(frameType)
        buffer.put(payload)
        return buffer.array()
    }

    fun packJson(type: String, payloadMap: Map<String, String> = emptyMap(), extraBlock: (JSONObject.() -> Unit)? = null): ByteArray {
        val root = JSONObject()
        root.put("type", type)
        root.put("timestamp", System.currentTimeMillis())
        val payloadObj = JSONObject()
        for ((k, v) in payloadMap) {
            payloadObj.put(k, v)
        }
        root.put("payload", payloadObj)
        extraBlock?.invoke(root)
        return packFrame(FRAME_JSON, root.toString().toByteArray(Charsets.UTF_8))
    }
}






```

---

### FILE: allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/LinkTouchAccessibilityService.kt
```kotlin
package com.linktomac.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.io.ByteArrayOutputStream

class LinkTouchAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "LinkTouchA11y"

        @Volatile
        var instance: LinkTouchAccessibilityService? = null

        fun getRealScreenSize(context: android.content.Context): Pair<Int, Int> {
            val metrics = context.resources.displayMetrics
            return Pair(metrics.widthPixels, metrics.heightPixels)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            serviceInfo = info
        } catch (_: Exception) {}
        Log.i(TAG, "LinkTouchAccessibilityService connected")
    }

    fun performNormalizedTap(normX: Float, normY: Float): Boolean {
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        val px = (normX * width).coerceIn(0f, width.toFloat())
        val py = (normY * height).coerceIn(0f, height.toFloat())

        val path = Path().apply {
            moveTo(px, py)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    fun performRemoteKey(keyCode: Int): Boolean {
        return when (keyCode) {
            3 -> performGlobalAction(GLOBAL_ACTION_HOME)
            4 -> performGlobalAction(GLOBAL_ACTION_BACK)
            187 -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            26 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                } else false
            }
            83 -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            else -> false
        }
    }

    fun captureScreenJpeg(callback: (ByteArray?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(null)
            return
        }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val hwBuffer = result.hardwareBuffer
                    val colorSpace = result.colorSpace
                    try {
                        val hwBmp = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                        if (hwBmp == null) {
                            callback(null)
                            return
                        }
                        val origW = hwBmp.width
                        val origH = hwBmp.height
                        val targetW = 540
                        val targetH = ((origH.toFloat() / origW.toFloat()) * targetW).toInt().coerceAtLeast(1)

                        val scaled = Bitmap.createScaledBitmap(hwBmp, targetW, targetH, true)
                        val baos = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                        scaled.recycle()
                        hwBmp.recycle()
                        callback(baos.toByteArray())
                    } catch (e: Exception) {
                        Log.w(TAG, "captureScreenJpeg processing failed: ${e.message}")
                        callback(null)
                    } finally {
                        hwBuffer.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "takeScreenshot failed with code: $errorCode")
                    callback(null)
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "captureScreenJpeg invocation failed: ${e.message}")
            callback(null)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                ClipboardManagerBridge.syncFromAccessibilityService(this)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "LinkTouchAccessibilityService interrupted")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "LinkTouchAccessibilityService destroyed")
    }
}





```

---

### FILE: allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/LinkConnectionService.kt
```kotlin
package com.linktomac.android

import android.app.*
import android.content.*
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class LinkConnectionService : Service() {

    companion object {
        var instance: LinkConnectionService? = null
        var isConnectedState: Boolean = false
        var statusMessage: String = "Disconnected"
        var discoveredMacHost: String = ""
        var discoveredMacPort: Int = 54921
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val incomingFiles = mutableMapOf<String, FileOutputStream>()

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundNotification("LinkToMac Ready — Waiting for Mac")
        startMdnsDiscovery()
        ClipboardManagerBridge.init(this)
        SmsContactsCallBridge.registerCallStateReceiver(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "CONNECT_HOST") {
            val host = intent.getStringExtra("host") ?: "127.0.0.1"
            val port = intent.getIntExtra("port", 54921)
            val token = intent.getStringExtra("token") ?: ""
            connectToMac(host, port, token)
        } else if (action == "DISCONNECT") {
            closeSocket()
        }
        return START_STICKY
    }

    fun connectToMac(host: String, port: Int, token: String) {
        serviceScope.launch {
            closeSocket()
            try {
                statusMessage = "Connecting to $host:$port..."
                val s = Socket()
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(host, port), 4000)
                socket = s
                outputStream = s.getOutputStream()
                isConnectedState = true
                val transport = if (host == "127.0.0.1") "USB" else "Local Wi-Fi"
                statusMessage = "Connected ($transport • $host)"
                startForegroundNotification("Connected to Mac ($transport)")

                // Save last successful host
                getSharedPreferences("linktomac", MODE_PRIVATE).edit()
                    .putString("lastHost", host)
                    .putInt("lastPort", port)
                    .putString("lastToken", token)
                    .apply()

                sendHelloAndStatus(transport, token)
                startStatusHeartbeat(transport)
                readLoop(DataInputStream(s.getInputStream()))
            } catch (e: Exception) {
                isConnectedState = false
                statusMessage = "Connection failed: ${e.localizedMessage}"
            }
        }
    }

    fun sendRawFrame(frameBytes: ByteArray) {
        serviceScope.launch {
            try {
                synchronized(this@LinkConnectionService) {
                    outputStream?.write(frameBytes)
                    outputStream?.flush()
                }
            } catch (_: Exception) {
                closeSocket()
            }
        }
    }

    private fun sendHelloAndStatus(transport: String, token: String) {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        val model = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

        val bytes = WireProtocol.packJson(
            type = "HELLO",
            payloadMap = mapOf(
                "deviceName" to model,
                "battery" to battery.toString(),
                "charging" to charging.toString(),
                "transport" to transport,
                "pairToken" to token,
                "mediaTitle" to LinkNotificationService.currentMediaTitle,
                "mediaArtist" to LinkNotificationService.currentMediaArtist,
                "screenWidth" to resources.displayMetrics.widthPixels.toString(),
                "screenHeight" to resources.displayMetrics.heightPixels.toString()
            )
        )
        sendRawFrame(bytes)
    }

    private fun startStatusHeartbeat(transport: String) {
        serviceScope.launch {
            while (isActive && isConnectedState) {
                delay(8000)
                val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
                val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val charging = bm.isCharging
                val bytes = WireProtocol.packJson(
                    type = "STATUS_UPDATE",
                    payloadMap = mapOf(
                        "deviceName" to "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                        "battery" to battery.toString(),
                        "charging" to charging.toString(),
                        "transport" to transport,
                        "mediaTitle" to LinkNotificationService.currentMediaTitle,
                        "mediaArtist" to LinkNotificationService.currentMediaArtist,
                        "screenWidth" to resources.displayMetrics.widthPixels.toString(),
                        "screenHeight" to resources.displayMetrics.heightPixels.toString()
                    )
                )
                sendRawFrame(bytes)
            }
        }
    }

    private fun readLoop(input: DataInputStream) {
        try {
            while (isConnectedState) {
                val length = input.readInt()
                val type = input.readByte()
                if (length <= 0 || length > 30_000_000) continue
                val payload = ByteArray(length)
                input.readFully(payload)

                when (type) {
                    WireProtocol.FRAME_JSON -> handleIncomingJson(String(payload, Charsets.UTF_8))
                    WireProtocol.FRAME_FILE_CHUNK -> handleIncomingFileChunk(payload)
                }
            }
        } catch (_: Exception) {
            closeSocket()
        }
    }

    private fun handleIncomingJson(jsonStr: String) {
        val obj = JSONObject(jsonStr)
        val type = obj.optString("type")
        val payload = obj.optJSONObject("payload") ?: JSONObject()

        when (type) {
            "NOTIFICATION_REPLY" -> {
                val id = payload.optString("id")
                val replyText = payload.optString("replyText")
                LinkNotificationService.performInlineReply(this, id, replyText)
            }
            "NOTIFICATION_REMOVED" -> {
                val id = payload.optString("id")
                LinkNotificationService.dismissNotification(id)
            }
            "SMS_THREADS_REQ" -> {
                SmsContactsCallBridge.syncSmsAndContactsToMac(this)
            }
            "SMS_SEND" -> {
                val addr = payload.optString("address")
                val body = payload.optString("body")
                SmsContactsCallBridge.sendSms(this, addr, body)
            }
            "CALL_ACTION" -> {
                val action = payload.optString("action")
                val number = payload.optString("number")
                val smsReply = payload.optString("smsReply")
                SmsContactsCallBridge.handleCallAction(this, action, number, smsReply)
            }
            "CLIPBOARD_SYNC" -> {
                val content = payload.optString("content")
                ClipboardManagerBridge.applyFromMac(this, content)
            }
            "URL_HANDOFF" -> {
                val url = payload.optString("url")
                if (url.isNotEmpty()) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(browserIntent)
                }
            }
            "FILE_LIST_REQ" -> {
                val path = payload.optString("path", "/sdcard/Download")
                FilePhotosBridge.sendDirectoryListing(path)
            }
            "PHOTOS_LIST_REQ" -> {
                FilePhotosBridge.sendPhotosList(this)
            }
            "FILE_PULL_REQ" -> {
                val path = payload.optString("path")
                val fileName = payload.optString("fileName")
                val reveal = payload.optString("revealInFinder", "true")
                FilePhotosBridge.streamFileToMac(path, fileName, reveal)
            }
            "FILE_TRANSFER_START" -> {
                val tid = payload.optString("transferId")
                val fileName = payload.optString("fileName")
                val destFolder = payload.optString("destFolder", "/sdcard/Download/LinkToMac")
                val dir = File(destFolder)
                if (!dir.exists()) dir.mkdirs()
                val outFile = File(dir, fileName)
                incomingFiles[tid] = FileOutputStream(outFile)
            }
            "FILE_TRANSFER_END" -> {
                val tid = payload.optString("transferId")
                incomingFiles.remove(tid)?.close()
            }
            "MEDIA_ACTION" -> {
                val action = payload.optString("action")
                handleMediaAction(action)
            }
            "STREAM_CONTROL" -> {
                ScreenCameraStreamer.handleStreamControl(this, payload)
            }
        }
    }

    private fun handleIncomingFileChunk(payload: ByteArray) {
        if (payload.size <= 36) return
        val tid = String(payload, 0, 36, Charsets.UTF_8).trim()
        val fos = incomingFiles[tid] ?: return
        fos.write(payload, 36, payload.size - 36)
    }

    private fun handleMediaAction(action: String) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        when (action) {
            "PLAY_PAUSE" -> dispatchMediaKey( android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "NEXT"       -> dispatchMediaKey( android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
            "PREV"       -> dispatchMediaKey( android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "VOL_UP"     -> audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "VOL_DOWN"   -> audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "RING_PHONE" -> {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val ringtone = RingtoneManager.getRingtone(applicationContext, uri)
                ringtone?.play()
            }
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
    }

    // MARK: - mDNS Automatic Discovery (_linktomac._tcp)
    private fun startMdnsDiscovery() {
        try {
            nsdManager = getSystemService(NSD_SERVICE) as NsdManager
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    if (service.serviceType.contains("_linktomac._tcp")) {
                        nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(resolved: NsdServiceInfo) {
                                val host = resolved.host?.hostAddress ?: return
                                discoveredMacHost = host
                                discoveredMacPort = resolved.port
                            }
                        })
                    }
                }
                override fun onServiceLost(service: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            discoveryListener = listener
            nsdManager?.discoverServices("_linktomac._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {}
    }

    private fun closeSocket() {
        isConnectedState = false
        statusMessage = "Disconnected"
        try { outputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        outputStream = null
        socket = null
    }

    private fun startForegroundNotification(text: String) {
        val channelId = "linktomac_channel"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val chan = NotificationChannel(channelId, "LinkToMac Connection", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(chan)

        val notif = Notification.Builder(this, channelId)
            .setContentTitle("LinkToMac Companion")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1001, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(1001, notif)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

// MARK: - Clipboard Bridge Helper
object ClipboardManagerBridge {
    private var lastClipboardText = ""

    fun init(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener {
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim() ?: return@addPrimaryClipChangedListener
            if (text.isNotEmpty() && text != lastClipboardText) {
                lastClipboardText = text
                sendClipboardToMac(text)
            }
        }
    }

    fun sendCurrentClipboardToMac(context: Context) {
        Handler(Looper.getMainLooper()).post {
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val text = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim() ?: return@post
                if (text.isNotEmpty() && text != lastClipboardText) {
                    lastClipboardText = text
                    sendClipboardToMac(text)
                }
            } catch (_: Exception) {}
        }
    }

    fun syncFromAccessibilityService(context: Context) {
        sendCurrentClipboardToMac(context)
    }

    fun sendClipboardToMac(text: String) {
        val isUrl = text.startsWith("http://") || text.startsWith("https://")
        val bytes = WireProtocol.packJson(
            type = "CLIPBOARD_SYNC",
            payloadMap = mapOf("content" to text, "isUrl" to isUrl.toString())
        )
        LinkConnectionService.instance?.sendRawFrame(bytes)
    }

    fun applyFromMac(context: Context, text: String) {
        if (text == lastClipboardText) return
        lastClipboardText = text
        Handler(Looper.getMainLooper()).post {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("LinkToMac", text))
        }
    }
}






```

---

### FILE: allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/LinkNotificationService.kt
```kotlin
package com.linktomac.android

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class LinkNotificationService : NotificationListenerService() {

    private data class ReplyActionHolder(
        val pendingIntent: PendingIntent,
        val remoteInputs: Array<RemoteInput>
    )

    companion object {
        private var instance: LinkNotificationService? = null
        private val replyActions = ConcurrentHashMap<String, ReplyActionHolder>()
        var currentMediaTitle: String = "Nothing Playing"
        var currentMediaArtist: String = "Android Media"

        fun performInlineReply(context: Context, key: String, replyText: String) {
            val holder = replyActions[key] ?: return
            val intent = Intent()
            val bundle = Bundle()
            for (remoteInput in holder.remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(holder.remoteInputs, intent, bundle)
            try {
                holder.pendingIntent.send(context, 0, intent)
            } catch (_: Exception) {}
        }

        fun dismissNotification(key: String) {
            try {
                instance?.cancelNotification(key)
            } catch (_: Exception) {}
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        updateActiveMediaSession()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        guardSbn(sbn) ?: return
        updateActiveMediaSession()

        val notif = sbn!!.notification
        if ((notif.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if (sbn.packageName == packageName || sbn.packageName == "com.android.systemui") return

        val extras = notif.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isEmpty() && body.isEmpty()) return

        // Check for inline RemoteInput reply actions
        var canReply = false
        notif.actions?.forEach { action ->
            val inputs = action.remoteInputs
            if (!inputs.isNullOrEmpty() && action.actionIntent != null) {
                canReply = true
                replyActions[sbn.key] = ReplyActionHolder(action.actionIntent, inputs)
            }
        }

        // Detect 4-8 digit 2FA / OTP codes automatically
        val otpRegex = Regex("\\b(\\d{4,8})\\b")
        val otpMatch = otpRegex.find(body)?.value ?: ""

        val pm = applicationContext.packageManager
        val appName = try {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        val itemJson = JSONObject().apply {
            put("id", sbn.key)
            put("packageName", sbn.packageName)
            put("appName", appName)
            put("title", title)
            put("body", body)
            put("timestamp", sbn.postTime)
            put("canReply", canReply)
            put("otpCode", otpMatch)
        }

        val arr = JSONArray().apply { put(itemJson) }
        val bytes = WireProtocol.packJson("NOTIFICATION_POSTED") {
            put("notifications", arr)
        }
        LinkConnectionService.instance?.sendRawFrame(bytes)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        replyActions.remove(key)
        val bytes = WireProtocol.packJson(
            type = "NOTIFICATION_REMOVED",
            payloadMap = mapOf("id" to key)
        )
        LinkConnectionService.instance?.sendRawFrame(bytes)
    }

    private fun updateActiveMediaSession() {
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers = msm.getActiveSessions(
                android.content.ComponentName(this, LinkNotificationService::class.java)
            )
            val first = controllers.firstOrNull()
            val meta = first?.metadata
            if (meta != null) {
                currentMediaTitle = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Playing Media"
                currentMediaArtist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            }
        } catch (_: Exception) {}
    }

    private fun guardSbn(sbn: StatusBarNotification?): StatusBarNotification? = sbn
}






```

---

### FILE: allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/SmsContactsCallBridge.kt
```kotlin
package com.linktomac.android

import android.annotation.SuppressLint
import android.content.*
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import org.json.JSONArray
import org.json.JSONObject

object SmsContactsCallBridge {

    @SuppressLint("Range")
    fun syncSmsAndContactsToMac(context: Context) {
        val contactsMap = mutableMapOf<String, String>()
        val contactsArray = JSONArray()

        // 1. Load Contacts
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count < 200) {
                    val id = cursor.getString(0) ?: ""
                    val name = cursor.getString(1) ?: ""
                    val number = cursor.getString(2)?.replace(" ", "") ?: ""
                    if (number.isNotEmpty()) {
                        contactsMap[number] = name
                        contactsArray.put(JSONObject().apply {
                            put("id", "$id-$number")
                            put("name", name)
                            put("phoneNumber", number)
                        })
                        count++
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Load Recent SMS Messages & Group by Address
        val threadsMap = linkedMapOf<String, MutableList<JSONObject>>()
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                ),
                null, null,
                Telephony.Sms.DATE + " DESC LIMIT 120"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: ""
                    val addr = cursor.getString(1) ?: "Unknown"
                    val body = cursor.getString(2) ?: ""
                    val date = cursor.getLong(3)
                    val type = cursor.getInt(4)
                    val isIncoming = (type == Telephony.Sms.MESSAGE_TYPE_INBOX)

                    val msgJson = JSONObject().apply {
                        put("id", id)
                        put("address", addr)
                        put("body", body)
                        put("date", date)
                        put("isIncoming", isIncoming)
                    }
                    threadsMap.getOrPut(addr) { mutableListOf() }.add(0, msgJson)
                }
            }
        } catch (_: Exception) {}

        val threadsArray = JSONArray()
        for ((addr, messages) in threadsMap) {
            val lastMsg = messages.lastOrNull()
            val contactName = contactsMap[addr] ?: addr
            threadsArray.put(JSONObject().apply {
                put("id", addr)
                put("contactName", contactName)
                put("address", addr)
                put("lastMessage", lastMsg?.optString("body") ?: "")
                put("timestamp", lastMsg?.optLong("date") ?: System.currentTimeMillis())
                put("messages", JSONArray(messages))
            })
        }

        val frame = WireProtocol.packJson("SMS_THREADS_SYNC") {
            put("smsThreads", threadsArray)
            put("contacts", contactsArray)
        }
        LinkConnectionService.instance?.sendRawFrame(frame)
    }

    fun sendSms(context: Context, address: String, body: String) {
        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(address, null, body, null, null)
        } catch (_: Exception) {}
    }

    // MARK: - Call Controls (Cellular Audio Remains on Phone)
    @SuppressLint("MissingPermission")
    fun handleCallAction(context: Context, action: String, number: String, smsReply: String) {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        try {
            when (action) {
                "ANSWER" -> tm.acceptRingingCall()
                "DECLINE", "END" -> tm.endCall()
                "DECLINE_WITH_SMS" -> {
                    tm.endCall()
                    if (number.isNotEmpty() && smsReply.isNotEmpty()) {
                        sendSms(context, number, smsReply)
                    }
                }
                "DIAL" -> {
                    if (number.isNotEmpty()) {
                        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(callIntent)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun registerCallStateReceiver(context: Context) {
        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val stateStr = intent?.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: "Cellular Call"
                val mappedState = when (stateStr) {
                    TelephonyManager.EXTRA_STATE_RINGING -> "RINGING"
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> "OFFHOOK"
                    else -> "IDLE"
                }
                val frame = WireProtocol.packJson(
                    type = "CALL_STATE",
                    payloadMap = mapOf(
                        "state" to mappedState,
                        "number" to incomingNumber,
                        "contactName" to incomingNumber
                    )
                )
                LinkConnectionService.instance?.sendRawFrame(frame)
            }
        }, filter)
    }
}






```

---

### FILE: allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/FilePhotosBridge.kt
```kotlin
package com.linktomac.android

import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Base64
import android.util.Size
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.UUID

object FilePhotosBridge {

    fun sendDirectoryListing(path: String) {
        val dir = File(path)
        val arr = JSONArray()
        dir.listFiles()?.forEach { f ->
            arr.put(JSONObject().apply {
                put("name", f.name)
                put("path", f.absolutePath)
                put("sizeBytes", if (f.isDirectory) 0L else f.length())
                put("isDirectory", f.isDirectory)
                put("lastModified", f.lastModified())
            })
        }
        val frame = WireProtocol.packJson(
            type = "FILE_LIST_RES",
            payloadMap = mapOf("path" to path)
        ) {
            put("files", arr)
        }
        LinkConnectionService.instance?.sendRawFrame(frame)
    }

    fun sendPhotosList(context: Context) {
        val arr = JSONArray()
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.SIZE
            )
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null, null,
                MediaStore.Images.Media.DATE_TAKEN + " DESC"
            )?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count < 40) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(1) ?: "photo.jpg"
                    val path = cursor.getString(2) ?: ""
                    val date = cursor.getLong(3)
                    val size = cursor.getLong(4)

                    var thumbB64 = ""
                    try {
                        val contentUri = android.content.ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                        )
                        val bmp = context.contentResolver.loadThumbnail(contentUri, Size(180, 180), null)
                        val baos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 65, baos)
                        thumbB64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    } catch (_: Exception) {}

                    arr.put(JSONObject().apply {
                        put("id", id.toString())
                        put("name", name)
                        put("path", path)
                        put("dateTaken", date)
                        put("sizeBytes", size)
                        put("isVideo", false)
                        put("thumbnailBase64", thumbB64)
                    })
                    count++
                }
            }
        } catch (_: Exception) {}

        val frame = WireProtocol.packJson("PHOTOS_LIST_RES") {
            put("photos", arr)
        }
        LinkConnectionService.instance?.sendRawFrame(frame)
    }

    fun streamFileToMac(path: String, fileName: String, revealInFinder: String = "true") {
        val file = File(path)
        if (!file.exists() || !file.isFile) return
        val tid = UUID.randomUUID().toString() // 36 chars

        val startFrame = WireProtocol.packJson(
            type = "FILE_TRANSFER_START",
            payloadMap = mapOf(
                "transferId" to tid,
                "fileName" to fileName,
                "totalBytes" to file.length().toString()
            )
        )
        LinkConnectionService.instance?.sendRawFrame(startFrame)

        val tidBytes = tid.padEnd(36, ' ').toByteArray(Charsets.UTF_8)
        FileInputStream(file).use { fis ->
            val buf = ByteArray(65536)
            while (true) {
                val read = fis.read(buf)
                if (read <= 0) break
                val chunkPayload = ByteArray(36 + read)
                System.arraycopy(tidBytes, 0, chunkPayload, 0, 36)
                System.arraycopy(buf, 0, chunkPayload, 36, read)
                val packed = WireProtocol.packFrame(WireProtocol.FRAME_FILE_CHUNK, chunkPayload)
                LinkConnectionService.instance?.sendRawFrame(packed)
                Thread.sleep(2)
            }
        }

        val endFrame = WireProtocol.packJson(
            type = "FILE_TRANSFER_END",
            payloadMap = mapOf(
                "transferId" to tid,
                "fileName" to fileName,
                "revealInFinder" to revealInFinder
            )
        )
        LinkConnectionService.instance?.sendRawFrame(endFrame)
    }

    fun streamUriToMac(context: Context, uri: android.net.Uri, fileName: String, revealInFinder: String = "true") {
        var size = -1L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}
        if (size <= 0) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    size = pfd.statSize
                }
            } catch (_: Exception) {}
        }
        if (size < 0) size = 0L

        val tid = UUID.randomUUID().toString()
        val startFrame = WireProtocol.packJson(
            type = "FILE_TRANSFER_START",
            payloadMap = mapOf(
                "transferId" to tid,
                "fileName" to fileName,
                "totalBytes" to size.toString()
            )
        )
        LinkConnectionService.instance?.sendRawFrame(startFrame)

        val tidBytes = tid.padEnd(36, ' ').toByteArray(Charsets.UTF_8)
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buf = ByteArray(65536)
                while (true) {
                    val read = inputStream.read(buf)
                    if (read <= 0) break
                    val chunkPayload = ByteArray(36 + read)
                    System.arraycopy(tidBytes, 0, chunkPayload, 0, 36)
                    System.arraycopy(buf, 0, chunkPayload, 36, read)
                    val packed = WireProtocol.packFrame(WireProtocol.FRAME_FILE_CHUNK, chunkPayload)
                    LinkConnectionService.instance?.sendRawFrame(packed)
                    Thread.sleep(2)
                }
            }
        } catch (_: Exception) {}

        val endFrame = WireProtocol.packJson(
            type = "FILE_TRANSFER_END",
            payloadMap = mapOf(
                "transferId" to tid,
                "fileName" to fileName,
                "revealInFinder" to revealInFinder
            )
        )
        LinkConnectionService.instance?.sendRawFrame(endFrame)
    }
}






```

---

### FILE: allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/ScreenCameraStreamer.kt
```kotlin
package com.linktomac.android

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Display
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

object ScreenCameraStreamer {
    private const val TAG = "ScreenCamStreamer"

    private var streamJob: Job? = null
    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var workerThread: HandlerThread? = null
    private var virtualDisplay: VirtualDisplay? = null

    @Volatile
    var activeMediaProjection: MediaProjection? = null

    fun handleStreamControl(context: Context, mode: String) {
        handleStreamControl(context, org.json.JSONObject().put("mode", mode))
    }

    fun handleStreamControl(context: Context, payload: org.json.JSONObject) {
        val mode = payload.optString("mode")
        when (mode) {
            "REMOTE_TAP" -> {
                val normX = payload.optString("x").toFloatOrNull() ?: return
                val normY = payload.optString("y").toFloatOrNull() ?: return
                LinkTouchAccessibilityService.instance?.performNormalizedTap(normX, normY)
            }
            "REMOTE_KEY" -> {
                val keyCode = payload.optString("keyCode").toIntOrNull() ?: return
                LinkTouchAccessibilityService.instance?.performRemoteKey(keyCode)
            }
            "WEBCAM_BACK" -> {
                stopAllStreams()
                startCameraStream(context, CameraCharacteristics.LENS_FACING_BACK)
            }
            "WEBCAM_FRONT" -> {
                stopAllStreams()
                startCameraStream(context, CameraCharacteristics.LENS_FACING_FRONT)
            }
            "MIC_STREAM" -> {
                stopAllStreams()
                startMicrophoneStream()
            }
            "MIRROR" -> {
                stopAllStreams()
                startMirrorStatusPreview(context)
            }
            "NONE" -> {
                stopAllStreams()
            }
            else -> {
                // Do NOT call stopAllStreams() for unknown or action commands!
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startCameraStream(context: Context, lensFacing: Int) {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == lensFacing
        } ?: cm.cameraIdList.firstOrNull() ?: return

        val ht = HandlerThread("LinkToMacCam").apply { start() }
        workerThread = ht
        val handler = Handler(ht.looper)

        val reader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2)
        imageReader = reader
        var lastSent = 0L

        reader.setOnImageAvailableListener({ ir ->
            val img = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
            val now = System.currentTimeMillis()
            if (now - lastSent >= 65) { // ~15 fps over universal socket
                lastSent = now
                val jpeg = yuv420ToJpeg(img)
                if (jpeg != null) {
                    val frame = WireProtocol.packFrame(WireProtocol.FRAME_VIDEO_JPEG, jpeg)
                    LinkConnectionService.instance?.sendRawFrame(frame)
                }
            }
            img.close()
        }, handler)

        try {
            cm.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    val req = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(reader.surface)
                    }
                    camera.createCaptureSession(
                        listOf(reader.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                session.setRepeatingRequest(req.build(), null, handler)
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {}
                        },
                        handler
                    )
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close() }
            }, handler)
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun startMicrophoneStream() {
        streamJob = CoroutineScope(Dispatchers.IO).launch {
            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
            val buf = ByteArray(2048)
            recorder.startRecording()
            try {
                while (isActive) {
                    val read = recorder.read(buf, 0, buf.size)
                    if (read > 0) {
                        val chunk = buf.copyOf(read)
                        val frame = WireProtocol.packFrame(WireProtocol.FRAME_AUDIO_PCM, chunk)
                        LinkConnectionService.instance?.sendRawFrame(frame)
                    }
                }
            } finally {
                recorder.stop()
                recorder.release()
            }
        }
    }

    fun startMirrorStatusPreview(context: Context) {
        val proj = activeMediaProjection
        if (proj != null) {
            startMediaProjectionMirror(context, proj)
            return
        }

        val width = context.resources.displayMetrics.widthPixels
        val height = context.resources.displayMetrics.heightPixels
        val targetW = 540
        val targetH = ((height.toFloat() / width.toFloat()) * targetW).toInt().coerceAtLeast(960)

        var inFlight = false
        streamJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && LinkTouchAccessibilityService.instance != null) {
                    if (!inFlight) {
                        inFlight = true
                        LinkTouchAccessibilityService.instance?.captureScreenJpeg { jpegBytes ->
                            try {
                                if (jpegBytes != null) {
                                    val frame = WireProtocol.packFrame(WireProtocol.FRAME_VIDEO_JPEG, jpegBytes)
                                    LinkConnectionService.instance?.sendRawFrame(frame)
                                }
                            } finally {
                                inFlight = false
                            }
                        }
                    }
                    delay(55) // ~18 fps
                } else {
                    // Fall back to drawing status canvas if LinkTouchAccessibilityService.instance == null
                    val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    canvas.drawColor(Color.rgb(18, 22, 28))
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(74, 222, 128)
                        textSize = 28f
                    }
                    canvas.drawText("LinkToMac Screen Mirroring", 50f, (targetH / 2 - 40).toFloat(), paint)
                    paint.color = Color.WHITE
                    paint.textSize = 20f
                    canvas.drawText("Enable LinkToMac Accessibility Service", 50f, (targetH / 2 + 10).toFloat(), paint)
                    canvas.drawText("on phone for Live Wireless Mirror", 50f, (targetH / 2 + 45).toFloat(), paint)

                    val baos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                    bmp.recycle()
                    val frame = WireProtocol.packFrame(WireProtocol.FRAME_VIDEO_JPEG, baos.toByteArray())
                    LinkConnectionService.instance?.sendRawFrame(frame)
                    delay(600)
                }
            }
        }
    }

    private fun startMediaProjectionMirror(context: Context, projection: MediaProjection) {
        val realW = context.resources.displayMetrics.widthPixels
        val realH = context.resources.displayMetrics.heightPixels
        val targetW = 540
        val targetH = ((realH.toFloat() / realW.toFloat()) * targetW).toInt().coerceAtLeast(960)

        val ht = HandlerThread("LinkToMacProj").apply { start() }
        workerThread = ht
        val handler = Handler(ht.looper)

        val reader = ImageReader.newInstance(targetW, targetH, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        val metrics = context.resources.displayMetrics
        val vd = projection.createVirtualDisplay(
            "LinkToMacMirror",
            targetW, targetH, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler
        )
        virtualDisplay = vd

        var lastSent = 0L
        reader.setOnImageAvailableListener({ ir ->
            val img = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
            val now = System.currentTimeMillis()
            if (now - lastSent >= 50) { // ~20 fps
                lastSent = now
                val planes = img.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * targetW

                val bmp = Bitmap.createBitmap(targetW + rowPadding / pixelStride, targetH, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(buffer)
                val finalBmp = if (rowPadding > 0) Bitmap.createBitmap(bmp, 0, 0, targetW, targetH) else bmp

                val baos = ByteArrayOutputStream()
                finalBmp.compress(Bitmap.CompressFormat.JPEG, 65, baos)
                if (rowPadding > 0) finalBmp.recycle()
                bmp.recycle()

                val frame = WireProtocol.packFrame(WireProtocol.FRAME_VIDEO_JPEG, baos.toByteArray())
                LinkConnectionService.instance?.sendRawFrame(frame)
            }
            img.close()
        }, handler)
    }

    fun stopAllStreams() {
        streamJob?.cancel()
        streamJob = null

        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null

        try { cameraDevice?.close() } catch (_: Exception) {}
        cameraDevice = null

        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null

        try { workerThread?.quitSafely() } catch (_: Exception) {}
        workerThread = null
    }

    private fun yuv420ToJpeg(image: android.media.Image): ByteArray? {
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 65, out)
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }
}






```

---

### FILE: allcode/LinkToMac-Android/app/src/main/java/com/linktomac/android/MainActivity.kt
```kotlin
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






```

---
