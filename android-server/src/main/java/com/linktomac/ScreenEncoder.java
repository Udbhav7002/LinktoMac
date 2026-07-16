package com.linktomac;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
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
    private ScreenCapture capture;
    private OutputStream outputStream;
    private volatile boolean formatSent = false;

    public ScreenEncoder(int width, int height, int bitRate, int frameRate) {
        this.width = width;
        this.height = height;
        this.bitRate = bitRate;
        this.frameRate = frameRate;
    }

    public void start(OutputStream outputStream) throws IOException {
        running.set(true);
        this.outputStream = outputStream;

        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1); // 1 second interval to quickly recover from dropped packets
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_LATENCY, 0);
        format.setInteger(MediaFormat.KEY_PRIORITY, 0);
        
        // Use HEVC Main profile
        format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain);
        
        // Explicitly disable B-frames to ensure decode order matches presentation order.
        format.setInteger("max-bframes", 0);
        
        // Battery Optimization: If the screen is static for 100ms (100000 microseconds), 
        // the encoder will stop blasting identical frames over the network, drastically saving battery!
        format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100000L);

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        
        inputSurface = codec.createInputSurface();
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
}
