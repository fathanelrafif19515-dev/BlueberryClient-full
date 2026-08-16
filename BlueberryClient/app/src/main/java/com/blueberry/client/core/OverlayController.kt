package com.blueberry.client.core

import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager

/**
 * Ngatur elemen-elemen overlay (HUD text, ClickGUI, replay control buttons)
 * yang ditampilkan sebagai window terpisah di atas Minecraft.
 */
class OverlayController(val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val activeViews = mutableMapOf<String, View>()
    private var surfaceView: SurfaceView? = null
    private val drawCallbacks = mutableListOf<(Canvas, OverlayController) -> Unit>()

    fun addView(
        key: String,
        view: View,
        x: Int = 0,
        y: Int = 0,
        gravity: Int = Gravity.TOP or Gravity.START,
        width: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        height: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        focusable: Boolean = false
    ) {
        if (activeViews.containsKey(key)) return

        val flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }

        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = x
            this.y = y
        }

        windowManager.addView(view, params)
        activeViews[key] = view
    }

    fun updatePosition(key: String, x: Int, y: Int) {
        val view = activeViews[key] ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.x = x
        params.y = y
        windowManager.updateViewLayout(view, params)
    }

    fun removeView(key: String) {
        activeViews[key]?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
            activeViews.remove(key)
        }
    }

    fun removeAll() {
        activeViews.keys.toList().forEach { removeView(it) }
    }

    fun isViewActive(key: String) = activeViews.containsKey(key)

    fun getView(key: String): View? = activeViews[key]

    // Screen dimensions for HUD modules
    val screenWidth: Int
        get() = windowManager.defaultDisplay.width
    val screenHeight: Int
        get() = windowManager.defaultDisplay.height

    /** Register a custom draw callback for HUD modules (called every frame). */
    fun addDrawCallback(callback: (Canvas, OverlayController) -> Unit) {
        drawCallbacks.add(callback)
        ensureSurfaceView()
    }

    /** Remove a draw callback. */
    fun removeDrawCallback(callback: (Canvas, OverlayController) -> Unit) {
        drawCallbacks.remove(callback)
        if (drawCallbacks.isEmpty()) removeSurfaceView()
    }

    private fun ensureSurfaceView() {
        if (surfaceView != null) return
        surfaceView = SurfaceView(context).apply {
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSLUCENT)
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    startDrawLoop(holder)
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                override fun surfaceDestroyed(holder: SurfaceHolder) {}
            })
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        windowManager.addView(surfaceView!!, params)
    }

    private fun removeSurfaceView() {
        surfaceView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            surfaceView = null
        }
    }

    private fun startDrawLoop(holder: SurfaceHolder) {
        Thread {
            while (holder.surface.isValid) {
                val canvas = holder.lockCanvas()
                if (canvas != null) {
                    try {
                        canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
                        drawCallbacks.forEach { it(canvas, this@OverlayController) }
                    } finally {
                        holder.unlockCanvasAndPost(canvas)
                    }
                }
                Thread.sleep(16) // ~60 FPS
            }
        }.start()
    }
}
