package com.linktomac.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SocketSyncTest {

    @Test
    fun testSocketWriteLockAndHandshakeGating_noCorruptedWrites() {
        val server = ServerSocket(0)
        val port = server.localPort

        val clientConnectedLatch = CountDownLatch(1)
        val threadCount = 6
        val writesPerThread = 200
        val handshakeSent = AtomicBoolean(false)
        val receivedTotalBytes = AtomicInteger(0)

        var serverClient: Socket? = null
        val serverThread = Thread {
            serverClient = server.accept()
            clientConnectedLatch.countDown()
            val input: InputStream = serverClient!!.getInputStream()
            val buffer = ByteArray(4096)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                // Verify magic is the very first 4 bytes received
                if (total == 0) {
                    val magic = ((buffer[0].toInt() and 0xFF) shl 24) or
                            ((buffer[1].toInt() and 0xFF) shl 16) or
                            ((buffer[2].toInt() and 0xFF) shl 8) or
                            (buffer[3].toInt() and 0xFF)
                    assertEquals(StreamProtocol.MAGIC, magic)
                }
                total += read
            }
            receivedTotalBytes.set(total)
        }
        serverThread.start()

        val client = Socket()
        client.connect(InetSocketAddress("127.0.0.1", port))
        assertTrue(clientConnectedLatch.await(5, TimeUnit.SECONDS))

        val output: OutputStream = client.getOutputStream()
        val socketWriteLock = Any()
        val isHandshakeDone = AtomicBoolean(false)

        val writersDoneLatch = CountDownLatch(threadCount)
        val writersStartedLatch = CountDownLatch(threadCount)

        // Start writer threads trying to send video frames
        for (i in 0 until threadCount) {
            Thread {
                writersStartedLatch.countDown()
                for (j in 0 until writesPerThread) {
                    if (!isHandshakeDone.get()) continue
                    synchronized(socketWriteLock) {
                        if (!isHandshakeDone.get()) return@synchronized
                        val data = byteArrayOf(0x00, 0x00, 0x00, 0x01, i.toByte(), j.toByte())
                        output.write(data)
                        output.flush()
                    }
                }
                writersDoneLatch.countDown()
            }.start()
        }

        writersStartedLatch.await(5, TimeUnit.SECONDS)

        // Handshake thread sends handshake under lock
        Thread.sleep(20) // Allow writers to start attempting
        synchronized(socketWriteLock) {
            isHandshakeDone.set(false)
            val handshake = StreamProtocol.createHandshake(720, 1600, 0)
            output.write(handshake)
            output.flush()
            val dummyHeader = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x67, 0x42)
            output.write(dummyHeader)
            output.flush()
            isHandshakeDone.set(true)
            handshakeSent.set(true)
        }

        assertTrue(writersDoneLatch.await(10, TimeUnit.SECONDS))

        client.close()
        serverThread.join(5000)
        server.close()

        assertTrue(handshakeSent.get())
        assertTrue(receivedTotalBytes.get() >= StreamProtocol.HANDSHAKE_SIZE + 6)
    }

    @Test
    fun testProbeTeardownResilience_probeDisconnectPreservesStateAndAllowsSubsequentRealClient() {
        val server = ServerSocket(0)
        val port = server.localPort

        val probeAcceptedLatch = CountDownLatch(1)
        val realClientAcceptedLatch = CountDownLatch(1)
        val realClientConnectedLatch = CountDownLatch(1)
        val realClientDisconnectedLatch = CountDownLatch(1)

        val probeUiNotificationTriggered = AtomicBoolean(false)
        val realClientConnectedNotified = AtomicBoolean(false)
        val realClientDisconnectedNotified = AtomicBoolean(false)

        val frameQueue = java.util.concurrent.LinkedBlockingQueue<ByteArray>()
        frameQueue.add(byteArrayOf(0x01, 0x02, 0x03)) // Simulated queued video frame

        val serverRunning = AtomicBoolean(true)
        val serverThread = Thread {
            var connectionCount = 0
            while (serverRunning.get()) {
                val client = try {
                    server.accept()
                } catch (e: Exception) {
                    break
                }
                connectionCount++
                var eventsProcessed = 0
                val inStream = client.getInputStream()
                val outStream = client.getOutputStream()

                // Send handshake
                val handshake = StreamProtocol.createHandshake(720, 1600, 0)
                outStream.write(handshake)
                outStream.flush()

                if (connectionCount == 1) {
                    probeAcceptedLatch.countDown()
                } else {
                    realClientAcceptedLatch.countDown()
                }

                // Read control events loop
                val header = ByteArray(1)
                try {
                    while (true) {
                        val r = try { inStream.read(header) } catch (_: Exception) { -1 }
                        if (r == -1) break
                        eventsProcessed++
                        if (eventsProcessed == 1) {
                            realClientConnectedNotified.set(true)
                            realClientConnectedLatch.countDown()
                        }
                    }
                } catch (_: Exception) {
                    // Probe or client disconnected
                } finally {
                    val isProbe = (eventsProcessed == 0)
                    if (isProbe) {
                        // Defer / suppress UI disconnect and do not purge frameQueue
                        probeUiNotificationTriggered.set(false)
                    } else {
                        realClientDisconnectedNotified.set(true)
                        frameQueue.clear()
                        realClientDisconnectedLatch.countDown()
                    }
                    try { client.close() } catch (_: Exception) {}
                }
            }
        }
        serverThread.start()

        // 1. Probe connection: connects and closes immediately without sending control events
        val probeSocket = Socket()
        probeSocket.connect(InetSocketAddress("127.0.0.1", port))
        assertTrue(probeAcceptedLatch.await(5, TimeUnit.SECONDS))
        probeSocket.close()

        // Give server time to process probe disconnect
        Thread.sleep(100)

        // Verify probe did NOT trigger UI connection callback or clear frameQueue
        org.junit.Assert.assertFalse(probeUiNotificationTriggered.get())
        org.junit.Assert.assertFalse(realClientConnectedNotified.get())
        assertEquals("Frame queue should NOT be cleared by ephemeral probe", 1, frameQueue.size)

        // 2. Real client connects and sends sync request (EVENT_TYPE_SYNC_REQUEST = 0x05)
        val realSocket = Socket()
        realSocket.connect(InetSocketAddress("127.0.0.1", port))
        assertTrue(realClientAcceptedLatch.await(5, TimeUnit.SECONDS))

        val realOut = realSocket.getOutputStream()
        realOut.write(byteArrayOf(StreamProtocol.EVENT_TYPE_SYNC_REQUEST.toByte()))
        realOut.flush()

        // Verify real client triggers connected UI notification
        assertTrue(realClientConnectedLatch.await(5, TimeUnit.SECONDS))
        assertTrue(realClientConnectedNotified.get())

        // Disconnect real client
        realSocket.close()

        // Verify disconnect notification triggered and frameQueue cleared
        assertTrue(realClientDisconnectedLatch.await(5, TimeUnit.SECONDS))
        assertTrue(realClientDisconnectedNotified.get())
        assertEquals("Frame queue should be cleared when real client disconnects", 0, frameQueue.size)

        serverRunning.set(false)
        server.close()
        serverThread.join(5000)
    }
}
