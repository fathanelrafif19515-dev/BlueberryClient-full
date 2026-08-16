package com.blueberry.client.modules

import android.graphics.Color

class MemoryModule : HudTextModule(
    id = "memory",
    displayName = "Memory",
    description = "App heap usage",
    color = Color.parseColor("#90CAF9"),
    x = 20,
    y = 620
) {
    private var tick = 0

    override fun onTick() {
        tick++
        if (tick % 30 != 0) return
        val used = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
        val max = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        setText("Mem: ${used}MB / ${max}MB")
    }
}
