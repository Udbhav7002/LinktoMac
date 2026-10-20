package com.linktomac.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Surface
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ScreenEncoder(
    @Volatile private var width: Int,
    @Volatile private var height: Int,
    @Volatile private var densityDpi: Int = 1,
    private val bitRate: Int = 8_000_000,
    private val frameRate: Int = 60
) {
    private val running = AtomicBoolean(false)
    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var encoderThread: Thread? = null
    private var captureThread: Thread? = null
    private val captureExecutor = Executors.newSingleThreadExecutor()
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var frameListener: OnFrameAvailableListener? = null
    private val lifecycleLock = Any()
    private val a11yWaitLock = java.lang.Object()
    private val keyFrameLock = Any()
    private var lastKeyFrameRequestTimeMs = 0L

    @Volatile private var currentBitrate: Int = bitRate
    private var lastBitrateAdjustmentMs: Long = 0L
    private val bitrateLock = Any()

    @Volatile private var lastSps: ByteArray? = null
    @Volatile private var lastPps: ByteArray? = null
    @Volatile private var lastCodecConfig: ByteArray? = null
    @Volatile private var lastKeyFrame: ByteArray? = null
    private var projectionStopCallback: (() -> Unit)? = null

    var isHevc: Boolean = false
        private set

    fun interface OnFrameAvailableListener {
        fun onFrameAvailable(packet: FramePacket)
    }

    /**
     * Starts encoding without MediaProjection (uses AccessibilityService GPU HardwareBuffer capture)
     * so Android NEVER shows the red status bar recording chip or permission dialog.
     */
    fun startWithoutProjection(
        onStopped: (() -> Unit)? = null,
        listener: OnFrameAvailableListener
    ) {
        start(projection = null, onProjectionStopped = onStopped, listener = listener)
    }

    fun start(
        projection: MediaProjection?,
        onProjectionStopped: (() -> Unit)? = null,
        listener: OnFrameAvailableListener
    ) {
        if (running.getAndSet(true)) return

        synchronized(lifecycleLock) {
            this.mediaProjection = projection
            this.projectionStopCallback = onProjectionStopped
            this.frameListener = listener

            val selectedMime = selectSupportedMimeType()
            Log.i(TAG, "Starting video codec: $selectedMime ($width x $height @ $densityDpi dpi)")

            var encoder: MediaCodec
            try {
                encoder = createAndConfigure(selectedMime, width, height)
                isHevc = (selectedMime == MediaFormat.MIMETYPE_VIDEO_HEVC)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to configure $selectedMime, falling back to AVC", e)
                encoder = createAndConfigure(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
                isHevc = false
            }

            val surface = encoder.createInputSurface()
            encoder.start()

            this.codec = encoder
            this.inputSurface = surface

            if (projection != null) {
                projection.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.i(TAG, "MediaProjection stopped")
                        stop()
                        projectionStopCallback?.invoke()
                    }
                }, android.os.Handler(android.os.Looper.getMainLooper()))

                this.virtualDisplay = projection.createVirtualDisplay(
                    "LinktoMac",
                    width,
                    height,
                    densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface,
                    null,
                    null
                )
            } else {
                // Seed initial frame so MediaCodec immediately outputs SPS/PPS + first IDR keyframe
                drawInitialFrame(surface, width, height)
                captureThread = Thread(::accessibilityCaptureLoop, "A11yScreenCaptureThread").also { it.start() }
            }

            encoderThread = Thread(::encodeLoop, "ScreenEncoderThread").also { it.start() }
        }
    }

    private fun drawInitialFrame(surface: Surface, w: Int, h: Int) {
        try {
            val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                surface.lockHardwareCanvas()
            } else {
                surface.lockCanvas(null)
            }
            canvas.drawColor(Color.parseColor("#0A0A0E"))
            surface.unlockCanvasAndPost(canvas)
        } catch (e: Exception) {
            Log.w(TAG, "Initial surface draw failed: ${e.message}")
        }
    }

    private fun accessibilityCaptureLoop() {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        while (running.get() && !Thread.currentThread().isInterrupted) {
            val a11y = LinktoMacAccessibilityService.instance
            val surface = inputSurface
            if (a11y == null || surface == null || !surface.isValid || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                synchronized(a11yWaitLock) {
                    try {
                        a11yWaitLock.wait(1000)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
                continue
            }

            val latch = java.util.concurrent.CountDownLatch(1)
            try {
                a11y.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    captureExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            val hb = screenshot.hardwareBuffer
                            try {
                                val hwBitmap = Bitmap.wrapHardwareBuffer(hb, screenshot.colorSpace)
                                if (hwBitmap != null && running.get() && surface.isValid) {
                                    val dstRect = Rect(0, 0, width, height)
                                    try {
                                        val canvas = surface.lockHardwareCanvas()
                                        try {
                                            canvas.drawBitmap(hwBitmap, null, dstRect, paint)
                                        } finally {
                                            surface.unlockCanvasAndPost(canvas)
                                        }
                                    } catch (hwErr: Exception) {
                                        val swBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                        if (swBitmap != null) {
                                            val canvas = surface.lockCanvas(null)
                                            try {
                                                canvas.drawBitmap(swBitmap, null, dstRect, paint)
                                            } finally {
                                                surface.unlockCanvasAndPost(canvas)
                                                swBitmap.recycle()
                                            }
                                        }
                                    }
                                    hwBitmap.recycle()
                                }
                            } catch (e: Exception) {
                                Log.d(TAG, "Frame draw error: ${e.message}")
                            } finally {
                                try { hb.close() } catch (_: Exception) {}
                                latch.countDown()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            latch.countDown()
                        }
                    }
                )
                latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                // Pace capture to avoid Android Accessibility rate-limit lockouts while keeping MediaCodec fed
                Thread.sleep(95)
            } catch (_: InterruptedException) {
                break
            } catch (e: Exception) {
                try { Thread.sleep(120) } catch (_: InterruptedException) { break }
            }
        }
    }

    private fun createAndConfigure(mimeType: String, w: Int, h: Int): MediaCodec {
        val format = MediaFormat.createVideoFormat(mimeType, w, h).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, currentBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)

            try { setInteger(MediaFormat.KEY_LATENCY, 0) } catch (_: Exception) {}
            try { setInteger(MediaFormat.KEY_PRIORITY, 0) } catch (_: Exception) {}
            try { setInteger("latency", 0) } catch (_: Exception) {}
            try { setInteger("max-bframes", 0) } catch (_: Exception) {}
            try { setInteger("tune", 4) } catch (_: Exception) {}
            try { setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000L) } catch (_: Exception) {}

            try { setInteger("vendor.google.zero-latency.enable", 1) } catch (_: Exception) {}
            try { setInteger("vendor.google.low-latency.enable", 1) } catch (_: Exception) {}
            try { setInteger("vendor.google.intra-refresh.enable", 1) } catch (_: Exception) {}
            try { setInteger("vendor.google.screen-content.enable", 1) } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try { setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1) } catch (_: Exception) {}
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try { setInteger("real-time", 1) } catch (_: Exception) {}
            }
        }
        val enc = MediaCodec.createEncoderByType(mimeType)
        enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        return enc
    }

    private fun selectSupportedMimeType(): String {
        return if (isHardwareCodecAvailable(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
            MediaFormat.MIMETYPE_VIDEO_HEVC
        } else {
            MediaFormat.MIMETYPE_VIDEO_AVC
        }
    }

    private fun isHardwareCodecAvailable(mimeType: String): Boolean {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in codecList.codecInfos) {
                if (!info.isEncoder) continue
                val types = info.supportedTypes ?: continue
                if (!types.any { it.equals(mimeType, ignoreCase = true) }) continue

                val isHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.isHardwareAccelerated
                } else {
                    val name = info.name.lowercase()
                    !name.startsWith("omx.google.") &&
                    !name.startsWith("c2.android.") &&
                    !name.startsWith("omx.ffmpeg.")
                }
                if (isHardware) return true
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error querying hardware codec for $mimeType: ${e.message}")
            false
        }
    }

    fun onNetworkFeedback(lossRate: Double, rttMs: Double) {
        synchronized(bitrateLock) {
            val now = SystemClock.uptimeMillis()
            if (now - lastBitrateAdjustmentMs < BITRATE_ADJUSTMENT_COOLDOWN_MS) return

            val oldBitrate = currentBitrate
            val newBitrate = calculateAdaptedBitrate(oldBitrate, lossRate, rttMs)

            if (newBitrate != oldBitrate) {
                Log.i(TAG, "ABR adjustment: ${oldBitrate / 1_000_000}M -> ${newBitrate / 1_000_000}M (loss=$lossRate, rtt=${rttMs}ms)")
                currentBitrate = newBitrate
                lastBitrateAdjustmentMs = now
                applyDynamicBitrate(newBitrate)
            }
        }
    }

    private fun applyDynamicBitrate(bitrate: Int) {
        val activeCodec = codec ?: return
        if (!running.get()) return
        try {
            val params = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate)
            }
            activeCodec.setParameters(params)
        } catch (_: Exception) {}
    }

    fun requestKeyFrame() {
        val activeCodec = codec ?: return
        if (!running.get()) return
        try {
            val params = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
            activeCodec.setParameters(params)
        } catch (_: Exception) {}
    }

    fun requestKeyFrameThrottled(minIntervalMs: Long = 250L) {
        val now = SystemClock.uptimeMillis()
        synchronized(keyFrameLock) {
            if (now - lastKeyFrameRequestTimeMs >= minIntervalMs) {
                lastKeyFrameRequestTimeMs = now
                requestKeyFrame()
            }
        }
    }

    fun updateDimensions(newWidth: Int, newHeight: Int, newDensityDpi: Int) {
        if (!running.get()) return
        synchronized(lifecycleLock) {
            encoderThread?.interrupt()
            try { encoderThread?.join(500) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            encoderThread = null

            virtualDisplay?.surface = null

            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            codec = null
            try { inputSurface?.release() } catch (_: Exception) {}
            inputSurface = null
            lastKeyFrame = null

            this.width = newWidth
            this.height = newHeight
            this.densityDpi = newDensityDpi

            val selectedMime = selectSupportedMimeType()
            var newCodec: MediaCodec
            try {
                newCodec = createAndConfigure(selectedMime, newWidth, newHeight)
                isHevc = (selectedMime == MediaFormat.MIMETYPE_VIDEO_HEVC)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to reconfigure $selectedMime during resize, falling back to AVC", e)
                newCodec = createAndConfigure(MediaFormat.MIMETYPE_VIDEO_AVC, newWidth, newHeight)
                isHevc = false
            }

            val newSurface = newCodec.createInputSurface()
            newCodec.start()

            this.codec = newCodec
            this.inputSurface = newSurface

            if (virtualDisplay != null) {
                virtualDisplay?.resize(newWidth, newHeight, newDensityDpi)
                virtualDisplay?.surface = newSurface
            } else {
                drawInitialFrame(newSurface, newWidth, newHeight)
            }

            encoderThread = Thread(::encodeLoop, "ScreenEncoderThread").also { it.start() }
            requestKeyFrame()
        }
    }

    private fun toAnnexB(nal: ByteArray): ByteArray {
        if (nal.size >= 4 && nal[0] == 0.toByte() && nal[1] == 0.toByte() && nal[2] == 0.toByte() && nal[3] == 1.toByte()) {
            return nal
        }
        if (nal.size >= 3 && nal[0] == 0.toByte() && nal[1] == 0.toByte() && nal[2] == 1.toByte()) {
            val out = ByteArray(nal.size + 1)
            out[0] = 0
            System.arraycopy(nal, 0, out, 1, nal.size)
            return out
        }
        val out = ByteArray(nal.size + 4)
        out[0] = 0; out[1] = 0; out[2] = 0; out[3] = 1
        System.arraycopy(nal, 0, out, 4, nal.size)
        return out
    }

    private fun appendAccessUnitDelimiter(frameBytes: ByteArray): ByteArray {
        val aud = if (isHevc) HEVC_AUD_NAL else H264_AUD_NAL
        val out = ByteArray(frameBytes.size + aud.size)
        System.arraycopy(frameBytes, 0, out, 0, frameBytes.size)
        System.arraycopy(aud, 0, out, frameBytes.size, aud.size)
        return out
    }

    fun getHeader(): ByteArray? {
        val sps = lastSps
        if (sps != null) {
            val annexBSps = toAnnexB(sps)
            val pps = lastPps
            return if (pps != null) {
                val annexBPps = toAnnexB(pps)
                val combined = ByteArray(annexBSps.size + annexBPps.size)
                System.arraycopy(annexBSps, 0, combined, 0, annexBSps.size)
                System.arraycopy(annexBPps, 0, combined, annexBSps.size, annexBPps.size)
                combined
            } else {
                annexBSps
            }
        }
        return lastCodecConfig
    }

    fun getLastKeyFrame(): ByteArray? = lastKeyFrame

    fun isActive(): Boolean = running.get() && codec != null

    private fun encodeLoop() {
        val encoder = codec ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            while (running.get() && !Thread.currentThread().isInterrupted) {
                val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    val outputBuffer = encoder.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val rawData = ByteArray(bufferInfo.size)
                        outputBuffer.get(rawData)
                        val annexBData = toAnnexB(rawData)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            lastCodecConfig = annexBData
                            frameListener?.onFrameAvailable(
                                FramePacket(annexBData, FramePriority.CODEC_CONFIG, bufferInfo.presentationTimeUs)
                            )
                        } else {
                            val basePacket = NalParser.classifyFrame(
                                data = annexBData,
                                offset = 0,
                                size = annexBData.size,
                                flags = bufferInfo.flags,
                                timestampUs = bufferInfo.presentationTimeUs,
                                isHevc = isHevc
                            )
                            val framedData = if (basePacket.priority == FramePriority.KEY_FRAME) {
                                val hdr = getHeader()
                                val withHeader = if (hdr != null) {
                                    val combined = ByteArray(hdr.size + annexBData.size)
                                    System.arraycopy(hdr, 0, combined, 0, hdr.size)
                                    System.arraycopy(annexBData, 0, combined, hdr.size, annexBData.size)
                                    combined
                                } else {
                                    annexBData
                                }
                                val withAud = appendAccessUnitDelimiter(withHeader)
                                lastKeyFrame = withAud
                                withAud
                            } else {
                                appendAccessUnitDelimiter(annexBData)
                            }

                            frameListener?.onFrameAvailable(
                                FramePacket(framedData, basePacket.priority, basePacket.timestampUs)
                            )
                        }
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = encoder.outputFormat
                    extractSpsPps(newFormat)
                    getHeader()?.let { headerBytes ->
                        frameListener?.onFrameAvailable(
                            FramePacket(headerBytes, FramePriority.CODEC_CONFIG, 0L)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            if (running.get()) {
                Log.e(TAG, "Encoder loop exception", e)
            }
        }
    }

    private fun extractSpsPps(format: MediaFormat) {
        val csd0 = format.getByteBuffer("csd-0")?.duplicate()
        val csd1 = format.getByteBuffer("csd-1")?.duplicate()
        if (csd0 != null && csd0.remaining() > 0) {
            val sps = ByteArray(csd0.remaining())
            csd0.get(sps)
            lastSps = sps
        }
        if (csd1 != null && csd1.remaining() > 0) {
            val pps = ByteArray(csd1.remaining())
            csd1.get(pps)
            lastPps = pps
        }
    }

    fun upgradeToMediaProjection(projection: MediaProjection, onProjectionStopped: (() -> Unit)? = null) {
        synchronized(lifecycleLock) {
            if (!running.get()) return
            captureThread?.interrupt()
            synchronized(a11yWaitLock) {
                a11yWaitLock.notifyAll()
            }
            try { captureThread?.join(300) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            captureThread = null

            this.mediaProjection = projection
            this.projectionStopCallback = onProjectionStopped
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "Upgraded MediaProjection stopped")
                    try { virtualDisplay?.release() } catch (_: Exception) {}
                    virtualDisplay = null
                    mediaProjection = null
                    projectionStopCallback?.invoke()
                }
            }, android.os.Handler(android.os.Looper.getMainLooper()))

            val surface = inputSurface ?: return
            this.virtualDisplay = projection.createVirtualDisplay(
                "LinktoMac",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )
            requestKeyFrame()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                requestKeyFrame()
            }, 80L)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                requestKeyFrame()
            }, 250L)
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        synchronized(lifecycleLock) {
            captureThread?.interrupt()
            synchronized(a11yWaitLock) {
                a11yWaitLock.notifyAll()
            }
            try { captureThread?.join(500) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            captureThread = null

            encoderThread?.interrupt()
            try { encoderThread?.join(1000) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            encoderThread = null
            cleanup()
        }
    }

    private fun cleanup() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null

        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null

        try {
            codec?.stop()
            codec?.release()
        } catch (_: Exception) {}
        codec = null

        try { inputSurface?.release() } catch (_: Exception) {}
        inputSurface = null
    }

    companion object {
        private const val TAG = "ScreenEncoder"
        const val MIN_BITRATE = 2_000_000
        const val MAX_BITRATE = 24_000_000
        const val BITRATE_ADJUSTMENT_COOLDOWN_MS = 1500L
        private val H264_AUD_NAL = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x09, 0xF0.toByte())
        private val HEVC_AUD_NAL = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x46, 0x01, 0x50)

        /**
         * Pure function calculating the adapted bitrate based on packet loss and round-trip time.
         * - Severe congestion (lossRate > 0.10 or rttMs > 300.0ms): aggressive 30% reduction.
         * - Moderate congestion (lossRate > 0.05 or rttMs > 150.0ms): moderate 15% reduction.
         * - Optimal conditions (lossRate < 0.01 and rttMs < 50.0ms): conservative 10% probe upward.
         * - Neutral band: maintains current bitrate.
         * - Output is strictly bounded between MIN_BITRATE (2 Mbps) and MAX_BITRATE (24 Mbps).
         */
        fun calculateAdaptedBitrate(currentBitrate: Int, lossRate: Double, rttMs: Double): Int {
            if (lossRate.isNaN() || rttMs.isNaN()) return currentBitrate
            return when {
                lossRate > 0.10 || rttMs > 300.0 -> {
                    (currentBitrate * 0.70).toInt().coerceAtLeast(MIN_BITRATE)
                }
                lossRate > 0.05 || rttMs > 150.0 -> {
                    (currentBitrate * 0.85).toInt().coerceAtLeast(MIN_BITRATE)
                }
                lossRate < 0.01 && rttMs < 50.0 -> {
                    (currentBitrate * 1.10).toInt().coerceAtMost(MAX_BITRATE)
                }
                else -> currentBitrate
            }
        }
    }
}
