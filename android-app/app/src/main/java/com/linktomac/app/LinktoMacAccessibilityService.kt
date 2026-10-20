package com.linktomac.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.PointF
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executor

/**
 * Accessibility service that injects touch gestures, scroll actions,
 * navigation keys, and captures screenshots received from the Mac desktop client.
 */
class LinktoMacAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "LinktoMacA11y"

        @Volatile
        var instance: LinktoMacAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null

        fun isAccessibilityEnabled(context: Context): Boolean {
            if (instance != null) {
                return true
            }

            try {
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
                if (am != null) {
                    val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    val expectedId = "${context.packageName}/${LinktoMacAccessibilityService::class.java.name}"
                    for (service in enabledServices) {
                        if (service.id.equals(expectedId, ignoreCase = true) ||
                            service.resolveInfo?.serviceInfo?.name?.equals(LinktoMacAccessibilityService::class.java.name, ignoreCase = true) == true) {
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking AccessibilityManager: ${e.message}")
            }

            try {
                val expectedServiceName = "${context.packageName}/${LinktoMacAccessibilityService::class.java.canonicalName}"
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false

                val colonSplitter = TextUtils.SimpleStringSplitter(':')
                colonSplitter.setString(enabledServices)
                while (colonSplitter.hasNext()) {
                    val componentName = colonSplitter.next()
                    if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking Settings.Secure: ${e.message}")
            }
            return false
        }

        /**
         * Takes a screenshot of the default display using the AccessibilityService API (API 30+).
         * This replaces MediaProjection and avoids the red status bar recording indicator.
         */
        fun takeScreenshot(
            displayId: Int = android.view.Display.DEFAULT_DISPLAY,
            executor: Executor,
            callback: AccessibilityService.TakeScreenshotCallback
        ) {
            val svc = instance
            if (svc == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    executor.execute { callback.onFailure(AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR) }
                }
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    svc.takeScreenshot(displayId, executor, callback)
                } catch (e: Exception) {
                    Log.w(TAG, "takeScreenshot failed: ${e.message}")
                    executor.execute { callback.onFailure(AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR) }
                }
            }
        }
    }

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchDownTime = 0L
    private var lastMoveTime = 0L
    private var activeStroke: GestureDescription.StrokeDescription? = null
    private var isStrokeInFlight = false
    private val pendingMoves = ArrayList<PointF>()
    private var pendingUp = false
    private var pendingUpX = 0f
    private var pendingUpY = 0f

    // Scroll coalescing and in-flight gating state
    private var isScrollInFlight = false
    private var accumulatedHScroll = 0f
    private var accumulatedVScroll = 0f
    private var lastScrollX = 0f
    private var lastScrollY = 0f
    private var lastScrollStreamW = 720
    private var lastScrollStreamH = 1600

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val gestureWatchdogRunnable = Runnable {
        Log.w(TAG, "Gesture watchdog triggered; terminating lingering continuous stroke to prevent stuck touch")
        cancelActiveGestures()
    }
    private val scrollFlushRunnable = Runnable {
        if (!isScrollInFlight && (Math.abs(accumulatedHScroll) > 0.001f || Math.abs(accumulatedVScroll) > 0.001f)) {
            flushScroll()
        }
    }

    private val strokeGestureCallback = object : GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) {
            onStrokeCompleted()
        }

        override fun onCancelled(gestureDescription: GestureDescription?) {
            onStrokeCancelled()
        }
    }

    private val scrollGestureCallback = object : GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) {
            onScrollFinished()
        }

        override fun onCancelled(gestureDescription: GestureDescription?) {
            onScrollFinished()
        }
    }

    /**
     * Immediately cancels and terminates any in-flight dragging gesture (willContinue = true)
     * by injecting a finalizing stroke with willContinue = false.
     * Prevents Android device from being left in a "stuck touch" state if the Mac disconnects mid-drag.
     */
    fun cancelActiveGestures() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { processCancelActiveGestures() }
        } else {
            processCancelActiveGestures()
        }
    }

    private fun processCancelActiveGestures() {
        mainHandler.removeCallbacks(gestureWatchdogRunnable)
        mainHandler.removeCallbacks(scrollFlushRunnable)
        pendingMoves.clear()
        pendingUp = false
        isStrokeInFlight = false
        isScrollInFlight = false
        accumulatedHScroll = 0f
        accumulatedVScroll = 0f

        val currentStroke = activeStroke
        activeStroke = null
        if (currentStroke != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val (realW, _) = getRealScreenSize()
                val path = Path().apply {
                    moveTo(lastTouchX, lastTouchY)
                    lineTo((lastTouchX + 1f).coerceAtMost(realW.toFloat()), lastTouchY)
                }
                val finalStroke = currentStroke.continueStroke(path, 0L, 20L, false)
                val gesture = GestureDescription.Builder().addStroke(finalStroke).build()
                dispatchGesture(gesture, null, mainHandler)
                Log.i(TAG, "Safely released active dragging gesture")
            } catch (e: Exception) {
                Log.w(TAG, "Error while safely releasing continuous gesture: ${e.message}")
            }
        }
    }

    private fun onStrokeCompleted() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { processStrokeCompleted() }
        } else {
            processStrokeCompleted()
        }
    }

    private fun processStrokeCompleted() {
        isStrokeInFlight = false

        if (pendingUp) {
            pendingUp = false
            val currentStroke = activeStroke
            activeStroke = null
            pendingMoves.clear()
            mainHandler.removeCallbacks(gestureWatchdogRunnable)

            if (currentStroke != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val (realW, _) = getRealScreenSize()
                val endX = if (Math.abs(pendingUpX - lastTouchX) < 1f && Math.abs(pendingUpY - lastTouchY) < 1f) {
                    (lastTouchX + 1f).coerceAtMost(realW.toFloat())
                } else {
                    pendingUpX
                }
                val path = Path().apply {
                    moveTo(lastTouchX, lastTouchY)
                    lineTo(endX, pendingUpY)
                }
                val duration = (System.currentTimeMillis() - lastMoveTime).coerceIn(15L, 100L)
                try {
                    val finalStroke = currentStroke.continueStroke(path, 0L, duration, false)
                    val gesture = GestureDescription.Builder().addStroke(finalStroke).build()
                    dispatchGesture(gesture, null, mainHandler)
                } catch (e: Exception) {
                    Log.w(TAG, "Error finalizing pendingUp stroke: ${e.message}")
                }
            }
            return
        }

        if (pendingMoves.isNotEmpty() && activeStroke != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val currentStroke = activeStroke ?: return
            val path = Path()
            path.moveTo(lastTouchX, lastTouchY)
            for (pt in pendingMoves) {
                path.lineTo(pt.x, pt.y)
            }
            val lastPt = pendingMoves.last()
            lastTouchX = lastPt.x
            lastTouchY = lastPt.y
            pendingMoves.clear()

            val now = System.currentTimeMillis()
            val duration = (now - lastMoveTime).coerceIn(20L, 100L)
            lastMoveTime = now

            try {
                val nextStroke = currentStroke.continueStroke(path, 0L, duration, true)
                activeStroke = nextStroke
                isStrokeInFlight = true
                mainHandler.removeCallbacks(gestureWatchdogRunnable)
                mainHandler.postDelayed(gestureWatchdogRunnable, 1500L)
                val gesture = GestureDescription.Builder().addStroke(nextStroke).build()
                dispatchGesture(gesture, strokeGestureCallback, mainHandler)
            } catch (e: Exception) {
                Log.w(TAG, "Chained continueStroke error: ${e.message}")
                isStrokeInFlight = false
            }
        }
    }

    private fun onStrokeCancelled() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { processStrokeCancelled() }
        } else {
            processStrokeCancelled()
        }
    }

    private fun processStrokeCancelled() {
        Log.w(TAG, "Stroke gesture was cancelled by framework; preserving activeStroke to prevent drag break")
        isStrokeInFlight = false
        // Do NOT reset activeStroke = null
        if (pendingUp) {
            processStrokeCompleted()
            return
        }
        if (pendingMoves.isNotEmpty() && activeStroke != null) {
            processStrokeCompleted()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "LinktoMacAccessibilityService connected and ready for input injection and screenshot capture")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive listener; events are received via socket from Mac
    }

    override fun onInterrupt() {
        Log.w(TAG, "LinktoMacAccessibilityService interrupted")
        cancelActiveGestures()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelActiveGestures()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "LinktoMacAccessibilityService destroyed")
    }

    private fun getRealScreenSize(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            val bounds = wm.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            val display = wm?.defaultDisplay
            if (display != null) {
                @Suppress("DEPRECATION")
                display.getRealMetrics(dm)
                Pair(dm.widthPixels, dm.heightPixels)
            } else {
                val metrics = resources.displayMetrics
                Pair(metrics.widthPixels, metrics.heightPixels)
            }
        }
    }

    /**
     * Injects touch events: DOWN (0), UP (1), MOVE (2)
     * Marshalled to Main Looper to serialize stroke mutations.
     * Uses continuous multi-stroke gestures (willContinue = true) on Android 8.0+ (API 26+)
     * with buffered MOVE coordinates and continueStroke chaining on onCompleted()
     * so dragging does not cancel or break into isolated mini-taps.
     */
    fun handleTouch(action: Int, x: Float, y: Float, pressure: Float, streamWidth: Int = 720, streamHeight: Int = 1600) {
        mainHandler.post {
            processTouch(action, x, y, pressure, streamWidth, streamHeight)
        }
    }

    private fun processTouch(action: Int, x: Float, y: Float, pressure: Float, streamWidth: Int, streamHeight: Int) {
        val (realW, realH) = getRealScreenSize()
        val scaledX = if (streamWidth > 0) (x * realW / streamWidth).coerceIn(0f, realW.toFloat()) else x
        val scaledY = if (streamHeight > 0) (y * realH / streamHeight).coerceIn(0f, realH.toFloat()) else y
        val now = System.currentTimeMillis()

        when (action) {
            0 -> { // ACTION_DOWN
                if (activeStroke != null || isStrokeInFlight) {
                    processCancelActiveGestures()
                }
                pendingMoves.clear()
                pendingUp = false
                touchDownX = scaledX
                touchDownY = scaledY
                lastTouchX = scaledX
                lastTouchY = scaledY
                touchDownTime = now
                lastMoveTime = now
                activeStroke = null
                isStrokeInFlight = false
            }
            2 -> { // ACTION_MOVE
                val dx = Math.abs(scaledX - lastTouchX)
                val dy = Math.abs(scaledY - lastTouchY)
                if (dx >= 2f || dy >= 2f) {
                    if (isStrokeInFlight) {
                        // Buffer move coordinates while stroke is in-flight
                        pendingMoves.add(PointF(scaledX, scaledY))
                    } else if (activeStroke != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // Inactive in-flight stroke completed; immediately chain next stroke
                        val path = Path().apply {
                            moveTo(lastTouchX, lastTouchY)
                            lineTo(scaledX, scaledY)
                        }
                        val duration = (now - lastMoveTime).coerceIn(20L, 100L)
                        lastTouchX = scaledX
                        lastTouchY = scaledY
                        lastMoveTime = now
                        try {
                            val stroke = activeStroke?.continueStroke(path, 0L, duration, true)
                            if (stroke != null) {
                                activeStroke = stroke
                                isStrokeInFlight = true
                                mainHandler.removeCallbacks(gestureWatchdogRunnable)
                                mainHandler.postDelayed(gestureWatchdogRunnable, 1500L)
                                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                                dispatchGesture(gesture, strokeGestureCallback, mainHandler)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "continueStroke error: ${e.message}")
                        }
                    } else {
                        // First move after DOWN - initiate continuous stroke
                        if (now - lastMoveTime >= 20L) {
                            val duration = (now - lastMoveTime).coerceIn(20L, 100L)
                            val path = Path().apply {
                                moveTo(lastTouchX, lastTouchY)
                                lineTo(scaledX, scaledY)
                            }
                            lastTouchX = scaledX
                            lastTouchY = scaledY
                            lastMoveTime = now
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val stroke = GestureDescription.StrokeDescription(path, 0L, duration, true)
                                activeStroke = stroke
                                isStrokeInFlight = true
                                mainHandler.removeCallbacks(gestureWatchdogRunnable)
                                mainHandler.postDelayed(gestureWatchdogRunnable, 1500L)
                                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                                dispatchGesture(gesture, strokeGestureCallback, mainHandler)
                            } else {
                                val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
                                dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, mainHandler)
                            }
                        }
                    }
                }
            }
            1 -> { // ACTION_UP
                mainHandler.removeCallbacks(gestureWatchdogRunnable)
                if (isStrokeInFlight) {
                    // Queue finalization to occur when in-flight stroke completes
                    pendingUp = true
                    pendingUpX = scaledX
                    pendingUpY = scaledY
                    return
                }

                val currentStroke = activeStroke
                if (currentStroke != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activeStroke = null
                    pendingMoves.clear()
                    val duration = (now - lastMoveTime).coerceIn(15L, 100L)
                    val endX = if (Math.abs(scaledX - lastTouchX) < 1f && Math.abs(scaledY - lastTouchY) < 1f) {
                        (lastTouchX + 1f).coerceAtMost(realW.toFloat())
                    } else {
                        scaledX
                    }
                    val path = Path().apply {
                        moveTo(lastTouchX, lastTouchY)
                        lineTo(endX, scaledY)
                    }
                    try {
                        val finalStroke = currentStroke.continueStroke(path, 0L, duration, false)
                        val gesture = GestureDescription.Builder().addStroke(finalStroke).build()
                        dispatchGesture(gesture, null, mainHandler)
                    } catch (e: Exception) {
                        Log.w(TAG, "Final stroke termination error: ${e.message}")
                    }
                } else {
                    activeStroke = null
                    pendingMoves.clear()
                    val duration = (now - touchDownTime).coerceIn(40L, 600L)
                    val path = Path().apply { moveTo(touchDownX, touchDownY) }
                    val dx = Math.abs(scaledX - touchDownX)
                    val dy = Math.abs(scaledY - touchDownY)

                    if (dx < 15f && dy < 15f) {
                        // Single Tap / Click
                        path.lineTo(touchDownX, (touchDownY + 1f).coerceAtMost(realH.toFloat()))
                        val stroke = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            GestureDescription.StrokeDescription(path, 0L, 50L, false)
                        } else {
                            GestureDescription.StrokeDescription(path, 0L, 50L)
                        }
                        val gesture = GestureDescription.Builder().addStroke(stroke).build()
                        dispatchGesture(gesture, null, mainHandler)
                    } else {
                        // Fast Flick / Swipe completed before ACTION_MOVE threshold
                        path.lineTo(scaledX, scaledY)
                        val stroke = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            GestureDescription.StrokeDescription(path, 0L, duration, false)
                        } else {
                            GestureDescription.StrokeDescription(path, 0L, duration)
                        }
                        val gesture = GestureDescription.Builder().addStroke(stroke).build()
                        dispatchGesture(gesture, null, mainHandler)
                    }
                }
            }
        }
    }

    private fun onScrollFinished() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { processScrollFinished() }
        } else {
            processScrollFinished()
        }
    }

    private fun processScrollFinished() {
        isScrollInFlight = false
        if (Math.abs(accumulatedHScroll) >= 0.05f || Math.abs(accumulatedVScroll) >= 0.05f) {
            flushScroll()
        }
    }

    /**
     * Injects scroll gestures (mouse wheel or trackpad scroll)
     * Coalesces high-frequency deltas and gates in-flight gestures to prevent gesture cancellation stutter.
     */
    fun handleScroll(x: Float, y: Float, hscroll: Float, vscroll: Float, streamWidth: Int = 720, streamHeight: Int = 1600) {
        mainHandler.post {
            processScroll(x, y, hscroll, vscroll, streamWidth, streamHeight)
        }
    }

    private fun processScroll(x: Float, y: Float, hscroll: Float, vscroll: Float, streamWidth: Int, streamHeight: Int) {
        accumulatedHScroll += hscroll
        accumulatedVScroll += vscroll
        lastScrollX = x
        lastScrollY = y
        lastScrollStreamW = streamWidth
        lastScrollStreamH = streamHeight

        if (!isScrollInFlight) {
            if (Math.abs(accumulatedHScroll) >= 0.05f || Math.abs(accumulatedVScroll) >= 0.05f) {
                mainHandler.removeCallbacks(scrollFlushRunnable)
                flushScroll()
            } else {
                mainHandler.removeCallbacks(scrollFlushRunnable)
                mainHandler.postDelayed(scrollFlushRunnable, 30L)
            }
        }
    }

    private fun flushScroll() {
        if (isScrollInFlight) return
        val h = accumulatedHScroll
        val v = accumulatedVScroll
        accumulatedHScroll = 0f
        accumulatedVScroll = 0f

        val (realW, realH) = getRealScreenSize()
        val scaledX = if (lastScrollStreamW > 0) (lastScrollX * realW / lastScrollStreamW).coerceIn(0f, realW.toFloat()) else lastScrollX
        val scaledY = if (lastScrollStreamH > 0) (lastScrollY * realH / lastScrollStreamH).coerceIn(0f, realH.toFloat()) else lastScrollY

        val targetX = (scaledX - h * 150f).coerceIn(0f, realW.toFloat())
        val targetY = (scaledY - v * 150f).coerceIn(0f, realH.toFloat())
        if (Math.abs(targetX - scaledX) < 1f && Math.abs(targetY - scaledY) < 1f) return

        val path = Path().apply {
            moveTo(scaledX, scaledY)
            lineTo(targetX, targetY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, 80L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        isScrollInFlight = true
        dispatchGesture(gesture, scrollGestureCallback, mainHandler)
    }

    /**
     * Injects UTF-8 text directly into the currently focused editable field.
     */
    fun handleText(text: String) {
        if (text.isEmpty()) return
        val focused = findFocusedEditableNode() ?: return
        try {
            val isHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focused.isShowingHintText else false
            val currentText = if (isHint) "" else (focused.text?.toString() ?: "")
            val selStart = focused.textSelectionStart
            val selEnd = focused.textSelectionEnd

            val newText: String
            val newCursor: Int
            if (!isHint && selStart >= 0 && selEnd >= 0 && selStart <= currentText.length && selEnd <= currentText.length) {
                val minSel = Math.min(selStart, selEnd)
                val maxSel = Math.max(selStart, selEnd)
                newText = currentText.substring(0, minSel) + text + currentText.substring(maxSel)
                newCursor = minSel + text.length
            } else {
                newText = currentText + text
                newCursor = newText.length
            }

            val setArgs = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)

            val selArgs = android.os.Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursor)
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inject UTF-8 text: ${e.message}")
        }
    }

    /**
     * Injects navigation keys and action keycodes (decoupled from character typing).
     */
    fun handleKey(action: Int, keyCode: Int) {
        if (action != 0) return // Process KeyDown events
        Log.d(TAG, "Handling key event: keyCode=$keyCode")

        when (keyCode) {
            4, 111 -> performGlobalAction(GLOBAL_ACTION_BACK) // KEYCODE_BACK (4), KEYCODE_ESCAPE (111)
            3 -> performGlobalAction(GLOBAL_ACTION_HOME) // KEYCODE_HOME (3)
            187 -> performGlobalAction(GLOBAL_ACTION_RECENTS) // KEYCODE_APP_SWITCH (187)
            26 -> { // KEYCODE_POWER (26)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                }
            }
            83 -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) // KEYCODE_NOTIFICATION (83)
            67 -> handleBackspace(forward = false) // KEYCODE_DEL (67)
            112 -> handleBackspace(forward = true) // KEYCODE_FORWARD_DEL (112)
            66 -> handleEnterKey() // KEYCODE_ENTER (66)
            61 -> handleTabKey() // KEYCODE_TAB (61)
            19, 20, 21, 22, 122, 123 -> handleCursorOrDpadKey(keyCode) // DPAD & HOME/END
        }
    }

    private fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val inputFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (inputFocused != null && inputFocused.isEditable) return inputFocused
        val a11yFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (a11yFocused != null && a11yFocused.isEditable) return a11yFocused
        return null
    }

    private fun handleBackspace(forward: Boolean) {
        val focused = findFocusedEditableNode() ?: return
        val isHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focused.isShowingHintText else false
        if (isHint) return
        val currentText = focused.text?.toString() ?: ""
        if (currentText.isEmpty()) return

        val selStart = focused.textSelectionStart
        val selEnd = focused.textSelectionEnd
        val newText: String
        val newCursor: Int

        if (selStart >= 0 && selEnd >= 0 && selStart <= currentText.length && selEnd <= currentText.length) {
            val minSel = Math.min(selStart, selEnd)
            val maxSel = Math.max(selStart, selEnd)
            if (minSel != maxSel) {
                newText = currentText.substring(0, minSel) + currentText.substring(maxSel)
                newCursor = minSel
            } else if (!forward && minSel > 0) {
                newText = currentText.substring(0, minSel - 1) + currentText.substring(minSel)
                newCursor = minSel - 1
            } else if (forward && minSel < currentText.length) {
                newText = currentText.substring(0, minSel) + currentText.substring(minSel + 1)
                newCursor = minSel
            } else {
                return
            }
        } else if (!forward) {
            newText = currentText.dropLast(1)
            newCursor = newText.length
        } else {
            return
        }

        val setArgs = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)

        val selArgs = android.os.Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursor)
        }
        focused.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
    }

    private fun handleEnterKey() {
        val focused = findFocusedEditableNode()
        if (focused != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val imeDone = focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                if (imeDone) return
            }
            if (focused.isMultiLine) {
                handleText("\n")
                return
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun handleTabKey() {
        val root = rootInActiveWindow ?: return
        val current = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val next = current.focusSearch(android.view.View.FOCUS_FORWARD)
        next?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    private fun handleCursorOrDpadKey(keyCode: Int) {
        val focused = findFocusedEditableNode()
        if (focused != null) {
            val isHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focused.isShowingHintText else false
            val text = if (isHint) "" else (focused.text?.toString() ?: "")
            val cur = focused.textSelectionEnd.coerceIn(0, text.length)
            val target = when (keyCode) {
                21 -> (cur - 1).coerceAtLeast(0) // DPAD_LEFT
                22 -> (cur + 1).coerceAtMost(text.length) // DPAD_RIGHT
                122, 19 -> 0 // MOVE_HOME or DPAD_UP
                123, 20 -> text.length // MOVE_END or DPAD_DOWN
                else -> cur
            }
            val selArgs = android.os.Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, target)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, target)
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
        } else {
            val root = rootInActiveWindow ?: return
            when (keyCode) {
                19 -> root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                20 -> root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
        }
    }
}
