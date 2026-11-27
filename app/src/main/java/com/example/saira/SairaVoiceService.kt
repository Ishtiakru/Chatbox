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
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Voice-mode settings & live status (shared by the service and the Saira screen). */
object SairaVoiceMode {
    private const val PREFS = "saira_voice_mode"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PRINT = "voiceprint"
    private const val KEY_SENS = "sensitivity"

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
 * Calls and messages are always confirmed by voice ("হ্যাঁ" / "না") before they happen.
 */
class SairaVoiceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private var loopJob: Job? = null
    private var workerJob: Job? = null
    private val segments = Channel<ShortArray>(capacity = 2, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val speaking = AtomicBoolean(false)
    @Volatile private var mutedUntil = 0L
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var brain: SairaVoiceBrain

    private data class Pending(val cmd: PhoneCommand, val number: String?, val who: String, val until: Long)
    @Volatile private var pending: Pending? = null
    @Volatile private var followUpUntil = 0L
    private val history = ArrayDeque<AiChatMessage>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        brain = SairaVoiceBrain(this)
        tts = TextToSpeech(applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = finishedSpeaking(utteranceId)
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
        SairaVoiceMode.setStatus("শুনছি — \"Saira, …\" বলুন")
        if (loopJob?.isActive != true) loopJob = scope.launch { listenLoop() }
        if (workerJob?.isActive != true) workerJob = scope.launch { for (seg in segments) handleSegment(seg) }
        return START_STICKY
    }

    override fun onDestroy() {
        SairaVoiceMode.setRunning(false)
        scope.cancel()
        segments.close()
        runCatching { tts?.stop(); tts?.shutdown() }
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
                var noise = 150.0
                var loudRun = 0
                var silence = 0
                var inSpeech = false
                val preRoll = ArrayDeque<ShortArray>()
                val speech = ArrayList<ShortArray>()
                var lastModeCheck = 0L
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
                        inSpeech = false; speech.clear(); preRoll.clear(); loudRun = 0
                        continue
                    }
                    val r = SairaVoiceprint.rms(buf, n)
                    val copy = buf.copyOf(n)
                    if (!inSpeech) {
                        if (r > maxOf(noise * 3.0, 380.0)) loudRun++ else {
                            loudRun = 0
                            noise = 0.96 * noise + 0.04 * r
                        }
                        preRoll.addLast(copy)
                        if (preRoll.size > 15) preRoll.removeFirst()
                        if (loudRun >= 3) {
                            inSpeech = true; silence = 0
                            speech.clear(); speech.addAll(preRoll); preRoll.clear()
                        }
                    } else {
                        speech.add(copy)
                        if (r < maxOf(noise * 2.0, 260.0)) silence++ else silence = 0
                        val tooLong = speech.size >= 600 // 12 s
                        if (silence >= 40 || tooLong) {
                            val total = speech.sumOf { it.size }
                            if (total >= rate * 6 / 10) {
                                val pcm = ShortArray(total)
                                var off = 0
                                for (s in speech) { s.copyInto(pcm, off); off += s.size }
                                segments.trySend(pcm)
                            }
                            inSpeech = false; speech.clear(); loudRun = 0; silence = 0
                        }
                    }
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
        val match = SairaVoiceprint.match(vp, pcm, SairaVoiceMode.sensitivity(this))
        if (!match.ok) {
            SairaVoiceMode.setStatus("অন্য কারো কণ্ঠ / শব্দ — উপেক্ষা করেছি")
            return
        }
        if (!isOnline()) {
            SairaVoiceMode.setStatus("ইন্টারনেট নেই — কমান্ড বুঝতে ইন্টারনেট লাগবে")
            return
        }
        SairaVoiceMode.setStatus("আপনার কণ্ঠ চিনেছি — বুঝে নিচ্ছি…")
        val text = brain.transcribe(pcm)?.takeIf { it.isNotBlank() } ?: run {
            SairaVoiceMode.setStatus("শুনছি — \"Saira, …\" বলুন")
            return
        }
        handleTranscript(text)
    }

    private suspend fun handleTranscript(text: String) {
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
                    speak("ঠিক আছে, বাতিল করলাম।")
                    return
                }
            }
        }
        val command = addressed ?: if (now < followUpUntil) text else null
        if (command == null) {
            SairaVoiceMode.setStatus("শুনছি — \"Saira, …\" বলুন")
            return
        }
        followUpUntil = 0
        if (command.isBlank()) {
            speak("জি, বলুন?")
            followUpUntil = System.currentTimeMillis() + 10_000
            return
        }
        SairaVoiceMode.setStatus("“$command”")
        handleCommand(command)
    }

    private suspend fun handleCommand(command: String) {
        var parsed: PhoneCommand? = SairaPhoneCommandParser.parse(command)
        if (parsed == null && SairaPhoneCommandParser.mightBeCommand(command)) parsed = SairaPhoneActions.extractWithAi(command)
        if (parsed != null && parsed.needsText) {
            val ai = SairaPhoneActions.extractWithAi(command)
            if (ai != null && ai.text.isNotBlank()) parsed = parsed.copy(text = ai.text)
        }
        val c = parsed
        if (c == null) {
            answerQuestion(command)
            return
        }
        if (c.needsTarget) { speak("কাকে? নামসহ আবার বলুন, যেমন: Saira, আম্মুকে কল দাও।"); return }
        if (c.needsText) { speak("কী লিখব? এভাবে বলুন: Saira, করিমকে মেসেজ দাও যে আমি আসছি।"); return }

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
        if (!c.needsConfirmation) {
            runConfirmed(c, number, who)
            return
        }
        pending = Pending(c, number, who, System.currentTimeMillis() + 20_000)
        speak(SairaPhoneCommandParser.describe(c, who) + " হ্যাঁ বা না বলুন।")
    }

    private fun runConfirmed(cmd: PhoneCommand, number: String?, who: String) {
        val needsScreen = cmd.app !in setOf(PhoneApp.PHONE, PhoneApp.SMS) || cmd.action == PhoneAction.OPEN_APP
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val go = {
            if (needsScreen && !pm.isInteractive) SairaPhoneActions.screenOn(this)
            val outcome = SairaPhoneActions.execute(this, cmd, number, who, tag = "voice")
            if (!outcome.ok || cmd.action in setOf(PhoneAction.OPEN_APP, PhoneAction.SCREEN_ON)) speak(plain(outcome.message))
            SairaVoiceMode.setStatus(outcome.message)
        }
        when (cmd.action) {
            // Say it first, then act (so Saira's voice doesn't go into the call / wake the screen after locking).
            PhoneAction.CALL, PhoneAction.VIDEO_CALL -> speak("কল দিচ্ছি।") { go() }
            PhoneAction.MESSAGE -> speak("পাঠাচ্ছি।") { go() }
            PhoneAction.SCREEN_OFF -> speak("ঠিক আছে।") { go() }
            else -> main.post { go() }
        }
    }

    private suspend fun answerQuestion(question: String) {
        val answer = withTimeoutOrNull(25_000L) {
            runCatching { SairaAiClient.complete(VOICE_SYSTEM, history.toList(), question) }.getOrNull()
        }?.trim().orEmpty()
        if (answer.isEmpty()) { speak("দুঃখিত, এখন উত্তর দিতে পারছি না।"); return }
        history.addLast(AiChatMessage("user", question))
        history.addLast(AiChatMessage("model", answer))
        while (history.size > 8) history.removeFirst()
        speak(plain(answer).take(600))
        followUpUntil = System.currentTimeMillis() + 8_000
    }

    // ------------------------------------------------------------------ speech out
    private fun plain(s: String): String =
        s.replace(Regex("[\\p{So}\\p{Cn}*_#`>]"), "").replace(Regex("\\s+"), " ").trim()

    private val afterSpeak = HashMap<String, () -> Unit>()

    private fun speak(text: String, then: (() -> Unit)? = null) {
        SairaVoiceMode.setStatus("Saira: $text")
        val engine = tts
        if (engine == null || !ttsReady) {
            then?.let { f -> main.post { f() } }
            return
        }
        val bengali = text.any { it in '\u0980'..'\u09FF' }
        val locale = if (bengali) {
            listOf(Locale("bn", "BD"), Locale("bn", "IN"), Locale("bn")).firstOrNull {
                engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE
            }
        } else Locale.US
        locale?.let { engine.language = it }
        val id = "saira_${System.nanoTime()}"
        then?.let { synchronized(afterSpeak) { afterSpeak[id] = it } }
        speaking.set(true)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    private fun finishedSpeaking(id: String?) {
        mutedUntil = System.currentTimeMillis() + 400
        speaking.set(false)
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

        private const val VOICE_SYSTEM =
            "You are Saira, a friendly voice assistant on the user's phone. Reply in the user's language " +
                "(Bengali, Banglish or English) in 1–3 short spoken sentences. No markdown, lists, emojis or links."
    }
}
