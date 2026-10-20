package com.linktomac.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class Milestone3AdversarialTest {

    @Before
    fun setUp() {
        ApprovedClientsStore.clear()
    }

    // ------------------------------------------------------------------------
    // 1. Port 27189 Client Authorization Stress & Security Testing
    // ------------------------------------------------------------------------

    @Test
    fun testApprovedClientsStore_concurrencyStress() {
        val threadCount = 16
        val opsPerThread = 500
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until opsPerThread) {
                        val ip = "192.168.$t.$i"
                        ApprovedClientsStore.approveClient(null, ip)
                        if (!ApprovedClientsStore.isAuthorized(ip)) {
                            errorCount.incrementAndGet()
                        }
                        if (i % 2 == 0) {
                            ApprovedClientsStore.revokeClient(null, ip)
                            if (ApprovedClientsStore.isAuthorized(ip)) {
                                errorCount.incrementAndGet()
                            }
                        }
                        // Loopback must ALWAYS remain authorized
                        if (!ApprovedClientsStore.isAuthorized("127.0.0.1") ||
                            !ApprovedClientsStore.isAuthorized("::1") ||
                            !ApprovedClientsStore.isAuthorized("localhost")) {
                            errorCount.incrementAndGet()
                        }
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("Stress test should finish within 10s", latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()
        assertEquals("Zero race conditions or data corruption under concurrent access", 0, errorCount.get())
    }

    @Test
    fun testApprovedClientsStore_comprehensiveLoopbackVariants() {
        val validLoopbacks = listOf(
            "127.0.0.1",
            "/127.0.0.1",
            "127.0.0.2",
            "127.1.2.3",
            "127.255.255.255",
            "::1",
            "/::1",
            "::1%0",
            "::1%lo0",
            "::ffff:127.0.0.1",
            "/::ffff:127.0.0.1",
            "::ffff:127.0.0.1%eth0",
            "localhost",
            "/localhost",
            "  127.0.0.1  ",
            "LOCALHOST"
        )

        for (addr in validLoopbacks) {
            assertTrue("Address '$addr' MUST be auto-authorized as loopback", ApprovedClientsStore.isAuthorized(addr))
        }
    }

    @Test
    fun testApprovedClientsStore_nonLoopbackAndAdversarialInputsRejectedByDefault() {
        val nonLoopbacks = listOf(
            "0.0.0.0",
            "192.168.1.1",
            "10.0.0.1",
            "172.16.0.1",
            "8.8.8.8",
            "::",
            "fe80::1",
            "1270.0.0.1", // Substring mismatch - NOT loopback
            "128.0.0.1",
            "12.7.0.0.1",
            "",
            "   ",
            "unknown",
            "attacker.com",
            "::ffff:192.168.1.50"
        )

        for (addr in nonLoopbacks) {
            assertFalse("Address '$addr' MUST NOT be authorized without approval", ApprovedClientsStore.isAuthorized(addr))
        }
    }

    @Test
    fun testApprovedClientsStore_approvalAndRevocationLifecycle() {
        val clientIp = "192.168.1.100"
        assertFalse(ApprovedClientsStore.isAuthorized(clientIp))

        ApprovedClientsStore.approveClient(null, clientIp)
        assertTrue(ApprovedClientsStore.isAuthorized(clientIp))
        assertTrue(ApprovedClientsStore.isAuthorized("/$clientIp"))
        assertTrue(ApprovedClientsStore.isAuthorized("::ffff:$clientIp"))
        assertTrue(ApprovedClientsStore.isAuthorized("$clientIp%wlan0"))

        ApprovedClientsStore.revokeClient(null, clientIp)
        assertFalse(ApprovedClientsStore.isAuthorized(clientIp))

        // Re-approve and test clear()
        ApprovedClientsStore.approveClient(null, clientIp)
        assertTrue(ApprovedClientsStore.isAuthorized(clientIp))
        ApprovedClientsStore.clear(null)
        assertFalse(ApprovedClientsStore.isAuthorized(clientIp))
        // Loopback survives clear
        assertTrue(ApprovedClientsStore.isAuthorized("127.0.0.1"))
    }

    // ------------------------------------------------------------------------
    // 2. Client Authorization Prompt & Timeout Logic Simulation
    // ------------------------------------------------------------------------

    private fun simulateIsClientAuthorized(
        clientIp: String,
        promptHandler: ((String, (Boolean) -> Unit) -> Unit)?,
        timeoutMs: Long
    ): Boolean {
        if (ApprovedClientsStore.isAuthorized(clientIp)) {
            return true
        }
        val handler = promptHandler ?: return false
        val latch = CountDownLatch(1)
        val approved = AtomicBoolean(false)
        try {
            // Simulate UI prompt execution
            Thread {
                try {
                    handler.invoke(clientIp) { isApproved ->
                        if (isApproved) {
                            ApprovedClientsStore.approveClient(null, clientIp)
                            approved.set(true)
                        }
                        latch.countDown()
                    }
                } catch (e: Exception) {
                    latch.countDown()
                }
            }.start()

            val waited = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            return waited && approved.get()
        } catch (_: Exception) {
            return false
        }
    }

    @Test
    fun testClientAuthorization_loopbackAutoApprovedImmediately() {
        var handlerCalled = false
        val handler: (String, (Boolean) -> Unit) -> Unit = { _, _ -> handlerCalled = true }

        val authorized = simulateIsClientAuthorized("127.0.0.1", handler, 1000)
        assertTrue("Loopback must be authorized immediately", authorized)
        assertFalse("Loopback must NEVER trigger user authorization prompt", handlerCalled)
    }

    @Test
    fun testClientAuthorization_nullHandlerRejectsImmediately() {
        val authorized = simulateIsClientAuthorized("192.168.1.88", null, 1000)
        assertFalse("Unapproved client with null prompt handler must be rejected immediately", authorized)
    }

    @Test
    fun testClientAuthorization_userDenialRejectsClient() {
        val handler: (String, (Boolean) -> Unit) -> Unit = { _, callback ->
            callback(false) // User clicked Deny
        }
        val authorized = simulateIsClientAuthorized("192.168.1.88", handler, 1000)
        assertFalse("Client must be rejected when user denies prompt", authorized)
        assertFalse(ApprovedClientsStore.isAuthorized("192.168.1.88"))
    }

    @Test
    fun testClientAuthorization_timeoutRejectsClient() {
        val handler: (String, (Boolean) -> Unit) -> Unit = { _, _ ->
            // Simulate user ignoring prompt (no callback call)
        }
        val start = System.currentTimeMillis()
        val authorized = simulateIsClientAuthorized("192.168.1.88", handler, 100)
        val elapsed = System.currentTimeMillis() - start

        assertFalse("Client must be rejected on authorization timeout", authorized)
        assertTrue("Timeout must take at least specified duration", elapsed >= 90)
        assertFalse(ApprovedClientsStore.isAuthorized("192.168.1.88"))
    }

    @Test
    fun testClientAuthorization_promptThrowsRejectsClientSafely() {
        val handler: (String, (Boolean) -> Unit) -> Unit = { _, _ ->
            throw RuntimeException("Simulated window manager crash")
        }
        val authorized = simulateIsClientAuthorized("192.168.1.88", handler, 1000)
        assertFalse("Client must be rejected if prompt handler throws", authorized)
    }

    @Test
    fun testClientAuthorization_userApprovalPersistsClient() {
        val handler: (String, (Boolean) -> Unit) -> Unit = { _, callback ->
            callback(true) // User clicked Allow
        }
        val authorizedFirst = simulateIsClientAuthorized("192.168.1.88", handler, 1000)
        assertTrue("Client must be approved when user accepts prompt", authorizedFirst)
        assertTrue("Client must now be stored in ApprovedClientsStore", ApprovedClientsStore.isAuthorized("192.168.1.88"))

        // Subsequent connection must not trigger prompt
        var secondPromptTriggered = false
        val secondHandler: (String, (Boolean) -> Unit) -> Unit = { _, _ -> secondPromptTriggered = true }
        val authorizedSecond = simulateIsClientAuthorized("192.168.1.88", secondHandler, 1000)

        assertTrue("Subsequent connection from approved client must succeed immediately", authorizedSecond)
        assertFalse("Subsequent connection must NOT prompt user again", secondPromptTriggered)
    }

    // ------------------------------------------------------------------------
    // 3. ABR Dynamic Bitrate Calculation Matrix & Edge Stress
    // ------------------------------------------------------------------------

    @Test
    fun testAbrBitrate_nanAndInfiniteValuesHandledSafely() {
        val current = 8_000_000
        assertEquals(current, ScreenEncoder.calculateAdaptedBitrate(current, Double.NaN, 50.0))
        assertEquals(current, ScreenEncoder.calculateAdaptedBitrate(current, 0.05, Double.NaN))
        assertEquals(current, ScreenEncoder.calculateAdaptedBitrate(current, Double.NaN, Double.NaN))

        // Infinite RTT triggers severe congestion branch (30% reduction)
        val adaptedInf = ScreenEncoder.calculateAdaptedBitrate(current, 0.0, Double.POSITIVE_INFINITY)
        assertEquals((current * 0.70).toInt(), adaptedInf)
    }

    @Test
    fun testAbrBitrate_decisionBoundaries() {
        val base = 10_000_000

        // Strict boundary: lossRate > 0.10 triggers severe (0.1001 vs 0.1000)
        assertEquals(7_000_000, ScreenEncoder.calculateAdaptedBitrate(base, 0.10001, 20.0))
        assertEquals(8_500_000, ScreenEncoder.calculateAdaptedBitrate(base, 0.10000, 20.0)) // drops into moderate (>0.05)

        // Strict boundary: rttMs > 300.0 triggers severe
        assertEquals(7_000_000, ScreenEncoder.calculateAdaptedBitrate(base, 0.0, 300.1))
        assertEquals(8_500_000, ScreenEncoder.calculateAdaptedBitrate(base, 0.0, 300.0)) // drops into moderate (>150)

        // Strict boundary: lossRate > 0.05 triggers moderate (0.0501 vs 0.0500)
        assertEquals(8_500_000, ScreenEncoder.calculateAdaptedBitrate(base, 0.05001, 20.0))
        assertEquals(10_000_000, ScreenEncoder.calculateAdaptedBitrate(base, 0.05000, 70.0)) // neutral band

        // Strict boundary: rttMs > 150.0 triggers moderate
        assertEquals(8_500_000, ScreenEncoder.calculateAdaptedBitrate(base, 0.0, 150.1))
        assertEquals(10_000_000, ScreenEncoder.calculateAdaptedBitrate(base, 0.0, 150.0)) // neutral band

        // Upward probe: BOTH lossRate < 0.01 AND rttMs < 50.0
        assertEquals(11_000_000, ScreenEncoder.calculateAdaptedBitrate(base, 0.009, 49.9))
        assertEquals(10_000_000, ScreenEncoder.calculateAdaptedBitrate(base, 0.010, 40.0)) // neutral (loss not < 0.01)
        assertEquals(10_000_000, ScreenEncoder.calculateAdaptedBitrate(base, 0.005, 50.0)) // neutral (rtt not < 50.0)
    }

    @Test
    fun testAbrBitrate_minAndMaxBitrateClamping() {
        // Severe congestion at minimum bitrate does not decrease below MIN_BITRATE
        val atMin = ScreenEncoder.calculateAdaptedBitrate(ScreenEncoder.MIN_BITRATE, 0.50, 800.0)
        assertEquals(ScreenEncoder.MIN_BITRATE, atMin)

        // Upward probe at maximum bitrate does not increase above MAX_BITRATE
        val atMax = ScreenEncoder.calculateAdaptedBitrate(ScreenEncoder.MAX_BITRATE, 0.0, 10.0)
        assertEquals(ScreenEncoder.MAX_BITRATE, atMax)

        // Very small initial bitrate clamps up to MIN_BITRATE
        val small = ScreenEncoder.calculateAdaptedBitrate(500_000, 0.20, 400.0)
        assertEquals(ScreenEncoder.MIN_BITRATE, small)
    }

    @Test
    fun testAbrBitrate_concurrentDeterministicCalculations() {
        val threads = 16
        val latch = CountDownLatch(threads)
        val executor = Executors.newFixedThreadPool(threads)
        val errors = AtomicInteger(0)

        for (t in 0 until threads) {
            executor.submit {
                try {
                    for (i in 0 until 1000) {
                        val res = ScreenEncoder.calculateAdaptedBitrate(8_000_000, 0.06, 160.0)
                        if (res != 6_800_000) {
                            errors.incrementAndGet()
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()
        assertEquals(0, errors.get())
    }

    // ------------------------------------------------------------------------
    // 4. Socket Probe Teardown & Sync Integrity
    // ------------------------------------------------------------------------

    @Test
    fun testSocketServer_handlesRepeatedProbeBurstsWithoutCrashing() {
        val server = ServerSocket(0)
        val port = server.localPort
        val probeCount = 20
        val probesConnected = AtomicInteger(0)
        val serverRunning = AtomicBoolean(true)

        val serverThread = Thread {
            while (serverRunning.get()) {
                val client = try {
                    server.accept()
                } catch (e: Exception) {
                    break
                }
                probesConnected.incrementAndGet()
                try {
                    val out = client.getOutputStream()
                    val handshake = StreamProtocol.createHandshake(720, 1600, 0)
                    out.write(handshake)
                    out.flush()
                } catch (_: Exception) {}
                try { client.close() } catch (_: Exception) {}
            }
        }
        serverThread.start()

        // Rapid burst of ephemeral probes
        for (i in 0 until probeCount) {
            val s = Socket()
            s.connect(InetSocketAddress("127.0.0.1", port), 1000)
            s.close()
        }

        Thread.sleep(100)
        serverRunning.set(false)
        server.close()
        serverThread.join(2000)

        assertTrue("Server should have accepted all probes", probesConnected.get() >= probeCount)
    }
}
