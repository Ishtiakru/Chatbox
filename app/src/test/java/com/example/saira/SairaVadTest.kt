package com.example.saira

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class SairaVadTest {
    private val rnd = java.util.Random(5)

    /** Crowd-like murmur: noise whose loudness wobbles like several people talking. */
    private fun murmur(seconds: Double, level: Double): DoubleArray {
        val n = (seconds * 16000).toInt()
        var env = 1.0
        return DoubleArray(n) { i ->
            if (i % 800 == 0) env = 0.6 + rnd.nextDouble() * 0.8
            rnd.nextGaussian() * level * env
        }
    }

    /** "Speech": voiced bursts (syllables) with short gaps, and one longer pause in the middle. */
    private fun talk(seconds: Double, level: Double, pauseAt: Double = -1.0, pause: Double = 0.0): DoubleArray {
        val n = (seconds * 16000).toInt()
        return DoubleArray(n) { i ->
            val t = i / 16000.0
            if (pauseAt >= 0 && t >= pauseAt && t < pauseAt + pause) 0.0
            else {
                val syl = (t * 4) % 1.0
                val amp = if (syl < 0.75) sin(PI * syl / 0.75) else 0.0
                level * 1.6 * amp * (sin(2 * PI * 150 * t) + 0.5 * sin(2 * PI * 450 * t) + 0.3 * sin(2 * PI * 1200 * t))
            }
        }
    }

    private fun run(signal: DoubleArray): List<IntRange> {
        val vad = SairaVad()
        val segs = ArrayList<IntRange>()
        var pos = 0
        while (pos + 320 <= signal.size) {
            val f = ShortArray(320) { signal[pos + it].coerceIn(-32767.0, 32767.0).toInt().toShort() }
            pos += 320
            val out = vad.push(f)
            if (out != null) {
                val end = if (out.size >= 749 * 320) pos else pos - 47 * 320
                segs.add((end - out.size) until end)
            }
        }
        return segs
    }

    private fun place(bg: DoubleArray, fg: DoubleArray, atSec: Double): IntRange {
        val at = (atSec * 16000).toInt()
        for (i in fg.indices) if (at + i < bg.size) bg[at + i] += fg[i]
        return at until at + fg.size
    }

    @Test
    fun `quiet room - one segment per command, a short pause inside does not split it`() {
        val sig = murmur(12.0, 40.0)
        val cmd = place(sig, talk(3.0, 3000.0, pauseAt = 1.2, pause = 0.6), 4.0)
        val segs = run(sig)
        assertEquals(segs.toString(), 1, segs.size)
        assertTrue(segs[0].first <= cmd.first + 1600 && segs[0].last >= cmd.last - 3200)
        assertTrue("segment ${segs[0].count() / 16000.0}s", segs[0].count() < 4.0 * 16000)
    }

    @Test
    fun `crowd around the phone - the command is cut out, not 15 s of murmur`() {
        val sig = murmur(30.0, 900.0)
        val cmd = place(sig, talk(2.5, 4500.0), 18.0)
        val segs = run(sig).filter { s -> s.first < cmd.last && s.last > cmd.first }
        assertEquals(segs.toString(), 1, segs.size)
        val extra = (segs[0].count() - cmd.count()) / 16000.0
        assertTrue("extra murmur in segment: ${extra}s", abs(extra) < 1.5)
    }
}
