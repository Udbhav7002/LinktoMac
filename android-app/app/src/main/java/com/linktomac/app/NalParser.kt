package com.linktomac.app

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong

enum class FramePriority {
    CODEC_CONFIG,        // SPS/PPS/VPS - Never drop
    KEY_FRAME,           // IDR - Never drop, evicts stale frames
    REFERENCE_FRAME,     // Reference P-frame - Drop only under severe lag + request IDR
    NON_REFERENCE_FRAME  // Disposable - Drop freely under congestion
}

data class FramePacket(
    val data: ByteArray,
    val priority: FramePriority,
    val timestampUs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FramePacket
        if (!data.contentEquals(other.data)) return false
        if (priority != other.priority) return false
        if (timestampUs != other.timestampUs) return false
        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + priority.hashCode()
        result = 31 * result + timestampUs.hashCode()
        return result
    }
}

object NalParser {
    private const val TAG = "NalParser"

    /**
     * Fast, allocation-free frame classification.
     * Combines MediaCodec BufferInfo flags with bitstream inspection.
     */
    fun classifyFrame(
        data: ByteArray,
        offset: Int,
        size: Int,
        flags: Int,
        timestampUs: Long,
        isHevc: Boolean
    ): FramePacket {
        // Fast path 1: BufferInfo flags (O(1))
        if ((flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            return FramePacket(data, FramePriority.CODEC_CONFIG, timestampUs)
        }
        if ((flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
            return FramePacket(data, FramePriority.KEY_FRAME, timestampUs)
        }

        // Fast path 2: Bitstream inspection for non-reference vs reference
        val priority = parseNalPriority(data, offset, size, isHevc)
        return FramePacket(data, priority, timestampUs)
    }

    fun parseNalPriority(
        data: ByteArray,
        offset: Int,
        size: Int,
        isHevc: Boolean
    ): FramePriority {
        var pos = offset
        val limit = offset + size

        while (pos <= limit - 4) {
            // Find start code (0x00 0x00 0x01 or 0x00 0x00 0x00 0x01)
            var startCodeLen = 0
            if (data[pos] == 0.toByte() && data[pos + 1] == 0.toByte()) {
                if (data[pos + 2] == 1.toByte()) {
                    startCodeLen = 3
                } else if (pos <= limit - 4 && data[pos + 2] == 0.toByte() && data[pos + 3] == 1.toByte()) {
                    startCodeLen = 4
                }
            }

            if (startCodeLen > 0) {
                val nalOffset = pos + startCodeLen
                if (nalOffset >= limit) break

                if (isHevc) {
                    val header0 = data[nalOffset].toInt() and 0xFF
                    val nalType = (header0 ushr 1) and 0x3F

                    when {
                        nalType in 32..34 -> return FramePriority.CODEC_CONFIG
                        nalType in 16..21 -> return FramePriority.KEY_FRAME
                        nalType == 35 || nalType in 39..40 -> {
                            // Non-VCL (AUD / SEI), advance past this NAL
                            pos = nalOffset
                            continue
                        }
                        nalType in 0..9 -> {
                            return if (nalType % 2 == 0) {
                                FramePriority.NON_REFERENCE_FRAME
                            } else {
                                FramePriority.REFERENCE_FRAME
                            }
                        }
                    }
                } else { // H.264 (AVC)
                    val nalByte = data[nalOffset].toInt() and 0xFF
                    val nalRefIdc = (nalByte ushr 5) and 0x03
                    val nalUnitType = nalByte and 0x1F

                    when (nalUnitType) {
                        7, 8 -> return FramePriority.CODEC_CONFIG
                        5 -> return FramePriority.KEY_FRAME
                        6, 9 -> {
                            // Non-VCL (SEI / AUD), advance past this NAL
                            pos = nalOffset
                            continue
                        }
                        in 1..4 -> {
                            return if (nalRefIdc == 0) {
                                FramePriority.NON_REFERENCE_FRAME
                            } else {
                                FramePriority.REFERENCE_FRAME
                            }
                        }
                    }
                }
            }
            pos++
        }

        // Fallback if VCL type couldn't be determined
        return FramePriority.REFERENCE_FRAME
    }
}

class BoundedFrameQueue(
    val capacity: Int = 2,
    private val onRequestKeyFrame: () -> Unit
) {
    private val queue = ArrayBlockingQueue<FramePacket>(capacity)

    val droppedNonRefFrames = AtomicLong(0)
    val droppedRefFrames = AtomicLong(0)
    val totalFramesEnqueued = AtomicLong(0)

    fun offer(frame: FramePacket): Boolean {
        // Case 1: Queue has space
        if (queue.offer(frame)) {
            totalFramesEnqueued.incrementAndGet()
            return true
        }

        // Case 2: Queue is full (Backpressure active)
        when (frame.priority) {
            FramePriority.CODEC_CONFIG -> {
                // Must never drop codec config. Evict oldest item to make space.
                queue.poll()
                queue.offer(frame)
                return true
            }

            FramePriority.NON_REFERENCE_FRAME -> {
                // Safely drop without visual artifacts
                val drops = droppedNonRefFrames.incrementAndGet()
                if (drops % 60L == 0L) {
                    try { Log.d(TAG, "Congestion backpressure: dropped $drops non-reference frames") } catch (_: Throwable) {}
                }
                return false
            }

            FramePriority.KEY_FRAME -> {
                // Keyframe supersedes all stale P-frames in queue
                queue.clear()
                queue.offer(frame)
                return true
            }

            FramePriority.REFERENCE_FRAME -> {
                // Check if queue contains any non-reference frames we can evict
                val iterator = queue.iterator()
                var evictedNonRef = false
                while (iterator.hasNext()) {
                    val queued = iterator.next()
                    if (queued.priority == FramePriority.NON_REFERENCE_FRAME) {
                        iterator.remove()
                        evictedNonRef = true
                        droppedNonRefFrames.incrementAndGet()
                        break
                    }
                }

                if (evictedNonRef) {
                    queue.offer(frame)
                    return true
                }

                // Extreme lag: all queued items are reference frames.
                // Drop incoming reference frame and immediately trigger IDR resync!
                val refDrops = droppedRefFrames.incrementAndGet()
                try { Log.w(TAG, "Extreme network congestion: dropping reference frame (total: $refDrops)") } catch (_: Throwable) {}
                onRequestKeyFrame()
                return false
            }
        }
    }

    @Throws(InterruptedException::class)
    fun take(): FramePacket = queue.take()

    fun poll(): FramePacket? = queue.poll()

    fun clear() {
        queue.clear()
    }

    val size: Int get() = queue.size

    companion object {
        private const val TAG = "BoundedFrameQueue"
    }
}

data class DisplayDimensions(
    val realWidth: Int,
    val realHeight: Int,
    val streamWidth: Int,
    val streamHeight: Int,
    val densityDpi: Int
)

object DisplayMetricsHelper {
    const val DEFAULT_MAX_DIMENSION = 1600
    const val MIN_STREAM_DIMENSION = 320

    fun align16(dimension: Int): Int {
        return (Math.round(dimension / 16.0) * 16).toInt().coerceAtLeast(MIN_STREAM_DIMENSION)
    }

    fun calculateStreamDimensions(realW: Int, realH: Int, maxDimension: Int = DEFAULT_MAX_DIMENSION): Pair<Int, Int> {
        val maxEdge = maxOf(realW, realH)
        val scale = if (maxEdge > maxDimension) maxDimension.toFloat() / maxEdge else 1.0f

        val streamW = (Math.round((realW * scale) / 16.0) * 16).toInt().coerceAtLeast(MIN_STREAM_DIMENSION)
        val streamH = (Math.round((realH * scale) / 16.0) * 16).toInt().coerceAtLeast(MIN_STREAM_DIMENSION)
        return Pair(streamW, streamH)
    }

    fun queryDisplayDimensions(context: Context, maxDimension: Int = DEFAULT_MAX_DIMENSION): DisplayDimensions {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val (realW, realH) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            val bounds = wm.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm?.defaultDisplay?.getRealMetrics(dm) ?: run {
                val resDm = context.resources.displayMetrics
                dm.widthPixels = resDm.widthPixels
                dm.heightPixels = resDm.heightPixels
            }
            Pair(dm.widthPixels, dm.heightPixels)
        }

        val densityDpi = context.resources.configuration.densityDpi
        val (streamW, streamH) = calculateStreamDimensions(realW, realH, maxDimension)

        return DisplayDimensions(realW, realH, streamW, streamH, densityDpi)
    }
}
