package com.blueberry.client.modules

import android.graphics.Color
import android.view.Gravity
import android.widget.TextView
import com.blueberry.client.core.IModule
import com.blueberry.client.core.ModuleCategory
import com.blueberry.client.core.ModuleContext

/**
 * Shared HUD text overlay helper — copy this pattern for simple modules.
 */
abstract class HudTextModule(
    override val id: String,
    override val displayName: String,
    override val description: String = "",
    private val color: Int = Color.GREEN,
    private val x: Int = 20,
    private val y: Int = 100
) : IModule {
    override val category = ModuleCategory.HUD
    override var isEnabled = false

    protected lateinit var ctx: ModuleContext
    protected var textView: TextView? = null

    override fun onLoad(context: ModuleContext) {
        ctx = context
    }

    override fun onEnable() {
        val tv = TextView(ctx.androidContext).apply {
            text = displayName
            setTextColor(color)
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            setPadding(16, 8, 16, 8)
            textSize = 13f
        }
        ctx.overlayController.addView(id, tv, x = x, y = y, gravity = Gravity.TOP or Gravity.START)
        textView = tv
    }

    override fun onDisable() {
        ctx.overlayController.removeView(id)
        textView = null
    }

    protected fun setText(value: String) {
        textView?.text = value
    }
}
