package com.example.data.ai

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class GeminiModelQuotaTest {
    private val now = ZonedDateTime.of(2026, 11, 28, 9, 30, 0, 0, ZoneId.of("Asia/Dhaka")).toInstant().toEpochMilli()

    @Before fun setUp() = GeminiModelQuota.reset()
    @After fun tearDown() = GeminiModelQuota.reset()

    @Test
    fun `daily limit parks the key only for that model, until midnight Pacific`() {
        GeminiModelQuota.markLimit("gemini-3.5-flash-lite", "k1", daily = true, now = now)
        assertFalse(GeminiModelQuota.usable("gemini-3.5-flash-lite", "k1", now + 3_600_000))
        assertTrue(GeminiModelQuota.usable("gemini-3.1-flash-lite", "k1", now))
        assertTrue(GeminiModelQuota.usable("gemini-3.5-flash-lite", "k2", now))
        val reset = GeminiModelQuota.nextDailyReset(now)
        // 09:30 Dhaka = 19:30 Pacific (previous day) → resets 4.5 h later at 00:00 Pacific
        assertEquals(4.5 * 3_600_000, (reset - now).toDouble(), 1.0)
        assertTrue(GeminiModelQuota.usable("gemini-3.5-flash-lite", "k1", reset))
    }

    @Test
    fun `per-minute limit only lasts about a minute`() {
        GeminiModelQuota.markLimit("gemini-3.1-flash-lite", "k1", daily = false, now = now)
        assertFalse(GeminiModelQuota.usable("gemini-3.1-flash-lite", "k1", now + 30_000))
        assertTrue(GeminiModelQuota.usable("gemini-3.1-flash-lite", "k1", now + 66_000))
    }

    @Test
    fun `a model that is out of daily quota everywhere is skipped for a while`() {
        repeat(8) { GeminiModelQuota.markLimit("gemini-3.5-flash-lite", "k$it", daily = true, now = now) }
        assertFalse(GeminiModelQuota.modelUsable("gemini-3.5-flash-lite", now + 60_000))
        assertTrue(GeminiModelQuota.modelUsable("gemini-3.1-flash-lite", now))
        assertTrue(GeminiModelQuota.modelUsable("gemini-3.5-flash-lite", now + 16 * 60_000))
    }

    @Test
    fun `quota error kind is read from Google's quotaId`() {
        assertTrue(GeminiModelQuota.isDailyLimit("\"quotaId\": \"GenerateRequestsPerDayPerProjectPerModel-FreeTier\""))
        assertFalse(GeminiModelQuota.isDailyLimit("\"quotaId\": \"GenerateRequestsPerMinutePerProjectPerModel-FreeTier\""))
    }

    @Test
    fun `chat walks several models, best first`() {
        assertEquals(GeminiAiProvider.DEFAULT_MODEL, GeminiModelQuota.CHAT_MODELS.first())
        assertTrue(GeminiModelQuota.CHAT_MODELS.size >= 4)
        assertTrue("gemini-3.1-flash-lite" in GeminiModelQuota.CHAT_MODELS)
    }
}
