package com.example.saira

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.ai.AiChatMessage
import com.example.data.ai.GeminiAiProvider
import com.example.data.ai.GeminiKeyPool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** What Saira heard, plus (for questions / chat) her short spoken reply from the same request. */
data class VoiceUnderstanding(val transcript: String, val reply: String?, val commandJson: String? = null)

/**
 * Speech → text (and the answer, in the same round trip) for voice mode, using the Gemini key pool.
 * In "owner only" mode audio is only sent after the on-device voice match says it is the owner.
 *
 * Speed: one request does both hearing and answering; flash-lite models answer in ~1 s
 * (measured live); a second key is raced if the first is slow ("hedging"); the TLS
 * connection is warmed while the owner is still talking.
 */
class SairaVoiceBrain(context: Context) {
    private val pool = GeminiKeyPool(context.applicationContext)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rr = AtomicInteger(0)

    /** Short reason for the last failure (for the status line). */
    @Volatile var lastError: String? = null
        private set

    /** Opens the HTTPS connection to Gemini early (called when speech starts) so the real request is faster. */
    fun warmUp() {
        io.launch {
            runCatching {
                val key = pool.availableKeys().firstOrNull() ?: return@launch
                val conn = URL("$BASE/${MODELS[0]}?key=$key").openConnection() as HttpURLConnection
                conn.connectTimeout = 4_000
                conn.readTimeout = 4_000
                conn.inputStream.use { it.readBytes() } // fully read → connection goes back to the keep-alive pool
            }
        }
    }

    /** Transcript only ("" = no clear speech), or null when no model could be reached. */
    suspend fun transcribe(pcm: ShortArray): String? =
        understand(pcm, wantReply = false, followUp = false, history = emptyList())?.transcript

    suspend fun understand(
        pcm: ShortArray,
        wantReply: Boolean,
        followUp: Boolean,
        history: List<AiChatMessage>,
        expectYesNo: Boolean = false
    ): VoiceUnderstanding? = withContext(Dispatchers.IO) {
        val body = buildBody(pcm, wantReply, followUp, history, expectYesNo)
        val keys = runCatching { pool.availableKeys() }.getOrDefault(emptyList())
        if (keys.isEmpty()) { lastError = "no keys"; return@withContext null }
        val ordered = keys.indices.map { keys[(it + rr.getAndIncrement()) % keys.size] }.distinct().take(MAX_TRIES)

        val result = CompletableDeferred<VoiceUnderstanding?>()
        val next = AtomicInteger(0)
        val running = AtomicInteger(0)
        val started = System.currentTimeMillis()

        fun launchAttempt() {
            val i = next.getAndIncrement()
            if (i >= ordered.size || result.isCompleted) return
            running.incrementAndGet()
            io.launch {
                val key = ordered[i]
                // First attempts use the fastest model; later ones fall back.
                val model = MODELS[(i / 3).coerceAtMost(MODELS.size - 1)]
                val (code, raw) = post("$BASE/$model:generateContent?key=$key", body)
                when {
                    code in 200..299 -> {
                        val text = parseText(raw)
                        if (text != null) result.complete(parse(text, wantReply))
                        else launchAttempt()
                    }
                    code == 429 || raw.contains("RESOURCE_EXHAUSTED") -> {
                        if (model == GeminiAiProvider.DEFAULT_MODEL) pool.markCooling(key, GeminiKeyPool.QUOTA_COOLDOWN_MS)
                        lastError = "quota"
                        launchAttempt()
                    }
                    else -> {
                        lastError = "HTTP $code"
                        Log.w(TAG, "voice $model HTTP $code ${raw.take(160)}")
                        launchAttempt()
                    }
                }
                if (running.decrementAndGet() == 0 && next.get() >= ordered.size) result.complete(null)
            }
        }

        launchAttempt()
        // Hedge: if the first answer is slow, race a second key (tail latency on mobile networks).
        io.launch {
            delay(HEDGE_AFTER_MS)
            if (!result.isCompleted) launchAttempt()
        }
        val out = withTimeoutOrNull(BUDGET_MS - (System.currentTimeMillis() - started)) { result.await() }
        if (out != null) lastError = null
        out
    }

    private fun buildBody(
        pcm: ShortArray,
        wantReply: Boolean,
        followUp: Boolean,
        history: List<AiChatMessage>,
        expectYesNo: Boolean
    ): String {
        val wav = SairaVoiceprint.toWav(pcm)
        val b64 = Base64.encodeToString(wav, Base64.NO_WRAP)
        val context = buildString {
            append("Now: ").append(SimpleDateFormat("EEEE d MMMM yyyy, h:mm a", Locale.US).format(Date())).append('\n')
            append("Follow-up turn (the person Saira is talking with may speak without saying Saira): ").append(if (followUp) "yes" else "no").append('\n')
            if (history.isNotEmpty()) {
                append("Recent conversation:\n")
                history.takeLast(6).forEach { append(if (it.role == "user") "User: " else "Saira: ").append(it.text.take(300)).append('\n') }
            }
            // No "it's probably yes/no" hint here on purpose: in a live test that bias turned a spoken "না" into "হ্যাঁ".
            // An unclear answer must fail safe (Saira keeps waiting), never confirm a call.
            if (expectYesNo) append("This is a short reply to Saira's question. Transcribe it exactly; if unclear, output T: ?\n")
            append(if (wantReply) "Transcribe and reply." else "Transcribe only (R: -).")
        }
        return JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "audio/wav").put("data", b64)))
                            .put(JSONObject().put("text", context))
                    )
                )
            )
            put("generationConfig", JSONObject().put("maxOutputTokens", 400).put("temperature", 0.3))
        }.toString()
    }

    private fun post(url: String, body: String): Pair<Int, String> = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 5_000
            conn.readTimeout = 8_000
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

    private fun parseText(raw: String): String? = runCatching {
        val parts = JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        buildString {
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (!p.optBoolean("thought")) append(p.optString("text"))
            }
        }.trim()
    }.getOrNull()

    companion object {
        private const val TAG = "SairaVoiceBrain"
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        // Measured live (Nov 2026): flash-lite ≈ 1 s for Bengali/English audio; 3.6-flash is mostly 429/503 on the pool.
        private val MODELS = listOf(GeminiAiProvider.DEFAULT_MODEL, GeminiAiProvider.FALLBACK_MODEL, "gemini-3.6-flash")
        private const val BUDGET_MS = 12_000L
        private const val HEDGE_AFTER_MS = 1_600L
        private const val MAX_TRIES = 7

        private const val SYSTEM =
            "You are Saira (Bengali: সায়রা), a warm, quick female voice assistant living on the user's phone. " +
                "You get a short audio clip from the phone's microphone. Listen carefully: the speaker may talk very " +
                "softly or loudly, fast or slowly, in Bengali, English or Banglish, and the phone may be in a noisy or " +
                "crowded place with several people talking. " +
                "If someone in the clip calls Saira by name (anywhere in the sentence, e.g. \"Saira, …\", \"… সায়রা?\", " +
                "\"এই সায়রা …\"), transcribe ONLY that person's words addressed to Saira, from the name on, and ignore " +
                "everyone else. Otherwise transcribe the clearest voice closest to the phone; ignore TV, music and " +
                "background conversations. The name may sound like সাইরা, সায়রা, শায়রা, Sayra or Shaira — always " +
                "write it as Saira (but সারা as in \"সারা দিন\" is an ordinary word, not the name). " +
                "Reply in EXACTLY this format, three lines:\n" +
                "T: <exact transcript — Bengali speech in Bengali script, English words in English, Banglish as spoken; " +
                "nothing else; empty if there is no clear speech; never invent words that were not said>\n" +
                "R: <your spoken reply, or ->\n" +
                "C: <if the speech orders a phone action, one JSON object {\"action\":\"call|video_call|message|answer|decline|" +
                "open_app|screen_on|screen_off\",\"app\":\"phone|sms|whatsapp|messenger|telegram|imo|viber|other\"," +
                "\"target\":\"contact name or number as said (app name for open_app, e.g. YouTube)\",\"text\":\"message to send\"}; otherwise ->\n" +
                "Rules for R: write - (a single dash) when the speech is a phone action (call, video call, message/SMS, " +
                "answer or decline/cut a call, open an app, screen on/off, yes/no), when it is not addressed to Saira " +
                "(the speaker must say Saira unless it is a follow-up turn), or when asked to transcribe only. " +
                "Otherwise answer the person who called you like a caring friend: if they spoke English answer in English, " +
                "otherwise answer in Bengali written in Bengali script (never romanized Banglish — a Bengali voice reads it aloud). " +
                "1–2 short spoken sentences, no markdown, emojis, lists or links. You are Saira; the user is NOT Saira, " +
                "so never call the user Saira. Use the given time for time/date questions."

        fun cleanTranscript(t: String): String =
            t.trim().trim('"', '“', '”').replace(Regex("^(transcript|transcription)\\s*:\\s*", RegexOption.IGNORE_CASE), "").trim()

        /** Parses the "T: … / R: …" answer (tolerant of a model that skips the labels). */
        fun parse(text: String, wantReply: Boolean): VoiceUnderstanding {
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val tLine = lines.firstOrNull { it.startsWith("T:", ignoreCase = true) }
            val cLine = lines.firstOrNull { it.startsWith("C:", ignoreCase = true) }
            val commandJson = cLine?.substring(2)?.trim()?.takeIf { it.contains('{') && it.contains("action") }
            val rIdx = lines.indexOfFirst { it.startsWith("R:", ignoreCase = true) }
            val transcript = when {
                tLine != null -> tLine.substring(2)
                rIdx > 0 -> lines.take(rIdx).joinToString(" ")
                rIdx == 0 -> ""
                else -> lines.joinToString(" ")
            }.let { cleanTranscript(it) }
            val reply = if (rIdx >= 0) {
                (listOf(lines[rIdx].substring(2)) + lines.drop(rIdx + 1).filterNot { it.startsWith("T:", true) || it.startsWith("C:", true) })
                    .joinToString(" ").trim()
            } else ""
            // The model sometimes tacks "…, Saira" onto the answer as if the user were Saira — drop that.
            val cleaned = reply.replace(Regex("[,،]?\\s*(সায়রা|সায়রা|Saira)\\s*(?=[।!.?]|$)"), "").trim()
            val r = cleaned.takeIf { wantReply && it.isNotBlank() && it.trim('-', '—', ' ', '.').isNotEmpty() }
            return VoiceUnderstanding(transcript, r, commandJson)
        }
    }
}
