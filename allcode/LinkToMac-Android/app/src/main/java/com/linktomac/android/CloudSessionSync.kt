/**
 * CloudSessionSync.kt
 * LinktoMac Android Companion
 *
 * Role:
 * Listens to Firestore `users/{uid}/sessions/current` (and `sessions/{sessionId}`)
 * to discover relay endpoints (videoHost, videoPort, controlHost, controlPort, token)
 * when operating across different networks (NAT'd Wi-Fi, 4G/5G, WAN).
 * Drives both the control channel (LinkConnectionService) and video channel
 * (ScreenCameraStreamer) toward the secure Pinggy relay.
 */

package com.linktomac.android

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Listens to Firestore `users/{uid}/sessions/current` and drives both
 * the control channel (54921) and the video channel (27189) toward the relay.
 *
 * Replaces mDNS as the discovery mechanism when the Mac is on a different network.
 */
class CloudSessionSync private constructor(private val context: Context) {

    companion object {
        private const val TAG = "CloudSessionSync"
        @Volatile private var INSTANCE: CloudSessionSync? = null

        /**
         * Returns the singleton instance of [CloudSessionSync].
         *
         * @param context Application context used for initialization.
         * @return The active [CloudSessionSync] instance.
         */
        fun get(context: Context): CloudSessionSync =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: CloudSessionSync(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    private var sessionListener: ListenerRegistration? = null

    private var lastSessionId: String? = null
    private var lastDisconnectTime: Long = 0

    /**
     * Initializes Firebase Auth and Firestore components.
     * Safe to call multiple times or during application startup.
     */
    fun initialize() {
        try {
            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()
            Log.i(TAG, "CloudSessionSync initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init failed", e)
        }
    }

    /**
     * Signs in with a Google OAuth ID token obtained from GoogleSignInClient.
     *
     * @param idToken The verified Google ID token.
     * @param onSuccess Callback executed upon successful authentication and subscription.
     * @param onError Callback executed with an error message upon failure.
     */
    fun signInWithGoogle(idToken: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth?.signInWithCredential(credential)
            ?.addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    publishAccount(user.uid, user.email ?: "")
                    subscribeToSession(user.uid)
                    onSuccess()
                } else {
                    onError("No user returned")
                }
            }
            ?.addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Sign-in failed")
            }
    }

    /**
     * Publishes account discovery metadata mirroring script.js::publishAccountForPhoneSync.
     */
    private fun publishAccount(uid: String, email: String) {
        val cleanEmail = email.lowercase().trim()
        val payload = hashMapOf(
            "uid" to uid,
            "email" to cleanEmail,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        firestore?.collection("accounts")?.document(cleanEmail)
            ?.set(payload, SetOptions.merge())
    }

    /**
     * Subscribes to live session updates for the given authenticated user UID.
     * When the Mac publishes a relay endpoint with `active: true`, Android
     * connects to both the control channel and video channel.
     *
     * @param uid The authenticated user UID.
     */
    fun subscribeToSession(uid: String) {
        sessionListener?.remove()
        sessionListener = firestore?.collection("users")?.document(uid)
            ?.collection("sessions")?.document("current")
            ?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Listen failed", error)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val active      = snapshot.getBoolean("active") ?: false
                val videoHost   = snapshot.getString("videoHost") ?: ""
                val videoPort   = snapshot.getLong("videoPort")?.toInt() ?: 27189
                val controlHost = snapshot.getString("controlHost") ?: ""
                val controlPort = snapshot.getLong("controlPort")?.toInt() ?: 54921
                val token       = snapshot.getString("token") ?: ""
                val sessionId   = snapshot.getString("sessionId") ?: ""

                val now = System.currentTimeMillis()
                if (lastSessionId == sessionId && !active && (now - lastDisconnectTime < 30_000)) {
                    // Mac is mid-restart; don't fully tear down, just hold for reconnect
                    Log.i(TAG, "Mac restart detected — holding for reconnect")
                    return@addSnapshotListener
                }

                if (active && videoHost.isNotEmpty()) {
                    Log.i(TAG, "Remote session active: $sessionId, video=$videoHost:$videoPort, control=$controlHost:$controlPort")
                    lastSessionId = sessionId
                    scope.launch {
                        LinkConnectionService.instance?.connectToMac(controlHost, controlPort, token)
                        ScreenCameraStreamer.startRemoteClient(videoHost, videoPort, token)
                    }
                } else {
                    Log.i(TAG, "Remote session inactive, disconnecting")
                    if (!active) lastDisconnectTime = now
                    LinkConnectionService.instance?.disconnect()
                    ScreenCameraStreamer.stopAllStreams()
                }
            }
    }

    /**
     * Unsubscribes from live session updates and frees listener registrations.
     */
    fun unsubscribe() {
        sessionListener?.remove()
        sessionListener = null
    }
}
