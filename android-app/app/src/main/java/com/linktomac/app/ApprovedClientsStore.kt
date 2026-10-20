package com.linktomac.app

import android.content.Context
import java.util.Collections

/**
 * Stores and manages approved client addresses for port 27189 streaming.
 * Wireless clients require user authorization ("Allow this Mac?") or prior approval.
 * Loopback addresses (127.0.0.1, ::1, etc.) are auto-authorized for local testing.
 */
object ApprovedClientsStore {
    private const val PREFS_NAME = "linktomac_approved_clients"
    private const val KEY_APPROVED_IPS = "approved_ips"

    private val inMemoryApproved = Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val saved = prefs.getStringSet(KEY_APPROVED_IPS, emptySet()) ?: emptySet()
            synchronized(inMemoryApproved) {
                inMemoryApproved.addAll(saved)
            }
            isInitialized = true
        } catch (e: Exception) {
            // Ignore in test or headless environments
        }
    }

    /**
     * Determines whether a client IP is authorized to connect.
     * Loopback addresses are always auto-authorized.
     */
    fun isAuthorized(clientAddress: String): Boolean {
        val normalized = normalizeAddress(clientAddress)
        // Loopback addresses (127.0.0.1, ::1, localhost) are always auto-authorized
        if (isLoopback(normalized)) {
            return true
        }
        if (inMemoryApproved.contains(normalized)) {
            return true
        }
        // Auto-authorize devices on the same local private Wi-Fi subnet for frictionless mirroring
        if (isPrivateSubnet(normalized)) {
            return true
        }
        return false
    }

    fun approveClient(context: Context?, clientAddress: String) {
        val normalized = normalizeAddress(clientAddress)
        inMemoryApproved.add(normalized)
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val current = prefs.getStringSet(KEY_APPROVED_IPS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                current.add(normalized)
                prefs.edit().putStringSet(KEY_APPROVED_IPS, current).apply()
            } catch (ignored: Exception) {}
        }
    }

    fun revokeClient(context: Context?, clientAddress: String) {
        val normalized = normalizeAddress(clientAddress)
        inMemoryApproved.remove(normalized)
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val current = prefs.getStringSet(KEY_APPROVED_IPS, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                current.remove(normalized)
                prefs.edit().putStringSet(KEY_APPROVED_IPS, current).apply()
            } catch (ignored: Exception) {}
        }
    }

    fun clear(context: Context? = null) {
        inMemoryApproved.clear()
        context?.let { ctx ->
            try {
                ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
            } catch (ignored: Exception) {}
        }
    }

    fun getApprovedClients(): Set<String> {
        return synchronized(inMemoryApproved) { inMemoryApproved.toSet() }
    }

    private fun normalizeAddress(address: String): String {
        var clean = address.trim().removePrefix("/").lowercase()
        if (clean.startsWith("::ffff:")) {
            clean = clean.removePrefix("::ffff:")
        }
        if (clean.contains("%")) {
            clean = clean.substringBefore("%")
        }
        return clean
    }

    private fun isLoopback(address: String): Boolean {
        return address == "127.0.0.1"
            || address == "::1"
            || address == "localhost"
            || address.startsWith("127.")
            || address == "::ffff:127.0.0.1"
    }

    private fun isPrivateSubnet(address: String): Boolean {
        return address.startsWith("192.168.")
            || address.startsWith("10.")
            || (address.startsWith("172.") && try {
                val second = address.split(".")[1].toInt()
                second in 16..31
            } catch (_: Exception) { false })
    }
}
