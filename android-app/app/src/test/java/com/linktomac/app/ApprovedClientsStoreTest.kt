package com.linktomac.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApprovedClientsStoreTest {

    @Before
    fun setUp() {
        ApprovedClientsStore.clear()
    }

    @Test
    fun testLoopbackAddressesAutoAuthorized() {
        assertTrue("IPv4 loopback should be auto-authorized", ApprovedClientsStore.isAuthorized("127.0.0.1"))
        assertTrue("IPv4 loopback with slash should be auto-authorized", ApprovedClientsStore.isAuthorized("/127.0.0.1"))
        assertTrue("IPv6 loopback should be auto-authorized", ApprovedClientsStore.isAuthorized("::1"))
        assertTrue("IPv6 loopback with zone id should be auto-authorized", ApprovedClientsStore.isAuthorized("::1%0"))
        assertTrue("IPv4-mapped IPv6 loopback should be auto-authorized", ApprovedClientsStore.isAuthorized("::ffff:127.0.0.1"))
        assertTrue("Localhost should be auto-authorized", ApprovedClientsStore.isAuthorized("localhost"))
    }

    @Test
    fun testWifiClientsRequireExplicitAuthorization() {
        val wifiIp = "192.168.1.105"
        assertFalse("Unapproved Wi-Fi client should not be authorized", ApprovedClientsStore.isAuthorized(wifiIp))
        assertFalse("Unapproved Wi-Fi client (IPv6 mapped) should not be authorized", ApprovedClientsStore.isAuthorized("::ffff:$wifiIp"))

        ApprovedClientsStore.approveClient(null, wifiIp)
        assertTrue("Approved Wi-Fi client should now be authorized", ApprovedClientsStore.isAuthorized(wifiIp))
        assertTrue("Approved Wi-Fi client (IPv6 mapped) should also be recognized", ApprovedClientsStore.isAuthorized("::ffff:$wifiIp"))
        assertTrue("Approved Wi-Fi client with zone index should also be recognized", ApprovedClientsStore.isAuthorized("$wifiIp%wlan0"))

        ApprovedClientsStore.revokeClient(null, wifiIp)
        assertFalse("Revoked Wi-Fi client should no longer be authorized", ApprovedClientsStore.isAuthorized(wifiIp))
    }

    @Test
    fun testMultipleApprovedClients() {
        ApprovedClientsStore.approveClient(null, "192.168.1.10")
        ApprovedClientsStore.approveClient(null, "192.168.1.20")

        assertTrue(ApprovedClientsStore.isAuthorized("192.168.1.10"))
        assertTrue(ApprovedClientsStore.isAuthorized("192.168.1.20"))
        assertFalse(ApprovedClientsStore.isAuthorized("192.168.1.30"))

        ApprovedClientsStore.clear()
        assertFalse(ApprovedClientsStore.isAuthorized("192.168.1.10"))
        assertFalse(ApprovedClientsStore.isAuthorized("192.168.1.20"))
        // Loopback remains authorized even after clear
        assertTrue(ApprovedClientsStore.isAuthorized("127.0.0.1"))
    }
}
