package com.linktomac.android

import android.app.*
import android.content.*
import android.media.AudioManager
import android.media.Ringtone
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
    private var reconnectJob: Job? = null
    private var userRequestedDisconnect: Boolean = false
    private var activeRingtone: Ringtone? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundNotification("LinkToMac Ready — Waiting for Mac")
        startMdnsDiscovery()
        ClipboardManagerBridge.init(this)
        SmsContactsCallBridge.registerCallStateReceiver(this)

        // Automatically connect to remembered device on startup
        autoConnectRemembered()
    }

    private fun autoConnectRemembered() {
        val prefs = getSharedPreferences("linktomac", MODE_PRIVATE)
        val savedHost = prefs.getString("lastHost", null)
        val savedPort = prefs.getInt("lastPort", 54921)
        val savedToken = prefs.getString("lastToken", "") ?: ""
        if (!savedHost.isNullOrBlank()) {
            connectToMac(savedHost, savedPort, savedToken)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "CONNECT_HOST") {
            userRequestedDisconnect = false
            reconnectJob?.cancel()
            val host = intent.getStringExtra("host") ?: "127.0.0.1"
            val port = intent.getIntExtra("port", 54921)
            val token = intent.getStringExtra("token") ?: ""
            connectToMac(host, port, token)
        } else if (action == "DISCONNECT") {
            userRequestedDisconnect = true
            reconnectJob?.cancel()
            closeSocket()
        }
        return START_STICKY
    }

    fun connectToMac(host: String, port: Int, token: String) {
        userRequestedDisconnect = false
        reconnectJob?.cancel()
        serviceScope.launch {
            closeSocketInternal()
            try {
                statusMessage = "Connecting to $host:$port..."
                val s = Socket()
                s.tcpNoDelay = true
                s.keepAlive = true
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
                if (!userRequestedDisconnect) {
                    scheduleReconnect()
                }
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
            "PLAY_PAUSE" -> dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "NEXT"       -> dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
            "PREV"       -> dispatchMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "VOL_UP"     -> audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "VOL_DOWN"   -> audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "RING_PHONE" -> {
                val current = activeRingtone
                if (current != null && current.isPlaying) {
                    current.stop()
                    activeRingtone = null
                } else {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    val ringtone = RingtoneManager.getRingtone(applicationContext, uri)
                    activeRingtone = ringtone
                    ringtone?.play()
                }
            }
            "STOP_RING", "STOP_RINGING" -> {
                activeRingtone?.stop()
                activeRingtone = null
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
                                if (!isConnectedState && !userRequestedDisconnect) {
                                    val prefs = getSharedPreferences("linktomac", MODE_PRIVATE)
                                    val token = prefs.getString("lastToken", "") ?: ""
                                    connectToMac(host, resolved.port, token)
                                }
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

    private fun closeSocketInternal() {
        isConnectedState = false
        try { outputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        outputStream = null
        socket = null
    }

    private fun closeSocket() {
        closeSocketInternal()
        statusMessage = "Disconnected"
        if (!userRequestedDisconnect) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true || userRequestedDisconnect) return
        reconnectJob = serviceScope.launch {
            var delayMs = 1500L
            while (isActive && !isConnectedState && !userRequestedDisconnect) {
                delay(delayMs)
                if (isConnectedState || userRequestedDisconnect) break

                val prefs = getSharedPreferences("linktomac", MODE_PRIVATE)
                val targetHost = if (discoveredMacHost.isNotEmpty()) discoveredMacHost else prefs.getString("lastHost", null)
                val targetPort = if (discoveredMacHost.isNotEmpty()) discoveredMacPort else prefs.getInt("lastPort", 54921)
                val targetToken = prefs.getString("lastToken", "") ?: ""

                if (!targetHost.isNullOrBlank()) {
                    statusMessage = "Reconnecting to $targetHost:$targetPort..."
                    try {
                        val s = Socket()
                        s.tcpNoDelay = true
                        s.keepAlive = true
                        s.connect(InetSocketAddress(targetHost, targetPort), 4000)
                        socket = s
                        outputStream = s.getOutputStream()
                        isConnectedState = true
                        val transport = if (targetHost == "127.0.0.1") "USB" else "Local Wi-Fi"
                        statusMessage = "Connected ($transport • $targetHost)"
                        startForegroundNotification("Connected to Mac ($transport)")

                        sendHelloAndStatus(transport, targetToken)
                        startStatusHeartbeat(transport)
                        readLoop(DataInputStream(s.getInputStream()))
                        break
                    } catch (_: Exception) {
                        isConnectedState = false
                        statusMessage = "Waiting for Mac ($targetHost)..."
                        delayMs = (delayMs * 1.5).toLong().coerceAtMost(10000L)
                    }
                } else {
                    delay(3000L)
                }
            }
        }
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

    override fun onDestroy() {
        super.onDestroy()
        activeRingtone?.stop()
        activeRingtone = null
        reconnectJob?.cancel()
        closeSocketInternal()
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
