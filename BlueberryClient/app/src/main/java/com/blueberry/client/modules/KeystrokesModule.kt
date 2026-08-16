package com.blueberry.client.modules

import android.graphics.Color

class KeystrokesModule : HudTextModule(
    id = "keystrokes",
    displayName = "Keystrokes",
    description = "WASD / click indicator (demo)",
    color = Color.WHITE,
    x = 20,
    y = 500
) {
    @Volatile var w = false
    @Volatile var a = false
    @Volatile var s = false
    @Volatile var d = false
    @Volatile var lmb = false
    @Volatile var rmb = false

    override fun onTick() {
        fun cell(on: Boolean, label: String) = if (on) "[$label]" else " $label "
        setText(
            "  ${cell(w, "W")}\n" +
                "${cell(a, "A")}${cell(s, "S")}${cell(d, "D")}\n" +
                "${cell(lmb, "LMB")} ${cell(rmb, "RMB")}"
        )
    }
}
