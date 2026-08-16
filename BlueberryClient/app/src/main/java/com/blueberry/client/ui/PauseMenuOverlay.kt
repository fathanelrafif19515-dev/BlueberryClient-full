package com.blueberry.client.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.blueberry.client.core.ModuleRegistry
import com.blueberry.client.core.OverlayController
import com.blueberry.client.modules.ReplayModule

/**
 * Overlay replay controls shown when ESC / pause is detected.
 * Option B: key-event driven (no Accessibility window-title scan required).
 */
class PauseMenuOverlay(
    private val context: Context,
    private val overlayController: OverlayController
) {
    companion object {
        const val VIEW_KEY = "pause_replay_controls"
    }

    private var visible = false

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) return false
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE || event.keyCode == KeyEvent.KEYCODE_BACK) {
            toggle()
            return true
        }
        // Optional hotkey: R toggles controls when overlay service holds focus window
        if (event.keyCode == KeyEvent.KEYCODE_R && event.isCtrlPressed) {
            toggle()
            return true
        }
        return false
    }

    fun toggle() {
        if (visible) hide() else show()
    }

    fun show() {
        if (visible) return
        val replay = ModuleRegistry.get("replay") as? ReplayModule ?: return

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(200, 20, 20, 20))
            setPadding(24, 20, 24, 20)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(context).apply {
            text = "Blueberry Replay"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 12)
        }
        layout.addView(title)

        val timerView = TextView(context).apply {
            text = "Idle"
            setTextColor(Color.parseColor("#FF6B6B"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }
        replay.bindTimerView(timerView)
        layout.addView(timerView)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        row.addView(makeBtn("REC") { replay.onRecClicked() })
        row.addView(makeBtn("PAUSE") { replay.onPauseClicked() })
        row.addView(makeBtn("STOP") { replay.onStopClicked() })
        row.addView(makeBtn("HIDE") { hide() })

        layout.addView(row)

        overlayController.addView(
            key = VIEW_KEY,
            view = layout,
            x = 0,
            y = 200,
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP,
            focusable = true
        )
        visible = true
    }

    fun hide() {
        overlayController.removeView(VIEW_KEY)
        visible = false
    }

    private fun makeBtn(label: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            textSize = 11f
            setPadding(16, 8, 16, 8)
            setOnClickListener { onClick() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(6, 0, 6, 0)
            layoutParams = lp
        }
    }
}
