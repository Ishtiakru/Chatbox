package com.example.saira

import android.content.Context
import android.util.Log

/**
 * "Is this the owner speaking?" for voice mode — the neural check (works with people talking around
 * the phone) when the voiceprint has a neural profile, otherwise the older MFCC/GMM check.
 * Everything runs on the phone; no audio leaves it for this.
 */
object SairaVoiceId {
    private const val TAG = "SairaVoiceId"

    @Volatile private var encoder: SairaVoiceEncoder? = null
    @Volatile private var loadFailed = false
    private val lock = Any()

    /** Loads the speaker model from the APK once (~3 MB, ~0.1 s). Null if it cannot be loaded. */
    fun encoder(context: Context): SairaVoiceEncoder? {
        encoder?.let { return it }
        if (loadFailed) return null
        synchronized(lock) {
            encoder?.let { return it }
            return runCatching {
                SairaVoiceEncoder.load(context.applicationContext.assets.open(SairaVoiceEncoder.ASSET))
            }.onFailure {
                loadFailed = true
                Log.w(TAG, "speaker model not available", it)
            }.getOrNull()?.also { encoder = it }
        }
    }

    /** Learns the owner's voice from the ~15 s enrollment talk (both checks, so either can be used). */
    fun enroll(context: Context, pcm: ShortArray, nowMs: Long): Voiceprint? {
        val legacy = SairaVoiceprint.enroll(pcm, nowMs) ?: return null
        val enc = encoder(context) ?: return legacy
        val (profile, selfCos) = SairaVoiceprint.neuralProfile(enc, pcm) ?: return legacy
        return legacy.copy(embedding = profile, selfCos = selfCos)
    }

    /**
     * [shortAnswer] = a quick "হ্যাঁ / না" after Saira asked something (less audio, so a little more lenient).
     */
    fun match(context: Context, vp: Voiceprint, pcm: ShortArray, sensitivity: Int, shortAnswer: Boolean = false): VoiceMatch {
        val enc = if (vp.isNeural) encoder(context) else null
        if (enc == null) {
            return SairaVoiceprint.match(
                vp, pcm, sensitivity,
                minFrames = if (shortAnswer) SairaVoiceprint.MIN_SHORT_FRAMES else SairaVoiceprint.MIN_COMMAND_FRAMES
            )
        }
        return SairaVoiceprint.matchNeural(enc, vp, pcm, sensitivity, shortAnswer)
    }
}
