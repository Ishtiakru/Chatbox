package com.example.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.UUID

data class RecordedVoiceNote(
    val file: File,
    val durationMs: Long
)

class VoiceNoteRecorder(private val appContext: Context) {

    private companion object {
        const val TAG = "VoiceNoteRecorder"
        const val VOICE_CACHE_DIR = "voice_notes"
        const val MIN_DURATION_MS = 500L
        const val SAMPLE_RATE_HZ = 44100
        const val BIT_RATE_BPS = 96000
    }

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtElapsedMs: Long = 0L

    fun start(): Result<File> {
        cancel()
        return runCatching {
            val dir = File(appContext.cacheDir, VOICE_CACHE_DIR).apply { mkdirs() }
            val file = File(dir, "voice_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.m4a")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(SAMPLE_RATE_HZ)
                setAudioEncodingBitRate(BIT_RATE_BPS)
                setMaxDuration(60_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            outputFile = file
            startedAtElapsedMs = SystemClock.elapsedRealtime()
            file
        }.onFailure { error ->
            Log.w(TAG, "Failed to start voice recording", error)
            cancel()
        }
    }

    fun currentAmplitudeNormalized(): Float {
        val active = recorder ?: return 0f
        return runCatching {
            (active.maxAmplitude.toFloat() / 32767f).coerceIn(0.08f, 1f)
        }.getOrDefault(0.08f)
    }

    fun stop(): Result<RecordedVoiceNote?> {
        val active = recorder ?: return Result.success(null)
        val file = outputFile
        val elapsedMs = (SystemClock.elapsedRealtime() - startedAtElapsedMs).coerceAtLeast(0L)
        recorder = null
        outputFile = null
        startedAtElapsedMs = 0L

        return try {
            active.stop()
            active.release()
            if (file == null || !file.exists() || file.length() == 0L || elapsedMs < MIN_DURATION_MS) {
                if (file != null && file.exists()) {
                    runCatching { file.delete() }
                }
                Result.success(null)
            } else {
                Result.success(RecordedVoiceNote(file = file, durationMs = elapsedMs))
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to finalize recording cleanly", e)
            runCatching { active.release() }
            if (file != null && file.exists()) {
                runCatching { file.delete() }
            }
            Result.success(null)
        }
    }

    fun cancel() {
        val active = recorder
        val file = outputFile
        recorder = null
        outputFile = null
        startedAtElapsedMs = 0L
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        if (file != null && file.exists()) {
            runCatching { file.delete() }
        }
    }

    fun deleteFile(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val file = File(path)
            val cacheVoiceDir = File(appContext.cacheDir, VOICE_CACHE_DIR).canonicalPath
            if (file.canonicalPath.startsWith(cacheVoiceDir) && file.exists()) {
                file.delete()
            }
        }
    }

    fun release() {
        cancel()
    }
}
