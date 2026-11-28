package com.example.call

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

/**
 * The "tring… tring…" the CALLER hears while the other phone is ringing (ringback tone,
 * 425 Hz 1 s on / 4 s off — the tone used in Bangladesh and most of the world).
 *
 * Played on the voice-call stream, so it follows the call's audio route: earpiece by default,
 * loudspeaker when the Speaker button is on, or a headset.
 */
object RingbackTone {
    private const val TAG = "RingbackTone"
    private var tone: ToneGenerator? = null

    @Synchronized
    fun start() {
        if (tone != null) return
        tone = runCatching {
            ToneGenerator(AudioManager.STREAM_VOICE_CALL, 90).also {
                it.startTone(ToneGenerator.TONE_SUP_RINGTONE)
            }
        }.onFailure { Log.w(TAG, "ringback start failed", it) }.getOrNull()
    }

    @Synchronized
    fun stop() {
        val t = tone ?: return
        tone = null
        runCatching { t.stopTone() }
        runCatching { t.release() }
    }
}
