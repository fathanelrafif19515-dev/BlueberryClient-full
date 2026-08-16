package com.blueberry.client.modules

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import com.blueberry.client.core.GameState
import com.blueberry.client.core.IModule
import com.blueberry.client.core.ModuleCategory
import com.blueberry.client.core.ModuleContext
import com.blueberry.client.core.OkHttpNetworkClient

class ReplayModule : IModule {
    override val id = "replay"
    override val displayName = "Replay Recorder"
    override val description = "Record & stream gameplay events to server"
    override val category = ModuleCategory.REPLAY
    override var isEnabled = true

    enum class State { IDLE, RECORDING, PAUSED }
    var state: State = State.IDLE
        private set

    private lateinit var ctx: ModuleContext
    private var network: OkHttpNetworkClient? = null
    private val handler = Handler(Looper.getMainLooper())

    private var timerView: TextView? = null
    private var recStartTime = 0L
    private var timerRunnable: Runnable? = null

    private var tickCounter = 0
    var currentEnvironment = "normal"

    override fun onLoad(context: ModuleContext) {
        ctx = context
        network = context.networkClient as? OkHttpNetworkClient
        network?.onMessage("replay_started") { data ->
            android.util.Log.d("ReplayModule", "Recording started: ${data["replayId"]}")
        }
        network?.onMessage("replay_stopped") { _ ->
            android.util.Log.d("ReplayModule", "Recording stopped")
        }
    }

    fun onRecClicked() {
        when (state) {
            State.IDLE -> startRecording()
            State.PAUSED -> resumeRecording()
            State.RECORDING -> {}
        }
    }

    fun onPauseClicked() {
        if (state != State.RECORDING) return
        state = State.PAUSED
        network?.send("replay_pause", emptyMap())
        stopTimer()
        timerView?.text = "Paused"
    }

    fun onStopClicked() {
        if (state == State.IDLE) return
        state = State.IDLE
        network?.send("replay_stop", emptyMap())
        stopTimer()
        timerView?.visibility = View.GONE
    }

    fun requestReplayList() = network?.send("replay_get_list", emptyMap()) ?: Unit

    fun playReplay(replayId: String, fromTimestamp: Long = 0) {
        network?.send("replay_play", mapOf("replayId" to replayId, "fromTimestamp" to fromTimestamp))
    }

    fun deleteReplay(replayId: String) {
        network?.send("replay_delete", mapOf("replayId" to replayId))
    }

    private fun startRecording() {
        state = State.RECORDING
        network?.send("replay_start", mapOf("serverId" to "main"))
        recStartTime = System.currentTimeMillis()
        timerView?.visibility = View.VISIBLE
        startTimer()
    }

    private fun resumeRecording() {
        state = State.RECORDING
        network?.send("replay_resume", emptyMap())
        startTimer()
    }

    override fun onTick() {
        tickCounter++
        if (tickCounter % 5 != 0) return
        tickCounter = 0
        if (state != State.RECORDING || network?.isConnected != true) return
        currentEnvironment = GameState.environment
        network?.send(
            "player_position",
            mapOf(
                "x" to GameState.x,
                "y" to GameState.y,
                "z" to GameState.z,
                "yaw" to GameState.yaw,
                "pitch" to GameState.pitch,
                "environment" to currentEnvironment
            )
        )
    }

    fun updatePosition(x: Double, y: Double, z: Double, yaw: Float = 0f, pitch: Float = 0f) {
        GameState.x = x
        GameState.y = y
        GameState.z = z
        GameState.yaw = yaw
        GameState.pitch = pitch
    }

    private fun startTimer() {
        stopTimer()
        timerRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - recStartTime
                val m = (elapsed / 60000).toInt()
                val s = ((elapsed % 60000) / 1000).toInt()
                timerView?.text = "REC %02d:%02d".format(m, s)
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
    }

    fun bindTimerView(view: TextView) {
        timerView = view
    }

    override fun onEnable() {}
    override fun onDisable() {
        onStopClicked()
    }
}
