package com.blueberry.client.core

import android.graphics.Canvas

/**
 * Setiap fitur (Replay, Proximity Chat, HUD, dll) implement interface ini.
 */
interface IModule {
    val id: String
    val displayName: String
    val category: ModuleCategory
    val description: String get() = ""

    var isEnabled: Boolean

    fun onLoad(context: ModuleContext) {}
    fun onEnable() {}
    fun onDisable() {}
    fun onTick() {}
    fun onDraw(canvas: Canvas, overlayController: OverlayController) {}
}

enum class ModuleCategory {
    HUD, VISUAL, UTILITY, COMBAT, MOVEMENT, AUDIO,
    SERVER_SPECIFIC, COSMETICS, REPLAY, PROXIMITY_CHAT, OPTIMIZATION, SETTINGS
}

/**
 * Dependency yang dibutuhkan module: akses overlay, network, Android context.
 */
class ModuleContext(
    val overlayController: OverlayController,
    val networkClient: NetworkClient
) {
    val androidContext get() = overlayController.context
}

/**
 * Shared game/player state that HUD modules can read.
 * Updated by ReplayModule position hooks or server packets.
 */
object GameState {
    @Volatile var x: Double = 0.0
    @Volatile var y: Double = 64.0
    @Volatile var z: Double = 0.0
    @Volatile var yaw: Float = 0f
    @Volatile var pitch: Float = 0f
    @Volatile var health: Float = 20f
    @Volatile var maxHealth: Float = 20f
    @Volatile var armor: Int = 0
    @Volatile var food: Int = 20
    @Volatile var pingMs: Long = -1
    @Volatile var fps: Int = 0
    @Volatile var cps: Int = 0
    @Volatile var environment: String = "normal"
    @Volatile var playerName: String = "Player"
}
