package com.example.saira

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp

class SairaVoiceprintTest {
    private val vowels = listOf(
        doubleArrayOf(730.0, 1090.0, 2440.0), doubleArrayOf(270.0, 2290.0, 3010.0), doubleArrayOf(300.0, 870.0, 2240.0),
        doubleArrayOf(530.0, 1840.0, 2480.0), doubleArrayOf(570.0, 840.0, 2410.0), doubleArrayOf(440.0, 1020.0, 2240.0)
    )

    /** Crude source-filter "voice": pitch [f0] and vocal-tract length [scale] make the speaker. */
    private fun voice(seconds: Double, f0: Double, scale: Double, seed: Long): ShortArray {
        val sr = 16000
        val n = (seconds * sr).toInt()
        val r = java.util.Random(seed)
        val out = DoubleArray(n)
        var phase = 0.0
        var i = 0
        while (i < n) {
            val segLen = (0.15 + r.nextDouble() * 0.2) * sr
            val silent = r.nextDouble() < 0.18
            val v = vowels[r.nextInt(vowels.size)].map { it * scale }
            val f = f0 * (0.85 + r.nextDouble() * 0.3)
            val y1 = DoubleArray(3)
            val y2 = DoubleArray(3)
            var k = 0
            while (k < segLen && i < n) {
                var src = 0.0
                if (!silent) { phase += f / sr; if (phase >= 1) { phase -= 1; src = 1.0 } }
                var x = src * 3000 + r.nextGaussian() * 20
                for (j in 0 until 3) {
                    val bw = 240.0 * (1 + j * 0.5)
                    val rr = exp(-PI * bw / sr)
                    val th = 2 * PI * v[j] / sr
                    val y = x + 2 * rr * cos(th) * y1[j] - rr * rr * y2[j]
                    y2[j] = y1[j]; y1[j] = y; x = y * (1 - rr)
                }
                out[i] = x; i++; k++
            }
        }
        val mx = out.maxOf { abs(it) }.coerceAtLeast(1.0)
        return ShortArray(n) { (out[it] / mx * 12000 + r.nextGaussian() * 30).toInt().coerceIn(-32768, 32767).toShort() }
    }

    @Test
    fun `owner passes and different voices are rejected`() {
        val vp = SairaVoiceprint.enroll(voice(15.0, 120.0, 1.0, 1), 1L)
        assertNotNull(vp)
        val print = Voiceprint.deserialize(vp!!.serialize())!!
        assertEquals(vp.selfMean, print.selfMean, 1e-9)
        assertTrue(SairaVoiceprint.match(print, voice(3.0, 120.0, 1.0, 7)).ok)
        assertTrue(SairaVoiceprint.match(print, voice(3.0, 140.0, 1.0, 8)).ok)   // speaking a bit higher
        assertFalse(SairaVoiceprint.match(print, voice(3.0, 210.0, 1.18, 3)).ok)  // another (female) voice
        assertFalse(SairaVoiceprint.match(print, voice(3.0, 125.0, 1.12, 5)).ok)  // another male voice
        assertFalse(SairaVoiceprint.match(print, ShortArray(32000) { (java.util.Random(9).nextGaussian() * 800).toInt().toShort() }).ok)
    }

    @Test
    fun `enrollment needs enough speech and wav header is valid`() {
        assertNull(SairaVoiceprint.enroll(voice(3.0, 120.0, 1.0, 2), 1L))
        val wav = SairaVoiceprint.toWav(ShortArray(16000))
        assertEquals(44 + 32000, wav.size)
        assertEquals("RIFF", String(wav.copyOfRange(0, 4)))
        assertNull(Voiceprint.deserialize("garbage"))
    }
}
