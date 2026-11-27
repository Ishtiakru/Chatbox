package com.example.saira

/**
 * Splits the microphone stream (20 ms frames) into utterances for voice mode (pure Kotlin, testable).
 *
 * Crowd-proof: the background level is the 20th percentile of the last ~3 s, so steady talk around the
 * phone (a crowd, TV, fan, traffic) becomes the "silence" floor. An utterance starts when someone is
 * clearly louder than the room (the owner close to the phone) and ends after [endSilenceFrames] of
 * room-level sound — so other people talking after the owner stops do not keep the segment open,
 * and short pauses inside a command do not cut it.
 */
class SairaVad(
    private val endSilenceFrames: Int = 55,   // 1.1 s
    private val maxFrames: Int = 750,         // 15 s
    private val preRollFrames: Int = 15,      // 0.3 s before the start
    private val minSamples: Int = SairaVoiceprint.SAMPLE_RATE * 6 / 10,
    private val startFactor: Double = START_FACTOR,
    private val endFactor: Double = END_FACTOR
) {
    /** Current background (room) level, RMS. */
    var ambient: Double = 150.0
        private set

    private val history = DoubleArray(HISTORY)
    private var hCount = 0
    private var hPos = 0
    private val sorted = DoubleArray(HISTORY)

    private var loudRun = 0
    private var silence = 0
    private var inSpeech = false
    private var speechPeak = 0.0
    private val recent = ArrayDeque<Double>()
    private val preRoll = ArrayDeque<ShortArray>()
    private val speech = ArrayList<ShortArray>()

    val isInSpeech: Boolean get() = inSpeech

    /** Drop whatever was being collected (Saira is speaking, a call started, …). */
    fun reset() {
        inSpeech = false; speech.clear(); preRoll.clear(); recent.clear(); loudRun = 0; silence = 0
    }

    /** True when a new utterance just started on the last [push] (used to warm up the network). */
    var justStarted: Boolean = false
        private set

    /** Feeds one frame; returns a finished utterance (16 kHz PCM) or null. */
    fun push(frame: ShortArray): ShortArray? {
        justStarted = false
        val r = SairaVoiceprint.rms(frame)
        // While someone speaks only their pauses (room level) move the floor — not their own voice.
        if (!inSpeech || r < ambient * startFactor || speech.size > LEARN_AFTER) updateAmbient(r)
        recent.addLast(r)
        if (recent.size > SMOOTH) recent.removeFirst()
        if (!inSpeech) {
            preRoll.addLast(frame)
            if (preRoll.size > preRollFrames) preRoll.removeFirst()
            if (r > maxOf(ambient * startFactor, MIN_START_RMS)) loudRun++ else loudRun = 0
            if (loudRun >= 3) {
                inSpeech = true; justStarted = true; silence = 0; speechPeak = 0.0
                speech.clear(); speech.addAll(preRoll); preRoll.clear()
            }
            return null
        }
        speech.add(frame)
        if (r > speechPeak) speechPeak = r
        val smooth = recent.average()
        // Quiet = back at the room's own level (or far below the speaker's peak).
        if (smooth < maxOf(ambient * endFactor, MIN_END_RMS, speechPeak * 0.12)) silence++ else silence = 0
        val tooLong = speech.size >= maxFrames
        if (silence < endSilenceFrames && !tooLong) return null
        if (tooLong && silence < endSilenceFrames) {
            // 15 s without a pause is the room itself (loud crowd / music): learn it as the new floor.
            speech.takeLast(HISTORY).forEach { updateAmbient(SairaVoiceprint.rms(it)) }
        }
        // Trailing room noise is not needed — less audio to upload, cleaner voice check.
        repeat((silence - 8).coerceAtLeast(0)) { if (speech.isNotEmpty()) speech.removeAt(speech.size - 1) }
        val total = speech.sumOf { it.size }
        val out = if (total >= minSamples) {
            ShortArray(total).also { pcm -> var off = 0; for (s in speech) { s.copyInto(pcm, off); off += s.size } }
        } else null
        inSpeech = false; speech.clear(); loudRun = 0; silence = 0
        return out
    }

    private fun updateAmbient(r: Double) {
        history[hPos] = r
        hPos = (hPos + 1) % HISTORY
        if (hCount < HISTORY) hCount++
        if (hCount < 10) { ambient = maxOf(60.0, minOf(ambient, r * 1.5)); return }
        System.arraycopy(history, 0, sorted, 0, hCount)
        java.util.Arrays.sort(sorted, 0, hCount)
        ambient = maxOf(60.0, sorted[(hCount * 0.2).toInt()])
    }

    companion object {
        private const val HISTORY = 150          // 3 s of 20 ms frames
        private const val SMOOTH = 10            // 200 ms average for the end decision
        /** After 4 s without a pause the "speech" is probably the room itself — keep learning its level. */
        private const val LEARN_AFTER = 200
        const val START_FACTOR = 3.2
        const val END_FACTOR = 2.4
        private const val MIN_START_RMS = 380.0
        private const val MIN_END_RMS = 260.0
    }
}
