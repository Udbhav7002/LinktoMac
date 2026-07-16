package com.linktomac;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.lang.reflect.Method;

public class Server {

    private static final int VIDEO_PORT = 27189;
    private static final int CONTROL_PORT = 27190;
    // Protocol magic number: "LINK" = 0x4C494E4B
    private static final int PROTOCOL_MAGIC = 0x4C494E4B;

    public static void main(String[] args) {
        // Bypass hidden API restrictions (Android 9+)
        try {
            java.lang.reflect.Method forName = Class.class.getDeclaredMethod("forName", String.class);
            java.lang.reflect.Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
            Class<?> vmRuntimeClass = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
            java.lang.reflect.Method getRuntime = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null);
            java.lang.reflect.Method setHiddenApiExemptions = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions", new Class[]{String[].class});
            Object vmRuntime = getRuntime.invoke(null);
            // Only exempt specific classes we actually use, not entire packages
            setHiddenApiExemptions.invoke(vmRuntime, new Object[]{new String[]{
                "Landroid/view/SurfaceControl;",
                "Landroid/hardware/input/InputManagerGlobal;",
                "Landroid/hardware/input/InputManager;",
                "Landroid/hardware/display/DisplayManagerGlobal;"
            }});
        } catch (Exception e) {
            System.err.println("Failed to bypass hidden API: " + e.getMessage());
        }

        int width = 1080;
        int height = 2400;
        int bitRate = 8_000_000;
        int frameRate = 60;

        if (args.length >= 2) {
            width = Integer.parseInt(args[0]);
            height = Integer.parseInt(args[1]);
        }
        if (args.length >= 3) {
            bitRate = Integer.parseInt(args[2]);
        }
        if (args.length >= 4) {
            frameRate = Integer.parseInt(args[3]);
        }

try (ServerSocket videoServerSocket = new ServerSocket(VIDEO_PORT);
             ServerSocket controlServerSocket = new ServerSocket(CONTROL_PORT)) {
              
            // Set timeouts to avoid indefinite blocking
            videoServerSocket.setSoTimeout(30000); // 30 second timeout
            controlServerSocket.setSoTimeout(30000);
              
            System.out.println("Waiting for connection...");
            
            Socket videoSocket = null;
            Socket controlSocket = null;
            
            try {
                videoSocket = videoServerSocket.accept();
                videoSocket.setTcpNoDelay(true);
                videoSocket.setSendBufferSize(256 * 1024); // STRICT 256KB Latency Limit! Drops frames instead of buffering
                System.out.println("Video connection accepted.");
                
                controlSocket = controlServerSocket.accept();
                controlSocket.setTcpNoDelay(true);
                controlSocket.setSoTimeout(5000); // 5 second read timeout for control channel
                System.out.println("Control connection accepted.");

                OutputStream videoOut = videoSocket.getOutputStream();
                InputStream controlIn = controlSocket.getInputStream();

                // Send handshake: 20 bytes = magic(4) + version(4) + width(4) + height(4) + flags(4)
                // Protocol version 1 = legacy, 2 = current with version field
                ByteBuffer handshake = ByteBuffer.allocate(20);
                handshake.putInt(PROTOCOL_MAGIC);
                handshake.putInt(2); // Protocol version 2
                handshake.putInt(width);
                handshake.putInt(height);
                handshake.putInt(0); // flags
                handshake.flip();
                Connection.writeFully(videoOut, handshake);
                System.out.println("Handshake sent: magic=0x" + Integer.toHexString(PROTOCOL_MAGIC) + " version=2 " + width + "x" + height);

                ScreenEncoder encoder = new ScreenEncoder(width, height, bitRate, frameRate);
                encoder.start(videoOut);
                
                InputController inputController = new InputController();
                inputController.start(controlIn, width, height);
                
                // Wait for disconnect by reading from the video socket with a timeout
                videoSocket.setSoTimeout(3000);
                byte[] discardBuf = new byte[1024];
                try {
                    while (true) {
                        int read = videoSocket.getInputStream().read(discardBuf);
                        if (read == -1) break;
                    }
                } catch (java.net.SocketTimeoutException e) {
                    // Timeout means socket is still alive, continue waiting
                } catch (java.io.IOException e) {
                    System.out.println("Video socket disconnected");
                }

                encoder.stop();
                inputController.stop();

            } finally {
                // Ensure both sockets are closed on any error
                if (videoSocket != null) {
                    try { videoSocket.close(); } catch (Exception ignored) {}
                }
                if (controlSocket != null) {
                    try { controlSocket.close(); } catch (Exception ignored) {}
                }
            }
        } catch (java.net.SocketTimeoutException e) {
            // Timeout on accept - loop will retry
        } catch (java.io.IOException e) {
            System.out.println("Server socket error: " + e.getMessage());
        }
    }
}