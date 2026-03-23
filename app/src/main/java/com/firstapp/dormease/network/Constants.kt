package com.firstapp.dormease.network

import android.os.Build

object Constants {

    // Keep tunnel optional; default to local backend so web + app use the same server.
    private const val USE_TUNNEL = true

    // Local backend hosts (when not using tunnel)
    private const val LAN_SERVER_IP = "192.168.1.20"
    private const val EMULATOR_HOST = "10.0.2.2"
    private const val SERVER_PORT = "3000"

    // Tunnel host only (NO https:// and NO trailing slash)
    private const val TUNNEL_HOST = "holdings-associated-struggle-martin.trycloudflare.com"

    private fun sanitizeHost(host: String): String {
        return host
            .trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
    }

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()

        return fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("emulator") ||
            model.contains("android sdk built for x86") ||
            (brand.startsWith("generic") && device.startsWith("generic")) ||
            product.contains("sdk")
    }

    private val localHost: String = if (isEmulator()) EMULATOR_HOST else LAN_SERVER_IP
    private val normalizedTunnelHost: String = sanitizeHost(TUNNEL_HOST)
    private val shouldUseTunnel: Boolean = USE_TUNNEL && normalizedTunnelHost.isNotBlank()

    // Computed values must be 'val', not 'const val'
    val BASE_URL: String =
        if (shouldUseTunnel) "https://$normalizedTunnelHost/" else "http://$localHost:$SERVER_PORT/"

    val SOCKET_URL: String =
        if (shouldUseTunnel) "https://$normalizedTunnelHost" else "http://$localHost:$SERVER_PORT"
}