package com.linktomac.android

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class LinkNotificationService : NotificationListenerService() {

    private data class ReplyActionHolder(
        val pendingIntent: PendingIntent,
        val remoteInputs: Array<RemoteInput>
    )

    companion object {
        private var instance: LinkNotificationService? = null
        private val replyActions = ConcurrentHashMap<String, ReplyActionHolder>()
        var currentMediaTitle: String = "Nothing Playing"
        var currentMediaArtist: String = "Android Media"

        fun performInlineReply(context: Context, key: String, replyText: String) {
            val holder = replyActions[key] ?: return
            val intent = Intent()
            val bundle = Bundle()
            for (remoteInput in holder.remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(holder.remoteInputs, intent, bundle)
            try {
                holder.pendingIntent.send(context, 0, intent)
            } catch (_: Exception) {}
        }

        fun dismissNotification(key: String) {
            try {
                instance?.cancelNotification(key)
            } catch (_: Exception) {}
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        updateActiveMediaSession()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        guardSbn(sbn) ?: return
        updateActiveMediaSession()

        val notif = sbn!!.notification
        if ((notif.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if (sbn.packageName == packageName || sbn.packageName == "com.android.systemui") return

        val extras = notif.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isEmpty() && body.isEmpty()) return

        // Check for inline RemoteInput reply actions
        var canReply = false
        notif.actions?.forEach { action ->
            val inputs = action.remoteInputs
            if (!inputs.isNullOrEmpty() && action.actionIntent != null) {
                canReply = true
                replyActions[sbn.key] = ReplyActionHolder(action.actionIntent, inputs)
            }
        }

        // Detect 4-8 digit 2FA / OTP codes automatically
        val otpRegex = Regex("\\b(\\d{4,8})\\b")
        val otpMatch = otpRegex.find(body)?.value ?: ""

        val pm = applicationContext.packageManager
        val appName = try {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        val itemJson = JSONObject().apply {
            put("id", sbn.key)
            put("packageName", sbn.packageName)
            put("appName", appName)
            put("title", title)
            put("body", body)
            put("timestamp", sbn.postTime)
            put("canReply", canReply)
            put("otpCode", otpMatch)
        }

        val arr = JSONArray().apply { put(itemJson) }
        val bytes = WireProtocol.packJson("NOTIFICATION_POSTED") {
            put("notifications", arr)
        }
        LinkConnectionService.instance?.sendRawFrame(bytes)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        replyActions.remove(key)
        val bytes = WireProtocol.packJson(
            type = "NOTIFICATION_REMOVED",
            payloadMap = mapOf("id" to key)
        )
        LinkConnectionService.instance?.sendRawFrame(bytes)
    }

    private fun updateActiveMediaSession() {
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers = msm.getActiveSessions(
                android.content.ComponentName(this, LinkNotificationService::class.java)
            )
            val first = controllers.firstOrNull()
            val meta = first?.metadata
            if (meta != null) {
                currentMediaTitle = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Playing Media"
                currentMediaArtist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            }
        } catch (_: Exception) {}
    }

    private fun guardSbn(sbn: StatusBarNotification?): StatusBarNotification? = sbn
}






