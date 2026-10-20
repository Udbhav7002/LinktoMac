package com.linktomac.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.common.SignInButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var nsdManager: NsdManager? = null
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var webAuthServer: ServerSocket? = null

    private lateinit var unauthenticatedCard: View
    private lateinit var authenticatedCard: View
    private lateinit var statusText: TextView
    private lateinit var userEmailText: TextView
    private var orDivider: View? = null
    private lateinit var signInButton: SignInButton
    private var startStreamBtnMain: Button? = null
    private var startCaptureBtn: Button? = null
    private lateinit var startStreamBtn: Button
    private lateinit var upgradeCaptureBtn: Button
    private lateinit var enableA11yBtn: Button
    private lateinit var controlStatusText: TextView
    private lateinit var stopStreamBtn: Button
    private lateinit var signOutBtn: Button

    private lateinit var activeConnectionCard: View
    private lateinit var connectedMacNameText: TextView
    private lateinit var discardConnectionBtn: Button

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isStreaming = false
    private var isActivityResumed = false
    private var needsProjectionUpgrade = false
    private var sessionListener: ListenerRegistration? = null
    private var pendingRemoteHost: String? = null
    private var pendingRemotePort: Int = 0

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            Log.i(TAG, "MediaProjection permission granted")
            if (MirrorService.isStreamingActive && MirrorService.instance != null) {
                MirrorService.instance?.upgradeToProjection(result.resultCode, data)
            } else {
                MirrorService.start(this, result.resultCode, data, pendingRemoteHost, pendingRemotePort)
            }
            setStreamingState(true)
        } else {
            Log.w(TAG, "MediaProjection permission denied or cancelled; falling back to accessibility capture")
            Toast.makeText(
                this,
                "Screen capture permission not granted. Running in fallback mode (10 FPS).",
                Toast.LENGTH_SHORT
            ).show()
            if (!MirrorService.isStreamingActive) {
                startMirrorServiceDirectly()
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        launchMediaProjectionRequest()
    }

    private fun getAuthPrefs() = getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)

    private fun getEffectiveUid(): String? {
        return getAuthPrefs().getString(KEY_UID, null) ?: auth.currentUser?.uid
    }

    private fun getEffectiveEmail(): String? {
        return getAuthPrefs().getString(KEY_EMAIL, null) ?: auth.currentUser?.email
    }

    private fun saveAccountSession(uid: String, email: String) {
        getAuthPrefs().edit()
            .putString(KEY_UID, uid.trim())
            .putString(KEY_EMAIL, email.trim())
            .apply()
    }

    private fun clearAccountSession() {
        getAuthPrefs().edit().clear().apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        userEmailText = findViewById(R.id.userEmailText)
        orDivider = findViewById(R.id.orDivider)
        signInButton = findViewById(R.id.signInButton)
        startStreamBtnMain = findViewById(R.id.startStreamBtnMain)
        startCaptureBtn = findViewById(R.id.startCaptureBtn)
        startStreamBtn = findViewById(R.id.startStreamBtn)
        upgradeCaptureBtn = findViewById(R.id.upgradeCaptureBtn)
        enableA11yBtn = findViewById(R.id.enableA11yBtn)
        controlStatusText = findViewById(R.id.controlStatusText)
        stopStreamBtn = findViewById(R.id.stopStreamBtn)
        signOutBtn = findViewById(R.id.signOutBtn)

        unauthenticatedCard = findViewById(R.id.unauthenticatedCard)
        authenticatedCard = findViewById(R.id.authenticatedCard)
        activeConnectionCard = findViewById(R.id.activeConnectionCard)
        connectedMacNameText = findViewById(R.id.connectedMacNameText)
        discardConnectionBtn = findViewById(R.id.discardConnectionBtn)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        signInButton.setOnClickListener {
            syncOrSignInWithGoogle()
        }

        enableA11yBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        startStreamBtnMain?.setOnClickListener {
            requestStreamingPermissionsAndStart()
        }

        startCaptureBtn?.setOnClickListener {
            requestStreamingPermissionsAndStart()
        }

        startStreamBtn.setOnClickListener {
            requestStreamingPermissionsAndStart()
        }

        upgradeCaptureBtn.setOnClickListener {
            requestStreamingPermissionsAndStart()
        }

        stopStreamBtn.setOnClickListener {
            MirrorService.stop(this)
            setStreamingState(false)
            updateConnectionUI(false, "")
        }

        discardConnectionBtn.setOnClickListener {
            MirrorService.disconnectAndRevoke(this)
            setStreamingState(false)
            updateConnectionUI(false, "")
            getEffectiveUid()?.let { uid ->
                try {
                    db.collection("users").document(uid)
                        .collection("sessions").document("current")
                        .set(mapOf("active" to false, "host" to null, "port" to 0), SetOptions.merge())
                } catch (_: Exception) {}
            }
            Toast.makeText(this, "Disconnected & unlinked Mac", Toast.LENGTH_SHORT).show()
        }

        signOutBtn.setOnClickListener {
            signOut()
        }

        ApprovedClientsStore.init(this)
        setupNetworkMonitoring()
        registerNsdBroadcast()
        if (!MirrorService.isStreamingActive) {
            MirrorService.startWithoutProjection(this)
        }
        val uid = getEffectiveUid()
        if (uid != null) {
            syncIpToFirebase()
        }
        handleDeepLinkIntent(intent)

        MirrorService.authorizationPromptHandler = { clientIp, callback ->
            if (!isFinishing && !isDestroyed) {
                AlertDialog.Builder(this)
                    .setTitle("Allow connection?")
                    .setMessage("A Mac at $clientIp is requesting to connect and mirror your screen.")
                    .setPositiveButton("Allow") { _, _ -> callback(true) }
                    .setNegativeButton("Deny") { _, _ -> callback(false) }
                    .setOnCancelListener { callback(false) }
                    .show()
            } else {
                callback(false)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "linktomac" && data.host == "auth") {
            val uid = data.getQueryParameter("uid")
            val email = data.getQueryParameter("email")
            if (!uid.isNullOrBlank()) {
                saveAccountSession(uid, email ?: "Google Account")
                updateUI(true)
                listenForRemoteSessions(uid)
                syncIpToFirebase()
                setupNetworkMonitoring()
                registerNsdBroadcast()
                Toast.makeText(this, "Signed in as $email", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Authenticates via loopback Web Auth page (identical to Mac's ui_app.py) using Firebase Web JS SDK.
     * All reads/writes to global `accounts/latest` are eradicated to enforce multi-tenant isolation.
     */
    private fun syncOrSignInWithGoogle() {
        statusText.text = "Linking Google Account..."
        launchLoopbackWebSignIn()
    }

    private fun launchLoopbackWebSignIn() {
        Thread {
            try {
                webAuthServer?.close()
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress("127.0.0.1", 0))
                webAuthServer = server
                val port = server.localPort

                runOnUiThread {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://localhost:$port/login"))
                        startActivity(browserIntent)
                    } catch (e: Exception) {
                        showManualEmailSyncDialog()
                    }
                }

                while (!server.isClosed) {
                    val client = server.accept()
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val requestLine = reader.readLine() ?: ""
                    val path = requestLine.split(" ").getOrNull(1) ?: "/"

                    if (path.startsWith("/callback")) {
                        val query = path.substringAfter("?", "")
                        val params = mutableMapOf<String, String>()
                        for (pair in query.split("&")) {
                            val parts = pair.split("=", limit = 2)
                            if (parts.size == 2) {
                                params[parts[0]] = URLDecoder.decode(parts[1], "UTF-8")
                            }
                        }
                        val uid = params["uid"] ?: ""
                        val email = params["email"] ?: "Google Account"

                        val html = """<!DOCTYPE html><html><body style="background:#121212;color:white;font-family:sans-serif;text-align:center;padding:50px;">
                            <h2>Linked Successfully!</h2>
                            <p>Signed in as $email</p>
                            <script>window.location.href = "linktomac://auth?uid=${Uri.encode(uid)}&email=${Uri.encode(email)}";</script>
                            <a href="linktomac://auth?uid=${Uri.encode(uid)}&email=${Uri.encode(email)}" style="display:inline-block;margin-top:20px;padding:12px 24px;background:#0A84FF;color:white;border-radius:8px;text-decoration:none;">Return to LinktoMac App</a>
                        </body></html>"""
                        val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n\r\n$html"
                        client.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                        client.close()

                        if (uid.isNotBlank()) {
                            runOnUiThread {
                                saveAccountSession(uid, email)
                                updateUI(true)
                                listenForRemoteSessions(uid)
                                syncIpToFirebase()
                                setupNetworkMonitoring()
                                registerNsdBroadcast()
                            }
                            server.close()
                            break
                        }
                    } else {
                        val loginHtml = """<!DOCTYPE html><html><head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <script src="https://www.gstatic.com/firebasejs/10.4.0/firebase-app-compat.js"></script>
                            <script src="https://www.gstatic.com/firebasejs/10.4.0/firebase-auth-compat.js"></script>
                            <script src="https://www.gstatic.com/firebasejs/10.4.0/firebase-firestore-compat.js"></script>
                        </head><body style="background:#121212;color:white;font-family:-apple-system,sans-serif;text-align:center;padding:40px 20px;">
                            <div style="max-width:360px;margin:20px auto;padding:28px;background:#1e1e1e;border-radius:14px;border:1px solid #333;">
                                <h2>LinktoMac</h2>
                                <p style="color:#aaa;font-size:13px;">Sign in with the same Google Account used on your Mac</p>
                                <button onclick="signInGoogle()" style="width:100%;padding:12px;background:white;color:black;border:none;border-radius:8px;font-size:15px;font-weight:600;margin-top:16px;cursor:pointer;">Sign in with Google</button>
                                <div id="status" style="margin-top:16px;color:#30D158;font-size:13px;"></div>
                            </div>
                            <script>
                                const firebaseConfig = { apiKey: "AIzaSyCr6T3Np-dI8lax8R2PfGDO57ZTPr0Eb2g", authDomain: "linktomac.firebaseapp.com", projectId: "linktomac" };
                                firebase.initializeApp(firebaseConfig);
                                const auth = firebase.auth();
                                const db = firebase.firestore();
                                const provider = new firebase.auth.GoogleAuthProvider();
                                function signInGoogle() {
                                    document.getElementById("status").innerText = "Connecting...";
                                    auth.signInWithPopup(provider).then(res => {
                                        const u = res.user;
                                        const cleanEmail = (u.email || "").toLowerCase().trim();
                                        db.collection("users").doc(u.uid).set({
                                            uid: u.uid,
                                            email: cleanEmail,
                                            updatedAt: firebase.firestore.FieldValue.serverTimestamp()
                                        }, { merge: true }).catch(() => {});
                                        if (cleanEmail) {
                                            db.collection("accounts").doc(cleanEmail).set({
                                                uid: u.uid,
                                                email: cleanEmail,
                                                updatedAt: firebase.firestore.FieldValue.serverTimestamp()
                                            }, { merge: true }).catch(() => {});
                                        }
                                        window.location.href = "/callback?uid=" + encodeURIComponent(u.uid) + "&email=" + encodeURIComponent(u.email);
                                    }).catch(err => {
                                        document.getElementById("status").innerText = err.message || "Sign in failed";
                                    });
                                }
                            </script>
                        </body></html>"""
                        val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n\r\n$loginHtml"
                        client.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                        client.close()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Loopback web sign-in error: ${e.message}")
            }
        }.start()
    }

    private fun showManualEmailSyncDialog() {
        val input = EditText(this).apply {
            hint = "Enter your Google Email"
        }
        AlertDialog.Builder(this)
            .setTitle("Link with Mac")
            .setMessage("Enter the Google email signed in on your Mac:")
            .setView(input)
            .setPositiveButton("Link") { _, _ ->
                val email = input.text.toString().trim().lowercase()
                if (email.isNotEmpty()) {
                    db.collection("accounts").document(email).get().addOnSuccessListener { doc ->
                        val uid = doc?.getString("uid")
                        if (!uid.isNullOrBlank()) {
                            saveAccountSession(uid, email)
                            updateUI(true)
                            listenForRemoteSessions(uid)
                            syncIpToFirebase()
                            setupNetworkMonitoring()
                            registerNsdBroadcast()
                            Toast.makeText(this, "Linked to $email", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "No Mac linked with $email. Please sign in on your Mac first.", Toast.LENGTH_LONG).show()
                        }
                    }.addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to link: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onStart() {
        super.onStart()
        val uid = getEffectiveUid()
        updateUI(uid != null)
        if (uid != null) {
            listenForRemoteSessions(uid)
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        isStreaming = MirrorService.isStreamingActive
        MirrorService.streamingStateListener = { active ->
            runOnUiThread { setStreamingState(active) }
        }
        MirrorService.clientConnectedListener = { connected, macName ->
            runOnUiThread {
                updateConnectionUI(connected, macName)
                if (connected && !MirrorService.isUsingMediaProjection) {
                    requestStreamingPermissionsAndStart()
                }
            }
        }
        MirrorService.captureModeListener = { isProjection ->
            runOnUiThread {
                updateUI(getEffectiveUid() != null)
                updateCaptureButtonsVisibility(!isProjection)
            }
        }
        setupNetworkMonitoring()
        registerNsdBroadcast()
        if (!MirrorService.isStreamingActive) {
            MirrorService.startWithoutProjection(this)
        }
        val uid = getEffectiveUid()
        updateUI(uid != null)
        if (uid != null) {
            listenForRemoteSessions(uid)
            syncIpToFirebase()
        }
        updateConnectionUI(MirrorService.connectedMacName.isNotBlank(), MirrorService.connectedMacName)
        if (MirrorService.isStreamingActive && !MirrorService.isUsingMediaProjection && (needsProjectionUpgrade || MirrorService.connectedMacName.isNotBlank())) {
            needsProjectionUpgrade = false
            requestStreamingPermissionsAndStart()
        }
    }

    override fun onPause() {
        super.onPause()
        isActivityResumed = false
        MirrorService.streamingStateListener = null
        MirrorService.clientConnectedListener = null
        MirrorService.captureModeListener = null
    }

    override fun onDestroy() {
        super.onDestroy()
        MirrorService.authorizationPromptHandler = null
        try { webAuthServer?.close() } catch (_: Exception) {}
        sessionListener?.remove()
        unregisterNsdBroadcast()
        unregisterNetworkMonitoring()
    }

    fun registerNsdBroadcast() {
        if (nsdRegistrationListener != null) return
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "${Build.MODEL} (LinktoMac)"
            serviceType = "_linktomac._tcp"
            port = StreamProtocol.DEFAULT_PORT
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { nsdRegistrationListener = null }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        try {
            nsdManager = getSystemService(Context.NSD_SERVICE) as? NsdManager
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            nsdRegistrationListener = listener
        } catch (_: Exception) {}
    }

    fun unregisterNsdBroadcast() {
        val listener = nsdRegistrationListener ?: return
        try { nsdManager?.unregisterService(listener) } catch (_: Exception) {}
        nsdRegistrationListener = null
    }

    private fun signOut() {
        if (isStreaming || MirrorService.isStreamingActive) {
            MirrorService.disconnectAndRevoke(this)
            setStreamingState(false)
            updateConnectionUI(false, "")
        }
        sessionListener?.remove()
        sessionListener = null
        getEffectiveUid()?.let { uid ->
            try {
                db.collection("users").document(uid)
                    .collection("devices").document(DeviceSyncHelper.DEVICE_DOC_PATH)
                    .delete()
            } catch (_: Exception) {}
        }
        clearAccountSession()
        try { auth.signOut() } catch (_: Exception) {}
        if (!MirrorService.isStreamingActive) {
            MirrorService.startWithoutProjection(this)
        }
        registerNsdBroadcast()
        setupNetworkMonitoring()
        updateUI(false)
        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI(isSignedIn: Boolean) {
        unauthenticatedCard.visibility = View.GONE
        authenticatedCard.visibility = View.VISIBLE

        if (!isSignedIn) {
            userEmailText.text = "Local Mode (Wi-Fi & USB)"
            signOutBtn.text = "Sign In"
            signOutBtn.setOnClickListener { syncOrSignInWithGoogle() }
        } else {
            val email = getEffectiveEmail() ?: "Google Account"
            userEmailText.text = email
            signOutBtn.text = "Sign Out"
            signOutBtn.setOnClickListener { signOut() }
            syncIpToFirebase()
        }

        if (MirrorService.connectedMacName.isNotBlank()) {
            activeConnectionCard.visibility = View.VISIBLE
            val displayName = MirrorService.connectedMacName
            connectedMacNameText.text = "Connected to $displayName"
            if (MirrorService.isUsingMediaProjection) {
                statusText.text = "● Streaming to $displayName (60 FPS)"
            } else {
                statusText.text = "● Linked to $displayName"
            }
            statusText.setTextColor(Color.parseColor("#34C759"))
        } else {
            activeConnectionCard.visibility = View.GONE
            statusText.text = "● Ready to Link"
            statusText.setTextColor(Color.parseColor("#34C759"))
        }

        updateAccessibilityUI()
        updateCaptureButtonsVisibility(!MirrorService.isUsingMediaProjection)
    }

    private fun updateConnectionUI(connected: Boolean, macName: String) {
        if (connected) {
            activeConnectionCard.visibility = View.VISIBLE
            val displayName = if (macName.isNotBlank()) macName else "Mac"
            connectedMacNameText.text = "Connected to $displayName"
            if (MirrorService.isUsingMediaProjection) {
                statusText.text = "● Streaming to $displayName (60 FPS)"
            } else {
                statusText.text = "● Linked to $displayName"
            }
            statusText.setTextColor(Color.parseColor("#34C759"))
        } else {
            activeConnectionCard.visibility = View.GONE
            statusText.text = "● Ready to Link"
            statusText.setTextColor(Color.parseColor("#34C759"))
        }
        updateCaptureButtonsVisibility(!MirrorService.isUsingMediaProjection)
    }

    private fun updateCaptureButtonsVisibility(show: Boolean) {
        val vis = if (show) View.VISIBLE else View.GONE
        startStreamBtnMain?.visibility = vis
        startCaptureBtn?.visibility = vis
    }

    private fun updateAccessibilityUI() {
        val enabled = LinktoMacAccessibilityService.isAccessibilityEnabled(this)
        if (enabled) {
            enableA11yBtn.visibility = View.GONE
            controlStatusText.visibility = View.VISIBLE
            controlStatusText.setBackgroundResource(R.drawable.bg_pill_green)
            controlStatusText.text = "✓ Remote Control: Ready"
            controlStatusText.setTextColor(Color.parseColor("#34C759"))
        } else {
            enableA11yBtn.visibility = View.VISIBLE
            controlStatusText.visibility = View.VISIBLE
            controlStatusText.setBackgroundResource(R.drawable.bg_pill_orange)
            controlStatusText.text = "⚠️ Remote Control: Disabled (Tap to enable)"
            controlStatusText.setTextColor(Color.parseColor("#FF9F0A"))
        }
    }

    private fun listenForRemoteSessions(uid: String) {
        try {
            sessionListener?.remove()
            sessionListener = db.collection("users").document(uid)
                .collection("sessions").document("current")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    val active = snapshot.getBoolean("active") ?: false

                    val rawUpdatedAtMs: Long? = try {
                        snapshot.getTimestamp("updatedAt")?.toDate()?.time
                            ?: snapshot.getLong("updatedAt")
                            ?: snapshot.getDate("updatedAt")?.time
                    } catch (_: Exception) {
                        try {
                            snapshot.getLong("updatedAt")
                        } catch (_: Exception) {
                            null
                        }
                    }

                    if (rawUpdatedAtMs != null) {
                        val updatedAtMs = if (rawUpdatedAtMs < 100_000_000_000L) rawUpdatedAtMs * 1000L else rawUpdatedAtMs
                        val sessionAgeMs = System.currentTimeMillis() - updatedAtMs
                        if (sessionAgeMs > 5 * 60 * 1000L) {
                            Log.i(TAG, "Ignoring stale remote session older than 5 minutes (age: ${sessionAgeMs / 1000}s)")
                            return@addSnapshotListener
                        }
                    }

                    val mode = snapshot.getString("mode") ?: "local"
                    val host = snapshot.getString("host")
                    val port = snapshot.getLong("port")?.toInt() ?: 0

                    val macIp = snapshot.getString("macIp")
                        ?: snapshot.getString("clientIp")
                        ?: if (!host.isNullOrBlank() && host != "local") host else null

                    if (active && !macIp.isNullOrBlank() && macIp != "0.0.0.0" && macIp != "local") {
                        Log.i(TAG, "Pre-authorizing Mac IP from Firestore session: $macIp")
                        ApprovedClientsStore.approveClient(this@MainActivity, macIp)
                    }

                    if (active) {
                        if (mode == "remote" && !host.isNullOrBlank() && port > 0 && host != "local") {
                            pendingRemoteHost = host
                            pendingRemotePort = port
                            if (!MirrorService.isStreamingActive) {
                                if (isActivityResumed) {
                                    requestStreamingPermissionsAndStart()
                                } else {
                                    needsProjectionUpgrade = true
                                    startMirrorServiceDirectly()
                                }
                            } else {
                                MirrorService.instance?.switchToRemote(host, port)
                            }
                        } else {
                            pendingRemoteHost = null
                            pendingRemotePort = 0
                            if (!MirrorService.isStreamingActive) {
                                if (isActivityResumed) {
                                    requestStreamingPermissionsAndStart()
                                } else {
                                    needsProjectionUpgrade = true
                                    startMirrorServiceDirectly()
                                }
                            } else if (MirrorService.connectedMacName.contains("Remote")) {
                                MirrorService.instance?.switchToLocal()
                            }
                        }
                    } else {
                        pendingRemoteHost = null
                        pendingRemotePort = 0
                        if (MirrorService.isStreamingActive && MirrorService.connectedMacName.contains("Remote")) {
                            MirrorService.instance?.switchToLocal()
                        }
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register remote session listener", e)
        }
    }

    private fun setStreamingState(streaming: Boolean) {
        isStreaming = streaming
        updateUI(getEffectiveUid() != null)
    }

    private fun requestStreamingPermissionsAndStart() {
        if (!LinktoMacAccessibilityService.isAccessibilityEnabled(this)) {
            Toast.makeText(this, "Please enable LinktoMac in Accessibility Settings first", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        launchMediaProjectionRequest()
    }

    private fun launchMediaProjectionRequest() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (projectionManager != null) {
            try {
                mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch MediaProjection intent", e)
            }
        }
        // Fallback to accessibility capture if MediaProjectionManager is unavailable
        if (!MirrorService.isStreamingActive) {
            startMirrorServiceDirectly()
        }
    }

    private fun startMirrorServiceDirectly() {
        MirrorService.startWithoutProjection(this, pendingRemoteHost, pendingRemotePort)
        setStreamingState(true)
    }

    private fun syncIpToFirebase() {
        try {
            val uid = getEffectiveUid() ?: return
            val ip = getLocalIpAddress() ?: "0.0.0.0"
            val now = FieldValue.serverTimestamp()
            val deviceData = DeviceSyncHelper.createDevicePayload(
                ip = ip,
                deviceName = Build.MODEL,
                timestamp = now
            )
            db.collection("users").document(uid)
                .collection("devices").document(DeviceSyncHelper.DEVICE_DOC_PATH)
                .set(deviceData, SetOptions.merge())
        } catch (_: Exception) {}
    }

    private fun setupNetworkMonitoring() {
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    if (getEffectiveUid() != null) syncIpToFirebase()
                }
            }
            override fun onLost(network: Network) {
                runOnUiThread {
                    MirrorService.instance?.dropLocalClientSocket()
                    if (getEffectiveUid() != null) syncIpToFirebase()
                    updateConnectionUI(false, "")
                }
            }
        }
        this.networkCallback = callback
        try { cm.registerNetworkCallback(request, callback) } catch (_: Exception) {}
    }

    private fun unregisterNetworkMonitoring() {
        val cm = connectivityManager ?: return
        val cb = networkCallback ?: return
        try { cm.unregisterNetworkCallback(cb) } catch (_: Exception) {}
        networkCallback = null
    }

    internal fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            DeviceSyncHelper.findBestIpAddress(interfaces.toList())
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_AUTH = "linktomac_auth"
        private const val KEY_UID = "saved_uid"
        private const val KEY_EMAIL = "saved_email"
    }
}
