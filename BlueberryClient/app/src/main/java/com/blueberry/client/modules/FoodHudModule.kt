package com.blueberry.client.modules

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.blueberry.client.core.*

/**
 * Food HUD module — AppleSkin style.
 * Shows hunger bar, saturation overlay, and exhaustion.
 * Client-only, reads from GameState (updated by server packets or local fallback).
 */
class FoodHudModule : IModule {
    override val id = "food_hud"
    override val displayName = "Food HUD (AppleSkin)"
    override val category = ModuleCategory.HUD
    override val description = "Tampilkan hunger bar + saturation seperti AppleSkin"
    override var isEnabled = false

    private lateinit var paintBg: Paint
    private lateinit var paintHunger: Paint
    private lateinit var paintSaturation: Paint
    private lateinit var paintExhaustion: Paint
    private lateinit var paintText: Paint

    private var lastHunger = 20f
    private var lastSaturation = 20f
    private var animProgress = 0f

    override fun onLoad(context: ModuleContext) {
        paintBg = Paint().apply {
            color = Color.argb(180, 0, 0, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        paintHunger = Paint().apply {
            color = Color.rgb(200, 80, 80)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        paintSaturation = Paint().apply {
            color = Color.rgb(255, 180, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        paintExhaustion = Paint().apply {
            color = Color.argb(120, 255, 255, 255)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        paintText = Paint().apply {
            color = Color.WHITE
            textSize = 14f
            isAntiAlias = true
            fakeBoldText = true
        }
    }

    override fun onTick() {
        // Smooth animation toward current values
        val targetHunger = GameState.food.toFloat()
        val targetSat = GameState.maxHealth // using maxHealth as saturation proxy for now
        lastHunger += (targetHunger - lastHunger) * 0.15f
        lastSaturation += (targetSat - lastSaturation) * 0.15f
        animProgress = min(animProgress + 0.05f, 1f)
    }

    override fun onDraw(canvas: Canvas, overlayController: OverlayController) {
        if (!isEnabled) return

        val width = overlayController.screenWidth
        val height = overlayController.screenHeight

        // Position: bottom-left, above hotbar area
        val barWidth = 182f
        val barHeight = 18f
        val margin = 16f
        val x = margin
        val y = height - margin - barHeight - 40f // above hotbar

        // Background
        val bgRect = RectF(x, y, x + barWidth, y + barHeight)
        canvas.drawRoundRect(bgRect, 4f, 4f, paintBg)

        // Saturation (gold) - drawn first so hunger covers it
        val satWidth = (barWidth - 4) * (lastSaturation / 20f)
        if (satWidth > 0) {
            val satRect = RectF(x + 2, y + 2, x + 2 + satWidth, y + barHeight - 2)
            canvas.drawRoundRect(satRect, 3f, 3f, paintSaturation)
        }

        // Hunger (red)
        val hungerWidth = (barWidth - 4) * (lastHunger / 20f)
        if (hungerWidth > 0) {
            val hungerRect = RectF(x + 2, y + 2, x + 2 + hungerWidth, y + barHeight - 2)
            canvas.drawRoundRect(hungerRect, 3f, 3f, paintHunger)
        }

        // Exhaustion indicator (small bar on top)
        val exhaustion = (GameState.maxHealth - GameState.health) / 20f // placeholder
        val exWidth = (barWidth - 4) * exhaustion
        if (exWidth > 0) {
            val exRect = RectF(x + 2, y - 6, x + 2 + exWidth, y - 2)
            canvas.drawRoundRect(exRect, 2f, 2f, paintExhaustion)
        }

        // Text: "Hunger: 16/20  Sat: 12.4"
        val text = String.format("Hunger: %.0f/20  Sat: %.1f", lastHunger, lastSaturation)
        canvas.drawText(text, x, y - 10f, paintText)
    }
}