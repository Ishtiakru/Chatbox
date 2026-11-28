package com.example.saira

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import com.example.data.ai.GeminiKeyPool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Saira's real-time voice (Gemini Live, BidiGenerateContent over a WebSocket).
 *
 * The microphone audio is streamed while the person is still talking; the model hears it, answers with
 * its own (female) voice within ~1 s, and asks for phone actions through the `phone_action` tool.
 * Measured from CI with the app's key pool (gemini-3.8-live): ~0.13 s setup, ~1.1 s from the end of
 * speech to Saira's first sound, the "call ammu" tool call in ~1.1 s, quiet speech heard fine.
 */
class SairaLiveVoice(context: Context) {
    private val app = context.applicationContext
    private val pool = GeminiKeyPool(app)

    interface Listener {
        fun onInputText(text: String)
        fun onOutputText(text: String)
        fun onAudio(pcm24k: ByteArray)
        fun onToolCall(id: String, name: String, args: JSONObject)
        fun onTurnComplete()
        fun onInterrupted()
        fun onClosed(reason: String)
    }

    /** One open Live connection. */
    inner class Session internal constructor(val model: String) {
        @Volatile internal var ws: WebSocket? = null
        @Volatile var open = false
            internal set

        fun sendAudio(pcm: ShortArray, count: Int = pcm.size) {
            val w = ws ?: return
            val bytes = ByteArray(count * 2)
            for (i in 0 until count) {
                val v = pcm[i].toInt()
                bytes[2 * i] = (v and 0xFF).toByte()
                bytes[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
            }
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            w.send("{\"realtimeInput\":{\"audio\":{\"data\":\"$b64\",\"mimeType\":\"audio/pcm;rate=16000\"}}}")
        }

        fun sendToolResponse(id: String, name: String, result: String) {
            val r = JSONObject().put("toolResponse", JSONObject().put("functionResponses", JSONArray().put(
                JSONObject().put("id", id).put("name", name).put("response", JSONObject().put("result", result))
            )))
            ws?.send(r.toString())
        }

        fun close() {
            open = false
            runCatching { ws?.close(1000, "idle") }
            ws = null
        }
    }

    /**
     * Opens a session (tries models × a few keys). Returns null if Live can't be reached right now —
     * the caller then falls back to the classic record → transcribe → speak pipeline.
     */
    suspend fun connect(listener: Listener): Session? = withContext(Dispatchers.IO) {
        val keys = runCatching { pool.availableKeys() }.getOrDefault(emptyList()).take(4)
        if (keys.isEmpty()) return@withContext null
        for (model in MODELS) {
            for (key in keys.take(if (model == MODELS.first()) 3 else 1)) {
                val outcome = tryConnect(model, key, listener)
                if (outcome.session != null) return@withContext outcome.session
                Log.w(TAG, "live $model failed: ${outcome.error}")
                if (outcome.quota) pool.markCooling(key, 60_000)
                if (outcome.modelMissing) break
            }
        }
        null
    }

    private class Outcome(val session: Session?, val error: String = "", val quota: Boolean = false, val modelMissing: Boolean = false)

    private suspend fun tryConnect(model: String, key: String, listener: Listener): Outcome {
        val session = Session(model)
        val ready = CompletableDeferred<String?>() // null = ok, else error text
        val req = Request.Builder()
            .url("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$key")
            .build()
        val ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(setupJson(model).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())

            private fun handle(raw: String) {
                val m = runCatching { JSONObject(raw) }.getOrNull() ?: return
                if (m.has("setupComplete")) {
                    session.open = true
                    ready.complete(null)
                    return
                }
                runCatching { dispatch(m, listener) }.onFailure { Log.w(TAG, "live message", it) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                finish("$code $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                finish("${response?.code ?: ""} ${t.javaClass.simpleName}: ${t.message.orEmpty()}")
            }

            private fun finish(reason: String) {
                val wasOpen = session.open
                session.open = false
                session.ws = null
                if (!ready.isCompleted) ready.complete(reason.ifBlank { "closed" })
                else if (wasOpen) listener.onClosed(reason)
            }
        })
        session.ws = ws
        val err = withTimeoutOrNull(5_000) { ready.await() } ?: "setup timeout"
        if (err == null && session.open) return Outcome(session)
        runCatching { ws.cancel() }
        val e = err ?: "closed"
        return Outcome(
            null, e,
            quota = Regex("quota|exhaust|429|rate", RegexOption.IGNORE_CASE).containsMatchIn(e),
            modelMissing = Regex("not found|not supported|404", RegexOption.IGNORE_CASE).containsMatchIn(e)
        )
    }

    private fun dispatch(m: JSONObject, l: Listener) {
        m.optJSONObject("serverContent")?.let { sc ->
            sc.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let(l::onInputText)
            sc.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let(l::onOutputText)
            if (sc.optBoolean("interrupted")) l.onInterrupted()
            sc.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    val data = parts.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data").orEmpty()
                    if (data.isNotEmpty()) l.onAudio(Base64.decode(data, Base64.DEFAULT))
                }
            }
            if (sc.optBoolean("turnComplete")) l.onTurnComplete()
        }
        m.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
            for (i in 0 until calls.length()) {
                val c = calls.optJSONObject(i) ?: continue
                l.onToolCall(c.optString("id"), c.optString("name"), c.optJSONObject("args") ?: JSONObject())
            }
        }
        if (m.has("goAway")) l.onClosed("goAway")
    }

    private fun setupJson(model: String): JSONObject {
        val tool = JSONObject().put("functionDeclarations", JSONArray().put(
            JSONObject()
                .put("name", "phone_action")
                .put("description", "Do something on the user's phone: call, video call, send a message, answer or decline a call, open an app, turn the screen on/off.")
                .put("parameters", JSONObject()
                    .put("type", "OBJECT")
                    .put("properties", JSONObject()
                        .put("action", JSONObject().put("type", "STRING").put("enum", JSONArray(listOf("call", "video_call", "message", "answer", "decline", "open_app", "screen_on", "screen_off"))))
                        .put("app", JSONObject().put("type", "STRING").put("enum", JSONArray(listOf("phone", "sms", "whatsapp", "messenger", "telegram", "imo", "viber", "other"))))
                        .put("target", JSONObject().put("type", "STRING").put("description", "Contact name or number exactly as said (Bengali script if said in Bengali), or the app name in English for open_app"))
                        .put("text", JSONObject().put("type", "STRING").put("description", "The message text to send, if any")))
                    .put("required", JSONArray(listOf("action"))))
        ))
        return JSONObject().put("setup", JSONObject()
            .put("model", "models/$model")
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray(listOf("AUDIO")))
                .put("speechConfig", JSONObject().put("voiceConfig", JSONObject()
                    .put("prebuiltVoiceConfig", JSONObject().put("voiceName", VOICE)))))
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt()))))
            .put("tools", JSONArray().put(tool))
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject()))
    }

    companion object {
        private const val TAG = "SairaLive"
        /** Fastest first (CI probe): 3.8-live ≈1.1 s, 3.1-flash-live ≈1.4 s, 2.5 native audio ≈2 s. */
        val MODELS = listOf("gemini-3.8-live", "gemini-3.1-flash-live-preview", "gemini-2.5-flash-native-audio-latest")
        /** A warm female prebuilt voice. */
        const val VOICE = "Kore"

        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(15, TimeUnit.SECONDS)
                .connectTimeout(6, TimeUnit.SECONDS)
                .build()
        }

        fun systemPrompt(now: ZonedDateTime = ZonedDateTime.now()): String {
            val time = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, h:mm a", Locale.ENGLISH))
            return "You are Saira (সায়রা), a warm, quick female voice assistant living on the user's phone. " +
                "The microphone hears everyone near the phone, speaking softly or loudly, in Bengali, English or Banglish. " +
                "Respond ONLY when someone calls you by name (Saira / সায়রা — anywhere in the sentence) or is clearly " +
                "continuing the conversation they just had with you. For all other speech (people talking to each other, " +
                "TV, music) stay completely silent — produce no audio. " +
                "When addressed: if they want a phone action (call, video call, message/SMS/WhatsApp, answer or " +
                "decline a call, open an app, screen on/off) call the phone_action tool at once and say nothing. " +
                "Otherwise answer the person who called you like a caring friend in 1–2 short spoken sentences: " +
                "Bengali if they spoke Bengali or Banglish, English if they spoke English. If they only said your " +
                "name, just say \"জি, বলুন\". You are Saira; never call the user Saira. No lists or links. " +
                "The local time now is $time (${now.zone.id}) — use it for time/date questions and never mention UTC."
        }
    }
}

/** Plays Saira's streamed 24 kHz voice as it arrives. */
class SairaLivePlayer {
    private val exec = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger(0)
    @Volatile private var track: AudioTrack? = null
    @Volatile private var framesWritten = 0L

    private fun ensureTrack(): AudioTrack? {
        track?.let { return it }
        return runCatching {
            val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
                )
                .setBufferSizeInBytes(maxOf(min * 4, RATE))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .also { it.play(); track = it; framesWritten = 0 }
        }.getOrNull()
    }

    fun write(pcm: ByteArray) {
        val gen = generation.get()
        exec.execute {
            if (gen != generation.get()) return@execute
            val t = ensureTrack() ?: return@execute
            if (t.playState != AudioTrack.PLAYSTATE_PLAYING) runCatching { t.play() }
            val n = t.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            if (n > 0) framesWritten += n / 2
        }
    }

    /** Calls [onDone] once everything written so far has actually been heard. */
    fun finish(onDone: () -> Unit) {
        val gen = generation.get()
        exec.execute {
            val t = track
            if (t != null && gen == generation.get()) {
                val deadline = System.currentTimeMillis() + framesWritten * 1000 / RATE + 1_500
                while (gen == generation.get() && System.currentTimeMillis() < deadline &&
                    (t.playbackHeadPosition.toLong() and 0xFFFFFFFFL) < framesWritten
                ) Thread.sleep(30)
            }
            onDone()
        }
    }

    /** Stop at once (interrupted / an action took over), dropping anything queued. */
    fun stop() {
        generation.incrementAndGet()
        exec.execute { releaseTrack() }
    }

    fun release() {
        generation.incrementAndGet()
        exec.execute { releaseTrack() }
        exec.shutdown()
    }

    private fun releaseTrack() {
        track?.let { runCatching { it.pause(); it.flush(); it.release() } }
        track = null
        framesWritten = 0
    }

    companion object {
        const val RATE = 24_000
    }
}
