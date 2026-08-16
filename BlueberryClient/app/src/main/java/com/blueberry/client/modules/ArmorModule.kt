package com.blueberry.client.modules

import android.graphics.Color
import com.blueberry.client.core.GameState

class ArmorModule : HudTextModule(
    id = "armor",
    displayName = "Armor",
    description = "Show armor points",
    color = Color.LTGRAY,
    x = 20,
    y = 220
) {
    override fun onTick() {
        val bars = "◆".repeat(GameState.armor.coerceIn(0, 20))
        setText("Armor: ${GameState.armor} $bars")
    }
}
