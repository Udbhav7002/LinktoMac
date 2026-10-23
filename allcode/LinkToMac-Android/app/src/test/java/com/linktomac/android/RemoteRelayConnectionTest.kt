package com.linktomac.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unit test suite verifying Remote Relay (WAN) parsing, wire-framing,
 * transport attribution, and status display.
 */
class RemoteRelayConnectionTest {

    private fun sanitizeHostAndPort(inputHost: String, fallbackPort: Int): Pair<String, Int> {
        var rawHost = inputHost.trim().removePrefix("tcp://").removePrefix("http://").removePrefix("https://")
        var p = fallbackPort
        if (rawHost.contains(":")) {
            val parts = rawHost.split(":")
            rawHost = parts[0]
            parts[1].toIntOrNull()?.let { p = it }
        }
        return Pair(rawHost, p)
    }

    private fun resolveTransport(host: String, mode: String): String {
        val isLocal = mode != "remote" && (
            host == "127.0.0.1" || host == "localhost" ||
            host.startsWith("10.") || host.startsWith("192.168.") ||
            host.startsWith("172.") || host.endsWith(".local")
        )
        return when {
            host == "127.0.0.1" -> "USB"
            mode == "remote" -> "Remote Relay"
            isLocal -> "Local Wi-Fi"
            else -> "Remote Relay"
        }
    }

    private fun friendlyStatus(raw: String): String = when {
        raw.startsWith("Connection failed") || raw.contains("ECONNREFUSED") ->
            "Couldn't reach your Mac. Keep LinkToMac open on the Mac, then try USB or QR again."
        raw.startsWith("Connected") -> when {
            raw.contains("USB") -> "Connected over USB"
            raw.contains("Remote Relay") -> "Connected over Remote Relay"
            else -> "Connected over Wi-Fi"
        }
        raw.startsWith("Connecting to") -> "Connecting to your Mac…"
        raw.startsWith("Reconnecting to") -> "Reconnecting…"
        raw.startsWith("Waiting for Mac") -> "Waiting for your Mac…"
        raw == "Disconnected" -> "Not connected"
        else -> raw
    }

    @Test
    fun testQrRemoteRelayHostAndPortSanitization() {
        val (h1, p1) = sanitizeHostAndPort("tcp://a.pinggy.io:44321", 54921)
        assertEquals("a.pinggy.io", h1)
        assertEquals(44321, p1)

        val (h2, p2) = sanitizeHostAndPort("ekxys-103-4-221-29.run.pinggy-free.link", 45935)
        assertEquals("ekxys-103-4-221-29.run.pinggy-free.link", h2)
        assertEquals(45935, p2)

        val (h3, p3) = sanitizeHostAndPort("http://192.168.1.15:54921", 54921)
        assertEquals("192.168.1.15", h3)
        assertEquals(54921, p3)
    }

    @Test
    fun testManualEndpointInputParsing() {
        val testCases = listOf(
            "tcp://free.pinggy.link:45935" to Pair("free.pinggy.link", 45935),
            "192.168.1.100:54921" to Pair("192.168.1.100", 54921),
            "127.0.0.1" to Pair("127.0.0.1", 54921),
            "ekxys-103-4-221-29.run.pinggy-free.link:38291" to Pair("ekxys-103-4-221-29.run.pinggy-free.link", 38291)
        )

        for ((input, expected) in testCases) {
            val (h, p) = sanitizeHostAndPort(input, 54921)
            assertEquals(expected.first, h)
            assertEquals(expected.second, p)
        }
    }

    @Test
    fun testTransportAttributionLogic() {
        assertEquals("USB", resolveTransport("127.0.0.1", ""))
        assertEquals("Remote Relay", resolveTransport("a.pinggy.io", "remote"))
        assertEquals("Remote Relay", resolveTransport("ekxys-103-4-221-29.run.pinggy-free.link", ""))
        assertEquals("Local Wi-Fi", resolveTransport("192.168.0.15", ""))
        assertEquals("Local Wi-Fi", resolveTransport("10.0.0.42", ""))
        assertEquals("Local Wi-Fi", resolveTransport("macbook.local", ""))
        // When mode is remote, even a local-looking IP is forced to Remote Relay
        assertEquals("Remote Relay", resolveTransport("192.168.1.5", "remote"))
    }

    @Test
    fun testWireProtocolPackFrameIntegrity() {
        val samplePayload = """{"type":"HELLO","payload":{"transport":"Remote Relay","pairToken":"tok_abc"}}""".toByteArray(Charsets.UTF_8)
        val packed = WireProtocol.packFrame(WireProtocol.FRAME_JSON, samplePayload)

        assertNotNull(packed)
        assertEquals(5 + samplePayload.size, packed.size)

        // Validate 5-byte header: 4-byte BE length + 1-byte opcode
        val bb = ByteBuffer.wrap(packed).order(ByteOrder.BIG_ENDIAN)
        val length = bb.int
        val opcode = bb.get()
        assertEquals(WireProtocol.FRAME_JSON, opcode)
        assertEquals(samplePayload.size, length)

        val payloadExtracted = ByteArray(length)
        bb.get(payloadExtracted)
        val jsonString = String(payloadExtracted, Charsets.UTF_8)
        assertTrue(jsonString.contains("\"transport\":\"Remote Relay\""))
        assertTrue(jsonString.contains("\"pairToken\":\"tok_abc\""))
    }

    @Test
    fun testFriendlyStatusAttribution() {
        assertEquals(
            "Connected over Remote Relay",
            friendlyStatus("Connected (Remote Relay • a.pinggy.io)")
        )
        assertEquals(
            "Connected over USB",
            friendlyStatus("Connected (USB • 127.0.0.1)")
        )
        assertEquals(
            "Connected over Wi-Fi",
            friendlyStatus("Connected (Local Wi-Fi • 192.168.1.10)")
        )
        assertEquals(
            "Not connected",
            friendlyStatus("Disconnected")
        )
    }
}
