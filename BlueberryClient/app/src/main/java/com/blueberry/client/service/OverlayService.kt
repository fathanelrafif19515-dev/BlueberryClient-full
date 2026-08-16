package com.blueberry.client.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.blueberry.client.R
import com.blueberry.client.core.GameState
import com.blueberry.client.core.ModuleBootstrap
import com.blueberry.client.core.ModuleContext
import com.blueberry.client.core.ModuleRegistry
import com.blueberry.client.core.OkHttpNetworkClient
import com.blueberry.client.core.OverlayController
import com.blueberry.client.modules.ProximityChatModule
import com.blueberry.client.ui.ClickGuiActivity
import com.blueberry.client.ui.LauncherActivity
import com.blueberry.client.ui.PauseMenuOverlay

class OverlayService : Service() {

    companion object {
        const val EXTRA_SERVER_URL = "server_url"
        private const val CHANNEL_ID = "blueberry_overlay"
        private const val NOTIF_ID = 42
        private const val TAG = "OverlayService"
        private const val PREFS = "blueberry"
        private const val KEY_SERVER_URL = "server_url"
        private const val DEFAULT_URL = "ws://192.168.1.100:9999/"
    }

    private lateinit var overlayController: OverlayController
    private lateinit var pauseMenuOverlay: PauseMenuOverlay
    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null
    private var networkClient: OkHttpNetworkClient = LauncherActivity.networkClient

    override fun onCreate() {
        super.onCreate()
        BlueberryAccessibilityService.overlayRunning = true
        startAsForeground()

        overlayController = OverlayController(this)
        pauseMenuOverlay = PauseMenuOverlay(this, overlayController)

        val moduleContext = ModuleContext(
            overlayController = overlayController,
            networkClient = networkClient
        )
        ModuleBootstrap.ensureRegistered(moduleContext, preferRebind = true)

        // Register draw callbacks for HUD modules
        ModuleRegistry.all().forEach { module ->
            overlayController.addDrawCallback { canvas, controller ->
                module.onDraw(canvas, controller)
            }
        }

        (ModuleRegistry.get("proximity_chat") as? ProximityChatModule)?.bindContext(this)

        startTickLoop()
        showFloatingControlsHint()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_SERVER_URL)
            ?: getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_SERVER_URL, DEFAULT_URL)
            ?: DEFAULT_URL

        connectToServer(url)
        return START_STICKY
    }

    private fun connectToServer(serverUrl: String) {
        if (networkClient.isConnected) {
            Log.d(TAG, "Already connected")
            sendPlayerConnect()
            return
        }

        networkClient.connect(
            serverUrl = serverUrl,
            onConnected = {
                Log.d(TAG, "Connected to server")
                sendPlayerConnect()
            },
            onError = { error ->
                Log.e(TAG, "Connection failed: $error")
            }
        )
    }

    private fun sendPlayerConnect() {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
        val name = "Player-${deviceId.takeLast(4)}"
        GameState.playerName = name
        networkClient.send(
            "player_connect",
            mapOf("playerId" to deviceId, "displayName" to name)
        )
    }

    private fun startTickLoop() {
        ModuleRegistry.get("fps_counter")?.let {
            if (!it.isEnabled) ModuleRegistry.toggle(it.id)
        }

        tickRunnable = object : Runnable {
            override fun run() {
                ModuleRegistry.tickAll()
                handler.postDelayed(this, 16)
            }
        }
        handler.post(tickRunnable!!)
    }

    private fun showFloatingControlsHint() {
        val hint = TextView(this).apply {
            text = "BB · tap=GUI · long=REC"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(160, 0, 0, 0))
            setPadding(20, 12, 20, 12)
            textSize = 12f
            setOnLongClickListener {
                pauseMenuOverlay.toggle()
                true
            }
            setOnClickListener {
                startActivity(
                    Intent(this@OverlayService, ClickGuiActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        overlayController.addView(
            key = "bb_hint",
            view = hint,
            x = 20,
            y = 40,
            gravity = Gravity.TOP or Gravity.START
        )
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Blueberry Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, LauncherActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Blueberry Client")
            .setContentText("Overlay active")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()

        startForeground(NOTIF_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        BlueberryAccessibilityService.overlayRunning = false
        tickRunnable?.let { handler.removeCallbacks(it) }
        pauseMenuOverlay.hide()
        overlayController.removeAll()
        ModuleRegistry.all().filter { it.isEnabled }.forEach { it.onDisable() }
    }
}
