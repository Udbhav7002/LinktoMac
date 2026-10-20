package com.linktomac.android

import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Base64
import android.util.Size
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.UUID

object FilePhotosBridge {

    fun sendDirectoryListing(path: String) {
        val dir = File(path)
        val arr = JSONArray()
        dir.listFiles()?.forEach { f ->
            arr.put(JSONObject().apply {
                put("name", f.name)
                put("path", f.absolutePath)
                put("sizeBytes", if (f.isDirectory) 0L else f.length())
                put("isDirectory", f.isDirectory)
                put("lastModified", f.lastModified())
            })
        }
        val frame = WireProtocol.packJson(
            type = "FILE_LIST_RES",
            payloadMap = mapOf("path" to path)
        ) {
            put("files", arr)
        }
        LinkConnectionService.instance?.sendRawFrame(frame)
    }

    fun sendPhotosList(context: Context) {
        val arr = JSONArray()
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.SIZE
            )
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null, null,
                MediaStore.Images.Media.DATE_TAKEN + " DESC"
            )?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count < 40) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(1) ?: "photo.jpg"
                    val path = cursor.getString(2) ?: ""
                    val date = cursor.getLong(3)
                    val size = cursor.getLong(4)

                    var thumbB64 = ""
                    try {
                        val contentUri = android.content.ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                        )
                        val bmp = context.contentResolver.loadThumbnail(contentUri, Size(180, 180), null)
                        val baos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 65, baos)
                        thumbB64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    } catch (_: Exception) {}

                    arr.put(JSONObject().apply {
                        put("id", id.toString())
                        put("name", name)
                        put("path", path)
                        put("dateTaken", date)
                        put("sizeBytes", size)
                        put("isVideo", false)
                        put("thumbnailBase64", thumbB64)
                    })
                    count++
                }
            }
        } catch (_: Exception) {}

        val frame = WireProtocol.packJson("PHOTOS_LIST_RES") {
            put("photos", arr)
        }
        LinkConnectionService.instance?.sendRawFrame(frame)
    }

    fun streamFileToMac(path: String, fileName: String, revealInFinder: String = "true") {
        val file = File(path)
        if (!file.exists() || !file.isFile) return
        val tid = UUID.randomUUID().toString() // 36 chars

        val startFrame = WireProtocol.packJson(
            type = "FILE_TRANSFER_START",
            payloadMap = mapOf(
                "transferId" to tid,
                "fileName" to fileName,
                "totalBytes" to file.length().toString()
            )
        )
        LinkConnectionService.instance?.sendRawFrame(startFrame)

        val tidBytes = tid.padEnd(36, ' ').toByteArray(Charsets.UTF_8)
        FileInputStream(file).use { fis ->
            val buf = ByteArray(65536)
            while (true) {
                val read = fis.read(buf)
                if (read <= 0) break
                val chunkPayload = ByteArray(36 + read)
                System.arraycopy(tidBytes, 0, chunkPayload, 0, 36)
                System.arraycopy(buf, 0, chunkPayload, 36, read)
                val packed = WireProtocol.packFrame(WireProtocol.FRAME_FILE_CHUNK, chunkPayload)
                LinkConnectionService.instance?.sendRawFrame(packed)
                Thread.sleep(2)
            }
        }

        val endFrame = WireProtocol.packJson(
            type = "FILE_TRANSFER_END",
            payloadMap = mapOf(
                "transferId" to tid,
                "fileName" to fileName,
                "revealInFinder" to revealInFinder
            )
        )
        LinkConnectionService.instance?.sendRawFrame(endFrame)
    }

    fun streamUriToMac(context: Context, uri: android.net.Uri, fileName: String, revealInFinder: String = "true") {
        var size = -1L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}
        if (size <= 0) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    size = pfd.statSize
                }
            } catch (_: Exception) {}
        }
        if (size < 0) size = 0L

        val tid = UUID.randomUUID().toString()
        val startFrame = WireProtocol.packJson(
            type = "FILE_TRANSFER_START",
            payloadMap = mapOf(
                "transferId" to tid,
                "fileName" to fileName,
                "totalBytes" to size.toString()
            )
        )
        LinkConnectionService.instance?.sendRawFrame(startFrame)

        val tidBytes = tid.padEnd(36, ' ').toByteArray(Charsets.UTF_8)
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buf = ByteArray(65536)
                while (true) {
                    val read = inputStream.read(buf)
                    if (read <= 0) break
                    val chunkPayload = ByteArray(36 + read)
                    System.arraycopy(tidBytes, 0, chunkPayload, 0, 36)
                    System.arraycopy(buf, 0, chunkPayload, 36, read)
                    val packed = WireProtocol.packFrame(WireProtocol.FRAME_FILE_CHUNK, chunkPayload)
                    LinkConnectionService.instance?.sendRawFrame(packed)
                    Thread.sleep(2)
                }
            }
        } catch (_: Exception) {}

        val endFrame = WireProtocol.packJson(
            type = "FILE_TRANSFER_END",
            payloadMap = mapOf(
                "transferId" to tid,
                "fileName" to fileName,
                "revealInFinder" to revealInFinder
            )
        )
        LinkConnectionService.instance?.sendRawFrame(endFrame)
    }
}







