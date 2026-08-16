package com.blueberry.client.modules

import android.graphics.Color
import com.blueberry.client.core.GameState
import com.blueberry.client.core.ModuleContext
import com.blueberry.client.core.OkHttpNetworkClient

class PingModule : HudTextModule(
    id = "ping",
    displayName = "Ping",
    description = "Server latency (ms)",
    color = Color.YELLOW,
    x = 20,
    y = 260
) {
    private var network: OkHttpNetworkClient? = null
    private var lastPingAt = 0L
    private var awaitingPong = false
    private var pingSentAt = 0L

    override fun onLoad(context: ModuleContext) {
        super.onLoad(context)
        network = context.networkClient as? OkHttpNetworkClient
        network?.onMessage("pong") {
            if (awaitingPong) {
                GameState.pingMs = System.currentTimeMillis() - pingSentAt
                awaitingPong = false
            }
        }
    }

    override fun onTick() {
        val now = System.currentTimeMillis()
        if (now - lastPingAt >= 1000 && network?.isConnected == true && !awaitingPong) {
            lastPingAt = now
            pingSentAt = now
            awaitingPong = true
            network?.send("ping", emptyMap())
        }
        val ms = GameState.pingMs
        setText(if (ms < 0) "Ping: --" else "Ping: ${ms}ms")
    }
}
