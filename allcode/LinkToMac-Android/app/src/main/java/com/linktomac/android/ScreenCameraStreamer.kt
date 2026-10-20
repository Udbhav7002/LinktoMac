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






