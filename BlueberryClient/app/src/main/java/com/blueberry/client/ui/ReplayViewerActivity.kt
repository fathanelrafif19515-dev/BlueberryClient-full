package com.blueberry.client.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.blueberry.client.R
import com.blueberry.client.core.OkHttpNetworkClient
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

data class ReplayEventUi(
    val timestamp: Long,
    val eventType: String,
    val data: String
)

class ReplayViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REPLAY_ID = "replay_id"
    }

    private val network: OkHttpNetworkClient get() = LauncherActivity.networkClient
    private val events = mutableListOf<ReplayEventUi>()
    private val handler = Handler(Looper.getMainLooper())

    private var playbackIndex = 0
    private var isPlaying = false
    private var speed = 1.0
    private var startTime = 0L
    private var endTime = 0L
    private var replayId = ""

    private lateinit var eventLog: TextView
    private lateinit var viewerMeta: TextView
    private lateinit var playbackTime: TextView
    private lateinit var scrubber: SeekBar
    private lateinit var btnPlayPause: Button

    private var playThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_replay_viewer)

        replayId = intent.getStringExtra(EXTRA_REPLAY_ID).orEmpty()
        if (replayId.isEmpty()) {
            Toast.makeText(this, "No replay id", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        eventLog = findViewById(R.id.eventLog)
        viewerMeta = findViewById(R.id.viewerMeta)
        playbackTime = findViewById(R.id.playbackTime)
        scrubber = findViewById(R.id.timelineScrubber)
        btnPlayPause = findViewById(R.id.btnPlayPause)

        findViewById<Button>(R.id.btnRewind).setOnClickListener { seekTo(0) }
        findViewById<Button>(R.id.btnStepBack).setOnClickListener {
            pause(); seekTo((playbackIndex - 1).coerceAtLeast(0))
        }
        findViewById<Button>(R.id.btnStepFwd).setOnClickListener {
            pause(); seekTo((playbackIndex + 1).coerceAtMost(events.lastIndex.coerceAtLeast(0)))
        }
        findViewById<Button>(R.id.btnFastFwd).setOnClickListener {
            seekTo((playbackIndex + 10).coerceAtMost(events.lastIndex.coerceAtLeast(0)))
        }
        btnPlayPause.setOnClickListener {
            if (isPlaying) pause() else play()
        }

        findViewById<Button>(R.id.btnSpeed05).setOnClickListener { speed = 0.5 }
        findViewById<Button>(R.id.btnSpeed1).setOnClickListener { speed = 1.0 }
        findViewById<Button>(R.id.btnSpeed2).setOnClickListener { speed = 2.0 }

        scrubber.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && events.isNotEmpty()) {
                    seekTo((progress / 1000.0 * events.lastIndex).toInt())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { pause() }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        network.onMessage("replay_meta") { data ->
            startTime = (data["startTime"] as? Number)?.toLong() ?: 0L
            endTime = (data["endTime"] as? Number)?.toLong() ?: 0L
            runOnUiThread {
                viewerMeta.text = "Replay ${replayId.take(8)} · player ${data["playerId"]}"
            }
        }

        network.onMessage("replay_events") { data ->
            appendEvents(data["events"])
            runOnUiThread { updateUi() }
        }

        network.onMessage("replay_done") { _ ->
            runOnUiThread {
                viewerMeta.text = "${viewerMeta.text} · ${events.size} events loaded"
                updateUi()
            }
        }

        network.onMessage("replay_error") { data ->
            runOnUiThread {
                Toast.makeText(this, data["message"]?.toString() ?: "Replay error", Toast.LENGTH_LONG)
                    .show()
            }
        }

        if (!network.isConnected) {
            Toast.makeText(this, "Not connected to server", Toast.LENGTH_LONG).show()
        } else {
            network.send("replay_play", mapOf("replayId" to replayId, "fromTimestamp" to 0))
        }
    }

    private fun appendEvents(raw: Any?) {
        when (raw) {
            is JSONArray -> {
                for (i in 0 until raw.length()) {
                    val obj = raw.getJSONObject(i)
                    events.add(
                        ReplayEventUi(
                            timestamp = obj.optLong("Timestamp", obj.optLong("timestamp", 0)),
                            eventType = obj.optString("EventType", obj.optString("eventType", "?")),
                            data = obj.opt("Data")?.toString()
                                ?: obj.opt("data")?.toString().orEmpty()
                        )
                    )
                }
            }
            is List<*> -> {
                raw.forEach { entry ->
                    if (entry is Map<*, *>) {
                        events.add(
                            ReplayEventUi(
                                timestamp = (entry["Timestamp"] as? Number)?.toLong()
                                    ?: (entry["timestamp"] as? Number)?.toLong() ?: 0L,
                                eventType = (entry["EventType"] ?: entry["eventType"])?.toString() ?: "?",
                                data = (entry["Data"] ?: entry["data"])?.toString().orEmpty()
                            )
                        )
                    }
                }
            }
        }
    }

    private fun play() {
        if (events.isEmpty()) {
            Toast.makeText(this, "No events loaded yet", Toast.LENGTH_SHORT).show()
            return
        }
        isPlaying = true
        btnPlayPause.text = "⏸"
        playThread = thread(name = "ReplayPlayback") {
            while (isPlaying && playbackIndex < events.size) {
                val evt = events[playbackIndex]
                handler.post { showEvent(evt) }
                playbackIndex++
                handler.post { updateUi() }

                val delay = ((16.0 / speed).toLong()).coerceAtLeast(1)
                try {
                    Thread.sleep(delay)
                } catch (_: InterruptedException) {
                    break
                }
            }
            handler.post {
                isPlaying = false
                btnPlayPause.text = "►"
            }
        }
    }

    private fun pause() {
        isPlaying = false
        playThread?.interrupt()
        btnPlayPause.text = "►"
    }

    private fun seekTo(index: Int) {
        playbackIndex = index.coerceIn(0, events.size.coerceAtLeast(1) - 1)
        if (events.isNotEmpty()) showEvent(events[playbackIndex])
        updateUi()
    }

    private fun showEvent(evt: ReplayEventUi) {
        val line = "[${formatMs(evt.timestamp - startTime)}] ${evt.eventType}: ${evt.data.take(80)}\n"
        eventLog.append(line)
        // Keep log from growing forever
        if (eventLog.text.length > 8000) {
            eventLog.text = eventLog.text.takeLast(4000)
        }
    }

    private fun updateUi() {
        val total = if (endTime > startTime) endTime - startTime
        else if (events.isNotEmpty()) events.last().timestamp - events.first().timestamp
        else 0L
        val current = if (events.isNotEmpty() && playbackIndex < events.size) {
            events[playbackIndex].timestamp - (events.firstOrNull()?.timestamp ?: startTime)
        } else 0L

        playbackTime.text = "${formatMs(current)} / ${formatMs(total)}"
        if (events.size > 1) {
            scrubber.progress = ((playbackIndex.toDouble() / events.lastIndex) * 1000).toInt()
        }
    }

    private fun formatMs(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%02d:%02d".format(s / 60, s % 60)
    }

    override fun onDestroy() {
        pause()
        super.onDestroy()
    }
}
