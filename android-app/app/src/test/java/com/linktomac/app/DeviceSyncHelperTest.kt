package com.linktomac.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSyncHelperTest {

    @Test
    fun testCreateDevicePayload_containsDualIpFields() {
        val testIp = "192.168.1.150"
        val payload = DeviceSyncHelper.createDevicePayload(testIp, "Pixel 8", 123456789L)

        assertTrue(payload.containsKey("ip"))
        assertTrue(payload.containsKey("localIp"))
        assertEquals(testIp, payload["ip"])
        assertEquals(testIp, payload["localIp"])
    }

    @Test
    fun testCreateDevicePayload_containsDualTimestamps() {
        val testTime = 1718000000000L
        val payload = DeviceSyncHelper.createDevicePayload("192.168.1.10", "Galaxy S24", testTime)

        assertTrue(payload.containsKey("lastSeen"))
        assertTrue(payload.containsKey("timestamp"))
        assertEquals(testTime, payload["lastSeen"])
        assertEquals(testTime, payload["timestamp"])
    }

    @Test
    fun testCreateDevicePayload_containsDeviceName() {
        val payload = DeviceSyncHelper.createDevicePayload("10.0.0.5", "OnePlus 12", 100L)
        assertEquals("OnePlus 12", payload["deviceName"])
    }

    @Test
    fun testCreateDevicePayload_handlesBlankDeviceNameWithFallback() {
        val payloadBlank = DeviceSyncHelper.createDevicePayload("10.0.0.5", "", 100L)
        assertEquals(DeviceSyncHelper.DEFAULT_DEVICE_NAME, payloadBlank["deviceName"])

        val payloadSpaces = DeviceSyncHelper.createDevicePayload("10.0.0.5", "   ", 100L)
        assertEquals(DeviceSyncHelper.DEFAULT_DEVICE_NAME, payloadSpaces["deviceName"])
    }

    @Test
    fun testIsInterfaceExcluded_filtersVirtualAndCellular() {
        assertTrue(DeviceSyncHelper.isInterfaceExcluded("rmnet0"))
        assertTrue(DeviceSyncHelper.isInterfaceExcluded("rmnet_data0"))
        assertTrue(DeviceSyncHelper.isInterfaceExcluded("tun0"))
        assertTrue(DeviceSyncHelper.isInterfaceExcluded("p2p-wlan0-0"))
        assertTrue(DeviceSyncHelper.isInterfaceExcluded("dummy0"))
    }

    @Test
    fun testIsInterfaceExcluded_allowsPhysicalInterfaces() {
        assertFalse(DeviceSyncHelper.isInterfaceExcluded("wlan0"))
        assertFalse(DeviceSyncHelper.isInterfaceExcluded("wlan1"))
        assertFalse(DeviceSyncHelper.isInterfaceExcluded("eth0"))
        assertFalse(DeviceSyncHelper.isInterfaceExcluded("en0"))
    }

    @Test
    fun testGetInterfacePriority_prioritizesWlanOverEthernetAndOthers() {
        assertEquals(1, DeviceSyncHelper.getInterfacePriority("wlan0"))
        assertEquals(1, DeviceSyncHelper.getInterfacePriority("WLAN0"))
        assertEquals(2, DeviceSyncHelper.getInterfacePriority("eth0"))
        assertEquals(2, DeviceSyncHelper.getInterfacePriority("eth1"))
        assertEquals(3, DeviceSyncHelper.getInterfacePriority("en0"))
        assertEquals(-1, DeviceSyncHelper.getInterfacePriority("rmnet0"))
        assertEquals(-1, DeviceSyncHelper.getInterfacePriority("tun0"))
    }

    @Test
    fun testFindBestIpAddress_emptyInterfacesReturnsNull() {
        val result = DeviceSyncHelper.findBestIpAddress(emptyList())
        assertNull(result)
    }
}
