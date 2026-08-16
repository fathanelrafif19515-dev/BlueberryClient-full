package com.blueberry.client.core

/**
 * Placeholder koneksi ke BlueberryClient Server.
 * Belum diimplementasi penuh — ini kontrak yang nanti dipakai
 * ReplayModule (upload event) dan ProximityChatModule (voice relay).
 *
 * Implementasi nyata (OkHttp WebSocket) menyusul begitu server backend
 * dibahas & dibangun.
 */
interface NetworkClient {
    fun connect(serverUrl: String, onConnected: () -> Unit, onError: (String) -> Unit)
    fun send(type: String, payload: Map<String, Any?>)
    fun onMessage(type: String, handler: (Map<String, Any?>) -> Unit)
    fun disconnect()
    val isConnected: Boolean
}

/** Stub sementara: log doang, belum konek beneran. Ganti pas server siap. */
class StubNetworkClient : NetworkClient {
    override var isConnected: Boolean = false
        private set

    override fun connect(serverUrl: String, onConnected: () -> Unit, onError: (String) -> Unit) {
        isConnected = false
        onError("Server belum di-setup — ini stub placeholder")
    }

    override fun send(type: String, payload: Map<String, Any?>) {
        // no-op sampai server backend dibuat
    }

    override fun onMessage(type: String, handler: (Map<String, Any?>) -> Unit) {
        // no-op
    }

    override fun disconnect() {
        isConnected = false
    }
}
