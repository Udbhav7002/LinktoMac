package com.linktomac.client;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.content.Intent;

public class TouchAccessibilityService extends AccessibilityService {

    private static TouchAccessibilityService instance;
    private static long lastDownTime = 0;
    private static int lastX = -1;
    private static int lastY = -1;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Logger.i("TouchAccessibilityService connected and instance assigned");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        instance = null;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    public static void injectTouch(int x, int y, int action) {
        if (instance == null) return;

        long now = SystemClock.uptimeMillis();

        if (action == 0) { // DOWN
            lastDownTime = now;
            lastX = x;
            lastY = y;
            sendGesture(x, y, 0, 1, false);
        } else if (action == 2) { // MOVE
            if (lastX < 0 || lastY < 0) return;
            lastX = x;
            lastY = y;
            sendGesture(x, y, 0, 1, false);
        } else if (action == 1) { // UP
            if (lastX < 0 || lastY < 0) return;
            sendGesture(x, y, 0, 1, true);
            lastX = -1;
            lastY = -1;
        }
    }

    public static void injectKey(int action, int keycode) {
        if (instance == null) return;
        long now = SystemClock.uptimeMillis();
        int keyAction = (action == 0) ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
        KeyEvent event = new KeyEvent(now, now, keyAction, keycode, 0);
        instance.dispatchGesture(new GestureDescription.Builder().build(), null, null); // wake up service
        // Note: AccessibilityService can't inject keys directly. 
        // Keys would need root or InputManager permission.
        // This is a placeholder - real key injection requires different approach.
    }

    public static void injectScroll(int x, int y, float hscroll, float vscroll) {
        if (instance == null) return;
        // Scroll via AccessibilityService is limited - this is a placeholder
        // Real scroll injection requires InputManager with INJECT_EVENTS permission
    }

    private static void sendGesture(int x, int y, long startOffset, long duration, boolean isUp) {
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription stroke;
        if (isUp) {
            // Use a minimal duration for the final position
            stroke = new GestureDescription.StrokeDescription(path, startOffset, 1);
        } else {
            stroke = new GestureDescription.StrokeDescription(path, startOffset, duration);
        }

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);

        instance.dispatchGesture(builder.build(), null, null);
    }
}
