package com.blueberry.client.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.blueberry.client.R
import com.blueberry.client.core.OkHttpNetworkClient
import com.blueberry.client.core.ServerDiscovery
import com.blueberry.client.service.BlueberryAccessibilityService
import com.blueberry.client.service.OverlayService
import kotlinx.coroutines.launch

class LauncherActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_RECORD = 1001
        val networkClient = OkHttpNetworkClient()
    }

    private val minecraftPackage = "com.mojang.minecraftpe"

    private lateinit var pcIpInput: EditText
    private lateinit var connectionStatus: TextView
    private lateinit var wifiStatus: TextView
    private lateinit var minecraftStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        pcIpInput = findViewById(R.id.pcIpInput)
        connectionStatus = findViewById(R.id.connectionStatus)
        wifiStatus = findViewById(R.id.wifiStatus)
        minecraftStatus = findViewById(R.id.minecraftStatus)

        // Pre-fill last working IP or guess from phone subnet
        val saved = ServerDiscovery.getSavedIp(this)
        pcIpInput.setText(saved ?: guessPcIp())

        findViewById<Button>(R.id.btnConnect).setOnClickListener { connectManual() }
        findViewById<Button>(R.id.btnAutoFind).setOnClickListener { connectAuto() }
        findViewById<Button>(R.id.btnGrantOverlay).setOnClickListener { grantOverlay() }
        findViewById<Button>(R.id.btnGrantAudio).setOnClickListener { requestAudio() }
        findViewById<Button>(R.id.btnEnableAccessibility).setOnClickListener { openAccessibility() }
        findViewById<Button>(R.id.btnLaunchMinecraft).setOnClickListener { launchMinecraft() }

        updateNetworkUi()
        updateMinecraftStatus()
    }

    override fun onResume() {
        super.onResume()
        updateNetworkUi()
        updateMinecraftStatus()
        updateAccessibilityButton()
        if (networkClient.isConnected) {
            connectionStatus.text = "Connected to ${ServerDiscovery.lastServerIp}"
            connectionStatus.setTextColor(Color.parseColor("#4CAF50"))
        }
    }

    private fun guessPcIp(): String {
        val phoneIp = ServerDiscovery.getPhoneIp(this) ?: return ""
        val prefix = ServerDiscovery.getIpPrefix(this)
        // Common: PC is .1 (router) or same subnet — leave prefix hint in placeholder, user types full IP
        return if (phoneIp.isNotEmpty()) "${prefix}1" else ""
    }

    private fun connectManual() {
        val ip = pcIpInput.text.toString().trim()
        if (ip.isEmpty() || !ip.contains('.')) {
            Toast.makeText(this, "Enter the IP from PC screen (e.g. 192.168.1.105)", Toast.LENGTH_LONG).show()
            return
        }

        if (!ServerDiscovery.isOnLocalNetwork(this)) {
            Toast.makeText(this, "Connect phone to Wi-Fi first (same as PC)", Toast.LENGTH_LONG).show()
            return
        }

        connectionStatus.text = "Testing $ip..."
        connectionStatus.setTextColor(Color.parseColor("#FFC107"))

        lifecycleScope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ServerDiscovery.testConnection(ip)
            }

            if (!ok) {
                connectionStatus.text = "Can't reach $ip — check START.bat is running on PC"
                connectionStatus.setTextColor(Color.parseColor("#FF5252"))
                Toast.makeText(
                    this@LauncherActivity,
                    "Failed. Is START.bat running? Same Wi-Fi? Allow firewall on PC?",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val url = ServerDiscovery.buildUrl(ip)
            ServerDiscovery.save(this@LauncherActivity, url)
            connectWebSocket(url)
        }
    }

    private fun connectAuto() {
        if (!ServerDiscovery.isOnLocalNetwork(this)) {
            Toast.makeText(this, "Connect phone to Wi-Fi first", Toast.LENGTH_LONG).show()
            return
        }

        connectionStatus.text = "Searching..."
        connectionStatus.setTextColor(Color.parseColor("#FFC107"))

        lifecycleScope.launch {
            val url = ServerDiscovery.autoDiscover(this@LauncherActivity)
            if (url == null) {
                connectionStatus.text = "Auto-find failed — type PC IP manually"
                connectionStatus.setTextColor(Color.parseColor("#FF5252"))
                return@launch
            }
            pcIpInput.setText(ServerDiscovery.lastServerIp)
            connectWebSocket(url)
        }
    }

    private fun connectWebSocket(url: String) {
        connectionStatus.text = "Connecting..."
        connectionStatus.setTextColor(Color.parseColor("#FFC107"))

        networkClient.connect(
            serverUrl = url,
            onConnected = {
                runOnUiThread {
                    connectionStatus.text = "Connected to ${ServerDiscovery.lastServerIp}"
                    connectionStatus.setTextColor(Color.parseColor("#4CAF50"))
                    Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show()

                    val id = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "phone"
                    networkClient.send("player_connect", mapOf(
                        "playerId" to id,
                        "displayName" to "Player-${id.takeLast(4)}"
                    ))
                    startOverlayIfReady(url)
                }
            },
            onError = { err ->
                runOnUiThread {
                    connectionStatus.text = "Error: $err"
                    connectionStatus.setTextColor(Color.parseColor("#FF5252"))
                }
            }
        )
    }

    private fun startOverlayIfReady(url: String) {
        if (!Settings.canDrawOverlays(this)) return
        if (BlueberryAccessibilityService.overlayRunning) return
        if (isAccessibilityEnabled()) return // overlay starts when MC opens

        val i = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_SERVER_URL, url)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
        else startService(i)
    }

    private fun updateNetworkUi() {
        val phoneIp = ServerDiscovery.getPhoneIp(this)
        wifiStatus.text = if (phoneIp != null)
            "Phone on network · $phoneIp (same Wi-Fi as PC)"
        else
            "No Wi-Fi — connect phone to same network as PC"
        wifiStatus.setTextColor(
            if (phoneIp != null) Color.parseColor("#81C784") else Color.parseColor("#FF5252")
        )
    }

    private fun updateMinecraftStatus() {
        minecraftStatus.text = when {
            BlueberryAccessibilityService.isMinecraftOpen -> "Minecraft open — overlay running"
            isMinecraftInstalled() -> "After connect → open Minecraft"
            else -> ""
        }
    }

    private fun updateAccessibilityButton() {
        findViewById<Button>(R.id.btnEnableAccessibility).text =
            if (isAccessibilityEnabled()) "Minecraft auto-detect: ON"
            else "Auto-start when Minecraft opens"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return s.contains(packageName)
    }

    private fun grantOverlay() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay already allowed", Toast.LENGTH_SHORT).show()
        } else {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }

    private fun openAccessibility() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        Toast.makeText(this, "Turn ON Blueberry Client", Toast.LENGTH_LONG).show()
    }

    private fun requestAudio() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Mic already allowed", Toast.LENGTH_SHORT).show()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_RECORD)
        }
    }

    private fun launchMinecraft() {
        packageManager.getLaunchIntentForPackage(minecraftPackage)?.let { startActivity(it) }
            ?: Toast.makeText(this, "Install Minecraft first", Toast.LENGTH_SHORT).show()
    }

    private fun isMinecraftInstalled() = try {
        packageManager.getPackageInfo(minecraftPackage, 0); true
    } catch (_: Exception) { false }
}
