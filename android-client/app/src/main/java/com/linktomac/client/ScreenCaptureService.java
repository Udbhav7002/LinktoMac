package com.linktomac.client;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.WindowManager;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {
    private static final String CHANNEL_ID = "ScreenCaptureChannel";
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaCodec mediaCodec;
    private Socket socket;
    private Thread networkThread;
    private volatile boolean running = false;
    private int videoWidth = 720;
    private int videoHeight = 1600;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Screen Capture", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("LinktoMac")
                .setContentText("Sharing screen...")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();
                
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String code = intent.getStringExtra("code");
        int resultCode = intent.getIntExtra("resultCode", 0);
        Intent data = intent.getParcelableExtra("data");

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(resultCode, data);

        running = true;
        networkThread = new Thread(() -> startStreaming(code));
        networkThread.start();

        return START_NOT_STICKY;
    }

    private void startStreaming(String code) {
        Logger.i("startStreaming initiated with raw code: " + code);
        try {
            // Aggressively clean the input in case the user pasted a protocol prefix
            code = code.trim()
                       .replace("tls://", "")
                       .replace("tcp://", "")
                       .replace("http://", "")
                       .replace("https://", "");
                       
            String[] parts = code.split(":");
            String host = parts[0].trim();
            int port = 27189; // Default fallback
            if (parts.length >= 2) {
                try {
                    String portStr = parts[1].replace("/", "").trim();
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    Logger.e("Failed to parse port from code: " + code, e);
                    throw new Exception("The port part after the colon must be a number. You entered: '" + parts[1] + "'");
                }
            }
            
            Logger.i("Attempting socket connection to " + host + ":" + port);

            // Revert back to raw Socket because Pinggy does not decrypt TLS at the edge
            socket = new Socket(host, port);
            socket.setTcpNoDelay(true);
            socket.setSendBufferSize(256 * 1024);
            
            Logger.i("Socket connected successfully to " + host + ":" + port);

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Handshake
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(metrics);
            
            // Hardcode performance mode 720p scaling for now
            int rawWidth = 720;
            int rawHeight = (int) (rawWidth * ((float)metrics.heightPixels / metrics.widthPixels));
            // Ensure even numbers for HEVC
            final int width = rawWidth & ~1;
            final int height = rawHeight & ~1;
            
            Logger.i("Calculated screen dimensions for stream: " + width + "x" + height);

            ByteBuffer handshake = ByteBuffer.allocate(20);
            handshake.putInt(0x4C494E4B); // Magic Number "LINK"
            handshake.putInt(1);          // Protocol Version
            handshake.putInt(width);
            handshake.putInt(height);
            handshake.putInt(0);          // Flags
            out.write(handshake.array());
            out.flush();
            Logger.i("Handshake with magic number 0x4C494E4B sent");

            // Start Input Reader Thread
            new Thread(() -> readControlInput(in, width, height, metrics.widthPixels, metrics.heightPixels)).start();

            // Start Encoder
            Logger.i("Starting H.264 video encoder");
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000);

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface inputSurface = mediaCodec.createInputSurface();
            
            mediaProjection.registerCallback(new android.media.projection.MediaProjection.Callback() {
                @Override
                public void onStop() {
                    Logger.i("MediaProjection stopped by system");
                    super.onStop();
                    cleanup();
                }
            }, new android.os.Handler(android.os.Looper.getMainLooper()));

            virtualDisplay = mediaProjection.createVirtualDisplay("LinktoMac",
                    width, height, metrics.densityDpi,
                    android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    inputSurface, null, null);

            mediaCodec.start();
            Logger.i("Video encoder started successfully. Waiting for frames...");
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                android.widget.Toast.makeText(this, "Streaming active! Waiting for frames...", android.widget.Toast.LENGTH_SHORT).show();
            });

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            while (running) {
                int outputBufferId = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                if (outputBufferId >= 0) {
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferId);
                    if (outputBuffer != null) {
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        byte[] data = new byte[bufferInfo.size];
                        outputBuffer.get(data);
                        try {
                            out.write(data);
                        } catch (Exception e) {
                            Logger.e("Socket closed while writing frame data", e);
                            break;
                        }
                    }
                    mediaCodec.releaseOutputBuffer(outputBufferId, false);
                }
            }

        } catch (Exception e) {
            Logger.e("Critical exception in startStreaming", e);
            e.printStackTrace();
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                android.widget.Toast.makeText(this, "Connection error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            });
        } finally {
            Logger.i("startStreaming loop exited. Cleaning up.");
            cleanup();
        }
    }

    private void readControlInput(InputStream in, int videoWidth, int videoHeight, int screenWidth, int screenHeight) {
        Logger.i("Control input reader thread started");
        try {
            while (running) {
                int type = in.read();
                if (type == -1) throw new Exception("Socket closed remotely");
                
                if (type == 1) { // TOUCH (14 bytes total, read remaining 13)
                    byte[] payload = readExact(in, 13);
                    ByteBuffer bb = ByteBuffer.wrap(payload);
                    int action = bb.get();
                    float x = bb.getFloat();
                    float y = bb.getFloat();
                    // float pressure = bb.getFloat();

                    int scaledX = (int) (x * ((float) screenWidth / videoWidth));
                    int scaledY = (int) (y * ((float) screenHeight / videoHeight));
                    // Logger.d("Injecting touch: action=" + action + ", x=" + scaledX + ", y=" + scaledY);
                    TouchAccessibilityService.injectTouch(scaledX, scaledY, action);
                } else if (type == 2) { // KEY (6 bytes total, read remaining 5)
                    byte[] payload = readExact(in, 5);
                    ByteBuffer bb = ByteBuffer.wrap(payload);
                    int action = bb.get();
                    int keycode = bb.getInt();
                    Logger.d("Injecting key: action=" + action + ", keycode=" + keycode);
                    TouchAccessibilityService.injectKey(action, keycode);
                } else if (type == 3) { // SCROLL (17 bytes total, read remaining 16)
                    byte[] payload = readExact(in, 16);
                    ByteBuffer bb = ByteBuffer.wrap(payload);
                    float x = bb.getFloat();
                    float y = bb.getFloat();
                    float hscroll = bb.getFloat();
                    float vscroll = bb.getFloat();
                    int scaledX = (int) (x * ((float) screenWidth / videoWidth));
                    int scaledY = (int) (y * ((float) screenHeight / videoHeight));
                    Logger.d("Injecting scroll at x=" + scaledX + ", y=" + scaledY);
                    TouchAccessibilityService.injectScroll(scaledX, scaledY, hscroll, vscroll);
                }
            }
        } catch (Exception e) {
            Logger.e("Exception in readControlInput", e);
            cleanup();
        }
    }

    private byte[] readExact(InputStream in, int length) throws Exception {
        byte[] buffer = new byte[length];
        int bytesRead = 0;
        while (bytesRead < length) {
            int read = in.read(buffer, bytesRead, length - bytesRead);
            if (read == -1) throw new Exception("EOF while reading exactly " + length + " bytes");
            bytesRead += read;
        }
        return buffer;
    }

    private void cleanup() {
        if (!running) return;
        Logger.i("Cleaning up ScreenCaptureService resources");
        running = false;
        try { if (socket != null) socket.close(); } catch (Exception e) { Logger.e("Failed to close socket", e); }
        try { if (mediaCodec != null) { mediaCodec.stop(); mediaCodec.release(); } } catch (Exception e) { Logger.e("Failed to stop mediaCodec", e); }
        try { if (virtualDisplay != null) virtualDisplay.release(); } catch (Exception e) {}
        try { if (mediaProjection != null) mediaProjection.stop(); } catch (Exception e) {}
        stopSelf();
        Logger.i("Service stopped");
    }

    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
