package com.blueberry.client.modules

import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClockModule : HudTextModule(
    id = "clock",
    displayName = "Clock",
    description = "Local time",
    color = Color.WHITE,
    x = 20,
    y = 380
) {
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onTick() {
        setText(fmt.format(Date()))
    }
}
