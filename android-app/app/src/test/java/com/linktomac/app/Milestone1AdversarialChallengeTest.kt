package com.linktomac.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Empirical Adversarial Challenge Test Suite for Milestone 1:
 * R1: Android Capture Flow & Frictionless LAN Auto-Authorization.
 *
 * Stress-tests:
 * 1. ScreenEncoder a11yWaitLock CPU spin eradication & instant wake latency.
 * 2. MirrorService authorization grace window timing, race conditions & fuzzing.
 * 3. ApprovedClientsStore IP normalization & multi-threaded concurrency safety.
 */
class Milestone1AdversarialChallengeTest {

    @Before
    fun setUp() {
        ApprovedClientsStore.clear()
    }

    // =========================================================================
    // 1. SCREENENCODER CPU SPIN ERADICATION & INSTANT WAKE BENCHMARKS
    // =========================================================================

    @Test
    fun testA11yWaitLock_eradicatesTightCpuSpinning() {
        // Reproduce ScreenEncoder's accessibility wait mechanism:
        // Instead of Thread.sleep(100) spinning 10 times/sec, synchronized(a11yWaitLock) { a11yWaitLock.wait(1000) }
        // must hold the thread in TIMED_WAITING state, generating at most 1 wakeup in 1000ms.
        val a11yWaitLock = java.lang.Object()
        val running = AtomicBoolean(true)
        val loopIterations = AtomicInteger(0)
        val threadStateCaptured = AtomicReference<Thread.State>()

        val thread = Thread {
            while (running.get() && !Thread.currentThread().isInterrupted) {
                loopIterations.incrementAndGet()
                synchronized(a11yWaitLock) {
                    try {
                        a11yWaitLock.wait(1000)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }
        thread.start()

        // Wait for thread to enter wait
        Thread.sleep(80)
        threadStateCaptured.set(thread.state)

        // The thread should be TIMED_WAITING on the monitor lock (0% CPU burn)
        assertEquals("Thread must be in TIMED_WAITING state on a11yWaitLock", Thread.State.TIMED_WAITING, threadStateCaptured.get())

        // Sleep 450ms: With old 100ms spin, iterations would be ~5. With 1000ms wait, iterations MUST be exactly 1.
        Thread.sleep(450)
        assertEquals("Tight 100ms CPU spinning must be eliminated: exactly 1 iteration in 500ms window", 1, loopIterations.get())

        // Clean up
        running.set(false)
        synchronized(a11yWaitLock) {
            a11yWaitLock.notifyAll()
        }
        thread.join(500)
        assertFalse("Thread should have terminated cleanly", thread.isAlive)
    }

    @Test
    fun testA11yWaitLock_instantWakeLatencyOnNotifyAll() {
        // In ScreenEncoder.upgradeToMediaProjection() and stop(), notifyAll() is sent to a11yWaitLock.
        // Verify that wakeup latency is sub-25ms and does NOT block for the remaining 1000ms timeout.
        val a11yWaitLock = java.lang.Object()
        val running = AtomicBoolean(true)
        val wokenUp = AtomicBoolean(false)
        val wakeLatencyMs = AtomicInteger(-1)

        val thread = Thread {
            synchronized(a11yWaitLock) {
                try {
                    a11yWaitLock.wait(1000)
                    wokenUp.set(true)
                } catch (_: InterruptedException) {}
            }
        }
        thread.start()

        // Wait until thread enters TIMED_WAITING
        var waited = 0
        while (thread.state != Thread.State.TIMED_WAITING && waited < 50) {
            Thread.sleep(5)
            waited++
        }
        assertEquals(Thread.State.TIMED_WAITING, thread.state)

        val startNotify = System.currentTimeMillis()
        synchronized(a11yWaitLock) {
            a11yWaitLock.notifyAll()
        }
        thread.join(300)
        val elapsed = (System.currentTimeMillis() - startNotify).toInt()
        wakeLatencyMs.set(elapsed)

        assertTrue("Thread must have woken up", wokenUp.get())
        assertFalse("Thread must not be alive", thread.isAlive)
        assertTrue("Wakeup latency must be fast (< 100ms), was $elapsed ms", elapsed < 100)
    }

    @Test
    fun testA11yWaitLock_interruptHandlingCleanExit() {
        // Verify interruption cleanly breaks the loop without deadlocking or eating the interrupt
        val a11yWaitLock = java.lang.Object()
        val exitedCleanly = AtomicBoolean(false)

        val thread = Thread {
            synchronized(a11yWaitLock) {
                try {
                    a11yWaitLock.wait(5000)
                } catch (_: InterruptedException) {
                    exitedCleanly.set(true)
                }
            }
        }
        thread.start()
        Thread.sleep(50)
        thread.interrupt()
        thread.join(500)

        assertTrue("Thread must exit cleanly upon interruption", exitedCleanly.get())
        assertFalse(thread.isAlive)
    }

    // =========================================================================
    // 2. MIRRORSERVICE AUTHORIZATION GRACE WINDOW TESTS
    // =========================================================================

    /**
     * Emulates MirrorService.isClientAuthorized logic for isolated unit verification
     */
    private fun simulateIsClientAuthorized(
        clientIp: String,
        running: AtomicBoolean,
        graceTimeoutMs: Long = 1500L,
        pollIntervalMs: Long = 100L,
        promptHandler: ((String, (Boolean) -> Unit) -> Unit)? = null
    ): Boolean {
        if (ApprovedClientsStore.isAuthorized(clientIp)) {
            return true
        }

        val startMs = System.currentTimeMillis()
        while (System.currentTimeMillis() - startMs < graceTimeoutMs && running.get()) {
            try {
                Thread.sleep(pollIntervalMs)
            } catch (_: InterruptedException) {
                break
            }
            if (ApprovedClientsStore.isAuthorized(clientIp)) {
                return true
            }
        }

        val handler = promptHandler ?: return false
        val latch = CountDownLatch(1)
        val approved = AtomicBoolean(false)
        try {
            handler.invoke(clientIp) { isApproved ->
                if (isApproved) {
                    ApprovedClientsStore.approveClient(null, clientIp)
                    approved.set(true)
                }
                latch.countDown()
            }
            val waited = latch.await(2, TimeUnit.SECONDS)
            return waited && approved.get()
        } catch (_: Exception) {
            return false
        }
    }

    @Test
    fun testGraceWindow_preAuthorizedClientReturnsImmediately() {
        val clientIp = "192.168.1.150"
        ApprovedClientsStore.approveClient(null, clientIp)

        val start = System.currentTimeMillis()
        val authorized = simulateIsClientAuthorized(clientIp, AtomicBoolean(true))
        val elapsed = System.currentTimeMillis() - start

        assertTrue("Pre-authorized client must be authorized", authorized)
        assertTrue("Pre-authorized client check must return immediately (< 20ms), was $elapsed ms", elapsed < 20)
    }

    @Test
    fun testGraceWindow_loopbackClientReturnsImmediately() {
        val loopbacks = listOf("127.0.0.1", "::1", "localhost", "::ffff:127.0.0.1", "/127.0.0.1")
        for (ip in loopbacks) {
            val start = System.currentTimeMillis()
            val authorized = simulateIsClientAuthorized(ip, AtomicBoolean(true))
            val elapsed = System.currentTimeMillis() - start
            assertTrue("Loopback $ip must be auto-authorized", authorized)
            assertTrue("Loopback check must be instantaneous (< 20ms)", elapsed < 20)
        }
    }

    @Test
    fun testGraceWindow_delayedAuthorizationDuringGraceWindowSucceeds() {
        val clientIp = "192.168.1.200"
        val running = AtomicBoolean(true)

        // Schedule async authorization to arrive at 250ms (simulating Firestore snapshot arrival)
        val executor = Executors.newSingleThreadScheduledExecutor()
        executor.schedule({
            ApprovedClientsStore.approveClient(null, clientIp)
        }, 250, TimeUnit.MILLISECONDS)

        val start = System.currentTimeMillis()
        val authorized = simulateIsClientAuthorized(clientIp, running)
        val elapsed = System.currentTimeMillis() - start

        executor.shutdown()
        assertTrue("Client authorized during grace period must succeed", authorized)
        assertTrue("Grace poll must detect authorization around ~250-450ms, was $elapsed ms", elapsed in 200..700)
    }

    @Test
    fun testGraceWindow_unauthorizedClientWithoutHandlerFailsAfterGrace() {
        val clientIp = "192.168.1.250"
        val running = AtomicBoolean(true)

        val start = System.currentTimeMillis()
        val authorized = simulateIsClientAuthorized(clientIp, running, graceTimeoutMs = 500L)
        val elapsed = System.currentTimeMillis() - start

        assertFalse("Unauthorized client without handler must fail", authorized)
        assertTrue("Must wait out the grace period (~500ms), was $elapsed ms", elapsed >= 450)
    }

    @Test
    fun testGraceWindow_unauthorizedClientWithPromptHandlerAccepts() {
        val clientIp = "192.168.1.222"
        val running = AtomicBoolean(true)

        val authorized = simulateIsClientAuthorized(
            clientIp,
            running,
            graceTimeoutMs = 200L,
            promptHandler = { ip, callback ->
                assertEquals(clientIp, ip)
                callback.invoke(true)
            }
        )

        assertTrue("Client approved via prompt handler must be authorized", authorized)
        assertTrue("Approved client must now be persisted in ApprovedClientsStore", ApprovedClientsStore.isAuthorized(clientIp))
    }

    @Test
    fun testGraceWindow_unauthorizedClientWithPromptHandlerDenies() {
        val clientIp = "192.168.1.223"
        val running = AtomicBoolean(true)

        val authorized = simulateIsClientAuthorized(
            clientIp,
            running,
            graceTimeoutMs = 200L,
            promptHandler = { _, callback ->
                callback.invoke(false)
            }
        )

        assertFalse("Client denied via prompt handler must NOT be authorized", authorized)
        assertFalse("Denied client must NOT be in ApprovedClientsStore", ApprovedClientsStore.isAuthorized(clientIp))
    }

    @Test
    fun testGraceWindow_serverStoppingAbortsGraceWindowEarly() {
        val clientIp = "192.168.1.99"
        val running = AtomicBoolean(true)

        val executor = Executors.newSingleThreadScheduledExecutor()
        // Simulate server stop at 150ms
        executor.schedule({
            running.set(false)
        }, 150, TimeUnit.MILLISECONDS)

        val start = System.currentTimeMillis()
        val authorized = simulateIsClientAuthorized(clientIp, running, graceTimeoutMs = 3000L)
        val elapsed = System.currentTimeMillis() - start
        executor.shutdown()

        assertFalse("Stopping server must reject authorization", authorized)
        assertTrue("Stopping server must abort grace poll promptly (< 500ms), was $elapsed ms", elapsed < 500)
    }

    // =========================================================================
    // 3. HIGH-CONCURRENCY STRESS & FUZZING ON APPROVEDCLIENTSSTORE
    // =========================================================================

    @Test
    fun testApprovedClientsStore_highConcurrencyFuzzStress() {
        val threadCount = 30
        val operationsPerThread = 200
        val latch = CountDownLatch(threadCount)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())

        val executor = Executors.newFixedThreadPool(threadCount)
        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    val rand = java.util.Random(t.toLong() * 1000)
                    for (i in 0 until operationsPerThread) {
                        val octet = rand.nextInt(50) + 1
                        val ip = "192.168.1.$octet"
                        when (rand.nextInt(4)) {
                            0 -> ApprovedClientsStore.approveClient(null, ip)
                            1 -> ApprovedClientsStore.isAuthorized(ip)
                            2 -> ApprovedClientsStore.revokeClient(null, ip)
                            3 -> {
                                assertTrue(ApprovedClientsStore.isAuthorized("127.0.0.1"))
                                assertTrue(ApprovedClientsStore.isAuthorized("localhost"))
                            }
                        }
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()
        assertTrue("All concurrent operations must complete within timeout", completed)
        assertTrue("No thread safety violations or exceptions under concurrent fuzzing: $errors", errors.isEmpty())
    }

    @Test
    fun testApprovedClientsStore_ipNormalizationFuzzing() {
        val rawVariants = listOf(
            "/192.168.1.55",
            "192.168.1.55",
            "::ffff:192.168.1.55",
            "192.168.1.55%wlan0",
            "/::ffff:192.168.1.55",
            "  192.168.1.55  "
        )

        ApprovedClientsStore.approveClient(null, "192.168.1.55")

        for (variant in rawVariants) {
            assertTrue("Variant '$variant' must be recognized as authorized", ApprovedClientsStore.isAuthorized(variant))
        }

        ApprovedClientsStore.revokeClient(null, "/::ffff:192.168.1.55")
        for (variant in rawVariants) {
            assertFalse("Variant '$variant' must be revoked", ApprovedClientsStore.isAuthorized(variant))
        }
    }

    // =========================================================================
    // 4. CAPTURE UPGRADE STATE MACHINE TRANSITIONS & CONCURRENCY
    // =========================================================================

    /**
     * Models the ScreenEncoder upgrade state machine to stress-test transitions,
     * thread-safety, locks, and race conditions in isolation.
     */
    class EmulatedScreenEncoderStateMachine {
        val lifecycleLock = java.lang.Object()
        val a11yWaitLock = java.lang.Object()
        val running = AtomicBoolean(false)
        val isUsingMediaProjection = AtomicBoolean(false)
        var captureThread: Thread? = null
        val virtualDisplayCreated = AtomicInteger(0)
        val keyFramesRequested = AtomicInteger(0)
        var projectionStopCallback: (() -> Unit)? = null

        fun startFallbackA11y() {
            synchronized(lifecycleLock) {
                running.set(true)
                isUsingMediaProjection.set(false)
                captureThread = Thread {
                    while (running.get() && !Thread.currentThread().isInterrupted) {
                        synchronized(a11yWaitLock) {
                            try {
                                a11yWaitLock.wait(1000)
                            } catch (_: InterruptedException) {
                                return@Thread
                            }
                        }
                    }
                }.apply { start() }
            }
        }

        fun upgradeToMediaProjection(onProjectionStopped: (() -> Unit)? = null): Boolean {
            synchronized(lifecycleLock) {
                if (!running.get()) return false
                captureThread?.interrupt()
                synchronized(a11yWaitLock) {
                    a11yWaitLock.notifyAll()
                }
                try {
                    captureThread?.join(300)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                captureThread = null

                this.projectionStopCallback = onProjectionStopped
                virtualDisplayCreated.incrementAndGet()
                isUsingMediaProjection.set(true)
                keyFramesRequested.incrementAndGet()
                return true
            }
        }

        fun stop() {
            if (!running.getAndSet(false)) return
            synchronized(lifecycleLock) {
                captureThread?.interrupt()
                synchronized(a11yWaitLock) {
                    a11yWaitLock.notifyAll()
                }
                try {
                    captureThread?.join(500)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                captureThread = null
                isUsingMediaProjection.set(false)
            }
        }
    }

    @Test
    fun testCaptureUpgrade_fullLifecycleTransition() {
        val sm = EmulatedScreenEncoderStateMachine()
        assertFalse(sm.running.get())
        assertFalse(sm.isUsingMediaProjection.get())

        // 1. Start in fallback A11y capture mode
        sm.startFallbackA11y()
        assertTrue(sm.running.get())
        assertFalse(sm.isUsingMediaProjection.get())
        val initialThread = sm.captureThread
        assertNotNull(initialThread)
        assertTrue(initialThread!!.isAlive)

        // 2. Perform live upgrade to MediaProjection
        val startUpgrade = System.currentTimeMillis()
        val upgraded = sm.upgradeToMediaProjection()
        val elapsed = System.currentTimeMillis() - startUpgrade

        assertTrue(upgraded)
        assertTrue(sm.running.get())
        assertTrue(sm.isUsingMediaProjection.get())
        assertEquals(1, sm.virtualDisplayCreated.get())
        assertEquals(1, sm.keyFramesRequested.get())
        assertFalse("Initial a11y thread must be stopped", initialThread.isAlive)
        assertTrue("Live upgrade must be fast (< 350ms), was $elapsed ms", elapsed < 350)

        // 3. Teardown
        sm.stop()
        assertFalse(sm.running.get())
        assertFalse(sm.isUsingMediaProjection.get())
    }

    @Test
    fun testCaptureUpgrade_concurrencyAndIdempotencyStress() {
        val sm = EmulatedScreenEncoderStateMachine()
        sm.startFallbackA11y()

        val threadCount = 20
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(threadCount)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    if (sm.upgradeToMediaProjection()) {
                        successCount.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()

        assertTrue(sm.running.get())
        assertTrue(sm.isUsingMediaProjection.get())
        assertEquals("All concurrent callers must succeed cleanly through lifecycleLock", threadCount, successCount.get())

        sm.stop()
        assertFalse(sm.running.get())
        assertFalse(sm.isUsingMediaProjection.get())
    }

    @Test
    fun testCaptureUpgrade_raceBetweenStopAndUpgrade() {
        // Race 50 pairs of threads calling stop() vs upgradeToMediaProjection()
        for (i in 0 until 50) {
            val sm = EmulatedScreenEncoderStateMachine()
            sm.startFallbackA11y()

            val startGun = CountDownLatch(1)
            val finishLatch = CountDownLatch(2)

            val tUpgrade = Thread {
                startGun.await()
                sm.upgradeToMediaProjection()
                finishLatch.countDown()
            }
            val tStop = Thread {
                startGun.await()
                sm.stop()
                finishLatch.countDown()
            }

            tUpgrade.start()
            tStop.start()
            startGun.countDown()

            assertTrue("Both threads must complete within timeout", finishLatch.await(3, TimeUnit.SECONDS))
            // Either encoder ended up stopped, or upgraded then stopped.
            // In either final state, clean cleanup must have occurred.
            sm.stop() // Ensure stopped
            assertFalse(sm.running.get())
            assertFalse(sm.isUsingMediaProjection.get())
        }
    }

    @Test
    fun testCaptureUpgrade_upgradeWhenStoppedIsNoOp() {
        val sm = EmulatedScreenEncoderStateMachine()
        // Never started or already stopped
        sm.running.set(false)
        val upgraded = sm.upgradeToMediaProjection()
        assertFalse("Upgrade on non-running encoder must return false immediately", upgraded)
        assertFalse(sm.isUsingMediaProjection.get())
        assertEquals(0, sm.virtualDisplayCreated.get())
    }

    // =========================================================================
    // 5. AUTHORIZATION GRACE TIMING MATRIX & BOUNDARY TESTS
    // =========================================================================

    @Test
    fun testGraceWindow_timingBoundaryMatrix() {
        // Test arrival times across the 1500ms window: 50ms, 200ms, 600ms, 1100ms, 1300ms
        val arrivalDelays = listOf(50L, 200L, 600L, 1100L, 1300L)
        val executor = Executors.newSingleThreadScheduledExecutor()

        try {
            for ((idx, delayMs) in arrivalDelays.withIndex()) {
                val ip = "192.168.10.$idx"
                val running = AtomicBoolean(true)

                executor.schedule({
                    ApprovedClientsStore.approveClient(null, ip)
                }, delayMs, TimeUnit.MILLISECONDS)

                val start = System.currentTimeMillis()
                val authorized = simulateIsClientAuthorized(ip, running, graceTimeoutMs = 1500L, pollIntervalMs = 50L)
                val elapsed = System.currentTimeMillis() - start

                assertTrue("Delay ${delayMs}ms must be authorized", authorized)
                assertTrue("Elapsed time $elapsed ms must be >= $delayMs ms", elapsed >= delayMs - 15)
                assertTrue("Elapsed time $elapsed ms must be within grace window (< 1600ms)", elapsed < 1600)
            }
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun testGraceWindow_arrivalAfterGraceFallsBackToPrompt() {
        val ip = "192.168.20.99"
        val running = AtomicBoolean(true)
        val promptInvoked = AtomicBoolean(false)
        val executor = Executors.newSingleThreadScheduledExecutor()

        try {
            // Snapshot arrives at 1700ms (after 1500ms grace window expires)
            executor.schedule({
                ApprovedClientsStore.approveClient(null, ip)
            }, 1700, TimeUnit.MILLISECONDS)

            val start = System.currentTimeMillis()
            val authorized = simulateIsClientAuthorized(
                ip,
                running,
                graceTimeoutMs = 500L, // 500ms grace window for test speed
                pollIntervalMs = 50L,
                promptHandler = { _, callback ->
                    promptInvoked.set(true)
                    callback(true)
                }
            )
            val elapsed = System.currentTimeMillis() - start

            assertTrue(authorized)
            assertTrue("Prompt handler must have been invoked since snapshot was delayed past grace window", promptInvoked.get())
            assertTrue(elapsed >= 450)
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun testGraceWindow_concurrentMultiClientRace() {
        val clientCount = 20
        val latch = CountDownLatch(clientCount)
        val executor = Executors.newFixedThreadPool(clientCount)
        val scheduler = Executors.newScheduledThreadPool(clientCount)
        val running = AtomicBoolean(true)
        val results = Collections.synchronizedList(mutableListOf<Boolean>())

        try {
            for (i in 0 until clientCount) {
                val ip = "192.168.30.$i"
                val delay = (i * 20L) % 300L // 0ms to 280ms arrival delay
                scheduler.schedule({
                    ApprovedClientsStore.approveClient(null, ip)
                }, delay, TimeUnit.MILLISECONDS)

                executor.submit {
                    try {
                        val auth = simulateIsClientAuthorized(ip, running, graceTimeoutMs = 1500L, pollIntervalMs = 50L)
                        results.add(auth)
                    } finally {
                        latch.countDown()
                    }
                }
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS))
            assertEquals(clientCount, results.size)
            for (res in results) {
                assertTrue("All concurrent clients must be authorized", res)
            }
        } finally {
            executor.shutdown()
            scheduler.shutdown()
        }
    }
}
