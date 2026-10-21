package com.linktomac;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.view.Surface;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenEncoder {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private MediaCodec codec;
    private Surface inputSurface;
    private final int width;
    private final int height;
    private final int bitRate;
    private final int frameRate;
    private volatile int currentBitrate = 2_000_000;
    private ScreenCapture capture;
    private OutputStream outputStream;
    private volatile boolean formatSent = false;

    public ScreenEncoder(int width, int height, int bitRate, int frameRate) {
        this.width = width;
        this.height = height;
        this.bitRate = bitRate;
        this.currentBitrate = (bitRate > 0) ? bitRate : 2_000_000;
        this.frameRate = frameRate;
    }

    /**
     * Handles dynamic adaptive bitrate (ABR) feedback telemetry packet (0x07) from native client.
     * Decreases bitrate on packet loss / elevated RTT; ramps bitrate up on clean low-latency links.
     *
     * @param packet 9-byte network feedback buffer: type(0x07) + lossRate(float) + rttMs(float)
     */
    public void handleNetworkFeedback(byte[] packet) {
        if (packet == null || packet.length != 9 || packet[0] != 0x07) return;
        float lossRate = readBeFloat(packet, 1);
        float rttMs = readBeFloat(packet, 5);
        int newBitrate = currentBitrate;
        if (lossRate > 0.05f || rttMs > 200.0f) {
            newBitrate = Math.max(800_000, currentBitrate - 500_000);
        } else if (lossRate < 0.01f && rttMs < 80.0f) {
            newBitrate = Math.min(6_000_000, currentBitrate + 500_000);
        }
        if (newBitrate != currentBitrate) {
            currentBitrate = newBitrate;
            if (codec != null) {
                Bundle params = new Bundle();
                params.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, newBitrate);
                codec.setParameters(params);
            }
            System.out.println("ScreenEncoder ABR: bitrate -> " + newBitrate + " (loss=" + lossRate + ", rtt=" + rttMs + "ms)");
        }
    }

    /**
     * Returns the currently active encoder bitrate in bps.
     */
    public int getCurrentBitrate() {
        return currentBitrate;
    }

    private static float readBeFloat(byte[] buf, int off) {
        int bits = ((buf[off] & 0xFF) << 24) | ((buf[off+1] & 0xFF) << 16)
                 | ((buf[off+2] & 0xFF) << 8)  | (buf[off+3] & 0xFF);
        return Float.intBitsToFloat(bits);
    }

    private volatile boolean isHevc = false;

    public int initCodec() throws IOException {
        if (codec != null) {
            return isHevc ? 0 : 1;
        }

        String[] mimeTypes = {MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AVC};
        MediaCodec configuredCodec = null;
        for (String mimeType : mimeTypes) {
            try {
                MediaFormat format = MediaFormat.createVideoFormat(mimeType, width, height);
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
                format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                MediaCodec encoder = MediaCodec.createEncoderByType(mimeType);
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                configuredCodec = encoder;
                isHevc = MediaFormat.MIMETYPE_VIDEO_HEVC.equals(mimeType);
                System.out.println("Using encoder: " + encoder.getName() + " (" + mimeType + ")");
                break;
            } catch (Exception e) {
                System.out.println("Encoder " + mimeType + " failed: " + e.getMessage() + ", trying fallback...");
            }
        }
        if (configuredCodec == null) {
            throw new IOException("Unable to create a video encoder for HEVC or AVC");
        }
        codec = configuredCodec;
        inputSurface = codec.createInputSurface();
        return isHevc ? 0 : 1;
    }

    public boolean isHevc() {
        return isHevc;
    }

    public int getCodecFlags() {
        return isHevc ? 0 : 1;
    }

    public void start(OutputStream outputStream) throws IOException {
        if (codec == null) {
            initCodec();
        }
        running.set(true);
        this.outputStream = outputStream;
        codec.start();

        capture = new ScreenCapture();
        capture.start(inputSurface, width, height);

        new Thread(() -> encodeLoop(outputStream)).start();
    }

    private void encodeLoop(OutputStream outputStream) {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        
        try {
            while (running.get()) {
                int outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000);
                if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        Connection.writeFully(outputStream, outputBuffer);
                    }
                    codec.releaseOutputBuffer(outputIndex, false);
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Format changed (e.g., resolution switch) - extract new SPS/PPS and send to client
                    MediaFormat newFormat = codec.getOutputFormat();
                    sendFormatChange(newFormat);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            stop();
        }
    }
    
    private void sendFormatChange(MediaFormat newFormat) throws IOException {
        // Extract CSD-0 (SPS) and CSD-1 (PPS) from new format
        ByteBuffer csd0 = newFormat.getByteBuffer("csd-0");
        ByteBuffer csd1 = newFormat.getByteBuffer("csd-1");
        
        if (csd0 != null && csd1 != null) {
            // Send format change marker + SPS + PPS
            // Format: EVENT_TYPE_FORMAT_CHANGE (0x04) + len(csd0) + csd0 + len(csd1) + csd1
            byte[] csd0Bytes = new byte[csd0.remaining()];
            byte[] csd1Bytes = new byte[csd1.remaining()];
            csd0.get(csd0Bytes);
            csd1.get(csd1Bytes);
            
            int totalLen = 1 + 4 + csd0Bytes.length + 4 + csd1Bytes.length;
            ByteBuffer buf = ByteBuffer.allocate(totalLen);
            buf.put((byte) 0x04); // EVENT_TYPE_FORMAT_CHANGE
            buf.putInt(csd0Bytes.length);
            buf.put(csd0Bytes);
            buf.putInt(csd1Bytes.length);
            buf.put(csd1Bytes);
            
            outputStream.write(buf.array());
            outputStream.flush();
            System.out.println("Sent format change: SPS=" + csd0Bytes.length + " PPS=" + csd1Bytes.length);
        }
    }

    public void stop() {
        if (running.getAndSet(false)) {
            if (capture != null) {
                capture.stop();
                capture = null;
            }
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    codec.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                codec = null;
            }
            if (inputSurface != null) {
                try {
                    inputSurface.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                inputSurface = null;
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }
}
