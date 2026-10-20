package com.linktomac.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class BoundedFrameQueueTest {

    private fun makePacket(priority: FramePriority, id: Byte = 1): FramePacket {
        return FramePacket(
            data = byteArrayOf(id),
            priority = priority,
            timestampUs = id.toLong() * 1000L
        )
    }

    @Test
    fun testQueueAcceptsUpToCapacity() {
        val idrRequests = AtomicInteger(0)
        val queue = BoundedFrameQueue(capacity = 2) { idrRequests.incrementAndGet() }

        val f1 = makePacket(FramePriority.REFERENCE_FRAME, 1)
        val f2 = makePacket(FramePriority.REFERENCE_FRAME, 2)

        assertTrue(queue.offer(f1))
        assertTrue(queue.offer(f2))
        assertEquals(2, queue.size)
        assertEquals(2L, queue.totalFramesEnqueued.get())
    }

    @Test
    fun testDropNonReferenceFrameWhenQueueFull() {
        val idrRequests = AtomicInteger(0)
        val queue = BoundedFrameQueue(capacity = 2) { idrRequests.incrementAndGet() }

        val f1 = makePacket(FramePriority.REFERENCE_FRAME, 1)
        val f2 = makePacket(FramePriority.REFERENCE_FRAME, 2)
        val nonRef = makePacket(FramePriority.NON_REFERENCE_FRAME, 3)

        assertTrue(queue.offer(f1))
        assertTrue(queue.offer(f2))

        // Queue is now full (size = 2). Offering non-reference frame should drop it immediately.
        assertFalse(queue.offer(nonRef))
        assertEquals(1L, queue.droppedNonRefFrames.get())
        assertEquals(0, idrRequests.get())
        assertEquals(2, queue.size)
    }

    @Test
    fun testEvictEnqueuedNonReferenceWhenReferenceArrives() {
        val idrRequests = AtomicInteger(0)
        val queue = BoundedFrameQueue(capacity = 2) { idrRequests.incrementAndGet() }

        val ref1 = makePacket(FramePriority.REFERENCE_FRAME, 1)
        val nonRef = makePacket(FramePriority.NON_REFERENCE_FRAME, 2)
        val ref2 = makePacket(FramePriority.REFERENCE_FRAME, 3)

        assertTrue(queue.offer(ref1))
        assertTrue(queue.offer(nonRef))
        assertEquals(2, queue.size)

        // Queue is full, but one of the queued frames is non-reference.
        // Incoming reference frame should evict nonRef and succeed without requesting IDR.
        assertTrue(queue.offer(ref2))
        assertEquals(1L, queue.droppedNonRefFrames.get())
        assertEquals(0, idrRequests.get())
        assertEquals(2, queue.size)

        // Verify remaining items are ref1 and ref2
        val popped1 = queue.take()
        val popped2 = queue.take()
        assertEquals(1.toByte(), popped1.data[0])
        assertEquals(3.toByte(), popped2.data[0])
    }

    @Test
    fun testDropReferenceFrameAndTriggerIdrUnderExtremeCongestion() {
        val idrRequests = AtomicInteger(0)
        val queue = BoundedFrameQueue(capacity = 2) { idrRequests.incrementAndGet() }

        val ref1 = makePacket(FramePriority.REFERENCE_FRAME, 1)
        val ref2 = makePacket(FramePriority.REFERENCE_FRAME, 2)
        val ref3 = makePacket(FramePriority.REFERENCE_FRAME, 3)

        assertTrue(queue.offer(ref1))
        assertTrue(queue.offer(ref2))

        // Queue has only reference frames and is full.
        // Incoming reference frame cannot evict anything -> must drop and trigger IDR request!
        assertFalse(queue.offer(ref3))
        assertEquals(1L, queue.droppedRefFrames.get())
        assertEquals(1, idrRequests.get())
    }

    @Test
    fun testKeyframeEvictsStaleFrames() {
        val idrRequests = AtomicInteger(0)
        val queue = BoundedFrameQueue(capacity = 2) { idrRequests.incrementAndGet() }

        val ref1 = makePacket(FramePriority.REFERENCE_FRAME, 1)
        val ref2 = makePacket(FramePriority.REFERENCE_FRAME, 2)
        val key = makePacket(FramePriority.KEY_FRAME, 99)

        assertTrue(queue.offer(ref1))
        assertTrue(queue.offer(ref2))

        // Incoming keyframe clears queue and enqueues itself
        assertTrue(queue.offer(key))
        assertEquals(1, queue.size)
        assertEquals(99.toByte(), queue.take().data[0])
    }

    @Test
    fun testCodecConfigNeverDropped() {
        val idrRequests = AtomicInteger(0)
        val queue = BoundedFrameQueue(capacity = 2) { idrRequests.incrementAndGet() }

        val ref1 = makePacket(FramePriority.REFERENCE_FRAME, 1)
        val ref2 = makePacket(FramePriority.REFERENCE_FRAME, 2)
        val config = makePacket(FramePriority.CODEC_CONFIG, 77)

        assertTrue(queue.offer(ref1))
        assertTrue(queue.offer(ref2))

        // Codec config must never be dropped, evicts oldest
        assertTrue(queue.offer(config))
        assertEquals(2, queue.size)
        val p1 = queue.take()
        val p2 = queue.take()
        assertEquals(2.toByte(), p1.data[0])
        assertEquals(77.toByte(), p2.data[0])
    }
}
