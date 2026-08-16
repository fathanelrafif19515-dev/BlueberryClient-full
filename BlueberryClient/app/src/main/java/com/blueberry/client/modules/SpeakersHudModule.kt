package com.blueberry.client.modules

import android.graphics.Color
import com.blueberry.client.core.ModuleRegistry

class SpeakersHudModule : HudTextModule(
    id = "speakers_hud",
    displayName = "Active Speakers",
    description = "Who is talking nearby",
    color = Color.parseColor("#CE93D8"),
    x = 20,
    y = 460
) {
    override fun onTick() {
        val prox = ModuleRegistry.get("proximity_chat") as? ProximityChatModule
        val speakers = prox?.getActiveSpeakers().orEmpty()
        setText(
            if (speakers.isEmpty()) "Speakers: none"
            else "Speakers: ${speakers.joinToString()}"
        )
    }
}
