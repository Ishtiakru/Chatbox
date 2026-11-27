package com.example.saira

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import com.example.data.ai.GeminiKeyPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** 16-bit mono PCM + its sample rate. */
class VoiceClip(val pcm: ShortArray, val rate: Int) {
    val durationMs: Long get() = pcm.size * 1000L / rate
}

/**
 * Saira's own female voice ("Leda", Gemini TTS) — the same on every phone, unlike the phone's TTS engine.
 * Measured live: ~1.6 s to synthesize a sentence, so Saira's fixed phrases are generated once and
 * cached on the phone (they then play instantly); only free-form answers are synthesized on the fly.
 */
class SairaCloudVoice(context: Context) {
    private val app = context.applicationContext
    private val pool = GeminiKeyPool(app)
    private val dir = File(app.filesDir, "saira_voice_cache").apply { mkdirs() }

    fun cached(text: String): VoiceClip? = runCatching { read(fileFor(text)) }.getOrNull()

    /** Cached clip, or synthesizes (and caches short phrases). Null when offline / every model failed. */
    suspend fun clip(text: String, timeoutMs: Long = 7_000L): VoiceClip? {
        cached(text)?.let { return it }
        val c = withTimeoutOrNull(timeoutMs) { synth(text) } ?: return null
        if (text.length <= 60) runCatching { write(fileFor(text), c) }
        return c
    }

    /** Generates Saira's fixed phrases once (background, at voice-mode start). */
    suspend fun prefetch(phrases: List<String>) {
        for (p in phrases) if (cached(p) == null) clip(p, 15_000L)
    }

    private suspend fun synth(text: String): VoiceClip? = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))))
            put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig",
                        JSONObject().put(
                            "voiceConfig",
                            JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", VOICE))
                        )
                    )
            )
        }.toString()
        val keys = runCatching { pool.availableKeys() }.getOrDefault(emptyList()).shuffled().take(4)
        for ((i, key) in keys.withIndex()) {
            val model = MODELS[i.coerceAtMost(MODELS.size - 1)]
            val (code, raw) = post("$BASE/$model:generateContent?key=$key", body)
            if (code !in 200..299) {
                Log.w(TAG, "tts $model HTTP $code ${raw.take(120)}")
                continue
            }
            val clip = runCatching { decode(raw) }.getOrNull()
            if (clip != null && clip.pcm.isNotEmpty()) return@withContext clip
        }
        null
    }

    private fun decode(raw: String): VoiceClip? {
        val parts = JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        for (i in 0 until parts.length()) {
            val inline = parts.getJSONObject(i).optJSONObject("inlineData") ?: continue
            val mime = inline.optString("mimeType").lowercase()
            val bytes = Base64.decode(inline.optString("data"), Base64.DEFAULT)
            return if (mime.contains("wav") || (bytes.size > 44 && String(bytes, 0, 4) == "RIFF")) {
                fromWav(bytes)
            } else {
                val rate = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 24_000
                VoiceClip(toShorts(bytes, 0, bytes.size), rate)
            }
        }
        return null
    }

    private fun fromWav(b: ByteArray): VoiceClip? {
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        var rate = 24_000
        var pos = 12
        while (pos + 8 <= b.size) {
            val id = String(b, pos, 4)
            val len = bb.getInt(pos + 4)
            if (id == "fmt ") rate = bb.getInt(pos + 12)
            if (id == "data") {
                val end = if (len <= 0 || pos + 8 + len > b.size) b.size else pos + 8 + len
                return VoiceClip(toShorts(b, pos + 8, end), rate)
            }
            pos += 8 + len + (len and 1)
        }
        return null
    }

    private fun toShorts(b: ByteArray, from: Int, to: Int): ShortArray {
        val n = (to - from) / 2
        return ShortArray(n) { i -> ((b[from + 2 * i].toInt() and 0xff) or (b[from + 2 * i + 1].toInt() shl 8)).toShort() }
    }

    private fun fileFor(text: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest("$VOICE|$text".toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$h.pcm")
    }

    private fun write(f: File, c: VoiceClip) {
        val bb = ByteBuffer.allocate(4 + c.pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(c.rate)
        c.pcm.forEach { bb.putShort(it) }
        f.writeBytes(bb.array())
    }

    private fun read(f: File): VoiceClip? {
        if (!f.exists() || f.length() < 8) return null
        val bb = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val rate = bb.getInt()
        val pcm = ShortArray(bb.remaining() / 2) { bb.getShort() }
        return VoiceClip(pcm, rate)
    }

    private fun post(url: String, body: String): Pair<Int, String> = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 5_000
            conn.readTimeout = 9_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.readBytes()?.decodeToString().orEmpty()
            code to text
        } finally {
            conn.disconnect()
        }
    }.getOrElse { 0 to (it.message ?: "network") }

    companion object {
        private const val TAG = "SairaCloudVoice"
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        /** Gemini prebuilt female voice. */
        const val VOICE = "Leda"
        // Measured live (Nov 2026): 3.8-flash-lite-tts ≈ 1.6 s, the others 2–3 s.
        private val MODELS = listOf("gemini-3.8-flash-lite-tts", "gemini-3.8-flash-tts", "gemini-2.5-flash-preview-tts", "gemini-3.1-flash-tts-preview")

        /** Plays [clip] on the speaker (assistant audio). Returns the track (caller may stop it) or null. */
        fun play(clip: VoiceClip): AudioTrack? = runCatching {
            val bytes = clip.pcm.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(clip.rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(bytes)
                .build()
            track.write(clip.pcm, 0, clip.pcm.size)
            track.play()
            track
        }.getOrNull()
    }
}
