package com.linktomac.companion;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class MirrorService extends Service {

    private static final String TAG = "MirrorService";
    private static final String EXTRA_RESULT_CODE = "result_code";
    private static final String EXTRA_RESULT_DATA = "result_data";
    private static final String CHANNEL_ID = "mirror_channel";
    private static final int NOTIFICATION_ID = 1;

    private MediaProjection mediaProjection;
    private MediaProjectionManager projectionManager;
    private ScreenEncoder encoder;
    private NetworkThread networkThread;

    public static void startService(Activity activity, int resultCode, Intent data) {
        Intent intent = new Intent(activity, MirrorService.class);
        intent.putExtra(EXTRA_RESULT_CODE, resultCode);
        intent.putExtra(EXTRA_RESULT_DATA, data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.startForegroundService(intent);
        } else {
            activity.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !intent.hasExtra(EXTRA_RESULT_CODE)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent data = intent.getParcelableExtra(EXTRA_RESULT_DATA);

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e(TAG, "Screen capture permission denied");
            stopSelf();
            return START_NOT_STICKY;
        }

        mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            Log.e(TAG, "Failed to get MediaProjection");
            stopSelf();
            return START_NOT_STICKY;
        }

        Log.i(TAG, "MediaProjection acquired successfully");

        // Start foreground service with notification
        Notification notification = createNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        // Start encoder and network thread
        startEncoder();

        return START_STICKY;
    }

    private void startEncoder() {
        try {
            encoder = new ScreenEncoder(720, 1600, 8_000_000, 60);
            encoder.start(mediaProjection, data -> {
                if (networkThread != null) {
                    networkThread.sendData(data);
                }
            });

            networkThread = new NetworkThread();
            networkThread.start();

        } catch (Exception e) {
            Log.e(TAG, "Failed to start encoder", e);
            stopSelf();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Mirroring",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Active screen mirroring session");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("LinktoMac")
                .setContentText("Screen mirroring active")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (encoder != null) {
            encoder.stop();
            encoder = null;
        }
        if (networkThread != null) {
            networkThread.stopServer();
            networkThread = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        stopForeground(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Network thread to handle TCP connection
    private class NetworkThread extends Thread {
        private java.net.ServerSocket serverSocket;
        private java.net.Socket clientSocket;
        private java.io.OutputStream outputStream;
        private java.io.InputStream inputStream;
        private volatile boolean running = true;

        @Override
        public void run() {
            try {
                serverSocket = new java.net.ServerSocket(27189);
                serverSocket.setReuseAddress(true);
                Log.i(TAG, "Mirror server listening on port 27189");

                while (running && !Thread.currentThread().isInterrupted()) {
                    try {
                        clientSocket = serverSocket.accept();
                        clientSocket.setTcpNoDelay(true);
                        clientSocket.setSendBufferSize(256 * 1024);
                        outputStream = clientSocket.getOutputStream();
                        inputStream = clientSocket.getInputStream();
                        Log.i(TAG, "Client connected");

                        // Send handshake: 20 bytes = magic(4) + version(4) + width(4) + height(4) + flags(4)
                        byte[] handshake = new byte[20];
                        java.nio.ByteBuffer.wrap(handshake).putInt(0x4C494E4B).putInt(2).putInt(720).putInt(1600).putInt(0);
                        outputStream.write(handshake);
                        outputStream.flush();

                        // Read control events from the Mac (touch/key/scroll)
                        readControlEvents();

                    } catch (Exception e) {
                        Log.e(TAG, "Client error", e);
                    } finally {
                        closeClient();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Server error", e);
            }
        }

        private void readControlEvents() {
            try {
                byte[] header = new byte[1];
                while (running && clientSocket != null && clientSocket.isConnected()) {
                    int read = inputStream.read(header);
                    if (read == -1) break;

                    int type = header[0] & 0xFF;
                    if (type == 0x01) { // EVENT_TYPE_TOUCH
                        byte[] payload = new byte[13];
                        readExact(payload);
                        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(payload);
                        int action = buf.get();
                        float x = buf.getFloat();
                        float y = buf.getFloat();
                        float pressure = buf.getFloat();
                        handleTouch(action, x, y, pressure);
                    } else if (type == 0x02) { // EVENT_TYPE_KEY
                        byte[] payload = new byte[5];
                        readExact(payload);
                        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(payload);
                        int action = buf.get();
                        int keycode = buf.getInt();
                        handleKey(action, keycode);
                    } else if (type == 0x03) { // EVENT_TYPE_SCROLL
                        byte[] payload = new byte[16];
                        readExact(payload);
                        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(payload);
                        float x = buf.getFloat();
                        float y = buf.getFloat();
                        float hscroll = buf.getFloat();
                        float vscroll = buf.getFloat();
                        handleScroll(x, y, hscroll, vscroll);
                    }
                }
            } catch (java.io.IOException e) {
                Log.i(TAG, "Control connection closed", e);
            }
        }

        private void readExact(byte[] buffer) throws java.io.IOException {
            int offset = 0;
            while (offset < buffer.length) {
                int read = inputStream.read(buffer, offset, buffer.length - offset);
                if (read == -1) throw new java.io.IOException("Stream closed");
                offset += read;
            }
        }

        private void handleTouch(int actionCode, float x, float y, float pressure) {
            long now = android.os.SystemClock.uptimeMillis();
            int androidAction;
            if (actionCode == 0) androidAction = android.view.MotionEvent.ACTION_DOWN;
            else if (actionCode == 2) androidAction = android.view.MotionEvent.ACTION_MOVE;
            else androidAction = android.view.MotionEvent.ACTION_UP;

            long downTime = (actionCode == 0) ? now : lastTouchDownTime;
            if (actionCode == 0) lastTouchDownTime = now;

            android.view.MotionEvent event = android.view.MotionEvent.obtain(
                downTime, now, androidAction, x, y, pressure, 1.0f, 0, 1.0f, 1.0f, 0, 0
            );
            event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            injectEvent(event);
            event.recycle();
        }

        private void handleKey(int actionCode, int keycode) {
            long now = android.os.SystemClock.uptimeMillis();
            int action = (actionCode == 0) ? android.view.KeyEvent.ACTION_DOWN : android.view.KeyEvent.ACTION_UP;
            android.view.KeyEvent event = new android.view.KeyEvent(now, now, action, keycode, 0);
            injectEvent(event);
        }

        private void handleScroll(float x, float y, float hscroll, float vscroll) {
            long now = android.os.SystemClock.uptimeMillis();
            android.view.MotionEvent.PointerProperties[] props = new android.view.MotionEvent.PointerProperties[1];
            props[0] = new android.view.MotionEvent.PointerProperties();
            props[0].id = 0;
            android.view.MotionEvent.PointerCoords[] coords = new android.view.MotionEvent.PointerCoords[1];
            coords[0] = new android.view.MotionEvent.PointerCoords();
            coords[0].x = x;
            coords[0].y = y;
            coords[0].setAxisValue(android.view.MotionEvent.AXIS_HSCROLL, hscroll);
            coords[0].setAxisValue(android.view.MotionEvent.AXIS_VSCROLL, vscroll);
            android.view.MotionEvent event = android.view.MotionEvent.obtain(
                lastTouchDownTime, now, android.view.MotionEvent.ACTION_SCROLL,
                1, props, coords, 0, 0, 1.0f, 1.0f, 0, 0,
                android.view.InputDevice.SOURCE_TOUCHSCREEN, 0
            );
            injectEvent(event);
            event.recycle();
        }

        private void injectEvent(android.view.InputEvent event) {
            try {
                Object inputManager = android.hardware.input.InputManager.class
                    .getMethod("getInstance").invoke(null);
                inputManager.getClass()
                    .getMethod("injectInputEvent", android.view.InputEvent.class, int.class)
                    .invoke(inputManager, event, 0);
            } catch (Exception e) {
                Log.e(TAG, "Inject error", e);
            }
        }

        void sendData(byte[] data) {
            if (outputStream != null) {
                try {
                    outputStream.write(data);
                    outputStream.flush();
                } catch (Exception e) {
                    Log.e(TAG, "Send error", e);
                }
            }
        }

        void stopServer() {
            running = false;
            closeClient();
            try {
                if (serverSocket != null) serverSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "Close error", e);
            }
            interrupt();
        }

        private void closeClient() {
            try {
                if (clientSocket != null) clientSocket.close();
            } catch (Exception ignored) {}
            clientSocket = null;
            outputStream = null;
            inputStream = null;
        }
    }

    private long lastTouchDownTime = 0;
}