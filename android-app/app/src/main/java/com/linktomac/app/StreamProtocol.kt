package com.linktomac.app

import java.nio.ByteBuffer

object StreamProtocol {
    const val MAGIC: Int = 0x4C494E4B // "LINK"
    const val VERSION: Int = 2
    const val DEFAULT_PORT: Int = 27189

    // QUIC Multiplexed Transport Protocol constants
    const val MAGIC_QUIC: Int = 0x51554943 // "QUIC"
    const val FLAG_0RTT_RESUMPTION: Int = 0x01
    const val QUIC_HEADER_SIZE: Int = 20

    const val STREAM_ID_CONTROL: Long = 0L    // Bidirectional control (Handshake, Sync, Feedback)
    const val STREAM_ID_VIDEO: Long = 1L      // Server -> Client Video frames
    const val STREAM_ID_INPUT: Long = 2L      // Client -> Server Input events
    const val STREAM_ID_TELEMETRY: Long = 3L  // Server -> Client Telemetry & Stats

    const val EVENT_TYPE_TOUCH: Int = 0x01
    const val EVENT_TYPE_KEY: Int = 0x02
    const val EVENT_TYPE_SCROLL: Int = 0x03
    const val EVENT_TYPE_FORMAT_CHANGE: Int = 0x04
    const val EVENT_TYPE_SYNC_REQUEST: Int = 0x05
    const val EVENT_TYPE_TEXT: Int = 0x06
    const val EVENT_TYPE_NETWORK_FEEDBACK: Int = 0x07

    const val FLAG_CODEC_HEVC: Int = 0x00
    const val FLAG_CODEC_H264: Int = 0x01

    const val HANDSHAKE_SIZE: Int = 20

    data class HandshakeData(
        val magic: Int,
        val version: Int,
        val width: Int,
        val height: Int,
        val flags: Int
    )

    data class QuicFrame(
        val magic: Int,
        val flags: Int,
        val streamId: Long,
        val payload: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as QuicFrame
            return magic == other.magic && flags == other.flags &&
                    streamId == other.streamId && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = magic
            result = 31 * result + flags
            result = 31 * result + streamId.hashCode()
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    data class NetworkFeedbackData(
        val lossRate: Float,
        val rttMs: Float,
        val timestampUs: Long = 0L
    )

    /**
     * Builds the 20-byte connection handshake packet:
     * magic (4) + version (4) + width (4) + height (4) + flags (4)
     */
    fun createHandshake(width: Int, height: Int, flags: Int = 0): ByteArray {
        val buffer = ByteArray(HANDSHAKE_SIZE)
        ByteBuffer.wrap(buffer).apply {
            putInt(MAGIC)
            putInt(VERSION)
            putInt(width)
            putInt(height)
            putInt(flags)
        }
        return buffer
    }

    /**
     * Parses a 20-byte connection handshake packet.
     */
    fun parseHandshake(bytes: ByteArray): HandshakeData? {
        if (bytes.size < HANDSHAKE_SIZE) return null
        val buffer = ByteBuffer.wrap(bytes)
        val magic = buffer.int
        if (magic != MAGIC) return null
        val version = buffer.int
        val width = buffer.int
        val height = buffer.int
        val flags = buffer.int
        return HandshakeData(magic, version, width, height, flags)
    }

    /**
     * Builds a multiplexed QUIC frame header (20 bytes) + payload:
     * magic (4) + flags (4) + streamId (8) + payloadLen (4) + payload
     */
    fun createQuicFrame(streamId: Long, payload: ByteArray, flags: Int = 0): ByteArray {
        val totalLen = QUIC_HEADER_SIZE + payload.size
        val out = ByteArray(totalLen)
        val buf = ByteBuffer.wrap(out)
        buf.putInt(MAGIC_QUIC)
        buf.putInt(flags)
        buf.putLong(streamId)
        buf.putInt(payload.size)
        buf.put(payload)
        return out
    }

    /**
     * Parses a multiplexed QUIC frame from raw bytes.
     */
    fun parseQuicFrame(bytes: ByteArray): QuicFrame? {
        if (bytes.size < QUIC_HEADER_SIZE) return null
        val buf = ByteBuffer.wrap(bytes)
        val magic = buf.int
        if (magic != MAGIC_QUIC) return null
        val flags = buf.int
        val streamId = buf.long
        val payloadLen = buf.int
        if (payloadLen < 0 || payloadLen > bytes.size - QUIC_HEADER_SIZE) return null

        val payload = ByteArray(payloadLen)
        buf.get(payload)
        return QuicFrame(magic, flags, streamId, payload)
    }

    /**
     * Generates a 0-RTT fast resumption token tied to device identifier.
     */
    fun create0RttResumptionToken(deviceId: String): ByteArray {
        val salt = "LinktoMac-0RTT-Resumption".toByteArray(Charsets.UTF_8)
        val devBytes = deviceId.toByteArray(Charsets.UTF_8)
        val token = ByteArray(salt.size + devBytes.size + 8)
        val buf = ByteBuffer.wrap(token)
        buf.put(salt)
        buf.put(devBytes)
        buf.putLong(System.currentTimeMillis())
        return token
    }

    /**
     * Builds network telemetry feedback packet (type 0x07):
     * type (1 byte: 0x07) + lossRate (4-byte Float) + rttMs (4-byte Float)
     */
    fun createNetworkFeedbackPacket(lossRate: Float, rttMs: Float, timestampUs: Long = System.nanoTime() / 1000): ByteArray {
        val buf = ByteArray(1 + 4 + 4 + 8)
        val bb = ByteBuffer.wrap(buf)
        bb.put(EVENT_TYPE_NETWORK_FEEDBACK.toByte())
        bb.putFloat(lossRate)
        bb.putFloat(rttMs)
        bb.putLong(timestampUs)
        return buf
    }

    /**
     * Parses a network telemetry feedback packet into lossRate and rttMs.
     */
    fun parseNetworkFeedbackPacket(bytes: ByteArray): NetworkFeedbackData? {
        if (bytes.size < 9) return null
        if (bytes[0].toInt() and 0xFF != EVENT_TYPE_NETWORK_FEEDBACK) return null
        val bb = ByteBuffer.wrap(bytes)
        bb.get() // Skip type byte
        val loss = bb.float
        val rtt = bb.float
        val timestamp = if (bytes.size >= 17) bb.long else 0L
        return NetworkFeedbackData(loss, rtt, timestamp)
    }

    /**
     * Builds the format change packet containing SPS and PPS parameter sets:
     * type (1 byte: 0x04) + spsLen (4 bytes) + sps + ppsLen (4 bytes) + pps
     */
    fun createFormatChangeEvent(sps: ByteArray, pps: ByteArray): ByteArray {
        val totalLen = 1 + 4 + sps.size + 4 + pps.size
        val buf = ByteArray(totalLen)
        var pos = 0
        buf[pos++] = EVENT_TYPE_FORMAT_CHANGE.toByte()

        writeBe32(buf, pos, sps.size)
        pos += 4
        System.arraycopy(sps, 0, buf, pos, sps.size)
        pos += sps.size

        writeBe32(buf, pos, pps.size)
        pos += 4
        System.arraycopy(pps, 0, buf, pos, pps.size)

        return buf
    }

    /**
     * Parses format change packet into Pair(sps, pps).
     */
    fun parseFormatChangeEvent(bytes: ByteArray): Pair<ByteArray, ByteArray>? {
        if (bytes.size < 9) return null
        if (bytes[0].toInt() and 0xFF != EVENT_TYPE_FORMAT_CHANGE) return null

        var pos = 1
        val spsLen = readBe32(bytes, pos)
        pos += 4
        if (spsLen < 0 || spsLen > bytes.size - pos) return null
        val sps = ByteArray(spsLen)
        System.arraycopy(bytes, pos, sps, 0, spsLen)
        pos += spsLen

        if (pos + 4 > bytes.size) return null
        val ppsLen = readBe32(bytes, pos)
        pos += 4
        if (ppsLen < 0 || ppsLen > bytes.size - pos) return null
        val pps = ByteArray(ppsLen)
        System.arraycopy(bytes, pos, pps, 0, ppsLen)

        return Pair(sps, pps)
    }

    /**
     * Builds the UTF-8 text event packet:
     * type (1 byte: 0x06) + length (4 bytes BE) + UTF-8 bytes
     */
    fun createTextEvent(text: String): ByteArray {
        val utf8Bytes = text.toByteArray(Charsets.UTF_8)
        val buf = ByteArray(1 + 4 + utf8Bytes.size)
        buf[0] = EVENT_TYPE_TEXT.toByte()
        writeBe32(buf, 1, utf8Bytes.size)
        System.arraycopy(utf8Bytes, 0, buf, 5, utf8Bytes.size)
        return buf
    }

    /**
     * Parses a UTF-8 text event packet into a String.
     */
    fun parseTextEvent(bytes: ByteArray): String? {
        if (bytes.size < 5) return null
        if (bytes[0].toInt() and 0xFF != EVENT_TYPE_TEXT) return null
        val len = readBe32(bytes, 1)
        if (len < 0 || len > 4096 || len > bytes.size - 5) return null
        return String(bytes, 5, len, Charsets.UTF_8)
    }

    private fun writeBe32(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value ushr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }

    private fun readBe32(buf: ByteArray, offset: Int): Int {
        return ((buf[offset].toInt() and 0xFF) shl 24) or
                ((buf[offset + 1].toInt() and 0xFF) shl 16) or
                ((buf[offset + 2].toInt() and 0xFF) shl 8) or
                (buf[offset + 3].toInt() and 0xFF)
    }
}
