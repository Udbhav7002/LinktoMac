package com.linktomac;

import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.view.Surface;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

public class ScreenCapture {

    private static Class<?> surfaceControlClass;
    private static Method createDisplayMethod;
    private static Method openTransactionMethod;
    private static Method closeTransactionMethod;
    private static Method setDisplaySurfaceMethod;
    private static Method setDisplayProjectionMethod;
    private static Method setDisplayLayerStackMethod;
    private static Method destroyDisplayMethod;
    private static boolean reflectionInitialized = false;

    private IBinder display;

    static {
        try {
            surfaceControlClass = Class.forName("android.view.SurfaceControl");
            createDisplayMethod = surfaceControlClass.getMethod("createDisplay", String.class, boolean.class);
            openTransactionMethod = surfaceControlClass.getMethod("openTransaction");
            closeTransactionMethod = surfaceControlClass.getMethod("closeTransaction");
            setDisplaySurfaceMethod = surfaceControlClass.getMethod("setDisplaySurface", IBinder.class, Surface.class);
            setDisplayProjectionMethod = surfaceControlClass.getMethod("setDisplayProjection", IBinder.class, int.class, Rect.class, Rect.class);
            setDisplayLayerStackMethod = surfaceControlClass.getMethod("setDisplayLayerStack", IBinder.class, int.class);
            destroyDisplayMethod = surfaceControlClass.getMethod("destroyDisplay", IBinder.class);
            reflectionInitialized = true;
        } catch (Exception e) {
            System.err.println("SurfaceControl reflection failed: " + e.getMessage());
        }
    }

    public void start(Surface surface, int deviceWidth, int deviceHeight) {
        if (!reflectionInitialized) {
            System.err.println("ScreenCapture unavailable: SurfaceControl reflection failed");
            return;
        }

        try {
            display = (IBinder) createDisplayMethod.invoke(null, "LinktoMac", false);

            if (openTransactionMethod != null) openTransactionMethod.invoke(null);
            try {
                if (setDisplaySurfaceMethod != null) setDisplaySurfaceMethod.invoke(null, display, surface);
                if (setDisplayProjectionMethod != null) setDisplayProjectionMethod.invoke(null, display, 0,
                        new Rect(0, 0, deviceWidth, deviceHeight),
                        new Rect(0, 0, deviceWidth, deviceHeight));
                if (setDisplayLayerStackMethod != null) setDisplayLayerStackMethod.invoke(null, display, 0);
            } finally {
                if (closeTransactionMethod != null) closeTransactionMethod.invoke(null);
            }
        } catch (Exception e) {
            System.err.println("ScreenCapture start failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
        if (display != null) {
            try {
                if (destroyDisplayMethod != null) {
                    destroyDisplayMethod.invoke(null, display);
                }
                display = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
