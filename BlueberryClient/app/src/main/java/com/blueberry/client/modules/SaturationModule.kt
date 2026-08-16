package com.blueberry.client.modules

import android.graphics.Color
import com.blueberry.client.core.GameState

class SaturationModule : HudTextModule(
    id = "saturation",
    displayName = "Hunger",
    description = "Food / hunger level",
    color = Color.parseColor("#FFB74D"),
    x = 20,
    y = 580
) {
    override fun onTick() {
        val icons = "🍗".repeat((GameState.food / 2).coerceIn(0, 10))
        setText("Food: ${GameState.food} $icons")
    }
}
