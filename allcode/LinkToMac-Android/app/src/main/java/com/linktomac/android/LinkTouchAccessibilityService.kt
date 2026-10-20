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
