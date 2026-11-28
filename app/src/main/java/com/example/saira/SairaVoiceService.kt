package com.example.saira

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.ToneGenerator
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ai.AiChatMessage
import com.example.data.ai.SairaAiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** Voice-mode settings & live status (shared by the service and the Saira screen). */
object SairaVoiceMode {
    private const val PREFS = "saira_voice_mode"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PRINT = "voiceprint"
    private const val KEY_SENS = "sensitivity"
    private const val KEY_CONFIRM = "confirm_first"
    private const val KEY_OWNER_ONLY = "owner_only"
    private const val KEY_LIVE = "live"

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    internal fun setStatus(s: String) { _status.value = s }
    internal fun setRunning(r: Boolean) { _running.value = r }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(c: Context) = prefs(c).getBoolean(KEY_ENABLED, false)
    fun voiceprint(c: Context): Voiceprint? = Voiceprint.deserialize(prefs(c).getString(KEY_PRINT, null))
    fun saveVoiceprint(c: Context, vp: Voiceprint?) {
        prefs(c).edit().apply { if (vp == null) remove(KEY_PRINT) else putString(KEY_PRINT, vp.serialize()) }.apply()
    }
    fun sensitivity(c: Context) = prefs(c).getInt(KEY_SENS, SairaVoiceprint.NORMAL)
    fun setSensitivity(c: Context, v: Int) = prefs(c).edit().putInt(KEY_SENS, v.coerceIn(0, 2)).apply()

    /** Ask "হ্যাঁ / না" before a call or message? Off by default: the owner asked for instant action. */
    fun confirmFirst(c: Context) = prefs(c).getBoolean(KEY_CONFIRM, false)
    fun setConfirmFirst(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_CONFIRM, v).apply()

    /**
     * true  = only the enrolled owner's voice is obeyed;
     * false (default) = Saira hears everyone and answers whoever calls her by name — and then only that
     * same person for the follow-up, until someone says "Saira" again.
     */
    fun ownerOnly(c: Context) = prefs(c).getBoolean(KEY_OWNER_ONLY, false)
    fun setOwnerOnly(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_OWNER_ONLY, v).apply()

    /** Real-time Gemini Live voice (fast); off = the classic record → transcribe → speak pipeline. */
    fun live(c: Context) = prefs(c).getBoolean(KEY_LIVE, true)
    fun setLive(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_LIVE, v).apply()

    fun canStart(c: Context): Boolean =
        (!ownerOnly(c) || voiceprint(c) != null) &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Turn voice mode on (call while the app is on screen — Android requires it for the mic). */
    fun start(c: Context): Boolean {
        if (!canStart(c)) return false
        prefs(c).edit().putBoolean(KEY_ENABLED, true).apply()
        return runCatching {
            ContextCompat.startForegroundService(c, Intent(c, SairaVoiceService::class.java))
        }.isSuccess
    }

    fun stop(c: Context) {
        prefs(c).edit().putBoolean(KEY_ENABLED, false).apply()
        runCatching { c.stopService(Intent(c, SairaVoiceService::class.java)) }
        setRunning(false)
    }

    /** Restarts voice mode after an app update / reboot, once the user opens the app. */
    fun resumeIfEnabled(c: Context) {
        if (isEnabled(c) && !_running.value) start(c)
    }
}

/**
 * Saira voice mode: listens in the background (also with the screen off), keeps only
 * speech addressed to "Saira, …" (from anyone, or only the enrolled owner in "owner only" mode) and acts on it.
 * Commands run immediately (optionally confirmed by voice first — see [SairaVoiceMode.confirmFirst]).
 * Saira speaks with her own female cloud voice; her fixed phrases are cached so they play instantly.
 */
class SairaVoiceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private var loopJob: Job? = null
    private var workerJob: Job? = null
    private var prefetchJob: Job? = null
    private val segments = Channel<ShortArray>(capacity = 2, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val speaking = AtomicBoolean(false)
    @Volatile private var mutedUntil = 0L
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    @Volatile private var currentUtterance: String? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var brain: SairaVoiceBrain
    private lateinit var cloudVoice: SairaCloudVoice
    @Volatile private var track: AudioTrack? = null
    private var tone: ToneGenerator? = null

    private data class Pending(val cmd: PhoneCommand, val number: String?, val who: String, val until: Long)
    @Volatile private var pending: Pending? = null
    @Volatile private var followUpUntil = 0L
    /** The command the hearing request itself read from the speech (JSON), used when local rules miss. */
    @Volatile private var aiCommand: String? = null
    @Volatile private var needsWho: PhoneCommand? = null
    @Volatile private var needsWhoUntil = 0L
    /** A message waiting for its text ("কী লিখব?"). */
    @Volatile private var needsWhat: PhoneCommand? = null
    @Volatile private var needsWhatUntil = 0L
    /** Voice of whoever last called "Saira" (everyone mode): only they may follow up without the name. */
    @Volatile private var speakerLock: FloatArray? = null
    private val history = ArrayDeque<AiChatMessage>()

    // ------------------------------------------------------------------ Gemini Live state
    private lateinit var liveVoice: SairaLiveVoice
    private lateinit var player: SairaLivePlayer
    @Volatile private var liveSession: SairaLiveVoice.Session? = null
    @Volatile private var liveConnecting = false
    @Volatile private var liveFailedUntil = 0L
    @Volatile private var lastLiveActivity = 0L
    /** The last ~2 s of microphone frames, so the start of the sentence survives the ~0.3 s connect. */
    private val livePre = ArrayDeque<ShortArray>()
    private val liveBatch = ShortArray(LIVE_BATCH)
    private var liveBatchN = 0
    private val turnLock = Any()
    private val turnText = StringBuilder()
    private val turnSaid = StringBuilder()
    private val turnAudio = ArrayList<ByteArray>()
    @Volatile private var turnOpen = false
    /** null = not decided yet (audio is held back), true = addressed to Saira (play), false = ignore. */
    private var turnDecision: Boolean? = null
    private var turnFollowUp = false
    private var turnLocal = false
    private var turnActed = false
    private var turnPlayed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        brain = SairaVoiceBrain(this)
        cloudVoice = SairaCloudVoice(this)
        liveVoice = SairaLiveVoice(this)
        player = SairaLivePlayer()
        tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()
        tts = TextToSpeech(applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = finishedSpeaking(utteranceId)
                override fun onStop(utteranceId: String?, interrupted: Boolean) = finishedSpeaking(utteranceId)
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finishedSpeaking(utteranceId)
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            SairaVoiceMode.stop(this)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!SairaVoiceMode.canStart(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }.isSuccess
        if (!ok) {
            SairaVoiceMode.setStatus("ভয়েস মোড চালু করা যায়নি — অ্যাপ খোলা অবস্থায় আবার চালু করুন।")
            stopSelf()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        SairaVoiceMode.setRunning(true)
        SairaVoiceMode.setStatus(
            if (SairaVoiceMode.ownerOnly(this) && SairaVoiceMode.voiceprint(this)?.isNeural == false)
                "শুনছি — ভিড়ে চেনার জন্য Saira-র পাতায় কণ্ঠ আবার শেখান"
            else if (SairaVoiceMode.ownerOnly(this)) "শুনছি — \"Saira, …\" বলুন"
            else "শুনছি — যে কেউ \"Saira, …\" বললেই সাড়া দেব"
        )
        if (loopJob?.isActive != true) loopJob = scope.launch { listenLoop() }
        if (workerJob?.isActive != true) workerJob = scope.launch { for (seg in segments) handleSegment(seg) }
        if (prefetchJob?.isActive != true) prefetchJob = scope.launch { runCatching { cloudVoice.prefetch(PHRASES) } }
        // Load the speaker model now, not on the first command.
        scope.launch { SairaVoiceId.encoder(this@SairaVoiceService) }
        return START_STICKY
    }

    override fun onDestroy() {
        SairaVoiceMode.setRunning(false)
        scope.cancel()
        segments.close()
        runCatching { liveSession?.close() }
        runCatching { player.release() }
        runCatching { tts?.stop(); tts?.shutdown() }
        runCatching { track?.release() }
        runCatching { tone?.release() }
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        super.onDestroy()
    }

    // ------------------------------------------------------------------ audio loop
    @SuppressLint("MissingPermission")
    private suspend fun listenLoop() {
        val rate = SairaVoiceprint.SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val frame = 320 // 20 ms
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        while (scope.isActive) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                SairaVoiceMode.setStatus("মাইক্রোফোনের অনুমতি নেই।")
                stopSelf(); return
            }
            val rec = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, frame * 2 * 10)
                )
            }.getOrNull()
            if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                rec?.release()
                kotlinx.coroutines.delay(3_000); continue
            }
            try {
                rec.startRecording()
                val buf = ShortArray(frame)
                // Follows the room's own level, so a crowd / TV / fan around the phone counts as "quiet".
                val vad = SairaVad(endSilenceFrames = END_SILENCE_FRAMES)
                var lastModeCheck = 0L
                var lastWarm = 0L
                var paused = false
                var liveMode = !SairaVoiceMode.ownerOnly(this) && SairaVoiceMode.live(this)
                while (scope.isActive) {
                    val n = rec.read(buf, 0, frame)
                    if (n <= 0) { kotlinx.coroutines.delay(50); continue }
                    val now = System.currentTimeMillis()
                    if (now - lastModeCheck > 1_000) {
                        lastModeCheck = now
                        // Pause while any call is using the mic (phone, WhatsApp, Chatbox calls).
                        paused = audioManager.mode != AudioManager.MODE_NORMAL
                        liveMode = !SairaVoiceMode.ownerOnly(this) && SairaVoiceMode.live(this)
                        if (paused) closeLive()
                    }
                    if (paused || speaking.get() || now < mutedUntil) {
                        vad.reset()
                        livePre.clear()
                        continue
                    }
                    val frame = buf.copyOf(n)
                    val done = vad.push(frame)
                    val live = liveMode && now >= liveFailedUntil
                    if (vad.justStarted && now - lastWarm > 25_000 && !live) { lastWarm = now; brain.warmUp() }
                    if (live) {
                        // (if Live can't connect, liveFailedUntil flips this sentence to the classic pipeline)
                        liveFrame(frame, vad.isInSpeech, now)
                    } else if (done != null) segments.trySend(done)
                }
            } catch (e: Exception) {
                Log.w(TAG, "listen loop error", e)
                kotlinx.coroutines.delay(2_000)
            } finally {
                runCatching { rec.stop() }
                rec.release()
            }
        }
    }

    // ------------------------------------------------------------------ Gemini Live
    private fun liveFrame(frame: ShortArray, inSpeech: Boolean, now: Long) {
        if (inSpeech) lastLiveActivity = now
        val s = liveSession
        if (s == null || !s.open) {
            if (s != null) liveSession = null
            livePre.addLast(frame)
            while (livePre.size > LIVE_PRE_FRAMES) livePre.removeFirst()
            if (inSpeech && !liveConnecting) connectLive()
            return
        }
        while (livePre.isNotEmpty()) queueLive(s, livePre.removeFirst())
        queueLive(s, frame)
        if (now - lastLiveActivity > LIVE_IDLE_MS && !turnOpen) {
            closeLive()
            SairaVoiceMode.setStatus("শুনছি — যে কেউ \"Saira, …\" বললেই সাড়া দেব")
        }
    }

    private fun queueLive(s: SairaLiveVoice.Session, frame: ShortArray) {
        var off = 0
        while (off < frame.size) {
            val n = minOf(frame.size - off, LIVE_BATCH - liveBatchN)
            frame.copyInto(liveBatch, liveBatchN, off, off + n)
            liveBatchN += n
            off += n
            if (liveBatchN == LIVE_BATCH) {
                runCatching { s.sendAudio(liveBatch, liveBatchN) }
                liveBatchN = 0
            }
        }
    }

    private fun connectLive() {
        liveConnecting = true
        lastLiveActivity = System.currentTimeMillis()
        scope.launch {
            val s = runCatching { liveVoice.connect(liveListener) }.getOrNull()
            if (s == null) {
                liveFailedUntil = System.currentTimeMillis() + 90_000
                SairaVoiceMode.setStatus("দ্রুত (Live) মোডে যুক্ত হতে পারিনি — সাধারণ মোডে শুনছি")
            } else {
                lastLiveActivity = System.currentTimeMillis()
                liveSession = s
            }
            liveConnecting = false
        }
    }

    private fun closeLive() {
        val s = liveSession ?: return
        liveSession = null
        liveBatchN = 0
        runCatching { s.close() }
    }

    private fun startTurnLocked() {
        val now = System.currentTimeMillis()
        turnOpen = true
        turnText.setLength(0)
        turnSaid.setLength(0)
        turnAudio.clear()
        turnFollowUp = now < followUpUntil
        // Saira asked something ("কাকে?", "হ্যাঁ / না?") — the answer is handled on the phone, not by the model.
        turnLocal = (pending?.let { now < it.until } == true) ||
            (needsWho != null && now < needsWhoUntil) || (needsWhat != null && now < needsWhatUntil)
        turnDecision = if (turnLocal) false else null
        turnActed = false
        turnPlayed = false
    }

    /** Was this turn addressed to Saira? Until we know, her answer is held back (never spoken to strangers). */
    private fun decideLocked(final: Boolean) {
        if (turnDecision != null) return
        if (turnFollowUp || SairaWakeWord.extract(turnText.toString()) != null) {
            turnDecision = true
            for (b in turnAudio) playLocked(b)
            turnAudio.clear()
        } else if (final) {
            turnDecision = false
            turnAudio.clear()
        }
    }

    private fun playLocked(b: ByteArray) {
        if (!turnPlayed) {
            turnPlayed = true
            speaking.set(true)
        }
        player.write(b)
    }

    private fun liveDoneSpeaking(followUp: Boolean) {
        if (currentUtterance == null) speaking.set(false)
        mutedUntil = System.currentTimeMillis() + 350
        if (followUp) followUpUntil = System.currentTimeMillis() + 8_000
    }

    private val liveListener = object : SairaLiveVoice.Listener {
        override fun onInputText(text: String) {
            synchronized(turnLock) {
                if (!turnOpen) startTurnLocked()
                turnText.append(text)
                lastLiveActivity = System.currentTimeMillis()
                SairaVoiceMode.setStatus("শুনছি: “${turnText.toString().trim().takeLast(60)}”")
                decideLocked(false)
            }
        }

        override fun onOutputText(text: String) {
            synchronized(turnLock) {
                turnSaid.append(text)
                if (turnDecision == true && !turnActed) SairaVoiceMode.setStatus("Saira: ${turnSaid.toString().trim().take(90)}")
            }
        }

        override fun onAudio(pcm24k: ByteArray) {
            synchronized(turnLock) {
                if (!turnOpen) startTurnLocked()
                lastLiveActivity = System.currentTimeMillis()
                if (turnDecision == null && turnFollowUp) turnDecision = true
                when (turnDecision) {
                    true -> if (!turnActed) playLocked(pcm24k)
                    false -> Unit
                    null -> turnAudio.add(pcm24k)
                }
            }
        }

        override fun onToolCall(id: String, name: String, args: JSONObject) {
            val run: Boolean
            val heard: String
            val wasPlaying: Boolean
            synchronized(turnLock) {
                if (!turnOpen) startTurnLocked()
                decideLocked(false)
                run = turnDecision == true && !turnLocal && !turnActed
                heard = turnText.toString().trim()
                wasPlaying = turnPlayed
                if (run) {
                    turnActed = true
                    turnAudio.clear()
                }
            }
            val s = liveSession
            if (!run) {
                runCatching { s?.sendToolResponse(id, name, "ignored: the speaker did not call Saira — stay silent") }
                return
            }
            runCatching { s?.sendToolResponse(id, name, "done — say nothing") }
            if (wasPlaying) {
                player.stop()
                liveDoneSpeaking(false)
            }
            val command = SairaWakeWord.extract(heard)?.takeIf { it.isNotBlank() } ?: heard.ifBlank { args.optString("target") }
            SairaVoiceMode.setStatus("“$command”")
            aiCommand = args.toString()
            scope.launch { handleCommand(command, null, preferAi = true) }
        }

        override fun onTurnComplete() {
            var local: String? = null
            val played: Boolean
            val heard: String
            val said: String
            val nameOnly: Boolean
            synchronized(turnLock) {
                if (!turnOpen) return
                decideLocked(true)
                heard = turnText.toString().trim()
                said = turnSaid.toString().trim()
                played = turnPlayed
                if (turnLocal && heard.isNotEmpty()) local = heard
                else if (turnDecision != true && heard.isNotEmpty() && !turnActed) {
                    SairaVoiceMode.setStatus("শুনেছি: “${heard.take(60)}” — কিছু চাইলে \"Saira\" বলে ডাকুন")
                }
                nameOnly = turnDecision == true && !turnActed && !played && SairaWakeWord.extract(heard) == ""
                turnOpen = false
                turnAudio.clear()
            }
            if (played) {
                if (heard.isNotEmpty() && said.isNotEmpty()) remember(heard, said)
                player.finish { main.post { liveDoneSpeaking(true) } }
            }
            local?.let { text -> scope.launch { handleTranscript(text, null, true) } }
            if (nameOnly) {
                beep()
                followUpUntil = System.currentTimeMillis() + 10_000
                SairaVoiceMode.setStatus("জি, বলুন — শুনছি…")
            }
        }

        override fun onInterrupted() {
            val wasPlaying = synchronized(turnLock) { turnPlayed.also { turnPlayed = false } }
            if (wasPlaying) {
                player.stop()
                main.post { liveDoneSpeaking(false) }
            }
        }

        override fun onClosed(reason: String) {
            Log.i(TAG, "live closed: $reason")
            liveSession = null
            val wasPlaying = synchronized(turnLock) {
                turnOpen = false
                turnAudio.clear()
                turnPlayed
            }
            if (wasPlaying) player.finish { main.post { liveDoneSpeaking(true) } }
        }
    }

    // ------------------------------------------------------------------ understanding
    private suspend fun handleSegment(pcm: ShortArray) {
        val now = System.currentTimeMillis()
        val waitingForAnswer = pending?.let { now < it.until } == true
        val inConversation = waitingForAnswer || now < followUpUntil
        val ownerOnly = SairaVoiceMode.ownerOnly(this)
        // trusted = this voice may continue the conversation without saying "Saira" again.
        var trusted: Boolean
        var voice: FloatArray? = null
        if (ownerOnly) {
            val vp = SairaVoiceMode.voiceprint(this) ?: return
            // A short "হ্যাঁ" / "না" after Saira asks may be shorter than a normal command, so it needs
            // less audio — but the same voice check.
            val match = SairaVoiceId.match(this, vp, pcm, SairaVoiceMode.sensitivity(this), shortAnswer = waitingForAnswer)
            if (!match.ok) {
                SairaVoiceMode.setStatus(
                    if (match.reason == "too short") "খুব ছোট শব্দ — উপেক্ষা করেছি"
                    else "অন্য কারো কণ্ঠ / শব্দ — উপেক্ষা করেছি"
                )
                return
            }
            trusted = true
        } else {
            // Everyone is heard. Mid-conversation, only the person who called Saira may go on without
            // her name; anyone else has to say "Saira" first.
            val lock = speakerLock
            voice = voiceOf(pcm)
            trusted = !inConversation || lock == null || voice == null || sameSpeaker(lock, voice, pcm)
        }
        if (!isOnline()) {
            SairaVoiceMode.setStatus("ইন্টারনেট নেই — কমান্ড বুঝতে ইন্টারনেট লাগবে")
            return
        }
        SairaVoiceMode.setStatus(if (ownerOnly) "আপনার কণ্ঠ চিনেছি — বুঝে নিচ্ছি…" else "শুনেছি — বুঝে নিচ্ছি…")
        val followUp = inConversation && trusted
        val understood = withTimeoutOrNull(14_000L) {
            brain.understand(
                // quietly spoken words are made louder first — much easier to recognise
                SairaVad.boost(pcm), wantReply = !(waitingForAnswer && trusted), followUp = followUp,
                history = history.toList(), expectYesNo = waitingForAnswer && trusted
            )
        }
        if (understood == null) {
            SairaVoiceMode.setStatus(
                if (brain.lastError == "quota") "AI এখন ব্যস্ত (limit) — একটু পরে আবার বলুন"
                else "বুঝতে পারিনি (নেটওয়ার্ক ধীর) — আবার বলুন"
            )
            return
        }
        val heard = understood.transcript
        if (heard.isBlank()) {
            SairaVoiceMode.setStatus("পরিষ্কার কথা শুনিনি — আবার বলুন")
            return
        }
        // Whoever calls Saira by name becomes the person she is talking with.
        if (!ownerOnly && SairaWakeWord.extract(heard) != null) {
            speakerLock = voice
            trusted = true
        }
        aiCommand = understood.commandJson
        handleTranscript(heard, understood.reply, trusted)
    }

    /** The speaker's voice fingerprint for this clip (on the phone), or null if too short / no model. */
    private fun voiceOf(pcm: ShortArray): FloatArray? = runCatching {
        val enc = SairaVoiceId.encoder(this) ?: return null
        val speech = SairaVoiceprint.speechOnly(pcm)
        if (speech.size < SairaVoiceprint.SAMPLE_RATE * 3 / 10) return null
        enc.embed(if (speech.size < SairaVoiceprint.SAMPLE_RATE / 2) speech.copyOf(SairaVoiceprint.SAMPLE_RATE / 2) else speech)
    }.getOrNull()

    /** Same person as the one who called Saira? Clip-to-clip, so a little more lenient in noise. */
    private fun sameSpeaker(lock: FloatArray, voice: FloatArray, pcm: ShortArray): Boolean {
        val snr = SairaDenoise.snrDb(pcm)
        val t = ((snr - 10.0) / 20.0).coerceIn(0.0, 1.0)
        val threshold = SAME_SPEAKER_NOISY + (SAME_SPEAKER_CLEAN - SAME_SPEAKER_NOISY) * t
        val ok = SairaVoiceEncoder.cosine(lock, voice) >= threshold
        if (!ok) SairaVoiceMode.setStatus("অন্য কেউ বলছে — কিছু বলতে চাইলে আগে \"Saira\" বলুন")
        return ok
    }

    private suspend fun handleTranscript(text: String, quickReply: String? = null, trusted: Boolean = true) {
        val now = System.currentTimeMillis()
        val p = pending
        val addressed = SairaWakeWord.extract(text)
        // A "yes" only counts from the person Saira asked (or said with her name).
        if (p != null && now < p.until && (trusted || addressed != null)) {
            val body = addressed ?: text
            when {
                SairaPhoneCommandParser.isYes(body) -> {
                    pending = null
                    runConfirmed(p.cmd, p.number, p.who)
                    return
                }
                SairaPhoneCommandParser.isNo(body) -> {
                    pending = null
                    speak(P_CANCELLED)
                    return
                }
            }
        }
        val command = addressed ?: if (now < followUpUntil && trusted) text else null
        if (command == null) {
            SairaVoiceMode.setStatus("শুনেছি: “${text.take(60)}” — শুরুতে \"Saira\" বলুন")
            return
        }
        followUpUntil = 0
        if (command.isBlank()) {
            // Only "Saira": beep at once and keep listening — no slow spoken "জি, বলুন" that would
            // mute the mic while the owner is already saying the command.
            beep()
            SairaVoiceMode.setStatus("জি, বলুন — শুনছি…")
            followUpUntil = System.currentTimeMillis() + 10_000
            return
        }
        SairaVoiceMode.setStatus("“$command”")
        handleCommand(command, quickReply)
    }

    private suspend fun handleCommand(command: String, quickReply: String? = null, preferAi: Boolean = false) {
        var parsed: PhoneCommand? = SairaPhoneCommandParser.parse(command)
        // Answer to "কাকে?": just a name / number completes the earlier command.
        val waiting = needsWho?.takeIf { System.currentTimeMillis() < needsWhoUntil }
        needsWho = null
        if (parsed == null && waiting != null && command.split(' ').size <= 4) {
            val n = SairaPhoneCommandParser.extractNumber(SairaPhoneCommandParser.normalize(command))
            val name = command.trim().trim('।', '.', '!', '?', ',')
                .replace(Regex("(কে|ke|কেই)$", RegexOption.IGNORE_CASE), "").trim()
            parsed = waiting.copy(target = n ?: name)
        }
        // Answer to "কী লিখব?": the whole sentence is the message text.
        val waitingText = needsWhat?.takeIf { System.currentTimeMillis() < needsWhatUntil }
        needsWhat = null
        if (parsed == null && waitingText != null && command.isNotBlank()) {
            parsed = waitingText.copy(text = command.trim().trim('।', '.', ' '))
        }
        val fromHearing = aiCommand?.let { SairaPhoneActions.parseAiJson(it) }
        aiCommand = null
        // Gemini Live's own phone_action call heard the whole sentence — trust it over the transcript rules.
        if (preferAi && fromHearing != null && waiting == null && waitingText == null) parsed = fromHearing
        if (parsed == null && fromHearing != null) parsed = fromHearing
        // Gemini writes app names in English ("YouTube", "camera") — easier to find on the launcher.
        if (parsed?.action == PhoneAction.OPEN_APP && fromHearing?.action == PhoneAction.OPEN_APP &&
            fromHearing.target.isNotBlank() && fromHearing.target.all { it.code < 128 }
        ) parsed = parsed.copy(target = fromHearing.target, app = if (parsed.app == PhoneApp.OTHER) fromHearing.app else parsed.app)
        if (parsed == null && SairaPhoneCommandParser.mightBeCommand(command)) parsed = SairaPhoneActions.extractWithAi(command)
        if (parsed != null && parsed.needsText && fromHearing != null && fromHearing.text.isNotBlank()) {
            parsed = parsed.copy(text = fromHearing.text)
        }
        if (parsed != null && parsed.needsText) {
            val ai = SairaPhoneActions.extractWithAi(command)
            if (ai != null && ai.text.isNotBlank()) parsed = parsed.copy(text = ai.text)
        }
        val c = parsed
        if (c == null) {
            if (quickReply != null) {
                // Answer came back with the transcript — speak it right away (no second AI round trip).
                remember(command, quickReply)
                speak(plain(quickReply).take(600)) { followUpUntil = System.currentTimeMillis() + 8_000 }
            } else answerQuestion(command)
            return
        }
        if (c.needsTarget) {
            needsWho = c
            needsWhoUntil = System.currentTimeMillis() + 16_000
            speak(P_WHO) { followUpUntil = System.currentTimeMillis() + 10_000 }
            return
        }
        if (c.needsText) {
            needsWhat = c
            needsWhatUntil = System.currentTimeMillis() + 20_000
            speak(P_WHAT) { followUpUntil = System.currentTimeMillis() + 12_000 }
            return
        }

        var number: String? = null
        var who = c.target
        val usesNumber = c.action in setOf(PhoneAction.CALL, PhoneAction.VIDEO_CALL, PhoneAction.MESSAGE) &&
            c.app !in setOf(PhoneApp.MESSENGER, PhoneApp.IMO)
        if (usesNumber) {
            if (c.targetIsNumber) {
                number = c.target
            } else {
                val match = SairaPhoneActions.findContacts(this, c.target, 1).firstOrNull()
                if (match == null && c.app != PhoneApp.VIBER && c.app != PhoneApp.TELEGRAM) {
                    speak("${c.target} নামে কোনো কন্টাক্ট পাইনি।")
                    return
                }
                match?.let { number = it.number; who = it.name }
            }
        }
        if (!c.needsConfirmation || !SairaVoiceMode.confirmFirst(this)) {
            runConfirmed(c, number, who)
            return
        }
        pending = Pending(c, number, who, System.currentTimeMillis() + 20_000)
        speak(SairaPhoneCommandParser.describe(c, who) + " বলুন: হ্যাঁ করো, অথবা না।")
    }

    private fun runConfirmed(cmd: PhoneCommand, number: String?, who: String) {
        val needsScreen = cmd.app !in setOf(PhoneApp.PHONE, PhoneApp.SMS) || cmd.action == PhoneAction.OPEN_APP
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val go = {
            if (needsScreen && !pm.isInteractive) SairaPhoneActions.screenOn(this)
            val outcome = SairaPhoneActions.execute(this, cmd, number, who, tag = "voice")
            if (!outcome.ok) speak(plain(outcome.message))
            else if (cmd.action == PhoneAction.MESSAGE && outcome.message.contains("পাঠি")) speak(P_SENT)
            SairaVoiceMode.setStatus(outcome.message)
        }
        // Act at once — the dialer / chat opening (or the screen going dark) is the feedback.
        beep()
        main.post { go() }
    }

    private suspend fun answerQuestion(question: String) {
        val answer = withTimeoutOrNull(25_000L) {
            runCatching { SairaAiClient.complete(VOICE_SYSTEM, history.toList(), question) }.getOrNull()
        }?.trim().orEmpty()
        if (answer.isEmpty()) { speak(P_SORRY); return }
        remember(question, answer)
        speak(plain(answer).take(600)) { followUpUntil = System.currentTimeMillis() + 8_000 }
    }

    private fun remember(question: String, answer: String) {
        history.addLast(AiChatMessage("user", question))
        history.addLast(AiChatMessage("model", answer))
        while (history.size > 8) history.removeFirst()
    }

    // ------------------------------------------------------------------ speech out
    private fun plain(s: String): String =
        s.replace(Regex("[\\p{So}\\p{Cn}*_#`>]"), "").replace(Regex("\\s+"), " ").trim()

    private val afterSpeak = HashMap<String, () -> Unit>()

    private fun beep() {
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 110) }
    }

    /** Saira's female cloud voice (cached phrases are instant); the phone's TTS only as an offline fallback. */
    private fun speak(text: String, then: (() -> Unit)? = null) {
        SairaVoiceMode.setStatus("Saira: $text")
        val id = "saira_${System.nanoTime()}"
        currentUtterance = id
        speaking.set(true)
        scope.launch {
            val clip = cloudVoice.cached(text) ?: if (isOnline()) cloudVoice.clip(text) else null
            if (currentUtterance != id) return@launch
            val t = clip?.let { SairaCloudVoice.play(it) }
            if (clip != null && t != null) {
                val old = track
                track = t
                runCatching { old?.release() }
                main.postDelayed({
                    if (track === t) { runCatching { t.release() }; track = null }
                    then?.let { synchronized(afterSpeak) { afterSpeak[id] = it } }
                    finishedSpeaking(id)
                }, clip.durationMs + 120)
            } else {
                main.post { if (currentUtterance == id) localSpeak(text, then, id) }
            }
        }
    }

    private fun localSpeak(text: String, then: (() -> Unit)?, reuseId: String) {
        val engine = tts
        if (engine == null || !ttsReady) {
            then?.let { f -> main.post { f() } }
            finishedSpeaking(reuseId)
            return
        }
        val bengali = text.any { it in '\u0980'..'\u09FF' }
        val hasVoice = runCatching { SairaTtsVoice.apply(engine, bengali) }.getOrDefault(false)
        if (bengali && !hasVoice) {
            // No Bengali voice on this phone: don't read Bengali with an English voice — just act.
            then?.let { f -> main.post { f() } }
            finishedSpeaking(reuseId)
            return
        }
        val id = reuseId
        then?.let { synchronized(afterSpeak) { afterSpeak[id] = it } }
        currentUtterance = id
        speaking.set(true)
        val result = runCatching { engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) }.getOrDefault(TextToSpeech.ERROR)
        if (result != TextToSpeech.SUCCESS) {
            finishedSpeaking(id)
            return
        }
        // Safety net: some TTS engines never report onDone — never stay deaf because of it.
        main.postDelayed({ if (currentUtterance == id) finishedSpeaking(id) },
            4_000L + text.length * 90L)
    }

    private fun finishedSpeaking(id: String?) {
        if (id == null || id == currentUtterance) {
            currentUtterance = null
            mutedUntil = System.currentTimeMillis() + 400
            speaking.set(false)
        }
        val next = id?.let { synchronized(afterSpeak) { afterSpeak.remove(it) } }
        if (next != null) main.post { next() }
    }

    // ------------------------------------------------------------------ misc
    private fun isOnline(): Boolean = runCatching {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    private fun acquireWakeLock() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WAKE_LOCK) != PackageManager.PERMISSION_GRANTED) return
        if (wakeLock?.isHeld == true) return
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Chatbox:SairaVoiceMode").apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L)
            }
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Saira voice mode", NotificationManager.IMPORTANCE_LOW).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
            )
        }
        val open = PendingIntent.getActivity(
            this, 7401,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 7402, Intent(this, SairaVoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_saira)
            .setContentTitle("Saira শুনছে")
            .setContentText("\"Saira, …\" বলে ডাকলে সাড়া দেবে")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "বন্ধ করুন", stop).build())
            .build()
    }

    companion object {
        private const val TAG = "SairaVoice"
        private const val CHANNEL = "saira_voice_mode_v1"
        private const val NOTIFICATION_ID = 7401
        const val ACTION_STOP = "com.example.saira.VOICE_STOP"
        /** Clip-to-clip voice similarity needed to continue without "Saira" (quiet room → crowd). */
        private const val SAME_SPEAKER_CLEAN = 0.70
        private const val SAME_SPEAKER_NOISY = 0.60

        const val P_CANCELLED = "ঠিক আছে, বাতিল করলাম।"
        const val P_WHO = "কাকে? নামটা বলুন।"
        const val P_WHAT = "কী লিখব? বলুন।"
        const val P_SENT = "পাঠিয়ে দিয়েছি।"
        const val P_SORRY = "দুঃখিত, এখন উত্তর দিতে পারছি না।"
        /** Saira's fixed lines — generated once in her cloud voice and cached, so they play instantly. */
        val PHRASES = listOf(P_WHO, P_SENT, P_CANCELLED, P_SORRY, P_WHAT, "জি, বলুন।")

        /** 1.1 s of quiet ends what the owner is saying — short pauses inside a command don't cut it. */
        private const val END_SILENCE_FRAMES = 55
        /** Gemini Live: 100 ms audio messages, 2 s pre-roll, close the connection after 20 s of quiet. */
        private const val LIVE_BATCH = 1600
        private const val LIVE_PRE_FRAMES = 100
        private const val LIVE_IDLE_MS = 20_000L
        private const val VOICE_SYSTEM =
            "You are Saira, a warm female voice assistant on the user's phone. Reply in the user's language " +
                "(Bengali, Banglish or English) in 1–3 short spoken sentences. No markdown, lists, emojis or links."
    }
}
