package com.linktomac.companion;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenEncoder {

    private static final String TAG = "ScreenEncoder";
    private final AtomicBoolean running = new AtomicBoolean(false);
    private MediaCodec codec;
    private Surface inputSurface;
    private Thread encoderThread;
    private MediaProjection mediaProjection;
    private final int width;
    private final int height;
    private final int bitRate;
    private final int frameRate;
    private OnEncodedDataListener listener;
    private byte[] lastSps = null;
    private byte[] lastPps = null;

    public interface OnEncodedDataListener {
        void onEncodedData(byte[] data);
    }

    public ScreenEncoder(int width, int height, int bitRate, int frameRate) {
        this.width = width;
        this.height = height;
        this.bitRate = bitRate;
        this.frameRate = frameRate;
    }

    public void start(MediaProjection projection, OnEncodedDataListener listener) throws Exception {
        if (running.getAndSet(true)) return;
        
        this.mediaProjection = projection;
        this.listener = listener;

        // Configure HEVC encoder
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_LATENCY, 0);
        format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain);
        format.setInteger("max-bframes", 0);
        format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100000L);

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        inputSurface = codec.createInputSurface();
        codec.start();

        // Create virtual display on the encoder's input surface
        mediaProjection.createVirtualDisplay(
                "LinktoMac",
                width, height,
                1, // densityDpi - using 1 to avoid scaling
                0,
                inputSurface,
                null,
                null
        );

        encoderThread = new Thread(this::encodeLoop);
        encoderThread.start();
    }

    private void encodeLoop() {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        try {
            while (running.get()) {
                int outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000);
                if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outputIndex);
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        byte[] data = new byte[bufferInfo.size];
                        outputBuffer.get(data);
                        
                        if (listener != null) {
                            listener.onEncodedData(data);
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false);
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = codec.getOutputFormat();
                    Log.i(TAG, "Output format changed: " + newFormat);
                    // Extract new SPS/PPS from the format
                    extractSpsPps(newFormat);
                    // Send format change event with new SPS/PPS
                    if (listener != null && lastSps != null && lastPps != null) {
                        byte[] formatChange = buildFormatChangeEvent(lastSps, lastPps);
                        listener.onEncodedData(formatChange);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Encode loop error", e);
        } finally {
            cleanup();
        }
    }
    
    private void extractSpsPps(MediaFormat format) {
        ByteBuffer csd0 = format.getByteBuffer("csd-0");
        ByteBuffer csd1 = format.getByteBuffer("csd-1");
        if (csd0 != null) {
            lastSps = new byte[csd0.remaining()];
            csd0.get(lastSps);
        }
        if (csd1 != null) {
            lastPps = new byte[csd1.remaining()];
            csd1.get(lastPps);
        }
    }
    
private byte[] buildFormatChangeEvent(byte[] sps, byte[] pps) {
        // Event: type(1) + sps_len(4) + sps + pps_len(4) + pps
        int totalLen = 1 + 4 + sps.length + 4 + pps.length;
        byte[] buf = new byte[totalLen];
        int pos = 0;
        buf[pos++] = 0x04; // EVENT_TYPE_FORMAT_CHANGE
        writeBe32(buf, pos, sps.length);
        pos += 4;
        System.arraycopy(sps, 0, buf, pos, sps.length);
        pos += sps.length;
        writeBe32(buf, pos, pps.length);
        pos += 4;
        System.arraycopy(pps, 0, buf, pos, pps.length);
        return buf;
    }
    
    private static void writeBe32(byte[] buf, int offset, int val) {
        buf[offset] = (byte)((val >> 24) & 0xFF);
        buf[offset + 1] = (byte)((val >> 16) & 0xFF);
        buf[offset + 2] = (byte)((val >> 8) & 0xFF);
        buf[offset + 3] = (byte)(val & 0xFF);
    }

    public void stop() {
        running.set(false);
        if (encoderThread != null) {
            encoderThread.interrupt();
            try {
                encoderThread.join(1000);
            } catch (InterruptedException ignored) {}
        }
        cleanup();
    }

    private void cleanup() {
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        if (codec != null) {
            try {
                codec.stop();
                codec.release();
            } catch (Exception ignored) {}
            codec = null;
        }
        inputSurface = null;
    }
}