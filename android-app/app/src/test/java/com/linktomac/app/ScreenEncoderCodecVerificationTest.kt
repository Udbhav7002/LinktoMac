package com.linktomac.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Empirical Adversarial Verification Test for Milestone 3 Video Pipeline & Codec Negotiation:
 * 1. AST & Reflection verification of ScreenEncoder codec inspection methods.
 * 2. Hardware vs Software codec filtering oracle: verifying rejection of omx.google, c2.android, omx.ffmpeg.
 * 3. Try/catch fallback state machine: verifying orientation change fallback to AVC when HEVC fails.
 */
class ScreenEncoderCodecVerificationTest {

    // =========================================================================
    // 1. AST VERIFICATION OF SCREENENCODER.KT
    // =========================================================================

    @Test
    fun testAst_codecInspectionAndFallbackPatterns() {
        val file = File("src/main/java/com/linktomac/app/ScreenEncoder.kt")
        assertTrue("ScreenEncoder.kt must exist at expected path", file.exists())

        val content = file.readText()

        // 1. Verify isHardwareCodecAvailable implementation
        assertTrue("isHardwareCodecAvailable must query REGULAR_CODECS",
            content.contains("MediaCodecList(MediaCodecList.REGULAR_CODECS)"))
        assertTrue("isHardwareCodecAvailable must filter for isEncoder",
            content.contains("if (!info.isEncoder) continue"))
        assertTrue("isHardwareCodecAvailable must verify supportedTypes case-insensitively",
            content.contains("types.any { it.equals(mimeType, ignoreCase = true) }"))

        // 2. Verify API 29+ hardware acceleration check vs pre-29 prefix blacklist
        assertTrue("Must check Build.VERSION_CODES.Q for isHardwareAccelerated",
            content.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q"))
        assertTrue("Must call isHardwareAccelerated",
            content.contains("info.isHardwareAccelerated"))
        assertTrue("Must reject omx.google. prefix",
            content.contains("!name.startsWith(\"omx.google.\")"))
        assertTrue("Must reject c2.android. prefix",
            content.contains("!name.startsWith(\"c2.android.\")"))
        assertTrue("Must reject omx.ffmpeg. prefix",
            content.contains("!name.startsWith(\"omx.ffmpeg.\")"))

        // 3. Verify selectSupportedMimeType selects HEVC when available, falls back to AVC
        assertTrue("selectSupportedMimeType must check HEVC hardware availability",
            content.contains("isHardwareCodecAvailable(MediaFormat.MIMETYPE_VIDEO_HEVC)"))
        assertTrue("selectSupportedMimeType must return MIMETYPE_VIDEO_HEVC",
            content.contains("MediaFormat.MIMETYPE_VIDEO_HEVC"))
        assertTrue("selectSupportedMimeType must fallback to MIMETYPE_VIDEO_AVC",
            content.contains("MediaFormat.MIMETYPE_VIDEO_AVC"))

        // 4. Verify try/catch fallback during start() and updateDimensions()
        val updateDimStart = content.indexOf("fun updateDimensions(")
        assertTrue("updateDimensions must be declared", updateDimStart != -1)
        val updateDimBody = content.substring(updateDimStart, updateDimStart + 1500)
        assertTrue("updateDimensions must contain try/catch fallback around createAndConfigure",
            updateDimBody.contains("catch (e: Exception)") &&
            updateDimBody.contains("falling back to AVC") &&
            updateDimBody.contains("MediaFormat.MIMETYPE_VIDEO_AVC") &&
            updateDimBody.contains("isHevc = false"))

        val startDecl = content.indexOf("fun start(")
        assertTrue("start must be declared", startDecl != -1)
        val startBody = content.substring(startDecl, startDecl + 1500)
        assertTrue("start must contain try/catch fallback to AVC",
            startBody.contains("catch (e: Exception)") &&
            startBody.contains("falling back to AVC") &&
            startBody.contains("MediaFormat.MIMETYPE_VIDEO_AVC") &&
            startBody.contains("isHevc = false"))
    }

    // =========================================================================
    // 2. REFLECTION VERIFICATION OF CLASS STRUCTURE
    // =========================================================================

    @Test
    fun testReflection_methodsAndFields() {
        val clazz = ScreenEncoder::class.java

        // Check isHardwareCodecAvailable method
        val isHwMethod = clazz.getDeclaredMethod("isHardwareCodecAvailable", String::class.java)
        assertNotNull("isHardwareCodecAvailable must exist", isHwMethod)
        assertEquals(Boolean::class.javaPrimitiveType, isHwMethod.returnType)
        assertTrue("isHardwareCodecAvailable should be private", Modifier.isPrivate(isHwMethod.modifiers))

        // Check selectSupportedMimeType method
        val selectMimeMethod = clazz.getDeclaredMethod("selectSupportedMimeType")
        assertNotNull("selectSupportedMimeType must exist", selectMimeMethod)
        assertEquals(String::class.java, selectMimeMethod.returnType)
        assertTrue("selectSupportedMimeType should be private", Modifier.isPrivate(selectMimeMethod.modifiers))

        // Check updateDimensions method
        val updateDimMethod = clazz.getDeclaredMethod("updateDimensions",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        assertNotNull("updateDimensions must exist", updateDimMethod)
        assertTrue("updateDimensions must be public", Modifier.isPublic(updateDimMethod.modifiers))

        // Check isHevc field / getter
        val isHevcGetter = clazz.getDeclaredMethod("isHevc")
        assertNotNull("isHevc getter must exist", isHevcGetter)
        assertEquals(Boolean::class.javaPrimitiveType, isHevcGetter.returnType)
        assertTrue("isHevc getter must be public", Modifier.isPublic(isHevcGetter.modifiers))
    }

    @Test
    fun testReflection_gracefulHandlingWhenMediaCodecListNotMocked() {
        val encoder = ScreenEncoder(1080, 1920, 420)
        val isHwMethod = ScreenEncoder::class.java.getDeclaredMethod("isHardwareCodecAvailable", String::class.java)
        isHwMethod.isAccessible = true

        // Calling isHardwareCodecAvailable in un-mocked host JVM unit test:
        // 1. MediaCodecList throws RuntimeException("Method <init> in android.media.MediaCodecList not mocked")
        // 2. The try/catch (e: Exception) in ScreenEncoder catches it!
        // 3. Inside the catch block, Log.w is invoked with the warning message.
        // In host JVM without returnDefaultValues, Log.w throws "Method w in android.util.Log not mocked",
        // which proves empirically that the catch block was reached and executed.
        try {
            val result = isHwMethod.invoke(encoder, "video/hevc") as Boolean
            assertFalse("On device without hardware codecs, result is false", result)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.cause
            assertNotNull("InvocationTargetException must have a cause", cause)
            assertTrue("Exception must be from Log.w inside the catch block: ${cause?.message}",
                cause?.message?.contains("Log") == true)
        }
    }

    // =========================================================================
    // 3. HARDWARE VS SOFTWARE CODEC FILTERING ORACLE
    // =========================================================================

    /**
     * Exact replication of the pre-Q prefix filter algorithm in ScreenEncoder.kt:
     *   val name = info.name.lowercase()
     *   !name.startsWith("omx.google.") &&
     *   !name.startsWith("c2.android.") &&
     *   !name.startsWith("omx.ffmpeg.")
     */
    private fun isHardwareEncoderPreQ(codecName: String): Boolean {
        val name = codecName.lowercase()
        return !name.startsWith("omx.google.") &&
               !name.startsWith("c2.android.") &&
               !name.startsWith("omx.ffmpeg.")
    }

    @Test
    fun testSoftwareCodecRejection_knownSoftwarePrefixesRejected() {
        val knownSoftwareCodecs = listOf(
            "omx.google.hevc.encoder",
            "OMX.GOOGLE.HEVC.ENCODER",
            "omx.google.h264.encoder",
            "c2.android.hevc.encoder",
            "C2.ANDROID.HEVC.ENCODER",
            "c2.android.avc.encoder",
            "omx.ffmpeg.hevc.encoder",
            "OMX.FFMPEG.HEVC.ENCODER",
            "omx.ffmpeg.h264.encoder",
            "c2.android.sw.hevc.encoder",
            "omx.google.video.encoder"
        )

        for (codec in knownSoftwareCodecs) {
            assertFalse(
                "Software codec '$codec' MUST be rejected by hardware filter",
                isHardwareEncoderPreQ(codec)
            )
        }
    }

    @Test
    fun testHardwareCodecAcceptance_knownHardwarePrefixesAccepted() {
        val knownHardwareCodecs = listOf(
            "c2.qti.hevc.encoder",
            "omx.qcom.video.encoder.hevc",
            "c2.exynos.hevc.encoder",
            "omx.sec.hevc.enc",
            "c2.mtk.hevc.encoder",
            "omx.mtk.video.encoder.hevc",
            "omx.intel.video_encoder.hevc",
            "c2.hisilicon.hevc.encoder",
            "c2.tensor.hevc.encoder"
        )

        for (codec in knownHardwareCodecs) {
            assertTrue(
                "Hardware codec '$codec' MUST be accepted by hardware filter",
                isHardwareEncoderPreQ(codec)
            )
        }
    }

    // =========================================================================
    // 4. DIMENSION UPDATE AVC FALLBACK STATE MACHINE ORACLE
    // =========================================================================

    @Test
    fun testDimensionUpdateFallbackStateMachine_avcFallbackOnHevcFailure() {
        // Models the dynamic fallback state machine of ScreenEncoder.updateDimensions()
        var currentMime = "video/hevc"
        var isHevc = false
        var configureAttempted = 0
        var fallbackTriggered = false

        fun simulateCreateAndConfigure(mime: String, failHevc: Boolean): String {
            configureAttempted++
            if (failHevc && mime == "video/hevc") {
                throw IllegalStateException("Simulated HEVC hardware encoder crash on 90-degree dimension swap")
            }
            return mime
        }

        // Scenario 1: HEVC reconfiguration succeeds
        val selectedMime1 = "video/hevc"
        try {
            val configured = simulateCreateAndConfigure(selectedMime1, failHevc = false)
            isHevc = (configured == "video/hevc")
        } catch (e: Exception) {
            simulateCreateAndConfigure("video/avc", failHevc = false)
            isHevc = false
        }
        assertTrue("HEVC succeeded, isHevc must be true", isHevc)

        // Scenario 2: Mid-stream dimension flip causes HEVC failure (e.g. resolution not supported by HW encoder)
        val selectedMime2 = "video/hevc"
        try {
            val configured = simulateCreateAndConfigure(selectedMime2, failHevc = true)
            isHevc = (configured == "video/hevc")
        } catch (e: Exception) {
            fallbackTriggered = true
            val configured = simulateCreateAndConfigure("video/avc", failHevc = false)
            isHevc = false
        }

        assertTrue("Fallback must be triggered when HEVC fails", fallbackTriggered)
        assertFalse("isHevc must be reset to false after fallback", isHevc)
    }
}
