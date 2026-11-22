package com.example.media

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class VoicePlaybackState(
    val activeKey: String? = null,
    val isBuffering: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null
)

class VoiceNotePlayer(private val scope: CoroutineScope) {

    private companion object {
        const val TAG = "VoiceNotePlayer"
        const val TICK_MS = 120L
    }

    private val _state = MutableStateFlow(VoicePlaybackState())
    val state: StateFlow<VoicePlaybackState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var tickerJob: Job? = null

    fun togglePlayPause(key: String, source: String, fallbackDurationMs: Long = 0L) {
        val current = _state.value
        val activePlayer = player

        if (current.activeKey == key && activePlayer != null) {
            if (current.isBuffering) return
            if (current.isPlaying) {
                runCatching { activePlayer.pause() }
                tickerJob?.cancel()
                _state.value = current.copy(
                    isPlaying = false,
                    positionMs = runCatching { activePlayer.currentPosition.toLong() }.getOrDefault(current.positionMs)
                )
            } else {
                runCatching { activePlayer.start() }
                startTicker()
                _state.value = current.copy(isPlaying = true, error = null)
            }
            return
        }

        stopAndRelease()
        _state.value = VoicePlaybackState(
            activeKey = key,
            isBuffering = true,
            isPlaying = false,
            positionMs = 0L,
            durationMs = fallbackDurationMs
        )

        val mp = MediaPlayer()
        player = mp
        runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(source)
            mp.setOnPreparedListener { prepared ->
                if (_state.value.activeKey != key || player !== prepared) return@setOnPreparedListener
                val actualDuration = prepared.duration.takeIf { it > 0 }?.toLong() ?: fallbackDurationMs
                prepared.start()
                _state.value = VoicePlaybackState(
                    activeKey = key,
                    isBuffering = false,
                    isPlaying = true,
                    positionMs = 0L,
                    durationMs = actualDuration
                )
                startTicker()
            }
            mp.setOnCompletionListener {
                if (_state.value.activeKey != key) return@setOnCompletionListener
                tickerJob?.cancel()
                runCatching { it.seekTo(0) }
                _state.value = _state.value.copy(
                    isBuffering = false,
                    isPlaying = false,
                    positionMs = 0L
                )
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Playback error what=$what extra=$extra")
                if (_state.value.activeKey == key) {
                    stopAndRelease()
                    _state.value = VoicePlaybackState(error = "Unable to play voice note.")
                }
                true
            }
            mp.prepareAsync()
        }.onFailure { error ->
            Log.w(TAG, "Failed to prepare voice playback", error)
            stopAndRelease()
            _state.value = VoicePlaybackState(error = "Unable to play voice note.")
        }
    }

    fun stopIfKey(key: String) {
        if (_state.value.activeKey == key) {
            stopAndRelease()
        }
    }

    fun stopAndRelease() {
        tickerJob?.cancel()
        tickerJob = null
        val mp = player
        player = null
        if (mp != null) {
            runCatching {
                if (mp.isPlaying) mp.stop()
            }
            runCatching { mp.release() }
        }
        _state.value = VoicePlaybackState()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val active = player ?: break
                val playing = runCatching { active.isPlaying }.getOrDefault(false)
                if (!playing) break
                val pos = runCatching { active.currentPosition.toLong() }.getOrDefault(_state.value.positionMs)
                val dur = runCatching { active.duration.takeIf { it > 0 }?.toLong() }.getOrNull() ?: _state.value.durationMs
                _state.value = _state.value.copy(positionMs = pos, durationMs = dur, isPlaying = true)
                delay(TICK_MS)
            }
        }
    }
}
