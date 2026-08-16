package com.blueberry.client.modules

import android.graphics.Color
import com.blueberry.client.core.GameState

/**
 * CPS display. Value is updated externally (e.g. AccessibilityService touch count)
 * or via GameState.cps for demo purposes.
 */
class CpsModule : HudTextModule(
    id = "cps",
    displayName = "CPS",
    description = "Clicks per second",
    color = Color.parseColor("#FF9800"),
    x = 20,
    y = 300
) {
    private val clickTimes = ArrayDeque<Long>()

    fun registerClick() {
        val now = System.currentTimeMillis()
        clickTimes.addLast(now)
        while (clickTimes.isNotEmpty() && now - clickTimes.first() > 1000) {
            clickTimes.removeFirst()
        }
        GameState.cps = clickTimes.size
    }

    override fun onTick() {
        val now = System.currentTimeMillis()
        while (clickTimes.isNotEmpty() && now - clickTimes.first() > 1000) {
            clickTimes.removeFirst()
        }
        GameState.cps = clickTimes.size
        setText("CPS: ${GameState.cps}")
    }
}
