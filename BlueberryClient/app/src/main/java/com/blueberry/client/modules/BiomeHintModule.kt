package com.blueberry.client.modules

import android.graphics.Color
import com.blueberry.client.core.GameState

class BiomeHintModule : HudTextModule(
    id = "biome_hint",
    displayName = "Environment",
    description = "Current ambient environment tag",
    color = Color.parseColor("#80CBC4"),
    x = 20,
    y = 420
) {
    override fun onTick() {
        setText("Env: ${GameState.environment}")
    }
}
