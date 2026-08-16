package com.blueberry.client.modules

import android.graphics.Color
import com.blueberry.client.core.GameState

class FpsCounterModule : HudTextModule(
    id = "fps_counter",
    displayName = "FPS Counter",
    description = "Device overlay refresh rate",
    color = Color.GREEN,
    x = 20,
    y = 100
) {
    private var frameCount = 0
    private var lastTimestamp = System.currentTimeMillis()

    override fun onTick() {
        frameCount++
        val now = System.currentTimeMillis()
        if (now - lastTimestamp >= 1000) {
            GameState.fps = frameCount
            setText("FPS: $frameCount")
            frameCount = 0
            lastTimestamp = now
        }
    }
}
