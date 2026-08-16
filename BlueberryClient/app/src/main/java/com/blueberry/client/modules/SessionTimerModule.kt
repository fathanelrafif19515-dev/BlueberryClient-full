package com.blueberry.client.modules

import android.graphics.Color

class SessionTimerModule : HudTextModule(
    id = "session_timer",
    displayName = "Session Timer",
    description = "Time since overlay started",
    color = Color.parseColor("#B0BEC5"),
    x = 20,
    y = 660
) {
    private var startAt = 0L

    override fun onEnable() {
        super.onEnable()
        startAt = System.currentTimeMillis()
    }

    override fun onTick() {
        if (startAt == 0L) startAt = System.currentTimeMillis()
        val elapsed = System.currentTimeMillis() - startAt
        val h = elapsed / 3600000
        val m = (elapsed % 3600000) / 60000
        val s = (elapsed % 60000) / 1000
        setText("Session: %02d:%02d:%02d".format(h, m, s))
    }
}
