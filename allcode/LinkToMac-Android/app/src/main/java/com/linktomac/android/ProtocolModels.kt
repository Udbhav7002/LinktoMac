package com.linktomac.android

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WireProtocol {
    const val FRAME_JSON: Byte       = 0x01
    const val FRAME_FILE_CHUNK: Byte = 0x02
    const val FRAME_VIDEO_JPEG: Byte = 0x03
    const val FRAME_AUDIO_PCM: Byte  = 0x04

    fun packFrame(frameType: Byte, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(5 + payload.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(payload.size)
        buffer.put(frameType)
        buffer.put(payload)
        return buffer.array()
    }

    fun packJson(type: String, payloadMap: Map<String, String> = emptyMap(), extraBlock: (JSONObject.() -> Unit)? = null): ByteArray {
        val root = JSONObject()
        root.put("type", type)
        root.put("timestamp", System.currentTimeMillis())
        val payloadObj = JSONObject()
        for ((k, v) in payloadMap) {
            payloadObj.put(k, v)
        }
        root.put("payload", payloadObj)
        extraBlock?.invoke(root)
        return packFrame(FRAME_JSON, root.toString().toByteArray(Charsets.UTF_8))
    }
}







