package com.firstapp.dormease.network

object Constants {

    private const val USE_TUNNEL = true

    // Local LAN (when not using tunnel)
    private const val SERVER_IP = "192.168.68.114"
    private const val SERVER_PORT = "3000"

    // Tunnel host only (NO https:// and NO trailing slash)
    private const val TUNNEL_HOST = "spatial-searches-sets-fighters.trycloudflare.com"

    // Computed values must be 'val', not 'const val'
    val BASE_URL: String =
        if (USE_TUNNEL) "https://$TUNNEL_HOST/" else "http://$SERVER_IP:$SERVER_PORT/"

    val SOCKET_URL: String =
        if (USE_TUNNEL) "https://$TUNNEL_HOST" else "http://$SERVER_IP:$SERVER_PORT"
}