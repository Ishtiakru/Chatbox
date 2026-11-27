package com.example.saira

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.round
import kotlin.math.sin

class SairaVoiceEncoderTest {
    private val encoder: SairaVoiceEncoder by lazy {
        val f = listOf("src/main/assets", "app/src/main/assets").map { File(it, SairaVoiceEncoder.ASSET) }.first { it.exists() }
        SairaVoiceEncoder.load(f.inputStream())
    }

    /** Same deterministic 2 s signal the numpy reference embedding was computed from. */
    private fun testSignal(): ShortArray = ShortArray(32000) { n ->
        val x = 0.2 * sin(2 * PI * 220 * n / 16000) * (1 + sin(2 * PI * 3 * n / 16000)) +
            0.05 * sin(2 * PI * 1330 * n / 16000 + 0.3) + 0.03 * sin(2 * PI * 3100 * n / 16000)
        round(x * 32767).toInt().toShort()
    }

    @Test
    fun `kotlin encoder matches the reference model exactly`() {
        val text = javaClass.getResourceAsStream("/saira/encoder_reference.txt")!!.bufferedReader().readLines()
            .first { !it.startsWith("#") }
        val ref = text.trim().split(' ').map { it.toFloat() }.toFloatArray()
        assertEquals(SairaVoiceEncoder.EMB, ref.size)
        val e = encoder.embed(testSignal(), threads = 2)
        assertNotNull(e)
        val cos = SairaVoiceEncoder.cosine(e!!, ref)
        assertTrue("cosine to reference = $cos", cos > 0.9999)
    }

    @Test
    fun `fingerprints are unit length and too-short audio gives none`() {
        val e = encoder.embed(testSignal())!!
        assertEquals(1.0, SairaVoiceEncoder.cosine(e, e), 1e-6)
        var n = 0.0
        for (v in e) n += v * v
        assertEquals(1.0, n, 1e-4)
        assertNull(encoder.embed(ShortArray(4000)))
    }

    @Test
    fun `half floats decode correctly`() {
        assertEquals(1.0f, SairaVoiceEncoder.halfToFloat(0x3c00))
        assertEquals(-2.0f, SairaVoiceEncoder.halfToFloat(0xc000))
        assertEquals(0.5f, SairaVoiceEncoder.halfToFloat(0x3800))
        assertEquals(0f, SairaVoiceEncoder.halfToFloat(0))
        assertEquals(65504f, SairaVoiceEncoder.halfToFloat(0x7bff))
    }

    @Test
    fun `voiceprint v2 round-trips and v1 still loads`() {
        val emb = encoder.embed(testSignal())!!
        val vp = Voiceprint(
            listOf(doubleArrayOf(1.0, 2.0)), listOf(doubleArrayOf(0.5, 0.5)), doubleArrayOf(1.0),
            -20.0, 1.0, 140.0, 12.0, 123L, embedding = emb, selfCos = 0.91
        )
        val back = Voiceprint.deserialize(vp.serialize())!!
        assertTrue(back.isNeural)
        assertEquals(0.91, back.selfCos, 1e-9)
        assertTrue(SairaVoiceEncoder.cosine(emb, back.embedding!!) > 0.99999)
        val v1 = Voiceprint.deserialize(vp.copy(embedding = null).serialize())!!
        assertTrue(!v1.isNeural)
        assertEquals(140.0, v1.pitchHz, 1e-9)
    }

    @Test
    fun `acceptance bar is high in quiet rooms and lower in crowds`() {
        val n = SairaVoiceprint.NORMAL
        val quiet = SairaVoiceprint.neuralThreshold(35.0, n)
        val crowd = SairaVoiceprint.neuralThreshold(14.0, n)
        assertTrue(quiet > crowd)
        assertTrue(SairaVoiceprint.neuralThreshold(35.0, SairaVoiceprint.STRICT) > quiet)
        assertTrue(SairaVoiceprint.neuralThreshold(35.0, SairaVoiceprint.RELAXED) < quiet)
        assertTrue(SairaVoiceprint.neuralThreshold(35.0, n, shortAnswer = true) < quiet)
        assertEquals(SairaVoiceprint.neuralThreshold(5.0, n), SairaVoiceprint.neuralThreshold(0.0, n), 1e-9)
    }
}
