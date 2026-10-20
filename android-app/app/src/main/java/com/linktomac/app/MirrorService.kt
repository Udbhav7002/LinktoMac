package com.linktomac.app

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MirrorService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var projectionManager: MediaProjectionManager? = null
    private var encoder: ScreenEncoder? = null
    private var networkThread: NetworkThread? = null

    private var displayListener: DisplayManager.DisplayListener? = null
    @Volatile private var currentStreamWidth: Int = 720
    @Volatile private var currentStreamHeight: Int = 1600
    @Volatile private var currentDensityDpi: Int = 1
    private val reconfigLock = Any()

    private val socketWriteLock = Any()
    @Volatile private var isHandshakeDone = false

    private val frameQueue = BoundedFrameQueue(capacity = 2) {
        encoder?.requestKeyFrameThrottled()
    }

    private var nsdManager: NsdManager? = null
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        createNotificationChannel()
        ApprovedClientsStore.init(this)
        registerNsdBroadcast()
    }

    private fun registerNsdBroadcast() {
        if (nsdRegistrationListener != null) return
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "${Build.MODEL} (LinktoMac)"
            serviceType = "_linktomac._tcp"
            port = PORT
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "MirrorService NSD registered: ${serviceInfo.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                nsdRegistrationListener = null
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        try {
            nsdManager = getSystemService(Context.NSD_SERVICE) as? NsdManager
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            nsdRegistrationListener = listener
        } catch (e: Exception) {
            Log.e(TAG, "Error registering NSD in MirrorService", e)
        }
    }

    private fun unregisterNsdBroadcast() {
        val listener = nsdRegistrationListener ?: return
        try {
            nsdManager?.unregisterService(listener)
        } catch (ignored: Exception) {}
        nsdRegistrationListener = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && ACTION_STOP == intent.action) {
            stopSelf()
            return START_NOT_STICKY
        }

        val remoteHost = intent?.getStringExtra(EXTRA_REMOTE_HOST)
        val remotePort = intent?.getIntExtra(EXTRA_REMOTE_PORT, 0) ?: 0

        val notification = createNotification()
        val hasProjectionIntent = intent != null && intent.hasExtra(EXTRA_RESULT_CODE)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val fgsType = if (hasProjectionIntent) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error starting foreground service with type flags, falling back", e)
            startForeground(NOTIFICATION_ID, notification)
        }

        // If already streaming with MediaProjection, dynamically switch transport mode if requested
        if (isStreamingActive && encoder != null && (mediaProjection != null || !hasProjectionIntent)) {
            if (!remoteHost.isNullOrBlank() && remotePort > 0) {
                switchToRemote(remoteHost, remotePort)
            }
            return START_STICKY
        }

        var projection: MediaProjection? = null
        if (intent != null && intent.hasExtra(EXTRA_RESULT_CODE)) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }
            if (resultCode == Activity.RESULT_OK && data != null) {
                try {
                    projection = projectionManager?.getMediaProjection(resultCode, data)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to obtain MediaProjection from manager", e)
                }
            }
        }
        this.mediaProjection = projection
        isUsingMediaProjection = (projection != null)
        captureModeListener?.invoke(isUsingMediaProjection)

        startEncoderAndServer(projection, remoteHost, remotePort)
        return START_STICKY
    }

    private fun startEncoderAndServer(projection: MediaProjection?, remoteHost: String?, remotePort: Int) {
        val dims = DisplayMetricsHelper.queryDisplayDimensions(this)
        currentStreamWidth = dims.streamWidth
        currentStreamHeight = dims.streamHeight
        currentDensityDpi = dims.densityDpi

        encoder?.stop()
        encoder = null

        try {
            val enc = ScreenEncoder(
                width = dims.streamWidth,
                height = dims.streamHeight,
                densityDpi = dims.densityDpi,
                bitRate = 8_000_000,
                frameRate = 60
            )
            this.encoder = enc
            enc.start(
                projection = projection,
                onProjectionStopped = {
                    isUsingMediaProjection = false
                    captureModeListener?.invoke(false)
                }
            ) { packet ->
                if (isHandshakeDone) {
                    frameQueue.offer(packet)
                }
            }
            isStreamingActive = true
            streamingStateListener?.invoke(true)
            registerDisplayListener()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ScreenEncoder", e)
            isStreamingActive = false
            connectedMacName = ""
            clientConnectedListener?.invoke(false, "")
            streamingStateListener?.invoke(false)
            stopSelf()
            return
        }

        networkThread?.stopServer()
        networkThread = NetworkThread(remoteHost, remotePort).also {
            it.start()
        }
    }

    private fun registerDisplayListener() {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
        displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                if (displayId != Display.DEFAULT_DISPLAY) return
                handleDisplayChange()
            }
        }
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
    }

    private fun handleDisplayChange() {
        val dims = DisplayMetricsHelper.queryDisplayDimensions(this)
        if (dims.streamWidth == currentStreamWidth && dims.streamHeight == currentStreamHeight) {
            return
        }
        synchronized(reconfigLock) {
            currentStreamWidth = dims.streamWidth
            currentStreamHeight = dims.streamHeight
            currentDensityDpi = dims.densityDpi
            frameQueue.clear()
            encoder?.updateDimensions(dims.streamWidth, dims.streamHeight, dims.densityDpi)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val content = if (connectedMacName.isNotBlank()) "Linked to $connectedMacName" else "Ready to Link"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LinktoMac")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun dropLocalClientSocket() {
        networkThread?.dropLocalClient()
    }

    fun switchToRemote(host: String, port: Int) {
        Log.i(TAG, "Switching stream to remote relay: $host:$port")
        networkThread?.stopServer()
        networkThread = NetworkThread(host, port).also { it.start() }
    }

    fun switchToLocal() {
        Log.i(TAG, "Switching stream to local server mode on port $PORT")
        networkThread?.stopServer()
        networkThread = NetworkThread(null, 0).also { it.start() }
    }

    fun upgradeToProjection(resultCode: Int, data: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to elevate foreground service type to MEDIA_PROJECTION", e)
            }
        }
        if (projectionManager == null) {
            projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        }
        val proj = try {
            projectionManager?.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to getMediaProjection", e)
            null
        } ?: return

        this.mediaProjection = proj
        isUsingMediaProjection = true
        encoder?.upgradeToMediaProjection(proj) {
            isUsingMediaProjection = false
            captureModeListener?.invoke(false)
        }
        captureModeListener?.invoke(true)
        streamingStateListener?.invoke(true)
    }

    fun stopEncoderAndNetwork() {
        isStreamingActive = false
        isUsingMediaProjection = false
        captureModeListener?.invoke(false)
        connectedMacName = ""
        try {
            Handler(Looper.getMainLooper()).post {
                clientConnectedListener?.invoke(false, "")
                streamingStateListener?.invoke(false)
            }
        } catch (_: Exception) {}

        try {
            LinktoMacAccessibilityService.instance?.cancelActiveGestures()
        } catch (_: Exception) {}

        networkThread?.stopServer()
        networkThread = null

        encoder?.stop()
        encoder = null

        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        stopEncoderAndNetwork()

        unregisterNsdBroadcast()
        displayListener?.let {
            (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.unregisterDisplayListener(it)
        }
        displayListener = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private inner class NetworkThread(
        private val remoteHost: String?,
        private val remotePort: Int
    ) : Thread("MirrorServerNetworkThread") {
        private var serverSocket: ServerSocket? = null
        private var clientSocket: Socket? = null
        private var outputStream: OutputStream? = null
        private var inputStream: InputStream? = null
        @Volatile private var running = true
        private var senderThread: VideoSenderThread? = null

        override fun run() {
            senderThread = VideoSenderThread().also { it.start() }
            if (!remoteHost.isNullOrBlank() && remotePort > 0) {
                runClientMode(remoteHost, remotePort)
            } else {
                runServerMode()
            }
        }

        private fun sendHandshakeAndHeader(): Boolean {
            synchronized(socketWriteLock) {
                isHandshakeDone = false
                try {
                    val stream = outputStream ?: return false
                    val isHevc = encoder?.isHevc ?: false
                    val codecFlag = if (isHevc) StreamProtocol.FLAG_CODEC_HEVC else StreamProtocol.FLAG_CODEC_H264
                    val handshake = StreamProtocol.createHandshake(
                        width = currentStreamWidth,
                        height = currentStreamHeight,
                        flags = codecFlag
                    )
                    stream.write(handshake)
                    stream.flush()

                    encoder?.getHeader()?.let { header ->
                        stream.write(header)
                        stream.flush()
                    }
                    encoder?.getLastKeyFrame()?.let { keyFrame ->
                        stream.write(keyFrame)
                        stream.flush()
                    }
                    isHandshakeDone = true
                    return true
                } catch (e: Exception) {
                    isHandshakeDone = false
                    return false
                }
            }
        }

        private fun runClientMode(host: String, port: Int) {
            Log.i(TAG, "Connecting outbound to remote Mac relay at $host:$port...")
            while (running && !isInterrupted) {
                try {
                    val client = Socket()
                    client.tcpNoDelay = true
                    client.sendBufferSize = 256 * 1024
                    client.soTimeout = 5000
                    client.connect(InetSocketAddress(host, port), 10000)
                    this.clientSocket = client
                    this.outputStream = client.getOutputStream()
                    this.inputStream = client.getInputStream()

                    val macName = "Mac (Remote)"
                    connectedMacName = macName

                    if (!sendHandshakeAndHeader()) {
                        throw IOException("Failed to send handshake")
                    }
                    if (encoder?.getLastKeyFrame() == null) {
                        encoder?.requestKeyFrame()
                    }

                    readControlEvents(client)
                } catch (e: Exception) {
                    if (running) {
                        try { sleep(1500) } catch (_: InterruptedException) { break }
                    }
                } finally {
                    closeClient()
                }
            }
        }

        private fun isClientAuthorized(clientIp: String): Boolean {
            if (ApprovedClientsStore.isAuthorized(clientIp)) {
                return true
            }

            // Short grace poll (up to 1500ms) for incoming Firestore session pre-authorization
            val startMs = SystemClock.uptimeMillis()
            while (SystemClock.uptimeMillis() - startMs < 1500L && running) {
                try {
                    sleep(100)
                } catch (_: InterruptedException) {
                    break
                }
                if (ApprovedClientsStore.isAuthorized(clientIp)) {
                    Log.i(TAG, "Client $clientIp auto-authorized via session pre-authorization")
                    return true
                }
            }

            val handler = authorizationPromptHandler ?: return false
            val latch = CountDownLatch(1)
            val approved = AtomicBoolean(false)
            try {
                Handler(Looper.getMainLooper()).post {
                    try {
                        handler.invoke(clientIp) { isApproved ->
                            if (isApproved) {
                                ApprovedClientsStore.approveClient(this@MirrorService, clientIp)
                                approved.set(true)
                            }
                            latch.countDown()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error invoking authorization prompt handler", e)
                        latch.countDown()
                    }
                }
                val waited = latch.await(10, TimeUnit.SECONDS)
                return waited && approved.get()
            } catch (_: Exception) {
                return false
            }
        }

        private fun runServerMode() {
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress(PORT))
                this.serverSocket = server
                Log.i(TAG, "Mirror server listening on port $PORT")

                while (running && !isInterrupted) {
                    val client = try {
                        server.accept()
                    } catch (e: Exception) {
                        if (running) Log.w(TAG, "Server accept error: ${e.message}")
                        break
                    }

                    client.soTimeout = 5000
                    val clientIp = client.inetAddress?.hostAddress ?: ""

                    if (!isClientAuthorized(clientIp)) {
                        Log.w(TAG, "Unauthorized client connection from $clientIp rejected")
                        try { client.close() } catch (_: Exception) {}
                        continue
                    }

                    var eventsProcessed = 0
                    try {
                        client.tcpNoDelay = true
                        client.sendBufferSize = 256 * 1024
                        this.clientSocket = client
                        this.outputStream = client.getOutputStream()
                        this.inputStream = client.getInputStream()

                        connectedMacName = "Mac"

                        if (!sendHandshakeAndHeader()) {
                            throw IOException("Failed to send handshake")
                        }
                        if (encoder?.getLastKeyFrame() == null) {
                            encoder?.requestKeyFrame()
                        }

                        eventsProcessed = readControlEvents(client)
                    } catch (e: Exception) {
                        if (running) {
                            Log.w(TAG, "Client socket session ended: ${e.message}")
                        }
                    } finally {
                        val isProbe = (eventsProcessed == 0)
                        closeClient(isProbeOrAborted = isProbe)
                    }
                }
            } catch (e: Exception) {
                if (running) {
                    Log.e(TAG, "Server socket error", e)
                }
            }
        }

        private fun readControlEvents(socket: Socket): Int {
            val inStream = inputStream ?: return 0
            val header = ByteArray(1)
            var eventsProcessed = 0
            try {
                while (running && socket.isConnected && !socket.isClosed) {
                    val read = try {
                        inStream.read(header)
                    } catch (te: java.net.SocketTimeoutException) {
                        if (running && socket.isConnected && !socket.isClosed) continue
                        -1
                    }
                    if (read == -1) break
                    eventsProcessed++
                    if (eventsProcessed == 1) {
                        val macName = connectedMacName.ifBlank { "Mac" }
                        Handler(Looper.getMainLooper()).post {
                            clientConnectedListener?.invoke(true, macName)
                        }
                    }
                    val type = header[0].toInt() and 0xFF
                    try {
                        when (type) {
                            StreamProtocol.EVENT_TYPE_TOUCH -> {
                                val payload = ByteArray(13)
                                readExact(inStream, payload)
                                val action = payload[0].toInt() and 0xFF
                                val buf = ByteBuffer.wrap(payload, 1, 12)
                                val x = buf.float
                                val y = buf.float
                                val pressure = buf.float
                                LinktoMacAccessibilityService.instance?.handleTouch(
                                    action, x, y, pressure, currentStreamWidth, currentStreamHeight
                                )
                            }
                            StreamProtocol.EVENT_TYPE_KEY -> {
                                val payload = ByteArray(5)
                                readExact(inStream, payload)
                                val action = payload[0].toInt() and 0xFF
                                val buf = ByteBuffer.wrap(payload, 1, 4)
                                val keyCode = buf.int
                                LinktoMacAccessibilityService.instance?.handleKey(action, keyCode)
                            }
                            StreamProtocol.EVENT_TYPE_SCROLL -> {
                                val payload = ByteArray(16)
                                readExact(inStream, payload)
                                val buf = ByteBuffer.wrap(payload)
                                val x = buf.float
                                val y = buf.float
                                val hscroll = buf.float
                                val vscroll = buf.float
                                LinktoMacAccessibilityService.instance?.handleScroll(
                                    x, y, hscroll, vscroll, currentStreamWidth, currentStreamHeight
                                )
                            }
                            StreamProtocol.EVENT_TYPE_SYNC_REQUEST -> {
                                encoder?.getHeader()?.let { sendData(it) }
                                encoder?.getLastKeyFrame()?.let { sendData(it) }
                                encoder?.requestKeyFrame()
                            }
                            StreamProtocol.EVENT_TYPE_TEXT -> {
                                val lenBuf = ByteArray(4)
                                readExact(inStream, lenBuf)
                                val textLen = ByteBuffer.wrap(lenBuf).int
                                if (textLen in 1..4096) {
                                    val textBytes = ByteArray(textLen)
                                    readExact(inStream, textBytes)
                                    val text = String(textBytes, Charsets.UTF_8)
                                    LinktoMacAccessibilityService.instance?.handleText(text)
                                } else if (textLen != 0) {
                                    break
                                }
                            }
                            StreamProtocol.EVENT_TYPE_NETWORK_FEEDBACK -> {
                                val feedbackBuf = ByteArray(8)
                                readExact(inStream, feedbackBuf)
                                val bb = ByteBuffer.wrap(feedbackBuf)
                                val lossRate = bb.float.toDouble()
                                val rttMs = bb.float.toDouble()
                                encoder?.onNetworkFeedback(lossRate, rttMs)
                            }
                        }
                    } catch (e: IOException) {
                        throw e
                    } catch (_: Exception) {}
                }
            } finally {
                if (eventsProcessed > 0) {
                    try { LinktoMacAccessibilityService.instance?.cancelActiveGestures() } catch (_: Exception) {}
                }
            }
            return eventsProcessed
        }

        private fun readExact(stream: InputStream, buffer: ByteArray) {
            var offset = 0
            while (offset < buffer.size) {
                val read = stream.read(buffer, offset, buffer.size - offset)
                if (read == -1) throw IOException("Socket closed")
                offset += read
            }
        }

        fun sendData(data: ByteArray) {
            if (!isHandshakeDone) return
            synchronized(socketWriteLock) {
                if (!isHandshakeDone) return
                try {
                    outputStream?.let { stream ->
                        stream.write(data)
                        stream.flush()
                    }
                } catch (e: IOException) {
                    if (running) closeClient()
                } catch (_: Exception) {}
            }
        }

        fun dropLocalClient() {
            if (remoteHost.isNullOrBlank() || remotePort <= 0) {
                closeClient()
            }
        }

        fun stopServer() {
            running = false
            closeClient()
            try { senderThread?.interrupt() } catch (_: Exception) {}
            senderThread = null
            try { serverSocket?.close() } catch (_: Exception) {}
            serverSocket = null
            interrupt()
        }

        private fun closeClient(isProbeOrAborted: Boolean = false) {
            isHandshakeDone = false
            connectedMacName = ""
            if (!isProbeOrAborted) {
                try {
                    Handler(Looper.getMainLooper()).post {
                        clientConnectedListener?.invoke(false, "")
                    }
                } catch (_: Exception) {}

                try { LinktoMacAccessibilityService.instance?.cancelActiveGestures() } catch (_: Exception) {}
                frameQueue.clear()
            }

            val sock = clientSocket
            try { sock?.close() } catch (_: Exception) {}
            synchronized(socketWriteLock) {
                clientSocket = null
                outputStream = null
                inputStream = null
            }
        }

        private inner class VideoSenderThread : Thread("MirrorVideoSenderThread") {
            override fun run() {
                while (running && !isInterrupted) {
                    try {
                        val packet = frameQueue.take()
                        if (!isHandshakeDone) continue

                        synchronized(socketWriteLock) {
                            if (!isHandshakeDone) return@synchronized
                            val stream = outputStream ?: return@synchronized
                            stream.write(packet.data)
                            stream.flush()
                        }
                    } catch (_: InterruptedException) {
                        break
                    } catch (e: IOException) {
                        if (running) closeClient()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    companion object {
        private const val TAG = "MirrorService"
        @Volatile var instance: MirrorService? = null
        @Volatile var isStreamingActive: Boolean = false
        @Volatile var isUsingMediaProjection: Boolean = false
        @Volatile var connectedMacName: String = ""
        @Volatile var clientConnectedListener: ((Boolean, String) -> Unit)? = null
        @Volatile var streamingStateListener: ((Boolean) -> Unit)? = null
        @Volatile var captureModeListener: ((Boolean) -> Unit)? = null
        @Volatile var authorizationPromptHandler: ((clientIp: String, callback: (Boolean) -> Unit) -> Unit)? = null
        @Volatile var pendingAuthClientIp: String? = null
        @Volatile var pendingAuthCallback: ((Boolean) -> Unit)? = null

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_REMOTE_HOST = "extra_remote_host"
        const val EXTRA_REMOTE_PORT = "extra_remote_port"
        const val ACTION_START = "com.linktomac.app.ACTION_START"
        const val ACTION_STOP = "com.linktomac.app.ACTION_STOP"
        private const val CHANNEL_ID = "linktomac_stream_channel"
        private const val NOTIFICATION_ID = 2001
        const val PORT = StreamProtocol.DEFAULT_PORT

        fun startWithoutProjection(
            context: Context,
            remoteHost: String? = null,
            remotePort: Int = 0
        ) {
            val intent = Intent(context, MirrorService::class.java).apply {
                action = ACTION_START
                if (!remoteHost.isNullOrBlank() && remotePort > 0) {
                    putExtra(EXTRA_REMOTE_HOST, remoteHost)
                    putExtra(EXTRA_REMOTE_PORT, remotePort)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun start(
            context: Context,
            resultCode: Int,
            data: Intent,
            remoteHost: String? = null,
            remotePort: Int = 0
        ) {
            val intent = Intent(context, MirrorService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
                if (!remoteHost.isNullOrBlank() && remotePort > 0) {
                    putExtra(EXTRA_REMOTE_HOST, remoteHost)
                    putExtra(EXTRA_REMOTE_PORT, remotePort)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            connectedMacName = ""
            val intent = Intent(context, MirrorService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }

        fun disconnectAndRevoke(context: Context) {
            try { instance?.stopEncoderAndNetwork() } catch (_: Exception) {}
            try { ApprovedClientsStore.clear(context) } catch (_: Exception) {}
            try { LinktoMacAccessibilityService.instance?.cancelActiveGestures() } catch (_: Exception) {}
            stop(context)
        }
    }
}
