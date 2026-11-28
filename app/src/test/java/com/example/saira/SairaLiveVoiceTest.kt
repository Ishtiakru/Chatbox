package com.example.saira

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SairaLiveVoiceTest {
    @Test
    fun `live prompt carries the phone's local time and the addressing rule`() {
        val p = SairaLiveVoice.systemPrompt(ZonedDateTime.of(2026, 11, 28, 9, 5, 0, 0, ZoneId.of("Asia/Dhaka")))
        assertTrue(p, p.contains("9:05 AM") && p.contains("Asia/Dhaka"))
        assertTrue(p.contains("ONLY when someone calls you by name"))
        assertTrue(p.contains("phone_action"))
    }

    @Test
    fun `fastest live model is tried first and the voice is female`() {
        assertEquals("gemini-3.8-live", SairaLiveVoice.MODELS.first())
        assertEquals("Kore", SairaLiveVoice.VOICE)
    }
}
