package com.blueberry.client.modules

import android.graphics.Color
import com.blueberry.client.core.GameState

class CoordinatesModule : HudTextModule(
    id = "coordinates",
    displayName = "Coordinates",
    description = "Show X Y Z position",
    color = Color.CYAN,
    x = 20,
    y = 140
) {
    override fun onTick() {
        setText(
            "XYZ: %.1f / %.1f / %.1f".format(GameState.x, GameState.y, GameState.z)
        )
    }
}
