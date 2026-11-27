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
import java.util.concurrent.atomic.AtomicBoolean

/** Voice-mode settings & live status (shared by the service and the Saira screen). */
object SairaVoiceMode {
    private const val PREFS = "saira_voice_mode"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PRINT = "voiceprint"
    private const val KEY_SENS = "sensitivity"
    private const val KEY_CONFIRM = "confirm_first"

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

    fun canStart(c: Context): Boolean =
        voiceprint(c) != null &&
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
 * speech that matches the owner's enrolled voice, and acts on "Saira, …" commands.
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
    private val history = ArrayDeque<AiChatMessage>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        brain = SairaVoiceBrain(this)
        cloudVoice = SairaCloudVoice(this)
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
            if (SairaVoiceMode.voiceprint(this)?.isNeural == false) "শুনছি — ভিড়ে চেনার জন্য Saira-র পাতায় কণ্ঠ আবার শেখান"
            else "শুনছি — \"Saira, …\" বলুন"
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
                while (scope.isActive) {
                    val n = rec.read(buf, 0, frame)
                    if (n <= 0) { kotlinx.coroutines.delay(50); continue }
                    val now = System.currentTimeMillis()
                    if (now - lastModeCheck > 1_000) {
                        lastModeCheck = now
                        // Pause while any call is using the mic (phone, WhatsApp, Chatbox calls).
                        paused = audioManager.mode != AudioManager.MODE_NORMAL
                    }
                    if (paused || speaking.get() || now < mutedUntil) {
                        vad.reset()
                        continue
                    }
                    val done = vad.push(buf.copyOf(n))
                    if (vad.justStarted && now - lastWarm > 25_000) { lastWarm = now; brain.warmUp() }
                    if (done != null) segments.trySend(done)
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

    // ------------------------------------------------------------------ understanding
    private suspend fun handleSegment(pcm: ShortArray) {
        val vp = SairaVoiceMode.voiceprint(this) ?: return
        // Only the owner's voice is ever answered. A short "হ্যাঁ" / "না" after Saira asks may be
        // shorter than a normal command, so it needs less audio — but the same voice check.
        val waitingForAnswer = pending?.let { System.currentTimeMillis() < it.until } == true
        val match = SairaVoiceId.match(this, vp, pcm, SairaVoiceMode.sensitivity(this), shortAnswer = waitingForAnswer)
        if (!match.ok) {
            SairaVoiceMode.setStatus(
                if (match.reason == "too short") "খুব ছোট শব্দ — উপেক্ষা করেছি"
                else "অন্য কারো কণ্ঠ / শব্দ — উপেক্ষা করেছি"
            )
            return
        }
        if (!isOnline()) {
            SairaVoiceMode.setStatus("ইন্টারনেট নেই — কমান্ড বুঝতে ইন্টারনেট লাগবে")
            return
        }
        SairaVoiceMode.setStatus("আপনার কণ্ঠ চিনেছি — বুঝে নিচ্ছি…")
        val followUp = System.currentTimeMillis() < followUpUntil
        val understood = withTimeoutOrNull(14_000L) {
            brain.understand(
                pcm, wantReply = !waitingForAnswer, followUp = followUp,
                history = history.toList(), expectYesNo = waitingForAnswer
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
        aiCommand = understood.commandJson
        handleTranscript(heard, understood.reply)
    }

    private suspend fun handleTranscript(text: String, quickReply: String? = null) {
        val now = System.currentTimeMillis()
        val p = pending
        val addressed = SairaWakeWord.extract(text)
        if (p != null && now < p.until) {
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
        val command = addressed ?: if (now < followUpUntil) text else null
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

    private suspend fun handleCommand(command: String, quickReply: String? = null) {
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
            .setContentText("শুধু আপনার কণ্ঠে \"Saira, …\" বললে কাজ করবে")
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

        const val P_CANCELLED = "ঠিক আছে, বাতিল করলাম।"
        const val P_WHO = "কাকে? নামটা বলুন।"
        const val P_WHAT = "কী লিখব? বলুন।"
        const val P_SENT = "পাঠিয়ে দিয়েছি।"
        const val P_SORRY = "দুঃখিত, এখন উত্তর দিতে পারছি না।"
        /** Saira's fixed lines — generated once in her cloud voice and cached, so they play instantly. */
        val PHRASES = listOf(P_WHO, P_SENT, P_CANCELLED, P_SORRY, P_WHAT, "জি, বলুন।")

        /** 1.1 s of quiet ends what the owner is saying — short pauses inside a command don't cut it. */
        private const val END_SILENCE_FRAMES = 55
        private const val VOICE_SYSTEM =
            "You are Saira, a warm female voice assistant on the user's phone. Reply in the user's language " +
                "(Bengali, Banglish or English) in 1–3 short spoken sentences. No markdown, lists, emojis or links."
    }
}
