package com.blueberry.client.modules

import android.graphics.Color
import com.blueberry.client.core.GameState

class HealthModule : HudTextModule(
    id = "health",
    displayName = "Health",
    description = "Show health points",
    color = Color.RED,
    x = 20,
    y = 180
) {
    override fun onTick() {
        val hearts = "❤".repeat((GameState.health / 2).toInt().coerceIn(0, 10))
        setText("HP: ${GameState.health.toInt()}/${GameState.maxHealth.toInt()} $hearts")
    }
}
