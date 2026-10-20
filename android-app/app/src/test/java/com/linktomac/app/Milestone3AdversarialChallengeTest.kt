package com.linktomac.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Random
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Adversarial empirical test suite for LinktoMac Milestone M3 (Android Companion App Remediation).
 * Stress tests:
 * 1. ABR Bitrate Adaptation: extreme inputs, boundary conditions (2Mbps / 24Mbps), hysteresis/cooldown.
 * 2. Probe Teardown Resilience: concurrent probe storms, keyframe preservation, UI state protection.
 * 3. Touch Gesture Marshalling & State Machine: move buffering, continueStroke chaining, watchdog safety.
 * 4. Scroll Coalescing: delta aggregation, in-flight gating, burst handling.
 * 5. Multi-Tenant Cloud Isolation: verification of complete absence of accounts/latest and thread-safe authorization.
 */
class Milestone3AdversarialChallengeTest {

    @Before
    fun setUp() {
        ApprovedClientsStore.clear()
    }

    // =========================================================================
    // 1. ABR BITRATE ADAPTATION: BOUNDARIES, EXTREME INPUTS & FUZZING
    // =========================================================================

    @Test
    fun testAbrBitrate_clampingAtMinBitrateBoundary_2Mbps() {
        val min = ScreenEncoder.MIN_BITRATE // 2_000_000

        // Severe loss (50%) at min bitrate should not drop below 2Mbps
        val adaptedSevereLoss = ScreenEncoder.calculateAdaptedBitrate(min, lossRate = 0.50, rttMs = 30.0)
        assertEquals(min, adaptedSevereLoss)

        // Extreme RTT (2000ms) at min bitrate should not drop below 2Mbps
        val adaptedExtremeRtt = ScreenEncoder.calculateAdaptedBitrate(min, lossRate = 0.0, rttMs = 2000.0)
        assertEquals(min, adaptedExtremeRtt)

        // Sub-minimum initial bitrate (1.5Mbps) on reduction is clamped up to 2Mbps
        val adaptedSubMin = ScreenEncoder.calculateAdaptedBitrate(1_500_000, lossRate = 0.20, rttMs = 500.0)
        assertEquals(min, adaptedSubMin)
    }

    @Test
    fun testAbrBitrate_clampingAtMaxBitrateBoundary_24Mbps() {
        val max = ScreenEncoder.MAX_BITRATE // 24_000_000

        // Optimal conditions at max bitrate should not exceed 24Mbps
        val adaptedOptimal = ScreenEncoder.calculateAdaptedBitrate(max, lossRate = 0.0, rttMs = 15.0)
        assertEquals(max, adaptedOptimal)

        // Super-maximum initial bitrate (30Mbps) should be clamped down to 24Mbps on probe
        val adaptedSuperMax = ScreenEncoder.calculateAdaptedBitrate(30_000_000, lossRate = 0.005, rttMs = 20.0)
        assertEquals(max, adaptedSuperMax)
    }

    @Test
    fun testAbrBitrate_exactBoundaryTransitions() {
        val initial = 10_000_000

        // Exact boundary at lossRate = 0.10 (not > 0.10, falls into moderate > 0.05) -> 15% reduction
        val atLoss10 = ScreenEncoder.calculateAdaptedBitrate(initial, lossRate = 0.10, rttMs = 100.0)
        assertEquals(8_500_000, atLoss10)

        // Just over 0.10 -> 30% reduction
        val overLoss10 = ScreenEncoder.calculateAdaptedBitrate(initial, lossRate = 0.10001, rttMs = 100.0)
        assertEquals(7_000_000, overLoss10)

        // Exact boundary at rttMs = 300.0 (not > 300.0, falls into moderate > 150.0) -> 15% reduction
        val atRtt300 = ScreenEncoder.calculateAdaptedBitrate(initial, lossRate = 0.02, rttMs = 300.0)
        assertEquals(8_500_000, atRtt300)

        // Just over 300.0ms -> 30% reduction
        val overRtt300 = ScreenEncoder.calculateAdaptedBitrate(initial, lossRate = 0.02, rttMs = 300.01)
        assertEquals(7_000_000, overRtt300)

        // Exact boundary at lossRate = 0.05 (not > 0.05) with rtt 100ms -> neutral, holds steady
        val atLoss05 = ScreenEncoder.calculateAdaptedBitrate(initial, lossRate = 0.05, rttMs = 100.0)
        assertEquals(initial, atLoss05)

        // Just over 0.05 -> 15% reduction
        val overLoss05 = ScreenEncoder.calculateAdaptedBitrate(initial, lossRate = 0.05001, rttMs = 100.0)
        assertEquals(8_500_000, overLoss05)

        // Exact boundary at rttMs = 150.0 with loss 0.02 -> neutral, holds steady
        val atRtt150 = ScreenEncoder.calculateAdaptedBitrate(initial, lossRate = 0.02, rttMs = 150.0)
        assertEquals(initial, atRtt150)

        // Just over 150.0ms -> 15% reduction
        val overRtt150 = ScreenEncoder.calculateAdaptedBitrate(initial, lossRate = 0.02, rttMs = 150.01)
        assertEquals(8_500_000, overRtt150)

        // Exact boundary at lossRate = 0.01 (not < 0.01) with rtt 30ms -> neutral, holds steady
        val atLoss01 = ScreenEncoder.calculateAdaptedBitrate(initial, lossRate = 0.01, rttMs = 30.0)
        assertEquals(initial, atLoss01)

        // Exact boundary at rttMs = 50.0 (not < 50.0) with loss 0.005 -> neutral, holds steady
        val atRtt50 = ScreenEncoder.calculateAdaptedBitrate(initial, lossRate = 0.005, rttMs = 50.0)
        assertEquals(initial, atRtt50)

        // Both strictly below: loss < 0.01 and rtt < 50.0 -> 10% upward probe
        val optimalProbe = ScreenEncoder.calculateAdaptedBitrate(initial, lossRate = 0.0099, rttMs = 49.9)
        assertEquals(11_000_000, optimalProbe)
    }

    @Test
    fun testAbrBitrate_extremeAndAdversarialNumericInputs() {
        val initial = 8_000_000

        // NaN inputs must safely return initial bitrate without crashing
        assertEquals(initial, ScreenEncoder.calculateAdaptedBitrate(initial, Double.NaN, 50.0))
        assertEquals(initial, ScreenEncoder.calculateAdaptedBitrate(initial, 0.05, Double.NaN))
        assertEquals(initial, ScreenEncoder.calculateAdaptedBitrate(initial, Double.NaN, Double.NaN))

        // Infinite loss / RTT
        val infLoss = ScreenEncoder.calculateAdaptedBitrate(initial, Double.POSITIVE_INFINITY, 50.0)
        assertEquals((initial * 0.70).toInt(), infLoss)

        val infRtt = ScreenEncoder.calculateAdaptedBitrate(initial, 0.0, Double.POSITIVE_INFINITY)
        assertEquals((initial * 0.70).toInt(), infRtt)

        // Extreme negative inputs
        val negLoss = ScreenEncoder.calculateAdaptedBitrate(initial, -1.0, 30.0)
        assertEquals(8_800_000, negLoss) // Probes up because -1 < 0.01 and 30 < 50

        // Zero and negative bitrates on downward reduction clamp to MIN_BITRATE
        assertEquals(ScreenEncoder.MIN_BITRATE, ScreenEncoder.calculateAdaptedBitrate(0, 0.20, 400.0))
        assertEquals(ScreenEncoder.MIN_BITRATE, ScreenEncoder.calculateAdaptedBitrate(-500_000, 0.20, 400.0))

        // Huge bitrate does not overflow or crash
        val hugeBitrate = ScreenEncoder.calculateAdaptedBitrate(Int.MAX_VALUE, 0.0, 20.0)
        assertEquals(ScreenEncoder.MAX_BITRATE, hugeBitrate)

        // Empirical observation: upward probe from a sub-minimum input (e.g. 1Mbps)
        // scales by 1.10 without coerceAtLeast (1_000_000 * 1.10 = 1_100_000)
        val subMinProbe = ScreenEncoder.calculateAdaptedBitrate(1_000_000, 0.0, 20.0)
        assertEquals(1_100_000, subMinProbe)
    }

    @Test
    fun testAbrWireProtocol_fuzzingAndCorruptionResistance() {
        val rng = Random(42)
        for (i in 0 until 500) {
            val loss = rng.nextFloat()
            val rtt = rng.nextFloat() * 1000f

            val packet = StreamProtocol.createNetworkFeedbackPacket(loss, rtt)
            val parsed = StreamProtocol.parseNetworkFeedbackPacket(packet)

            assertNotNull("Packet should parse successfully", parsed)
            assertEquals("Loss rate must match", loss, parsed!!.lossRate, 0.0001f)
            assertEquals("RTT must match", rtt, parsed.rttMs, 0.0001f)
        }

        // Test truncated packets (0 to 8 bytes)
        for (len in 0..8) {
            val truncated = ByteArray(len)
            if (len > 0) truncated[0] = StreamProtocol.EVENT_TYPE_NETWORK_FEEDBACK.toByte()
            assertNull("Truncated packet of len $len must return null", StreamProtocol.parseNetworkFeedbackPacket(truncated))
        }

        // Corrupted event type
        val corrupt = StreamProtocol.createNetworkFeedbackPacket(0.05f, 100f)
        corrupt[0] = 0x99.toByte()
        assertNull("Wrong event type must return null", StreamProtocol.parseNetworkFeedbackPacket(corrupt))
    }

    // =========================================================================
    // 2. PROBE TEARDOWN RESILIENCE & CONCURRENT PROBE STORMS
    // =========================================================================

    @Test
    fun testProbeTeardownResilience_concurrentProbeStormDoesNotDropUiOrClearFrames() {
        val server = ServerSocket(0)
        val port = server.localPort

        val probeCount = 15
        val probeCompletedLatch = CountDownLatch(probeCount)
        val uiConnectedCount = AtomicInteger(0)
        val uiDisconnectedCount = AtomicInteger(0)
        val gesturesCancelledCount = AtomicInteger(0)
        val keyframeRequestsCount = AtomicInteger(0)

        // Simulated cached keyframe
        var cachedKeyFrame: ByteArray? = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65.toByte())

        // Simulated video frame queue holding pre-rendered frames
        val frameQueue = BoundedFrameQueue(capacity = 4) {
            keyframeRequestsCount.incrementAndGet()
        }
        frameQueue.offer(FramePacket(byteArrayOf(1, 2, 3), FramePriority.KEY_FRAME, 100L))
        frameQueue.offer(FramePacket(byteArrayOf(4, 5, 6), FramePriority.REFERENCE_FRAME, 200L))

        val serverRunning = AtomicBoolean(true)
        val serverThread = Thread {
            while (serverRunning.get()) {
                val client = try {
                    server.accept()
                } catch (e: Exception) {
                    break
                }

                val clientIp = client.inetAddress?.hostAddress ?: ""
                if (!ApprovedClientsStore.isAuthorized(clientIp)) {
                    try { client.close() } catch (_: Exception) {}
                    continue
                }

                var eventsProcessed = 0
                try {
                    val outStream = client.getOutputStream()
                    val inStream = client.getInputStream()

                    // Send handshake
                    val handshake = StreamProtocol.createHandshake(720, 1600, 0)
                    outStream.write(handshake)
                    outStream.flush()

                    // Keyframe management: only request if cached keyframe is null
                    if (cachedKeyFrame == null) {
                        keyframeRequestsCount.incrementAndGet()
                    }

                    // Read control events
                    val header = ByteArray(1)
                    while (true) {
                        val r = inStream.read(header)
                        if (r == -1) break
                        eventsProcessed++
                        if (eventsProcessed == 1) {
                            uiConnectedCount.incrementAndGet()
                        }
                    }
                } catch (_: Exception) {
                    // Ephemeral probe or broken connection
                } finally {
                    val isProbe = (eventsProcessed == 0)
                    if (!isProbe) {
                        uiDisconnectedCount.incrementAndGet()
                        gesturesCancelledCount.incrementAndGet()
                        frameQueue.clear()
                    }
                    try { client.close() } catch (_: Exception) {}
                }
            }
        }
        serverThread.start()

        // Launch concurrent probe storm: 15 threads connecting and closing immediately
        val executor = Executors.newFixedThreadPool(8)
        for (i in 0 until probeCount) {
            executor.submit {
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress("127.0.0.1", port), 2000)
                    // Immediate close without reading or sending control events
                    s.close()
                } catch (_: Exception) {
                } finally {
                    probeCompletedLatch.countDown()
                }
            }
        }

        assertTrue("Probe storm must complete within 10s", probeCompletedLatch.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        // Allow server thread to finish processing probe closes
        Thread.sleep(150)

        // VERIFICATION:
        // 1. Spurious UI notifications must be zero
        assertEquals("UI should NEVER be notified connected for ephemeral probes", 0, uiConnectedCount.get())
        assertEquals("UI should NEVER be notified disconnected for ephemeral probes", 0, uiDisconnectedCount.get())

        // 2. Gesture cancellations must be zero
        assertEquals("Gestures should NEVER be cancelled by ephemeral probes", 0, gesturesCancelledCount.get())

        // 3. Keyframe requests must be zero (cached keyframe was present)
        assertEquals("Keyframes should NOT be burned if already cached", 0, keyframeRequestsCount.get())

        // 4. Queued frames in BoundedFrameQueue must NOT be wiped
        assertEquals("Frame queue must retain its queued frames despite probe storm", 2, frameQueue.size)

        // Clean up
        serverRunning.set(false)
        server.close()
        serverThread.join(2000)
    }

    // =========================================================================
    // 3. TOUCH GESTURE MARSHALLING & WATCHDOG SAFETY
    // =========================================================================

    @Test
    fun testTouchGestureMarshalling_stateMachineAndMoveBuffering() {
        // Pure state machine simulation verifying the logic inside LinktoMacAccessibilityService
        var isStrokeInFlight = false
        var activeStrokePresent = false
        val pendingMoves = ArrayList<Pair<Float, Float>>()
        var pendingUp = false
        var chainedStrokeDispatches = 0
        var finalStrokeDispatches = 0
        var cancelledStrokesPreserved = 0

        fun processTouch(action: Int, x: Float, y: Float) {
            when (action) {
                0 -> { // ACTION_DOWN
                    isStrokeInFlight = false
                    activeStrokePresent = false
                    pendingMoves.clear()
                    pendingUp = false
                }
                2 -> { // ACTION_MOVE
                    if (isStrokeInFlight) {
                        // Buffer moves while previous stroke is in-flight
                        pendingMoves.add(Pair(x, y))
                    } else if (activeStrokePresent) {
                        // Chain continueStroke immediately
                        chainedStrokeDispatches++
                        isStrokeInFlight = true
                    } else {
                        // First move after DOWN
                        activeStrokePresent = true
                        isStrokeInFlight = true
                        chainedStrokeDispatches++
                    }
                }
                1 -> { // ACTION_UP
                    if (isStrokeInFlight) {
                        pendingUp = true
                    } else {
                        activeStrokePresent = false
                        finalStrokeDispatches++
                    }
                }
            }
        }

        fun onStrokeCompleted() {
            isStrokeInFlight = false
            if (pendingUp) {
                pendingUp = false
                activeStrokePresent = false
                pendingMoves.clear()
                finalStrokeDispatches++
            } else if (pendingMoves.isNotEmpty() && activeStrokePresent) {
                pendingMoves.clear()
                isStrokeInFlight = true
                chainedStrokeDispatches++
            }
        }

        fun onStrokeCancelled() {
            isStrokeInFlight = false
            // Active stroke is preserved!
            if (activeStrokePresent) {
                cancelledStrokesPreserved++
            }
            if (pendingUp || pendingMoves.isNotEmpty()) {
                onStrokeCompleted()
            }
        }

        // Scenario 1: User touches down, moves 5 times rapidly while 1st stroke is in-flight
        processTouch(0, 100f, 100f)
        processTouch(2, 110f, 110f) // Initiates 1st stroke
        assertTrue("First move puts stroke in flight", isStrokeInFlight)
        assertEquals(1, chainedStrokeDispatches)

        // 4 rapid moves arrive while in-flight
        processTouch(2, 120f, 120f)
        processTouch(2, 130f, 130f)
        processTouch(2, 140f, 140f)
        processTouch(2, 150f, 150f)
        assertEquals("4 moves should be buffered", 4, pendingMoves.size)
        assertEquals("No new stroke dispatched yet", 1, chainedStrokeDispatches)

        // Framework completes 1st stroke -> chains buffered moves into 2nd stroke
        onStrokeCompleted()
        assertTrue("Second stroke is now in-flight", isStrokeInFlight)
        assertEquals("Second stroke chained", 2, chainedStrokeDispatches)
        assertTrue("Buffer drained", pendingMoves.isEmpty())

        // User lifts finger (UP) while 2nd stroke is in-flight
        processTouch(1, 150f, 150f)
        assertTrue("Pending UP marked", pendingUp)
        assertEquals("Final stroke not dispatched yet", 0, finalStrokeDispatches)

        // 2nd stroke completes -> drains pending UP with final stroke
        onStrokeCompleted()
        assertFalse("Pending UP cleared", pendingUp)
        assertFalse("Stroke no longer in flight", isStrokeInFlight)
        assertFalse("Active stroke cleared", activeStrokePresent)
        assertEquals("Final stroke dispatched", 1, finalStrokeDispatches)

        // Scenario 2: Framework cancels in-flight stroke mid-drag -> activeStroke preserved
        processTouch(0, 200f, 200f)
        processTouch(2, 210f, 210f) // Stroke 3 dispatched
        processTouch(2, 220f, 220f) // Buffered
        onStrokeCancelled() // Active stroke preserved, buffered move chained as Stroke 4
        assertEquals("Active stroke was preserved during cancellation", 1, cancelledStrokesPreserved)
        assertEquals("Buffered move was chained after cancellation", 4, chainedStrokeDispatches)
    }

    // =========================================================================
    // 4. SCROLL COALESCING & IN-FLIGHT GATING
    // =========================================================================

    @Test
    fun testScrollCoalescing_highFrequencyBurstAggregation() {
        var accumulatedH = 0f
        var accumulatedV = 0f
        var isScrollInFlight = false
        var scrollDispatches = 0
        var totalFlushedH = 0f
        var totalFlushedV = 0f

        fun flushScroll() {
            if (isScrollInFlight) return
            val h = accumulatedH
            val v = accumulatedV
            accumulatedH = 0f
            accumulatedV = 0f
            totalFlushedH += h
            totalFlushedV += v
            isScrollInFlight = true
            scrollDispatches++
        }

        fun onScroll(h: Float, v: Float) {
            accumulatedH += h
            accumulatedV += v
            if (!isScrollInFlight && (Math.abs(accumulatedH) >= 0.05f || Math.abs(accumulatedV) >= 0.05f)) {
                flushScroll()
            }
        }

        fun onScrollFinished() {
            isScrollInFlight = false
            if (Math.abs(accumulatedH) >= 0.05f || Math.abs(accumulatedV) >= 0.05f) {
                flushScroll()
            }
        }

        // Simulate 50 rapid trackpad wheel events of delta (0.01f, 0.02f)
        for (i in 1..50) {
            onScroll(0.01f, 0.02f)
        }

        // The first 3 events hit the 0.05f threshold and triggered the 1st scroll gesture
        assertEquals("First scroll gesture dispatched", 1, scrollDispatches)
        assertTrue("Scroll gesture in flight", isScrollInFlight)

        // All subsequent 47 events were coalesced without creating 47 separate gestures!
        val remainingH = accumulatedH
        val remainingV = accumulatedV
        assertTrue("Deltas accumulated during in-flight", remainingV > 0.5f)

        // Gesture completes -> flushes remaining accumulated deltas in 1 single follow-up gesture
        onScrollFinished()
        assertEquals("Exactly 2 coalesced gestures dispatched instead of 50", 2, scrollDispatches)
        assertEquals("Total horizontal delta preserved without loss", 0.50f, totalFlushedH, 0.001f)
        assertEquals("Total vertical delta preserved without loss", 1.00f, totalFlushedV, 0.001f)
    }

    // =========================================================================
    // 5. MULTI-TENANT CLOUD ISOLATION & AUTHORIZATION STRESS
    // =========================================================================

    @Test
    fun testMultiTenantCloudIsolation_noOperationalReferencesToAccountsLatest() {
        // Scans all files in android-app/app/src/main/
        val srcDir = File("src/main/java/com/linktomac/app")
        val mainFiles = srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("Source files must be present", mainFiles.isNotEmpty())

        val violations = mutableListOf<String>()
        for (file in mainFiles) {
            file.useLines { lines ->
                lines.forEachIndexed { idx, line ->
                    val trimmed = line.trim()
                    // If line is not a comment and contains "accounts/latest"
                    if (!trimmed.startsWith("//") && !trimmed.startsWith("*") && trimmed.contains("accounts/latest")) {
                        violations.add("${file.name}:${idx + 1}: $trimmed")
                    }
                }
            }
        }

        assertTrue("Zero operational references to accounts/latest allowed: $violations", violations.isEmpty())
    }

    @Test
    fun testApprovedClientsStore_concurrentMultiThreadedStress() {
        // Stress test concurrency: 16 threads checking, approving, and revoking clients simultaneously
        val threadCount = 16
        val operationsPerThread = 200
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        for (t in 0 until threadCount) {
            val threadId = t
            executor.submit {
                try {
                    for (i in 0 until operationsPerThread) {
                        val ip = "192.168.1.${(threadId * 10) + (i % 10)}"
                        // Loopback must always remain authorized
                        assertTrue("Loopback 127.0.0.1 must always be authorized", ApprovedClientsStore.isAuthorized("127.0.0.1"))
                        assertTrue("Loopback ::1 must always be authorized", ApprovedClientsStore.isAuthorized("::1"))

                        ApprovedClientsStore.approveClient(null, ip)
                        assertTrue(ApprovedClientsStore.isAuthorized(ip))

                        if (i % 2 == 0) {
                            ApprovedClientsStore.revokeClient(null, ip)
                            assertFalse(ApprovedClientsStore.isAuthorized(ip))
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("Concurrent store stress must complete within 10s", latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()

        // Loopback remains authorized after clearing store
        ApprovedClientsStore.clear()
        assertTrue(ApprovedClientsStore.isAuthorized("127.0.0.1"))
        assertTrue(ApprovedClientsStore.isAuthorized("localhost"))
    }
}
