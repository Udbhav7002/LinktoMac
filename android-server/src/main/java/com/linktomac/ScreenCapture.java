package com.linktomac;

import android.graphics.Rect;
import android.os.IBinder;
import android.view.Surface;
import java.lang.reflect.Method;

public class ScreenCapture {

    private static Class<?> surfaceControlClass;
    private static Class<?> transactionClass;
    private static Method createDisplayMethod;
    private static Method destroyDisplayMethod;

    // Modern SurfaceControl.Transaction methods (Android 10+ / 12–15)
    private static Method setDisplaySurfaceMethod;
    private static Method setDisplayProjectionMethod;
    private static Method setDisplayLayerStackMethod;
    private static Method applyMethod;
    private static Method closeMethod;

    // Legacy static fallback methods (pre-Android 10)
    private static Method legacySetDisplaySurfaceMethod;
    private static Method legacySetDisplayProjectionMethod;
    private static Method legacySetDisplayLayerStackMethod;

    private static boolean reflectionInitialized = false;

    private IBinder display;

    static {
        try {
            surfaceControlClass = Class.forName("android.view.SurfaceControl");
            createDisplayMethod = surfaceControlClass.getMethod("createDisplay", String.class, boolean.class);
            destroyDisplayMethod = surfaceControlClass.getMethod("destroyDisplay", IBinder.class);

            // Attempt to resolve modern SurfaceControl.Transaction
            try {
                transactionClass = Class.forName("android.view.SurfaceControl$Transaction");
                setDisplaySurfaceMethod = transactionClass.getMethod("setDisplaySurface", IBinder.class, Surface.class);
                setDisplayProjectionMethod = transactionClass.getMethod("setDisplayProjection", IBinder.class, int.class, Rect.class, Rect.class);
                setDisplayLayerStackMethod = transactionClass.getMethod("setDisplayLayerStack", IBinder.class, int.class);
                applyMethod = transactionClass.getMethod("apply");
                try {
                    closeMethod = transactionClass.getMethod("close");
                } catch (NoSuchMethodException ignored) {
                    // close() added in Android 12 (API 31)
                }
                reflectionInitialized = true;
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                // Fall back to legacy static methods on SurfaceControl (pre-Android 10)
                try {
                    legacySetDisplaySurfaceMethod = surfaceControlClass.getMethod("setDisplaySurface", IBinder.class, Surface.class);
                    legacySetDisplayProjectionMethod = surfaceControlClass.getMethod("setDisplayProjection", IBinder.class, int.class, Rect.class, Rect.class);
                    legacySetDisplayLayerStackMethod = surfaceControlClass.getMethod("setDisplayLayerStack", IBinder.class, int.class);
                    reflectionInitialized = true;
                } catch (Exception ex) {
                    System.err.println("Legacy SurfaceControl reflection failed: " + ex.getMessage());
                }
            }
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

            if (transactionClass != null && setDisplaySurfaceMethod != null) {
                Object transaction = transactionClass.getDeclaredConstructor().newInstance();
                try {
                    setDisplaySurfaceMethod.invoke(transaction, display, surface);
                    if (setDisplayProjectionMethod != null) {
                        setDisplayProjectionMethod.invoke(transaction, display, 0,
                                new Rect(0, 0, deviceWidth, deviceHeight),
                                new Rect(0, 0, deviceWidth, deviceHeight));
                    }
                    if (setDisplayLayerStackMethod != null) {
                        setDisplayLayerStackMethod.invoke(transaction, display, 0);
                    }
                    if (applyMethod != null) {
                        applyMethod.invoke(transaction);
                    }
                } finally {
                    if (closeMethod != null) {
                        try {
                            closeMethod.invoke(transaction);
                        } catch (Exception ignored) {}
                    }
                }
            } else if (legacySetDisplaySurfaceMethod != null) {
                legacySetDisplaySurfaceMethod.invoke(null, display, surface);
                if (legacySetDisplayProjectionMethod != null) {
                    legacySetDisplayProjectionMethod.invoke(null, display, 0,
                            new Rect(0, 0, deviceWidth, deviceHeight),
                            new Rect(0, 0, deviceWidth, deviceHeight));
                }
                if (legacySetDisplayLayerStackMethod != null) {
                    legacySetDisplayLayerStackMethod.invoke(null, display, 0);
                }
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

