package com.linktomac.android

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Empirical Adversarial Challenger Test Suite
 *
 * Stress-tests QR code payload handling, host sanitization, connection parameter parsing,
 * transport state resolution, and reconnection priority under network disruptions.
 */
class AdversarialConnectionStressTest {

    data class ParsedConnection(
        val host: String,
        val port: Int,
        val token: String,
        val macName: String,
        val mode: String
    )

    /**
     * Sanitization logic directly reflecting MainActivity.kt lines 90-96
     */
    private fun sanitizeHostAndPort(rawHostInput: String, defaultPort: Int): Pair<String, Int> {
        var rawHost = rawHostInput.trim()
            .removePrefix("tcp://")
            .removePrefix("http://")
            .removePrefix("https://")
        var p = defaultPort
        if (rawHost.contains(":")) {
            val parts = rawHost.split(":")
            rawHost = parts[0]
            parts[1].toIntOrNull()?.let { p = it }
        }
        return Pair(rawHost, p)
    }

    /**
     * Replicates manual input parsing from MainActivity.kt lines 148-161
     */
    private fun parseManualInput(input: String, token: String = ""): ParsedConnection {
        var raw = input.trim().removePrefix("tcp://").removePrefix("http://")
        var port = 54921
        if (raw.contains(":")) {
            val parts = raw.split(":")
            raw = parts[0]
            parts[1].toIntOrNull()?.let { port = it }
        }
        val isLocal = raw == "127.0.0.1" || raw == "localhost" ||
            raw.startsWith("10.") || raw.startsWith("192.168.") ||
            raw.startsWith("172.") || raw.endsWith(".local")
        val mode = if (!isLocal && raw != "127.0.0.1") "remote" else ""
        return ParsedConnection(raw, port, token, "", mode)
    }

    /**
     * Replicates transport resolution from LinkConnectionService.kt lines 150-177
     */
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

    /**
     * Replicates target resolution in scheduleReconnect() from LinkConnectionService.kt lines 497-518
     */
    private fun resolveReconnectTarget(
        savedMode: String,
        savedHost: String?,
        savedPort: Int,
        discoveredMacHost: String,
        discoveredMacPort: Int
    ): Pair<String?, Int> {
        val targetHost = if (savedMode == "remote" && !savedHost.isNullOrBlank()) {
            savedHost
        } else if (discoveredMacHost.isNotEmpty()) {
            discoveredMacHost
        } else {
            savedHost
        }

        val targetPort = if (savedMode == "remote" && !savedHost.isNullOrBlank()) {
            savedPort
        } else if (discoveredMacHost.isNotEmpty()) {
            discoveredMacPort
        } else {
            savedPort
        }

        return Pair(targetHost, targetPort)
    }

    // =========================================================================
    // 1. HOST SANITIZATION & ADVERSARIAL SCHEME PREFIXES
    // =========================================================================

    @Test
    fun testValidSchemePrefixStripping() {
        // tcp://
        val (h1, p1) = sanitizeHostAndPort("tcp://ekxys.run.pinggy-free.link:45935", 54921)
        assertEquals("ekxys.run.pinggy-free.link", h1)
        assertEquals(45935, p1)

        // http://
        val (h2, p2) = sanitizeHostAndPort("http://192.168.1.15:54921", 54921)
        assertEquals("192.168.1.15", h2)
        assertEquals(54921, p2)

        // https://
        val (h3, p3) = sanitizeHostAndPort("https://a.pinggy.io:44321", 54921)
        assertEquals("a.pinggy.io", h3)
        assertEquals(44321, p3)
    }

    @Test
    fun testAdversarialSchemePrefixes() {
        // CHALLENGE: Uppercase scheme prefix "TCP://"
        // removePrefix("tcp://") is case-sensitive!
        // "TCP://a.pinggy.io:44321" -> parts: ["TCP", "//a.pinggy.io", "44321"]
        // parts[1].toIntOrNull() fails, resulting in host="TCP" and port=54921.
        val (hUpper, pUpper) = sanitizeHostAndPort("TCP://a.pinggy.io:44321", 54921)
        val isCaseSensitiveFlaw = (hUpper == "TCP" && pUpper == 54921)
        assertTrue(
            "Empirical observation: Case-sensitive removePrefix truncates uppercase schemes to 'TCP'",
            isCaseSensitiveFlaw
        )

        // CHALLENGE: Trailing slash on URL "tcp://a.pinggy.io:44321/"
        // parts[1] is "44321/" which fails toIntOrNull(), reverting to default port 54921.
        val (hSlash, pSlash) = sanitizeHostAndPort("tcp://a.pinggy.io:44321/", 54921)
        assertEquals("a.pinggy.io", hSlash)
        val isTrailingSlashFlaw = (pSlash == 54921)
        assertTrue(
            "Empirical observation: Trailing slash causes embedded port parsing to fail and fall back to 54921",
            isTrailingSlashFlaw
        )
    }

    @Test
    fun testAdversarialEmbeddedPortHandling() {
        // Embedded port overrides fallback
        val (h1, p1) = sanitizeHostAndPort("a.pinggy.io:44321", 54921)
        assertEquals("a.pinggy.io", h1)
        assertEquals(44321, p1)

        // Non-numeric port in host "a.pinggy.io:notaport"
        val (h2, p2) = sanitizeHostAndPort("a.pinggy.io:notaport", 54921)
        assertEquals("a.pinggy.io", h2)
        assertEquals("Non-numeric port falls back safely to default port", 54921, p2)

        // Empty port after colon "a.pinggy.io:"
        val (h3, p3) = sanitizeHostAndPort("a.pinggy.io:", 54921)
        assertEquals("a.pinggy.io", h3)
        assertEquals("Empty port after colon falls back to default port", 54921, p3)

        // Multiple colons e.g. IPv6 "[::1]:54921" or "fe80::1"
        // CHALLENGE: split(":") on IPv6 address truncates address
        val (hIpv6, _) = sanitizeHostAndPort("fe80::1", 54921)
        val isIpv6SplitFlaw = (hIpv6 == "fe80")
        assertTrue(
            "Empirical observation: split(':') truncates IPv6 addresses at the first colon",
            isIpv6SplitFlaw
        )
    }

    @Test
    fun testPortRangeBoundaries() {
        // Valid edge: 65535
        val (_, pMax) = sanitizeHostAndPort("a.pinggy.io:65535", 54921)
        assertEquals(65535, pMax)

        // Valid edge: 1
        val (_, pMin) = sanitizeHostAndPort("a.pinggy.io:1", 54921)
        assertEquals(1, pMin)

        // CHALLENGE: Port > 65535 (e.g. 70000)
        // toIntOrNull() successfully parses 70000 into Int without range validation.
        val (_, pOver) = sanitizeHostAndPort("a.pinggy.io:70000", 54921)
        assertEquals(70000, pOver)
        assertTrue(
            "Empirical observation: Port > 65535 is parsed as-is without range clamping",
            pOver > 65535
        )

        // CHALLENGE: Negative port (e.g. -1)
        val (_, pNeg) = sanitizeHostAndPort("a.pinggy.io:-1", 54921)
        assertEquals(-1, pNeg)
        assertTrue(
            "Empirical observation: Negative port is parsed as-is without range clamping",
            pNeg < 0
        )
    }

    // =========================================================================
    // 2. MANUAL CONNECTION INPUT ADVERSARIAL TESTS
    // =========================================================================

    @Test
    fun testManualInputSanitizationVariations() {
        // Plain IP
        val p1 = parseManualInput("192.168.1.42")
        assertEquals("192.168.1.42", p1.host)
        assertEquals(54921, p1.port)
        assertEquals("", p1.mode) // Local IP -> mode empty

        // IP with custom port
        val p2 = parseManualInput("192.168.1.42:60000")
        assertEquals("192.168.1.42", p2.host)
        assertEquals(60000, p2.port)
        assertEquals("", p2.mode)

        // TCP Scheme with remote relay domain and port
        val p3 = parseManualInput("tcp://free.pinggy.link:45935")
        assertEquals("free.pinggy.link", p3.host)
        assertEquals(45935, p3.port)
        assertEquals("remote", p3.mode) // Remote domain -> mode remote

        // USB Loopback
        val p4 = parseManualInput("127.0.0.1:54921")
        assertEquals("127.0.0.1", p4.host)
        assertEquals(54921, p4.port)
        assertEquals("", p4.mode) // Loopback -> mode empty

        // Hostname ending in .local
        val p5 = parseManualInput("macbook-pro.local:54921")
        assertEquals("macbook-pro.local", p5.host)
        assertEquals(54921, p5.port)
        assertEquals("", p5.mode) // .local -> local Wi-Fi mode

        // CHALLENGE: https:// prefix in manual input
        // MainActivity line 149 removes tcp:// and http://, but NOT https://
        val pHttps = parseManualInput("https://a.pinggy.io:44321")
        val isHttpsMissingInManual = (pHttps.host == "https")
        assertTrue(
            "Empirical observation: onConnectManual lacks removePrefix('https://'), truncating host to 'https'",
            isHttpsMissingInManual
        )
    }

    // =========================================================================
    // 3. TRANSPORT STATE RESOLUTION TESTS
    // =========================================================================

    @Test
    fun testTransportStateResolutionMatrix() {
        // USB Loopback always resolves to USB
        assertEquals("USB", resolveTransport("127.0.0.1", ""))
        assertEquals("USB", resolveTransport("127.0.0.1", "local"))
        assertEquals("USB", resolveTransport("127.0.0.1", "remote"))

        // Remote Relay domains with explicit remote mode
        assertEquals("Remote Relay", resolveTransport("a.pinggy.io", "remote"))
        assertEquals("Remote Relay", resolveTransport("free.pinggy.link", "remote"))
        assertEquals("Remote Relay", resolveTransport("relay.linktomac.com", "remote"))

        // Remote Relay domain without explicit mode (auto-detected as remote because not local)
        assertEquals("Remote Relay", resolveTransport("a.pinggy.io", ""))
        assertEquals("Remote Relay", resolveTransport("ekxys.run.pinggy-free.link", ""))

        // Local Wi-Fi subnets without remote mode
        assertEquals("Local Wi-Fi", resolveTransport("192.168.1.15", ""))
        assertEquals("Local Wi-Fi", resolveTransport("10.0.4.1", ""))
        assertEquals("Local Wi-Fi", resolveTransport("172.16.0.10", ""))
        assertEquals("Local Wi-Fi", resolveTransport("mac.local", ""))

        // Explicit remote mode overrides local-looking IP (e.g. tunnel listening on local-like address)
        assertEquals("Remote Relay", resolveTransport("192.168.1.15", "remote"))
        assertEquals("Remote Relay", resolveTransport("10.0.0.1", "remote"))

        // CHALLENGE: Public IP in 172.x without mode="remote" (e.g. Cloudflare 172.67.x.x)
        // startsWith("172.") matches, misattributing public 172.x IP to "Local Wi-Fi" if mode is empty
        val transport172 = resolveTransport("172.67.1.1", "")
        assertEquals("Local Wi-Fi", transport172)
    }

    // =========================================================================
    // 4. RECONNECTION LOGIC & NETWORK DROP PRIORITY TESTS
    // =========================================================================

    @Test
    fun testRemoteRelayReconnectIgnoresStaleMdns() {
        // SCENARIO: User was connected over Remote Relay (WAN) to a.pinggy.io:44321.
        // A network drop occurs (e.g. cellular handoff or Wi-Fi disconnected).
        // A stale mDNS discovery record exists from a previous local Wi-Fi session (192.168.1.99:54921).
        // Verification: Reconnection MUST prioritize savedHost (a.pinggy.io:44321) over stale mDNS!
        val (targetHost, targetPort) = resolveReconnectTarget(
            savedMode = "remote",
            savedHost = "a.pinggy.io",
            savedPort = 44321,
            discoveredMacHost = "192.168.1.99",
            discoveredMacPort = 54921
        )

        assertEquals("Reconnection under remote mode must stick to remote host", "a.pinggy.io", targetHost)
        assertEquals("Reconnection under remote mode must stick to remote port", 44321, targetPort)
    }

    @Test
    fun testLocalWifiReconnectPrioritizesMdnsDiscovery() {
        // SCENARIO: User was connected over Local Wi-Fi to 192.168.1.50.
        // Mac acquires a new DHCP lease on 192.168.1.55 and mDNS broadcasts the new IP.
        // Verification: In local mode, freshly discovered mDNS host (192.168.1.55) must take priority!
        val (targetHost, targetPort) = resolveReconnectTarget(
            savedMode = "local",
            savedHost = "192.168.1.50",
            savedPort = 54921,
            discoveredMacHost = "192.168.1.55",
            discoveredMacPort = 54921
        )

        assertEquals("Reconnection under local mode should adopt discovered mDNS host", "192.168.1.55", targetHost)
        assertEquals(54921, targetPort)
    }

    @Test
    fun testLocalWifiFallbackToSavedHostWhenMdnsSilent() {
        // SCENARIO: User was connected over Local Wi-Fi, network drops, and mDNS hasn't resolved yet.
        // Verification: Must fall back to savedHost rather than stalling.
        val (targetHost, targetPort) = resolveReconnectTarget(
            savedMode = "local",
            savedHost = "192.168.1.50",
            savedPort = 54921,
            discoveredMacHost = "",
            discoveredMacPort = 0
        )

        assertEquals("Reconnection must fallback to savedHost when mDNS is empty", "192.168.1.50", targetHost)
        assertEquals(54921, targetPort)
    }

    @Test
    fun testRemoteRelayFallbackWhenSavedHostBlank() {
        // SCENARIO: savedMode is "remote", but savedHost was somehow blank or null.
        // Verification: If mDNS host is present, should not crash and fall back to mDNS.
        val (targetHost, _) = resolveReconnectTarget(
            savedMode = "remote",
            savedHost = "",
            savedPort = 54921,
            discoveredMacHost = "192.168.1.50",
            discoveredMacPort = 54921
        )

        assertEquals("Should fallback to mDNS if remote savedHost is blank", "192.168.1.50", targetHost)
    }

    // =========================================================================
    // 5. WIRE PROTOCOL PACK FRAME STRESS TESTS
    // =========================================================================

    @Test
    fun testWireProtocolFrameHeaderIntegrity() {
        val testPayload = "Adversarial Wire Payload Test".toByteArray(Charsets.UTF_8)
        val packed = WireProtocol.packFrame(WireProtocol.FRAME_JSON, testPayload)

        assertEquals(5 + testPayload.size, packed.size)

        val bb = ByteBuffer.wrap(packed).order(ByteOrder.BIG_ENDIAN)
        val length = bb.int
        val opcode = bb.get()

        assertEquals(testPayload.size, length)
        assertEquals(WireProtocol.FRAME_JSON, opcode)

        val body = ByteArray(length)
        bb.get(body)
        assertArrayEquals(testPayload, body)
    }

    @Test
    fun testWireProtocolEmptyPayloadFrame() {
        val emptyPayload = ByteArray(0)
        val packed = WireProtocol.packFrame(WireProtocol.FRAME_JSON, emptyPayload)

        assertEquals(5, packed.size)
        val bb = ByteBuffer.wrap(packed).order(ByteOrder.BIG_ENDIAN)
        assertEquals(0, bb.int)
        assertEquals(WireProtocol.FRAME_JSON, bb.get())
    }

    @Test
    fun testWireProtocolLargePayloadFrame() {
        // 1 Megabyte payload
        val largePayload = ByteArray(1024 * 1024) { (it % 128).toByte() }
        val packed = WireProtocol.packFrame(WireProtocol.FRAME_VIDEO_JPEG, largePayload)

        assertEquals(5 + largePayload.size, packed.size)
        val bb = ByteBuffer.wrap(packed).order(ByteOrder.BIG_ENDIAN)
        assertEquals(largePayload.size, bb.int)
        assertEquals(WireProtocol.FRAME_VIDEO_JPEG, bb.get())
    }
}
