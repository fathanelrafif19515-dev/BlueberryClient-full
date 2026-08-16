package com.blueberry.client.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.SocketTimeoutException

private const val TAG = "ServerDiscovery"
private const val BEACON_PORT = 9998
private const val SERVER_PORT = 9999
private const val PREFS = "blueberry"
private const val KEY_SERVER_URL = "server_url"
private const val KEY_SERVER_IP = "server_ip"

object ServerDiscovery {

    @Volatile var lastServerIp: String? = null
        private set

    @Volatile var lastServerUrl: String? = null
        private set

    /** True if phone has a local network IP (Wi-Fi or hotspot). */
    fun isOnLocalNetwork(context: Context): Boolean = getPhoneIp(context) != null

    fun getPhoneIp(context: Context): String? {
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                if (!ni.isUp || ni.isLoopback) return@forEach
                ni.inetAddresses.toList().forEach { addr ->
                    val host = addr.hostAddress ?: return@forEach
                    if (host.contains(':')) return@forEach
                    if (isPrivateIp(host)) return host
                }
            }
        } catch (_: Exception) { }

        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ip = wm.connectionInfo.ipAddress
            if (ip != 0) {
                return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
            }
        } catch (_: Exception) { }
        return null
    }

    /** Guess PC IP prefix e.g. "192.168.1." for the input field. */
    fun getIpPrefix(context: Context): String {
        val phoneIp = getPhoneIp(context) ?: return "192.168.1."
        val parts = phoneIp.split('.')
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}." else "192.168.1."
    }

    fun getSavedIp(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SERVER_IP, null)

    fun buildUrl(ip: String): String {
        val clean = ip.trim().removePrefix("ws://").substringBefore(":").substringBefore("/")
        lastServerIp = clean
        lastServerUrl = "ws://$clean:$SERVER_PORT/"
        return lastServerUrl!!
    }

    fun save(context: Context, url: String) {
        lastServerUrl = url
        lastServerIp = url.removePrefix("ws://").substringBefore(":").substringBefore("/")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SERVER_URL, url)
            .putString(KEY_SERVER_IP, lastServerIp)
            .apply()
    }

    /** Test if IP is reachable before connecting WebSocket. */
    fun testConnection(ip: String): Boolean = probeServer(ip.trim())

    suspend fun autoDiscover(context: Context): String? = withContext(Dispatchers.IO) {
        listenForBeacon(context, 3000L)?.let {
            save(context, it)
            return@withContext it
        }
        scanSubnetParallel(context)?.let {
            save(context, it)
            return@withContext it
        }
        // If not found via LAN discovery, try common emulator/host addresses
        tryEmulatorFallback(context)?.let {
            save(context, it)
            return@withContext it
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SERVER_URL, null)?.also {
                lastServerUrl = it
                lastServerIp = it.removePrefix("ws://").substringBefore(":")
            }
    }

    private fun tryEmulatorFallback(context: Context): String? {
        // If phone is not on local network or looks like an emulator, try host loopback addresses
        val tryList = listOf("10.0.2.2", "10.0.3.2", "127.0.0.1")
        // Also try common local prefixes if phone has an IP
        val phoneIp = getPhoneIp(context)
        val extra = if (phoneIp != null) {
            val prefix = phoneIp.substringBeforeLast('.')
            listOf("$prefix.1", "$prefix.2")
        } else emptyList()

        val candidates = (tryList + extra).distinct()
        for (ip in candidates) {
            if (probeServer(ip)) return buildUrl(ip)
        }
        return null
    }

    private fun listenForBeacon(context: Context, timeoutMs: Long): String? {
        var socket: DatagramSocket? = null
        var multicastLock: WifiManager.MulticastLock? = null
        try {
            try {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                multicastLock = wm.createMulticastLock("blueberry").apply {
                    setReferenceCounted(true)
                    acquire()
                }
            } catch (_: Exception) { }

            socket = DatagramSocket(BEACON_PORT).apply {
                reuseAddress = true
                soTimeout = 400
                broadcast = true
            }

            val buf = ByteArray(256)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    parseBeacon(String(packet.data, 0, packet.length).trim())?.let { return it }
                } catch (_: SocketTimeoutException) { }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Beacon: ${e.message}")
        } finally {
            socket?.close()
            try { multicastLock?.release() } catch (_: Exception) { }
        }
        return null
    }

    private fun parseBeacon(msg: String): String? {
        if (!msg.startsWith("BLUEBERRY|")) return null
        val parts = msg.split("|")
        if (parts.size < 3) return null
        return buildUrl(parts[1])
    }

    private suspend fun scanSubnetParallel(context: Context): String? = coroutineScope {
        val phoneIp = getPhoneIp(context) ?: return@coroutineScope null
        val prefix = phoneIp.substringBeforeLast('.')
        Log.d(TAG, "Fast scan $prefix.0/24")

        val chunkSize = 32
        for (chunkStart in 1..254 step chunkSize) {
            val jobs = (chunkStart until (chunkStart + chunkSize).coerceAtMost(255)).map { host ->
                async(Dispatchers.IO) {
                    val ip = "$prefix.$host"
                    if (probeServer(ip)) ip else null
                }
            }
            val found = jobs.awaitAll().firstOrNull { it != null }
            if (found != null) return@coroutineScope buildUrl(found)
        }
        null
    }

    private fun probeServer(ip: String): Boolean {
        return try {
            val conn = (java.net.URL("http://$ip:$SERVER_PORT/").openConnection() as HttpURLConnection).apply {
                connectTimeout = 600
                readTimeout = 600
                requestMethod = "GET"
            }
            if (conn.responseCode != 200) return false
            val body = conn.inputStream.bufferedReader().readText()
            body.contains("Blueberry", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun isPrivateIp(ip: String): Boolean =
        ip.startsWith("192.168.") || ip.startsWith("10.") ||
            (ip.startsWith("172.") && ip.split('.').getOrNull(1)?.toIntOrNull()?.let { it in 16..31 } == true)
}
