package com.linktomac.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdversarialStressTest {

    @Test
    fun testParseFormatChangeEvent_integerOverflowSafelyReturnsNull() {
        // Construct adversarial packet with 0x7FFFFFFF as spsLen
        val packet = ByteArray(10)
        packet[0] = StreamProtocol.EVENT_TYPE_FORMAT_CHANGE.toByte() // 0x04
        packet[1] = 0x7F.toByte()
        packet[2] = 0xFF.toByte()
        packet[3] = 0xFF.toByte()
        packet[4] = 0xFF.toByte() // spsLen = 2147483647

        var threwOom = false
        val result = try {
            StreamProtocol.parseFormatChangeEvent(packet)
        } catch (e: OutOfMemoryError) {
            threwOom = true
            null
        }
        // Bounds check 'spsLen > bytes.size - pos' prevents signed 32-bit integer overflow,
        // avoiding OutOfMemoryError and safely returning null.
        assertTrue("No OutOfMemoryError should be thrown", !threwOom)
        assertNull("Integer overflow in spsLen must safely return null", result)
    }

    @Test
    fun testParseFormatChangeEvent_ppsIntegerOverflowSafelyReturnsNull() {
        // Construct adversarial packet with valid sps but 0x7FFFFFFF as ppsLen
        val packet = ByteArray(14)
        packet[0] = StreamProtocol.EVENT_TYPE_FORMAT_CHANGE.toByte()
        packet[1] = 0x00.toByte()
        packet[2] = 0x00.toByte()
        packet[3] = 0x00.toByte()
        packet[4] = 0x01.toByte() // spsLen = 1
        packet[5] = 0x67.toByte() // sps payload byte
        packet[6] = 0x7F.toByte()
        packet[7] = 0xFF.toByte()
        packet[8] = 0xFF.toByte()
        packet[9] = 0xFF.toByte() // ppsLen = 2147483647

        var threwOom = false
        val result = try {
            StreamProtocol.parseFormatChangeEvent(packet)
        } catch (e: OutOfMemoryError) {
            threwOom = true
            null
        }
        assertTrue("No OutOfMemoryError should be thrown", !threwOom)
        assertNull("Integer overflow in ppsLen must safely return null", result)
    }

    @Test
    fun testParseFormatChangeEvent_negativeSpsLenReturnsNull() {
        // Construct packet with negative spsLen (0x80000001)
        val packet = ByteArray(10)
        packet[0] = StreamProtocol.EVENT_TYPE_FORMAT_CHANGE.toByte()
        packet[1] = 0x80.toByte()
        packet[2] = 0x00.toByte()
        packet[3] = 0x00.toByte()
        packet[4] = 0x01.toByte()

        val result = StreamProtocol.parseFormatChangeEvent(packet)
        assertNull(result)
    }

    @Test
    fun testParseHandshake_zeroDimensionsHandled() {
        val packet = StreamProtocol.createHandshake(0, 0, 0)
        val parsed = StreamProtocol.parseHandshake(packet)
        assertNotNull(parsed)
        assertEquals(0, parsed!!.width)
        assertEquals(0, parsed.height)
    }

    @Test
    fun testParseHandshake_negativeDimensions() {
        val packet = StreamProtocol.createHandshake(-1, -1, -1)
        val parsed = StreamProtocol.parseHandshake(packet)
        assertNotNull(parsed)
        assertEquals(-1, parsed!!.width)
        assertEquals(-1, parsed.height)
    }

    @Test
    fun testCreateDevicePayload_emptyAndWhitespaceStrings() {
        val payloadEmpty = DeviceSyncHelper.createDevicePayload("", "")
        assertEquals("", payloadEmpty["ip"])
        assertEquals("", payloadEmpty["localIp"])
        assertEquals(DeviceSyncHelper.DEFAULT_DEVICE_NAME, payloadEmpty["deviceName"])

        val payloadSpaces = DeviceSyncHelper.createDevicePayload("   ", "   ")
        assertEquals("   ", payloadSpaces["ip"])
        assertEquals("   ", payloadSpaces["localIp"])
        assertEquals(DeviceSyncHelper.DEFAULT_DEVICE_NAME, payloadSpaces["deviceName"])
    }

    @Test
    fun testCreateDevicePayload_allKeysPresent() {
        val payload = DeviceSyncHelper.createDevicePayload("10.0.0.1", "Test", "2026-10-07")
        val expectedKeys = setOf("ip", "localIp", "deviceName", "lastSeen", "timestamp")
        assertEquals(expectedKeys, payload.keys)
    }

    @Test
    fun testInterfacePriority_caseInsensitivity() {
        assertEquals(1, DeviceSyncHelper.getInterfacePriority("WLAN0"))
        assertEquals(1, DeviceSyncHelper.getInterfacePriority("wLaN1"))
        assertEquals(2, DeviceSyncHelper.getInterfacePriority("ETH0"))
        assertEquals(2, DeviceSyncHelper.getInterfacePriority("Eth1"))
        assertEquals(-1, DeviceSyncHelper.getInterfacePriority("TUN0"))
        assertEquals(-1, DeviceSyncHelper.getInterfacePriority("RMNET_DATA0"))
    }
}
