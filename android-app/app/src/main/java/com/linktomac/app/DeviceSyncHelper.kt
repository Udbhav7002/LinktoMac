package com.linktomac.app

import com.google.firebase.firestore.FieldValue
import java.net.Inet4Address
import java.net.NetworkInterface

object DeviceSyncHelper {
    const val DEVICE_DOC_PATH = "android"
    const val DEFAULT_DEVICE_NAME = "Android Device"

    fun createDevicePayload(
        ip: String,
        deviceName: String,
        timestamp: Any = FieldValue.serverTimestamp()
    ): Map<String, Any> {
        val safeName = if (deviceName.isNotBlank()) deviceName else DEFAULT_DEVICE_NAME
        return hashMapOf(
            "ip" to ip,
            "localIp" to ip,
            "deviceName" to safeName,
            "lastSeen" to timestamp,
            "timestamp" to timestamp
        )
    }

    fun isInterfaceExcluded(interfaceName: String): Boolean {
        val name = interfaceName.lowercase()
        return name.contains("dummy") ||
                name.contains("rmnet") ||
                name.contains("tun") ||
                name.contains("p2p")
    }

    fun getInterfacePriority(interfaceName: String): Int {
        val name = interfaceName.lowercase()
        if (isInterfaceExcluded(name)) return -1
        return when {
            name.startsWith("wlan") || name.startsWith("swlan") || name.startsWith("ap") || name.startsWith("softap") -> 1
            name.startsWith("eth") || name.startsWith("rndis") -> 2
            else -> 3
        }
    }

    fun findBestIpAddress(interfaces: List<NetworkInterface>): String? {
        val activeInterfaces = interfaces.filter { intf ->
            try {
                intf.isUp && !intf.isLoopback
            } catch (e: Exception) {
                false
            }
        }

        for (priority in 1..3) {
            for (intf in activeInterfaces) {
                if (getInterfacePriority(intf.name) == priority) {
                    val ip = extractFirstIpv4(intf)
                    if (ip != null) return ip
                }
            }
        }

        // Fallback for pure 5G/LTE when Wi-Fi is off so Firestore presence still syncs
        for (intf in activeInterfaces) {
            val ip = extractFirstIpv4(intf)
            if (ip != null) return ip
        }

        return null
    }

    private fun extractFirstIpv4(intf: NetworkInterface): String? {
        try {
            for (addr in intf.inetAddresses) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    val host = addr.hostAddress
                    if (!host.isNullOrBlank()) {
                        return host
                    }
                }
            }
        } catch (ignored: Exception) {}
        return null
    }
}
