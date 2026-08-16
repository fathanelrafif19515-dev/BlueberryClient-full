package com.blueberry.client.modules

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.util.Log
import androidx.core.content.ContextCompat
import com.blueberry.client.core.*
import org.concentus.OpusApplication
import org.concentus.OpusDecoder
import org.concentus.OpusEncoder
import java.util.Base64
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

private const val TAG = "ProxChat"
private const val SAMPLE_RATE = 48000
private const val FRAME_SIZE = 960 // 20ms @ 48kHz

class ProximityChatModule : IModule {
    override val id = "proximity_chat"
    override val displayName = "Proximity Chat"
    override val description = "Voice chat with distance + ambient filters"
    override val category = ModuleCategory.PROXIMITY_CHAT
    override var isEnabled = false

    private lateinit var ctx: ModuleContext
    private var network: OkHttpNetworkClient? = null
    private var appContext: Context? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val playbackQueue = LinkedBlockingQueue<Triple<ShortArray, Float, String>>()
    private var isCapturing = false
    private var isPlaying = false
    var currentEnvironment = "normal"
    private val activeSpeakers = mutableMapOf<String, Long>()

    private var encoder: OpusEncoder? = null
    private var decoder: OpusDecoder? = null

    override fun onLoad(context: ModuleContext) {
        ctx = context
        network = context.networkClient as? OkHttpNetworkClient
        network?.onMessage("proximity_chat_audio") { handleIncomingAudio(it) }
    }

    fun bindContext(c: Context) { appContext = c }

    override fun onEnable() {
        if (appContext == null) appContext = ctx.androidContext
        if (!hasPermission()) { Log.w(TAG, "No RECORD_AUDIO permission"); return }
        encoder = OpusEncoder(SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_VOIP).also { it.bitrate = 24000 }
        decoder = OpusDecoder(SAMPLE_RATE, 1)
        startCapture()
        startPlayback()
    }

    override fun onDisable() {
        isCapturing = false; isPlaying = false
        audioRecord?.stop(); audioRecord?.release(); audioRecord = null
        audioTrack?.stop(); audioTrack?.release(); audioTrack = null
    }

    private fun startCapture() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, FRAME_SIZE * 4))
        audioRecord?.startRecording()
        isCapturing = true

        thread(name = "ProxChat-Capture") {
            val buf = ShortArray(FRAME_SIZE)
            while (isCapturing) {
                val read = audioRecord?.read(buf, 0, FRAME_SIZE) ?: break
                if (read <= 0 || network?.isConnected != true) continue
                currentEnvironment = GameState.environment
                encodeOpus(buf)?.let { encoded ->
                    network?.send("voice_chunk", mapOf(
                        "audioData" to Base64.getEncoder().encodeToString(encoded),
                        "environment" to currentEnvironment
                    ))
                }
            }
        }
    }

    private fun startPlayback() {
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(minBuf * 4).setTransferMode(AudioTrack.MODE_STREAM).build()
        audioTrack?.play()
        isPlaying = true

        thread(name = "ProxChat-Playback") {
            while (isPlaying) {
                val (pcm, vol, env) = playbackQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                val filtered = applyAmbientFilter(applyVolume(pcm, vol), env)
                audioTrack?.write(filtered, 0, filtered.size)
            }
        }
    }

    private fun handleIncomingAudio(data: Map<String, Any?>) {
        val b64 = data["audioData"] as? String ?: return
        val fromId = data["fromPlayerId"] as? String ?: "unknown"
        val vol = (data["volumeFactor"] as? Double)?.toFloat() ?: 1f
        val env = data["receiverEnvironment"] as? String ?: "normal"
        val pcm = decodeOpus(Base64.getDecoder().decode(b64)) ?: return
        playbackQueue.offer(Triple(pcm, vol, env))
        activeSpeakers[fromId] = System.currentTimeMillis()
    }

    // Ambient filters
    private fun applyAmbientFilter(pcm: ShortArray, env: String) = when (env) {
        "water" -> applyLowPass(pcm, 0.12f, 0.55f)
        "cave" -> applyEcho(pcm, 120, 0.4f)
        "nether" -> applyLowPass(applyEcho(pcm, 80, 0.35f), 0.3f, 0.8f)
        else -> pcm
    }

    private fun applyLowPass(pcm: ShortArray, alpha: Float, vol: Float): ShortArray {
        val out = ShortArray(pcm.size); var prev = 0f
        for (i in pcm.indices) {
            val s = pcm[i] / 32768f
            val f = prev + alpha * (s - prev); prev = f
            out[i] = (f * vol * 32767f).toInt().toShort()
        }
        return out
    }

    private fun applyEcho(pcm: ShortArray, delayMs: Int, decay: Float): ShortArray {
        val delay = SAMPLE_RATE * delayMs / 1000
        return ShortArray(pcm.size) { i ->
            val orig = pcm[i].toFloat()
            val d = if (i >= delay) pcm[i - delay].toFloat() * decay else 0f
            (orig + d).coerceIn(-32768f, 32767f).toInt().toShort()
        }
    }

    private fun applyVolume(pcm: ShortArray, f: Float) =
        if (f >= 1f) pcm else ShortArray(pcm.size) { i -> (pcm[i] * f).toInt().toShort() }

    // Opus
    private fun encodeOpus(pcm: ShortArray): ByteArray? = try {
        val out = ByteArray(4000)
        val n = encoder!!.encode(pcm, 0, FRAME_SIZE, out, 0, out.size)
        out.copyOf(n)
    } catch (e: Exception) { null }

    private fun decodeOpus(data: ByteArray): ShortArray? = try {
        val out = ShortArray(FRAME_SIZE)
        val n = decoder!!.decode(data, 0, data.size, out, 0, FRAME_SIZE, false)
        out.copyOf(n)
    } catch (e: Exception) { null }

    private fun hasPermission() = appContext?.let {
        ContextCompat.checkSelfPermission(it, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    } ?: false

    fun getActiveSpeakers() = activeSpeakers.filter { System.currentTimeMillis() - it.value < 500 }.keys.toList()
}
