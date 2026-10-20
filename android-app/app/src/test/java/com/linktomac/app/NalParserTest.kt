package com.linktomac.app

import android.media.MediaCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class NalParserTest {

    private fun makeAnnexBPacket(nalHeaderBytes: ByteArray): ByteArray {
        val startCode = byteArrayOf(0x00, 0x00, 0x00, 0x01)
        return startCode + nalHeaderBytes + byteArrayOf(0x11, 0x22, 0x33)
    }

    private fun makeAnnexBMultiPacket(nal1: ByteArray, nal2: ByteArray): ByteArray {
        val startCode = byteArrayOf(0x00, 0x00, 0x00, 0x01)
        return startCode + nal1 + startCode + nal2 + byteArrayOf(0x55, 0x66)
    }

    @Test
    fun testBufferInfoFlags_overrideInspection() {
        val dummyData = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x01, 0x02)
        val configPacket = NalParser.classifyFrame(
            data = dummyData,
            offset = 0,
            size = dummyData.size,
            flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG,
            timestampUs = 1000L,
            isHevc = false
        )
        assertEquals(FramePriority.CODEC_CONFIG, configPacket.priority)

        val keyPacket = NalParser.classifyFrame(
            data = dummyData,
            offset = 0,
            size = dummyData.size,
            flags = MediaCodec.BUFFER_FLAG_KEY_FRAME,
            timestampUs = 2000L,
            isHevc = false
        )
        assertEquals(FramePriority.KEY_FRAME, keyPacket.priority)
    }

    @Test
    fun testH264_SpsPpsClassifiedAsCodecConfig() {
        // H.264 SPS: nal_unit_type = 7, e.g. 0x67 (nal_ref_idc = 3, type = 7)
        val spsData = makeAnnexBPacket(byteArrayOf(0x67.toByte()))
        val spsPacket = NalParser.classifyFrame(spsData, 0, spsData.size, 0, 0L, isHevc = false)
        assertEquals(FramePriority.CODEC_CONFIG, spsPacket.priority)

        // H.264 PPS: nal_unit_type = 8, e.g. 0x68 (nal_ref_idc = 3, type = 8)
        val ppsData = makeAnnexBPacket(byteArrayOf(0x68.toByte()))
        val ppsPacket = NalParser.classifyFrame(ppsData, 0, ppsData.size, 0, 0L, isHevc = false)
        assertEquals(FramePriority.CODEC_CONFIG, ppsPacket.priority)
    }

    @Test
    fun testH264_IdrClassifiedAsKeyFrame() {
        // H.264 IDR: nal_unit_type = 5, e.g. 0x65 (nal_ref_idc = 3, type = 5)
        val idrData = makeAnnexBPacket(byteArrayOf(0x65.toByte()))
        val idrPacket = NalParser.classifyFrame(idrData, 0, idrData.size, 0, 0L, isHevc = false)
        assertEquals(FramePriority.KEY_FRAME, idrPacket.priority)
    }

    @Test
    fun testH264_NonReferenceSliceClassifiedAsNonReference() {
        // H.264 Non-ref slice: nal_ref_idc = 0, nal_unit_type = 1 (e.g. 0x01)
        val nonRefData = makeAnnexBPacket(byteArrayOf(0x01.toByte()))
        val nonRefPacket = NalParser.classifyFrame(nonRefData, 0, nonRefData.size, 0, 0L, isHevc = false)
        assertEquals(FramePriority.NON_REFERENCE_FRAME, nonRefPacket.priority)
    }

    @Test
    fun testH264_ReferenceSliceClassifiedAsReference() {
        // H.264 Reference slice: nal_ref_idc = 2 (0x40) or 3 (0x60), nal_unit_type = 1 -> 0x41 or 0x61
        val refData = makeAnnexBPacket(byteArrayOf(0x41.toByte()))
        val refPacket = NalParser.classifyFrame(refData, 0, refData.size, 0, 0L, isHevc = false)
        assertEquals(FramePriority.REFERENCE_FRAME, refPacket.priority)
    }

    @Test
    fun testH264_AudPrefixSkipsToVclSlice() {
        // AUD is nal_unit_type = 9 (0x09), followed by non-ref slice (0x01)
        val audAndNonRef = makeAnnexBMultiPacket(byteArrayOf(0x09.toByte()), byteArrayOf(0x01.toByte()))
        val packet = NalParser.classifyFrame(audAndNonRef, 0, audAndNonRef.size, 0, 0L, isHevc = false)
        assertEquals(FramePriority.NON_REFERENCE_FRAME, packet.priority)

        // AUD followed by ref slice (0x41)
        val audAndRef = makeAnnexBMultiPacket(byteArrayOf(0x09.toByte()), byteArrayOf(0x41.toByte()))
        val packetRef = NalParser.classifyFrame(audAndRef, 0, audAndRef.size, 0, 0L, isHevc = false)
        assertEquals(FramePriority.REFERENCE_FRAME, packetRef.priority)
    }

    @Test
    fun testHevc_VpsSpsPpsClassifiedAsCodecConfig() {
        // HEVC VPS: nal_unit_type = 32 -> (32 << 1) = 0x40
        val vpsData = makeAnnexBPacket(byteArrayOf(0x40.toByte(), 0x01.toByte()))
        val vpsPacket = NalParser.classifyFrame(vpsData, 0, vpsData.size, 0, 0L, isHevc = true)
        assertEquals(FramePriority.CODEC_CONFIG, vpsPacket.priority)

        // HEVC SPS: nal_unit_type = 33 -> (33 << 1) = 0x42
        val spsData = makeAnnexBPacket(byteArrayOf(0x42.toByte(), 0x01.toByte()))
        val spsPacket = NalParser.classifyFrame(spsData, 0, spsData.size, 0, 0L, isHevc = true)
        assertEquals(FramePriority.CODEC_CONFIG, spsPacket.priority)

        // HEVC PPS: nal_unit_type = 34 -> (34 << 1) = 0x44
        val ppsData = makeAnnexBPacket(byteArrayOf(0x44.toByte(), 0x01.toByte()))
        val ppsPacket = NalParser.classifyFrame(ppsData, 0, ppsData.size, 0, 0L, isHevc = true)
        assertEquals(FramePriority.CODEC_CONFIG, ppsPacket.priority)
    }

    @Test
    fun testHevc_IdrClassifiedAsKeyFrame() {
        // HEVC IDR_W_RADL: nal_unit_type = 19 -> (19 << 1) = 0x26
        val idrData = makeAnnexBPacket(byteArrayOf(0x26.toByte(), 0x01.toByte()))
        val idrPacket = NalParser.classifyFrame(idrData, 0, idrData.size, 0, 0L, isHevc = true)
        assertEquals(FramePriority.KEY_FRAME, idrPacket.priority)
    }

    @Test
    fun testHevc_TrailNClassifiedAsNonReference() {
        // HEVC TRAIL_N: nal_unit_type = 0 -> (0 << 1) = 0x00
        val trailNData = makeAnnexBPacket(byteArrayOf(0x00.toByte(), 0x01.toByte()))
        val trailNPacket = NalParser.classifyFrame(trailNData, 0, trailNData.size, 0, 0L, isHevc = true)
        assertEquals(FramePriority.NON_REFERENCE_FRAME, trailNPacket.priority)

        // HEVC TSA_N: nal_unit_type = 2 -> (2 << 1) = 0x04
        val tsaNData = makeAnnexBPacket(byteArrayOf(0x04.toByte(), 0x01.toByte()))
        val tsaNPacket = NalParser.classifyFrame(tsaNData, 0, tsaNData.size, 0, 0L, isHevc = true)
        assertEquals(FramePriority.NON_REFERENCE_FRAME, tsaNPacket.priority)
    }

    @Test
    fun testHevc_TrailRClassifiedAsReference() {
        // HEVC TRAIL_R: nal_unit_type = 1 -> (1 << 1) = 0x02
        val trailRData = makeAnnexBPacket(byteArrayOf(0x02.toByte(), 0x01.toByte()))
        val trailRPacket = NalParser.classifyFrame(trailRData, 0, trailRData.size, 0, 0L, isHevc = true)
        assertEquals(FramePriority.REFERENCE_FRAME, trailRPacket.priority)
    }

    @Test
    fun testHevc_AudPrefixSkipsToVclSlice() {
        // HEVC AUD: nal_unit_type = 35 -> (35 << 1) = 0x46
        val audHeader = byteArrayOf(0x46.toByte(), 0x01.toByte())
        val trailNHeader = byteArrayOf(0x00.toByte(), 0x01.toByte())
        val audAndTrailN = makeAnnexBMultiPacket(audHeader, trailNHeader)
        val packet = NalParser.classifyFrame(audAndTrailN, 0, audAndTrailN.size, 0, 0L, isHevc = true)
        assertEquals(FramePriority.NON_REFERENCE_FRAME, packet.priority)
    }
}
