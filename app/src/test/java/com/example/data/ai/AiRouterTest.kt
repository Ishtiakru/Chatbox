package com.example.data.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiRouterTest {

    private class FakeProvider(
        override val id: String,
        private val respond: suspend () -> String
    ) : AiProvider {
        var calls = 0

        override suspend fun generate(
            systemInstruction: String,
            history: List<AiChatMessage>,
            prompt: String
        ): String {
            calls++
            return respond()
        }
    }

    private class QuotaStreamingProvider(
        override val id: String,
        private val emitBeforeQuota: Boolean
    ) : AiProvider {
        var calls = 0

        override suspend fun generate(
            systemInstruction: String,
            history: List<AiChatMessage>,
            prompt: String
        ): String = "unused"

        override fun stream(
            systemInstruction: String,
            history: List<AiChatMessage>,
            prompt: String
        ): Flow<String> = flow {
            calls++
            if (emitBeforeQuota) emit("partial answer")
            throw AiQuotaExhaustedException("quota exhausted")
        }
    }

    private var nowMs = 1_000_000L

    private fun newRouter(
        primary: AiProvider,
        backup: AiProvider?
    ) = AiRouter(
        primary = primary,
        fallback = { backup },
        cooldowns = AiCooldownStore(InMemoryAiCooldownBackend()),
        now = { nowMs },
        cooldownMs = 60_000L
    )

    @Test
    fun `primary is used when it is healthy`() = runTest {
        val primary = FakeProvider("gemini") { "hello from gemini" }
        val backup = FakeProvider("backup") { "hello from backup" }
        val router = newRouter(primary, backup)

        val answer = router.complete("system", emptyList(), "hi")

        assertEquals("hello from gemini", answer)
        assertEquals(1, primary.calls)
        assertEquals(0, backup.calls)
    }

    @Test
    fun `quota error on primary switches to the backup provider`() = runTest {
        val primary = FakeProvider("gemini") { throw AiQuotaExhaustedException("429") }
        val backup = FakeProvider("backup") { "hello from backup" }
        val router = newRouter(primary, backup)

        val answer = router.complete("system", emptyList(), "hi")

        assertEquals("hello from backup", answer)
        assertEquals(1, primary.calls)
        assertEquals(1, backup.calls)
        assertTrue(router.cooldownRemainingMs("gemini") > 0L)
    }

    @Test
    fun `quota error without a backup explains that no backup is configured`() = runTest {
        val primary = FakeProvider("gemini") { throw AiQuotaExhaustedException("429") }
        val router = newRouter(primary, null)

        try {
            router.complete("system", emptyList(), "hi")
            fail("expected AiUnavailableException")
        } catch (e: AiUnavailableException) {
            assertEquals(AiRouter.NO_BACKUP_MESSAGE, e.message)
        }
    }

    @Test
    fun `both providers exhausted cools down both and fails fast afterwards`() = runTest {
        val primary = FakeProvider("gemini") { throw AiQuotaExhaustedException("429") }
        val backup = FakeProvider("backup") { throw AiQuotaExhaustedException("429") }
        val router = newRouter(primary, backup)

        try {
            router.complete("system", emptyList(), "hi")
            fail("expected AiUnavailableException")
        } catch (e: AiUnavailableException) {
            assertEquals(AiRouter.ALL_EXHAUSTED_MESSAGE, e.message)
        }

        assertEquals(1, primary.calls)
        assertEquals(1, backup.calls)

        try {
            router.complete("system", emptyList(), "hi again")
            fail("expected AiUnavailableException")
        } catch (e: AiUnavailableException) {
            assertEquals(AiRouter.ALL_EXHAUSTED_MESSAGE, e.message)
        }

        assertEquals(1, primary.calls)
        assertEquals(1, backup.calls)
    }

    @Test
    fun `cooldown expiry retries the primary provider`() = runTest {
        var attempts = 0
        val primary = FakeProvider("gemini") {
            attempts++
            if (attempts == 1) throw AiQuotaExhaustedException("429")
            "recovered"
        }
        val backup = FakeProvider("backup") { "backup answer" }
        val router = newRouter(primary, backup)

        assertEquals("backup answer", router.complete("system", emptyList(), "hi"))
        nowMs += 60_001L

        assertEquals("recovered", router.complete("system", emptyList(), "hi"))
        assertEquals(2, primary.calls)
    }

    @Test
    fun `non quota primary failure also falls back`() = runTest {
        val primary = FakeProvider("gemini") { throw IllegalStateException("network down") }
        val backup = FakeProvider("backup") { "backup answer" }
        val router = newRouter(primary, backup)

        assertEquals("backup answer", router.complete("system", emptyList(), "hi"))
        // Transient failures must not put the primary into cooldown.
        assertEquals(0L, router.cooldownRemainingMs("gemini"))
    }

    @Test
    fun `non quota stream failure without backup surfaces real error instead of quota message`() = runTest {
        val primary = object : AiProvider {
            override val id = "gemini"
            override suspend fun generate(systemInstruction: String, history: List<AiChatMessage>, prompt: String) = ""
            override fun stream(systemInstruction: String, history: List<AiChatMessage>, prompt: String) = flow<String> {
                throw AiUnavailableException("Saira AI response timed out. Please try again.")
            }
        }
        val router = newRouter(primary, null)

        try {
            router.stream("system", emptyList(), "hi").toList()
            fail("expected AiUnavailableException")
        } catch (e: AiUnavailableException) {
            assertEquals("Saira AI response timed out. Please try again.", e.message)
        }
    }

    @Test
    fun `stream quota before first chunk switches to backup`() = runTest {
        val primary = QuotaStreamingProvider("gemini", emitBeforeQuota = false)
        val backup = FakeProvider("backup") { "backup stream answer" }
        val router = newRouter(primary, backup)

        val chunks = router.stream("system", emptyList(), "hi").toList()

        assertEquals(listOf("backup stream answer"), chunks)
        assertEquals(1, backup.calls)
    }

    @Test
    fun `stream quota after partial output keeps the partial answer`() = runTest {
        val primary = QuotaStreamingProvider("gemini", emitBeforeQuota = true)
        val backup = FakeProvider("backup") { "backup stream answer" }
        val router = newRouter(primary, backup)

        val chunks = router.stream("system", emptyList(), "hi").toList()

        assertEquals(listOf("partial answer"), chunks)
        assertEquals(0, backup.calls)
    }

    @Test
    fun `quota classifier matches the usual provider errors`() {
        assertTrue(AiErrorClassifier.isQuotaExhausted(IllegalStateException("RESOURCE_EXHAUSTED")))
        assertTrue(AiErrorClassifier.isQuotaExhausted(IllegalStateException("HTTP 429 Too Many Requests")))
        assertTrue(AiErrorClassifier.isQuotaExhausted(IllegalStateException("quota exceeded", IllegalStateException("rate limit"))))
        assertTrue(!AiErrorClassifier.isQuotaExhausted(IllegalStateException("invalid request")))
        assertTrue(!AiErrorClassifier.isQuotaExhausted(null))
    }

    @Test
    fun `backup config requires a key before it is usable`() {
        val missingKey = AiFallbackConfig(
            kind = AiFallbackProviderKind.GROQ,
            apiKey = "",
            baseUrl = "",
            model = ""
        )
        val withKey = missingKey.copy(apiKey = "gsk_test")

        assertTrue(!missingKey.isConfigured)
        assertTrue(missingKey.toProvider() == null)
        assertTrue(withKey.isConfigured)
        assertEquals("fallback-groq", withKey.toProvider()?.id)
        assertEquals(AiFallbackProviderKind.GROQ.defaultModel, withKey.toProvider()?.model)
    }
}
