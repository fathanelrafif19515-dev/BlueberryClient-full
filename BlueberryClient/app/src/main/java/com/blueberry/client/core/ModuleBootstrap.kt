package com.blueberry.client.core

import com.blueberry.client.modules.*

/**
 * Registers the default module set once. Safe to call from OverlayService or UI.
 * OverlayService should call with preferRebind=true so HUD views use the service context.
 */
object ModuleBootstrap {
    @Volatile private var ready = false

    fun ensureRegistered(ctx: ModuleContext, preferRebind: Boolean = false) {
        synchronized(this) {
            if (ready && ModuleRegistry.all().isNotEmpty()) {
                if (preferRebind) ModuleRegistry.rebind(ctx)
                return
            }

            ModuleRegistry.init(ctx)
            listOf(
                FpsCounterModule(),
                CoordinatesModule(),
                HealthModule(),
                ArmorModule(),
                PingModule(),
                CpsModule(),
                DirectionModule(),
                ClockModule(),
                BiomeHintModule(),
                SpeakersHudModule(),
                KeystrokesModule(),
                SaturationModule(),
                FoodHudModule(),
                MemoryModule(),
                SessionTimerModule(),
                ReplayModule(),
                ProximityChatModule()
            ).forEach { ModuleRegistry.register(it) }

            ready = true
        }
    }
}
