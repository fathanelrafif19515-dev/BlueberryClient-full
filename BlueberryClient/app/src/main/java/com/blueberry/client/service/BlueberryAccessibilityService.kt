package com.blueberry.client.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.blueberry.client.core.ModuleRegistry
import com.blueberry.client.core.ServerDiscovery
import com.blueberry.client.modules.CpsModule
import com.blueberry.client.ui.LauncherActivity

/**
 * Detects when Minecraft opens → auto-starts Blueberry overlay.
 * Also tracks clicks for CPS module.
 */
class BlueberryAccessibilityService : AccessibilityService() {

    companion object {
        const val MINECRAFT_PACKAGE = "com.mojang.minecraftpe"
        @Volatile var isMinecraftOpen = false
        @Volatile var overlayRunning = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                (ModuleRegistry.get("cps") as? CpsModule)?.registerClick()
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                when (pkg) {
                    MINECRAFT_PACKAGE -> {
                        if (!isMinecraftOpen) {
                            isMinecraftOpen = true
                            onMinecraftOpened()
                        }
                    }
                    packageName -> { /* our app */ }
                    else -> {
                        if (isMinecraftOpen && pkg != MINECRAFT_PACKAGE) {
                            isMinecraftOpen = false
                        }
                    }
                }
            }
        }
    }

    private fun onMinecraftOpened() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Minecraft detected! Grant overlay permission in Blueberry app first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val serverUrl = ServerDiscovery.lastServerUrl
            ?: applicationContext.getSharedPreferences("blueberry", MODE_PRIVATE)
                .getString("server_url", null)

        if (serverUrl == null) {
            Toast.makeText(
                this,
                "Minecraft detected! Open Blueberry app on Wi-Fi to find server first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (overlayRunning) return

        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_SERVER_URL, serverUrl)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        overlayRunning = true

        // Ensure connected
        if (!LauncherActivity.networkClient.isConnected) {
            LauncherActivity.networkClient.connect(
                serverUrl,
                onConnected = { },
                onError = { }
            )
        }

        Toast.makeText(this, "Blueberry overlay started", Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {}
}
