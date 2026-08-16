package com.blueberry.client.modules

import android.graphics.Color
import com.blueberry.client.core.GameState

class DirectionModule : HudTextModule(
    id = "direction",
    displayName = "Direction",
    description = "Facing compass direction",
    color = Color.parseColor("#81C784"),
    x = 20,
    y = 340
) {
    override fun onTick() {
        val yaw = ((GameState.yaw % 360) + 360) % 360
        val dir = when {
            yaw < 22.5 || yaw >= 337.5 -> "S"
            yaw < 67.5 -> "SW"
            yaw < 112.5 -> "W"
            yaw < 157.5 -> "NW"
            yaw < 202.5 -> "N"
            yaw < 247.5 -> "NE"
            yaw < 292.5 -> "E"
            else -> "SE"
        }
        setText("Facing: $dir (${yaw.toInt()}°)")
    }
}
