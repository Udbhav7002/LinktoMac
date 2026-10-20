package com.linktomac.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StreamProtocolTest {

    @Test
    fun testCreateHandshake_buildsValid20BytePacket() {
        val packet = StreamProtocol.createHandshake(width = 720, height = 1600, flags = 0)
        assertEquals(StreamProtocol.HANDSHAKE_SIZE, packet.size)
    }

    @Test
    fun testParseHandshake_recoversFields() {
        val original = StreamProtocol.createHandshake(width = 1080, height = 1920, flags = 1)
        val parsed = StreamProtocol.parseHandshake(original)

        assertNotNull(parsed)
        assertEquals(StreamProtocol.MAGIC, parsed!!.magic)
        assertEquals(StreamProtocol.VERSION, parsed.version)
        assertEquals(1080, parsed.width)
        assertEquals(1920, parsed.height)
        assertEquals(1, parsed.flags)
    }

    @Test
    fun testParseHandshake_corruptOrTruncatedHandshakeReturnsNull() {
        // Less than 20 bytes
        val truncated = ByteArray(10)
        assertNull(StreamProtocol.parseHandshake(truncated))

        // Wrong magic
        val badMagic = StreamProtocol.createHandshake(720, 1600, 0)
        badMagic[0] = 0x00
        assertNull(StreamProtocol.parseHandshake(badMagic))
    }

    @Test
    fun testSyncRequestConstant_isDefinedAs0x05() {
        assertEquals(0x05, StreamProtocol.EVENT_TYPE_SYNC_REQUEST)
    }

    @Test
    fun testCreateFormatChangeEvent_serializesSpsAndPps() {
        val dummySps = byteArrayOf(0x67.toByte(), 0x42.toByte(), 0x00.toByte(), 0x1f.toByte())
        val dummyPps = byteArrayOf(0x68.toByte(), 0xce.toByte(), 0x3c.toByte(), 0x80.toByte())

        val packet = StreamProtocol.createFormatChangeEvent(dummySps, dummyPps)
        // 1 byte (type) + 4 bytes (sps len) + 4 (sps) + 4 bytes (pps len) + 4 (pps) = 17 bytes
        assertEquals(17, packet.size)
        assertEquals(StreamProtocol.EVENT_TYPE_FORMAT_CHANGE.toByte(), packet[0])
    }

    @Test
    fun testParseFormatChangeEvent_roundtripSuccess() {
        val dummySps = byteArrayOf(0x27.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte())
        val dummyPps = byteArrayOf(0x28.toByte(), 0x05.toByte(), 0x06.toByte())

        val packet = StreamProtocol.createFormatChangeEvent(dummySps, dummyPps)
        val parsed = StreamProtocol.parseFormatChangeEvent(packet)

        assertNotNull(parsed)
        assertArrayEquals(dummySps, parsed!!.first)
        assertArrayEquals(dummyPps, parsed.second)
    }

    @Test
    fun testParseFormatChangeEvent_corruptOrTruncatedReturnsNull() {
        val dummySps = byteArrayOf(0x01.toByte(), 0x02.toByte())
        val dummyPps = byteArrayOf(0x03.toByte(), 0x04.toByte())
        val packet = StreamProtocol.createFormatChangeEvent(dummySps, dummyPps)

        // Truncated packet
        val truncated = packet.copyOf(5)
        assertNull(StreamProtocol.parseFormatChangeEvent(truncated))

        // Wrong type byte
        val badType = packet.clone()
        badType[0] = 0x99.toByte()
        assertNull(StreamProtocol.parseFormatChangeEvent(badType))
    }

    @Test
    fun testCreateAndParseNetworkFeedbackPacket_roundtripSuccess() {
        val loss = 0.06f
        val rtt = 160.0f
        val packet = StreamProtocol.createNetworkFeedbackPacket(loss, rtt)
        val parsed = StreamProtocol.parseNetworkFeedbackPacket(packet)

        assertNotNull(parsed)
        assertEquals(loss, parsed!!.lossRate, 0.0001f)
        assertEquals(rtt, parsed.rttMs, 0.0001f)
    }

    @Test
    fun testParseNetworkFeedbackPacket_truncatedReturnsNull() {
        val packet = ByteArray(5)
        packet[0] = StreamProtocol.EVENT_TYPE_NETWORK_FEEDBACK.toByte()
        assertNull(StreamProtocol.parseNetworkFeedbackPacket(packet))
    }

    @Test
    fun testAbrBitrate_severeLossReducesBy30Percent() {
        val initial = 10_000_000
        val adapted = ScreenEncoder.calculateAdaptedBitrate(initial, lossRate = 0.12, rttMs = 30.0)
        assertEquals(7_000_000, adapted)
    }

    @Test
    fun testAbrBitrate_severeRttReducesBy30Percent() {
        val initial = 10_000_000
        val adapted = ScreenEncoder.calculateAdaptedBitrate(initial, lossRate = 0.0, rttMs = 350.0)
        assertEquals(7_000_000, adapted)
    }

    @Test
    fun testAbrBitrate_highRttReducesBy15Percent() {
        val initial = 10_000_000
        val adapted = ScreenEncoder.calculateAdaptedBitrate(initial, lossRate = 0.0, rttMs = 180.0)
        assertEquals(8_500_000, adapted)
    }

    @Test
    fun testAbrBitrate_moderateLossReducesBy15Percent() {
        val initial = 10_000_000
        val adapted = ScreenEncoder.calculateAdaptedBitrate(initial, lossRate = 0.07, rttMs = 40.0)
        assertEquals(8_500_000, adapted)
    }

    @Test
    fun testAbrBitrate_healthyNetworkProbesUpward10Percent() {
        val initial = 10_000_000
        val adapted = ScreenEncoder.calculateAdaptedBitrate(initial, lossRate = 0.005, rttMs = 30.0)
        assertEquals(11_000_000, adapted)
    }

    @Test
    fun testAbrBitrate_neutralConditionsHoldSteady() {
        val initial = 10_000_000
        val adapted = ScreenEncoder.calculateAdaptedBitrate(initial, lossRate = 0.03, rttMs = 80.0)
        assertEquals(10_000_000, adapted)
    }

    @Test
    fun testAbrBitrate_boundsClamping() {
        val belowMin = ScreenEncoder.calculateAdaptedBitrate(ScreenEncoder.MIN_BITRATE, lossRate = 0.20, rttMs = 500.0)
        assertEquals(ScreenEncoder.MIN_BITRATE, belowMin)

        val aboveMax = ScreenEncoder.calculateAdaptedBitrate(ScreenEncoder.MAX_BITRATE, lossRate = 0.0, rttMs = 20.0)
        assertEquals(ScreenEncoder.MAX_BITRATE, aboveMax)
    }
}
