package com.blueberry.client.core

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

private const val TAG = "BlueberryNetwork"

/**
 * Implementasi NetworkClient pakai OkHttp WebSocket.
 * Packet format: { "type": "...", "data": { ... } }
 */
class OkHttpNetworkClient : NetworkClient {

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val messageHandlers = ConcurrentHashMap<String, (Map<String, Any?>) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    override var isConnected: Boolean = false
        private set

    override fun connect(serverUrl: String, onConnected: () -> Unit, onError: (String) -> Unit) {
        disconnect()
        val request = Request.Builder().url(serverUrl).build()

        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                webSocket = ws
                isConnected = true
                Log.d(TAG, "Connected to $serverUrl")
                mainHandler.post { onConnected() }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type", "")
                    if (type.isEmpty()) return
                    val dataMap = json.toFlatMap()

                    messageHandlers[type]?.let { handler ->
                        mainHandler.post { handler(dataMap) }
                    }
                    messageHandlers["*"]?.let { handler ->
                        mainHandler.post { handler(dataMap) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message: ${e.message}")
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                webSocket = null
                Log.e(TAG, "WebSocket error: ${t.message}")
                mainHandler.post { onError(t.message ?: "Connection failed") }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                isConnected = false
                webSocket = null
                Log.d(TAG, "WebSocket closed: $reason")
            }
        })
    }

    override fun send(type: String, payload: Map<String, Any?>) {
        if (!isConnected) return
        val data = mapToJson(payload)
        val json = JSONObject().apply {
            put("type", type)
            put("data", data)
        }
        webSocket?.send(json.toString())
    }

    fun sendBinary(data: ByteArray) {
        if (!isConnected) return
        webSocket?.send(ByteString.of(*data))
    }

    override fun onMessage(type: String, handler: (Map<String, Any?>) -> Unit) {
        messageHandlers[type] = handler
    }

    override fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isConnected = false
    }

    private fun mapToJson(map: Map<String, Any?>): JSONObject {
        val obj = JSONObject()
        map.forEach { (k, v) ->
            when (v) {
                null -> obj.put(k, JSONObject.NULL)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    obj.put(k, mapToJson(v as Map<String, Any?>))
                }
                is List<*> -> obj.put(k, JSONArray(v))
                else -> obj.put(k, v)
            }
        }
        return obj
    }
}

private fun JSONObject.toFlatMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    keys().forEach { key ->
        val value = get(key)
        map[key] = when (value) {
            is JSONObject -> value.toFlatMap()
            JSONObject.NULL -> null
            else -> value
        }
    }
    return map
}
