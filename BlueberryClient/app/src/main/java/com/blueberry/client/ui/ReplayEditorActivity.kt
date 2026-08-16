package com.blueberry.client.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blueberry.client.R
import com.blueberry.client.core.OkHttpNetworkClient
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReplayItem(
    val id: String,
    val playerId: String,
    val startTime: Long,
    val endTime: Long?,
    val status: String
)

class ReplayEditorActivity : AppCompatActivity() {

    private val network: OkHttpNetworkClient get() = LauncherActivity.networkClient
    private lateinit var adapter: ReplayAdapter
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_replay_editor)

        statusText = findViewById(R.id.replayStatus)
        adapter = ReplayAdapter(
            onPlay = { item ->
                startActivity(
                    Intent(this, ReplayViewerActivity::class.java)
                        .putExtra(ReplayViewerActivity.EXTRA_REPLAY_ID, item.id)
                )
            },
            onDelete = { item ->
                if (!network.isConnected) {
                    Toast.makeText(this, "Not connected to server", Toast.LENGTH_SHORT).show()
                    return@ReplayAdapter
                }
                network.send("replay_delete", mapOf("replayId" to item.id))
            }
        )

        findViewById<RecyclerView>(R.id.replayList).apply {
            layoutManager = LinearLayoutManager(this@ReplayEditorActivity)
            adapter = maria.s@example.com
        }

        findViewById<Button>(R.id.btnRefreshReplays).setOnClickListener { requestList() }

        network.onMessage("replay_list") { data ->
            val list = parseReplayList(data["replays"])
            runOnUiThread {
                adapter.submit(list)
                statusText.text = if (list.isEmpty()) "No replays yet" else "${list.size} replay(s)"
            }
        }

        network.onMessage("replay_deleted") { data ->
            runOnUiThread {
                Toast.makeText(this, "Deleted ${data["replayId"]}", Toast.LENGTH_SHORT).show()
                requestList()
            }
        }

        requestList()
    }

    private fun requestList() {
        if (!network.isConnected) {
            statusText.text = "Not connected — connect from launcher first"
            return
        }
        statusText.text = "Fetching..."
        network.send("replay_get_list", emptyMap())
    }

    private fun parseReplayList(raw: Any?): List<ReplayItem> {
        val items = mutableListOf<ReplayItem>()
        when (raw) {
            is JSONArray -> {
                for (i in 0 until raw.length()) {
                    parseOne(raw.getJSONObject(i))?.let { items.add(it) }
                }
            }
            is List<*> -> {
                raw.forEach { entry ->
                    when (entry) {
                        is Map<*, *> -> parseOneMap(entry)?.let { items.add(it) }
                        is JSONObject -> parseOne(entry)?.let { items.add(it) }
                    }
                }
            }
            is String -> {
                runCatching {
                    val arr = JSONArray(raw)
                    for (i in 0 until arr.length()) {
                        parseOne(arr.getJSONObject(i))?.let { items.add(it) }
                    }
                }
            }
        }
        return items
    }

    private fun parseOne(obj: JSONObject): ReplayItem? {
        val id = obj.optString("Id", obj.optString("id", ""))
        if (id.isEmpty()) return null
        return ReplayItem(
            id = id,
            playerId = obj.optString("PlayerId", obj.optString("playerId", "")),
            startTime = obj.optLong("StartTime", obj.optLong("startTime", 0)),
            endTime = if (obj.has("EndTime") || obj.has("endTime"))
                obj.optLong("EndTime", obj.optLong("endTime", 0)) else null,
            status = obj.optString("Status", obj.optString("status", ""))
        )
    }

    private fun parseOneMap(map: Map<*, *>): ReplayItem? {
        val id = (map["Id"] ?: map["id"])?.toString() ?: return null
        return ReplayItem(
            id = id,
            playerId = (map["PlayerId"] ?: map["playerId"])?.toString().orEmpty(),
            startTime = ((map["StartTime"] ?: map["startTime"]) as? Number)?.toLong() ?: 0L,
            endTime = ((map["EndTime"] ?: map["endTime"]) as? Number)?.toLong(),
            status = (map["Status"] ?: map["status"])?.toString().orEmpty()
        )
    }

    class ReplayAdapter(
        private val onPlay: (ReplayItem) -> Unit,
        private val onDelete: (ReplayItem) -> Unit
    ) : RecyclerView.Adapter<ReplayAdapter.VH>() {

        private val items = mutableListOf<ReplayItem>()
        private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        fun submit(list: List<ReplayItem>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_replay, parent, false)
            return VH(view)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position], dateFmt, onPlay, onDelete)
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            private val name: TextView = view.findViewById(R.id.replayName)
            private val meta: TextView = view.findViewById(R.id.replayMeta)
            private val play: Button = view.findViewById(R.id.btnPlayReplay)
            private val delete: Button = view.findViewById(R.id.btnDeleteReplay)

            fun bind(
                item: ReplayItem,
                dateFmt: SimpleDateFormat,
                onPlay: (ReplayItem) -> Unit,
                onDelete: (ReplayItem) -> Unit
            ) {
                name.text = "Replay ${item.id.take(8)}"
                val durationMs = if (item.endTime != null && item.endTime > item.startTime) {
                    item.endTime - item.startTime
                } else 0L
                val durSec = durationMs / 1000
                val duration = "%d:%02d".format(durSec / 60, durSec % 60)
                meta.text = "${dateFmt.format(Date(item.startTime))} · $duration · ${item.status}"
                play.setOnClickListener { onPlay(item) }
                delete.setOnClickListener { onDelete(item) }
            }
        }
    }
}
