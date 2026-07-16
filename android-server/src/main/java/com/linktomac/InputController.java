package com.linktomac;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class InputController {

    private static final int EVENT_TYPE_TOUCH = 0x01;
    private static final int EVENT_TYPE_KEY = 0x02;
    private static final int EVENT_TYPE_SCROLL = 0x03;
    
    private static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;

    private Object inputManager;
    private Method injectInputEventMethod;
    private long lastTouchDownTime = 0;
    private int virtualWidth = 0;
    private int virtualHeight = 0;
    private int realWidth = 0;
    private int realHeight = 0;

    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // SDL to Android KeyCode mapping
    private static final int[][] SDL_TO_ANDROID_KEYCODE = {
        {4, 4},      // SDLK_ESCAPE -> BACK
        {40, 66},    // SDLK_RETURN -> ENTER
        {41, 61},    // SDLK_TAB -> TAB
        {42, 67},    // SDLK_BACKSPACE -> DEL
        {43, 62},    // SDLK_SPACE -> SPACE
        {44, 68},    // SDLK_EXCLAIM -> 1
        {45, 69},    // SDLK_QUOTEDBL -> 2
        {46, 70},    // SDLK_HASH -> 3
        {47, 71},    // SDLK_PERCENT -> 4
        {48, 72},    // SDLK_AMPERSAND -> 5
        {49, 73},    // SDLK_QUOTE -> 6
        {50, 74},    // SDLK_LEFTPAREN -> 7
        {51, 75},    // SDLK_RIGHTPAREN -> 8
        {52, 76},    // SDLK_ASTERISK -> 9
        {53, 77},    // SDLK_PLUS -> 0
        {54, 78},    // SDLK_COMMA -> COMMA
        {55, 79},    // SDLK_MINUS -> MINUS
        {56, 80},    // SDLK_PERIOD -> PERIOD
        {57, 81},    // SDLK_SLASH -> SLASH
        {58, 158},   // SDLK_0 -> 0 (KEYCODE_0 = 7)
        {59, 159},   // SDLK_1 -> 1
        {60, 160},   // SDLK_2 -> 2
        {61, 161},   // SDLK_3 -> 3
        {62, 162},   // SDLK_4 -> 4
        {63, 163},   // SDLK_5 -> 5
        {64, 164},   // SDLK_6 -> 6
        {65, 165},   // SDLK_7 -> 7
        {66, 166},   // SDLK_8 -> 8
        {67, 167},   // SDLK_9 -> 9
        {68, 168},   // SDLK_COLON -> :
        {69, 169},   // SDLK_SEMICOLON -> ;
        {70, 170},   // SDLK_LESS -> <
        {71, 171},   // SDLK_EQUALS -> =
        {72, 172},   // SDLK_GREATER -> >
        {73, 173},   // SDLK_QUESTION -> ?
        {74, 174},   // SDLK_AT -> @
        {75, 29},    // SDLK_LEFTBRACKET -> [
        {76, 92},    // SDLK_BACKSLASH -> \
        {77, 30},    // SDLK_RIGHTBRACKET -> ]
        {78, 31},    // SDLK_CARET -> ^
        {79, 32},    // SDLK_UNDERSCORE -> _
        {80, 33},    // SDLK_BACKQUOTE -> `
        {81, 13},    // SDLK_a -> A
        {82, 14},    // SDLK_b -> B
        {83, 15},    // SDLK_c -> C
        {84, 16},    // SDLK_d -> D
        {85, 17},    // SDLK_e -> E
        {86, 18},    // SDLK_f -> F
        {87, 19},    // SDLK_g -> G
        {88, 20},    // SDLK_h -> H
        {89, 21},    // SDLK_i -> I
        {90, 22},    // SDLK_j -> J
        {91, 23},    // SDLK_k -> K
        {92, 24},    // SDLK_l -> L
        {93, 25},    // SDLK_m -> M
        {94, 26},    // SDLK_n -> N
        {95, 27},    // SDLK_o -> O
        {96, 28},    // SDLK_p -> P
        {97, 29},    // SDLK_q -> Q
        {98, 30},    // SDLK_r -> R
        {99, 31},    // SDLK_s -> S
        {100, 32},   // SDLK_t -> T
        {101, 33},   // SDLK_u -> U
        {102, 34},   // SDLK_v -> V
        {103, 35},   // SDLK_w -> W
        {104, 36},   // SDLK_x -> X
        {105, 37},   // SDLK_y -> Y
        {106, 38},   // SDLK_z -> Z
        {107, 122},  // SDLK_CAPSLOCK -> CAPS_LOCK
        {108, 123},  // SDLK_F1 -> F1
        {109, 124},  // SDLK_F2 -> F2
        {110, 125},  // SDLK_F3 -> F3
        {111, 126},  // SDLK_F4 -> F4
        {112, 127},  // SDLK_F5 -> F5
        {113, 128},  // SDLK_F6 -> F6
        {114, 129},  // SDLK_F7 -> F7
        {115, 130},  // SDLK_F8 -> F8
        {116, 131},  // SDLK_F9 -> F9
        {117, 132},  // SDLK_F10 -> F10
        {118, 133},  // SDLK_F11 -> F11
        {119, 134},  // SDLK_F12 -> F12
        {120, 113},  // SDLK_PRINTSCREEN -> SYSRQ
        {121, 114},  // SDLK_SCROLLLOCK -> BREAK
        {122, 115},  // SDLK_PAUSE -> PAUSE
        {123, 116},  // SDLK_INSERT -> INSERT
        {124, 117},  // SDLK_HOME -> HOME
        {125, 118},  // SDLK_PAGEUP -> PAGE_UP
        {126, 122},  // SDLK_DELETE -> DELETE (forward)
        {127, 119},  // SDLK_END -> END
        {128, 120},  // SDLK_PAGEDOWN -> PAGE_DOWN
        {129, 121},  // SDLK_RIGHT -> DPAD_RIGHT
        {130, 122},  // SDLK_LEFT -> DPAD_LEFT
        {131, 19},   // SDLK_DOWN -> DPAD_DOWN
        {132, 18},   // SDLK_UP -> DPAD_UP
        {133, 124},  // SDLK_NUMLOCKCLEAR -> NUM_LOCK
        {134, 141},  // SDLK_KP_DIVIDE -> NUMPAD_DIVIDE
        {135, 142},  // SDLK_KP_MULTIPLY -> NUMPAD_MULTIPLY
        {136, 143},  // SDLK_KP_MINUS -> NUMPAD_SUBTRACT
        {137, 144},  // SDLK_KP_PLUS -> NUMPAD_ADD
        {138, 145},  // SDLK_KP_ENTER -> NUMPAD_ENTER
        {139, 146},  // SDLK_KP_1 -> NUMPAD_1
        {140, 147},  // SDLK_KP_2 -> NUMPAD_2
        {141, 148},  // SDLK_KP_3 -> NUMPAD_3
        {142, 149},  // SDLK_KP_4 -> NUMPAD_4
        {143, 150},  // SDLK_KP_5 -> NUMPAD_5
        {144, 151},  // SDLK_KP_6 -> NUMPAD_6
        {145, 152},  // SDLK_KP_7 -> NUMPAD_7
        {146, 153},  // SDLK_KP_8 -> NUMPAD_8
        {147, 154},  // SDLK_KP_9 -> NUMPAD_9
        {148, 155},  // SDLK_KP_0 -> NUMPAD_0
        {149, 156},  // SDLK_KP_PERIOD -> NUMPAD_DOT
    };

    public InputController() {
        try {
            // Android 14+ InputManagerGlobal
            try {
                Class<?> globalClass = Class.forName("android.hardware.input.InputManagerGlobal");
                Method getInstanceMethod = globalClass.getMethod("getInstance");
                inputManager = getInstanceMethod.invoke(null);
            } catch (Exception e) {
                // Fallback to InputManager
                try {
                    Class<?> inputManagerClass = Class.forName("android.hardware.input.InputManager");
                    Method getInstanceMethod = inputManagerClass.getMethod("getInstance");
                    inputManager = getInstanceMethod.invoke(null);
                } catch (Exception e2) {
                    System.err.println("Failed to get InputManager: " + e2.getMessage());
                    inputManager = null;
                }
            }
            if (inputManager != null) {
                injectInputEventMethod = inputManager.getClass().getMethod("injectInputEvent", android.view.InputEvent.class, int.class);
            } else {
                System.err.println("InputManager not available — input injection disabled (requires INJECT_EVENTS permission)");
            }
        } catch (Exception e) {
            System.err.println("Input controller initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private int mapSDLKeycode(int sdlKeycode) {
        for (int[] mapping : SDL_TO_ANDROID_KEYCODE) {
            if (mapping[0] == sdlKeycode) return mapping[1];
        }
        // Default: if it's a printable ASCII, try direct mapping
        if (sdlKeycode >= 32 && sdlKeycode <= 126) {
            // Android KeyEvent keycodes for printable ASCII roughly match ASCII for letters/numbers
            return sdlKeycode; // best effort
        }
        return sdlKeycode; // pass through unknown
    }
    
    private void initDisplayMetrics() {
        if (realWidth > 0) return; // already initialized
        try {
            // Try to get real display metrics via reflection
            Class<?> displayManagerClass = Class.forName("android.hardware.display.DisplayManagerGlobal");
            Method getInstanceMethod = displayManagerClass.getMethod("getInstance");
            Object displayManager = getInstanceMethod.invoke(null);
            Method getRealDisplayMetricsMethod = displayManagerClass.getMethod("getRealDisplayMetrics", int.class, android.util.DisplayMetrics.class);
            
            // Get default display (0)
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            getRealDisplayMetricsMethod.invoke(displayManager, 0, metrics);
            realWidth = metrics.widthPixels;
            realHeight = metrics.heightPixels;
            System.out.println("Real device screen: " + realWidth + "x" + realHeight);
        } catch (Exception e) {
            System.err.println("Could not get real display metrics: " + e.getMessage());
            // Fallback to virtual dimensions
            realWidth = virtualWidth;
            realHeight = virtualHeight;
        }
    }
    
    private float scaleX(float x) {
        if (realWidth == virtualWidth) return x;
        return x * (float) realWidth / virtualWidth;
    }
    
    private float scaleY(float y) {
        if (realHeight == virtualHeight) return y;
        return y * (float) realHeight / virtualHeight;
    }

    public void start(InputStream inputStream, int width, int height) {
        virtualWidth = width;
        virtualHeight = height;
        initDisplayMetrics();
        running.set(true);
        new Thread(() -> controlLoop(inputStream)).start();
    }
    
    public void start(InputStream inputStream) {
        // Backward compat - will use virtual dimensions if not set
        running.set(true);
        new Thread(() -> controlLoop(inputStream)).start();
    }
    
    public void stop() {
        running.set(false);
    }
    
    private void controlLoop(InputStream in) {
        try {
            byte[] header = new byte[1];
            while (running.get()) {
                Connection.readFully(in, header, 0, 1);
                int type = header[0];
                
                if (type == EVENT_TYPE_TOUCH) {
                    byte[] payload = new byte[13];
                    Connection.readFully(in, payload, 0, 13);
                    ByteBuffer buf = ByteBuffer.wrap(payload);
                    int action = buf.get();
                    float x = buf.getFloat();
                    float y = buf.getFloat();
                    float pressure = buf.getFloat();
                    handleTouch(action, x, y, pressure);
                    
                } else if (type == EVENT_TYPE_KEY) {
                    byte[] payload = new byte[5];
                    Connection.readFully(in, payload, 0, 5);
                    ByteBuffer buf = ByteBuffer.wrap(payload);
                    int action = buf.get();
                    int keycode = buf.getInt();
                    handleKey(action, keycode);
                    
                } else if (type == EVENT_TYPE_SCROLL) {
                    byte[] payload = new byte[16];
                    Connection.readFully(in, payload, 0, 16);
                    ByteBuffer buf = ByteBuffer.wrap(payload);
                    float x = buf.getFloat();
                    float y = buf.getFloat();
                    float hscroll = buf.getFloat();
                    float vscroll = buf.getFloat();
                    handleScroll(x, y, hscroll, vscroll);
                }
            }
        } catch (IOException e) {
            System.err.println("Control connection closed");
        } finally {
            stop();
        }
    }
    
    private void handleTouch(int actionCode, float x, float y, float pressure) {
        long now = SystemClock.uptimeMillis();
        
        int androidAction = -1;
        if (actionCode == 0) { // DOWN
            androidAction = MotionEvent.ACTION_DOWN;
            lastTouchDownTime = now;
        } else if (actionCode == 1) { // UP
            androidAction = MotionEvent.ACTION_UP;
        } else if (actionCode == 2) { // MOVE
            androidAction = MotionEvent.ACTION_MOVE;
        }
        
        if (androidAction == -1) return;

        // Scale coordinates from virtual display space to real device space
        float scaledX = scaleX(x);
        float scaledY = scaleY(y);
        
        MotionEvent event = MotionEvent.obtain(
            lastTouchDownTime, now, androidAction,
            scaledX, scaledY, pressure, 1.0f, 0, 1.0f, 1.0f, 0, 0
        );
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        
        injectEvent(event);
        event.recycle();
    }
    
    private void handleKey(int actionCode, int keycode) {
        long now = SystemClock.uptimeMillis();
        int action = (actionCode == 0) ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
        
        // Map SDL keycode to Android keycode
        int androidKeycode = mapSDLKeycode(keycode);
        
        KeyEvent event = new KeyEvent(now, now, action, androidKeycode, 0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD);
        injectEvent(event);
    }
    
    private void handleScroll(float x, float y, float hscroll, float vscroll) {
        long now = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] pointerProperties = new MotionEvent.PointerProperties[1];
        pointerProperties[0] = new MotionEvent.PointerProperties();
        pointerProperties[0].id = 0;
        
        MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[1];
        pointerCoords[0] = new MotionEvent.PointerCoords();
        pointerCoords[0].x = scaleX(x);
        pointerCoords[0].y = scaleY(y);
        pointerCoords[0].setAxisValue(MotionEvent.AXIS_HSCROLL, hscroll);
        pointerCoords[0].setAxisValue(MotionEvent.AXIS_VSCROLL, vscroll);
        
        MotionEvent event = MotionEvent.obtain(
            lastTouchDownTime, now, MotionEvent.ACTION_SCROLL,
            1, pointerProperties, pointerCoords, 0, 0, 1.0f, 1.0f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        );
        
        injectEvent(event);
        event.recycle();
    }
    
    private void injectEvent(InputEvent event) {
        if (injectInputEventMethod != null && inputManager != null) {
            try {
                injectInputEventMethod.invoke(inputManager, event, INJECT_INPUT_EVENT_MODE_ASYNC);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Log once to avoid spam
            System.err.println("Input injection unavailable (requires INJECT_EVENTS permission)");
        }
    }
}
