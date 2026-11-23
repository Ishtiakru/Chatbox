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
        vararg backups: AiProvider
    ) = AiRouter(
        primary = primary,
        fallbacks = { backups.toList() },
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
    fun `quota error on primary switches to the first backup`() = runTest {
        val primary = FakeProvider("gemini") { throw AiQuotaExhaustedException("429") }
        val first = FakeProvider("backup-1") { "hello from backup 1" }
        val second = FakeProvider("backup-2") { "hello from backup 2" }
        val router = newRouter(primary, first, second)

        val answer = router.complete("system", emptyList(), "hi")

        assertEquals("hello from backup 1", answer)
        assertEquals(1, primary.calls)
        assertEquals(1, first.calls)
        assertEquals(0, second.calls)
        assertTrue(router.cooldownRemainingMs("gemini") > 0L)
    }

    @Test
    fun `quota error on the first backup moves to the second backup`() = runTest {
        val primary = FakeProvider("gemini") { throw AiQuotaExhaustedException("429") }
        val first = FakeProvider("backup-1") { throw AiQuotaExhaustedException("429") }
        val second = FakeProvider("backup-2") { "hello from backup 2" }
        val router = newRouter(primary, first, second)

        val answer = router.complete("system", emptyList(), "hi")

        assertEquals("hello from backup 2", answer)
        assertEquals(1, first.calls)
        assertEquals(1, second.calls)
        assertTrue(router.cooldownRemainingMs("backup-1") > 0L)
    }

    @Test
    fun `quota error without any backup explains that nothing is configured`() = runTest {
        val primary = FakeProvider("gemini") { throw AiQuotaExhaustedException("429") }
        val router = newRouter(primary)

        try {
            router.complete("system", emptyList(), "hi")
            fail("expected AiUnavailableException")
        } catch (e: AiUnavailableException) {
            assertEquals(AiRouter.NO_BACKUP_MESSAGE, e.message)
        }
    }

    @Test
    fun `everything exhausted cools down every provider and fails fast afterwards`() = runTest {
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
    fun `non quota primary failure also falls back without cooling down`() = runTest {
        val primary = FakeProvider("gemini") { throw IllegalStateException("network down") }
        val backup = FakeProvider("backup") { "backup answer" }
        val router = newRouter(primary, backup)

        assertEquals("backup answer", router.complete("system", emptyList(), "hi"))
        assertEquals(0L, router.cooldownRemainingMs("gemini"))
    }

    @Test
    fun `a broken backup is skipped in favour of the next one`() = runTest {
        val primary = FakeProvider("gemini") { throw AiQuotaExhaustedException("429") }
        val broken = FakeProvider("backup-1") { throw AiUnavailableException("HTTP 401") }
        val working = FakeProvider("backup-2") { "working answer" }
        val router = newRouter(primary, broken, working)

        assertEquals("working answer", router.complete("system", emptyList(), "hi"))
        assertEquals(1, broken.calls)
        assertEquals(1, working.calls)
    }

    @Test
    fun `stream quota before first chunk switches to the backup chain`() = runTest {
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
    fun `backup provider requires a key and keeps its kind defaults`() {
        val missingKey = AiBackupProvider(
            kindId = AiBackupProviderKind.GROQ.id,
            apiKey = "",
            baseUrl = "",
            model = ""
        )
        val withKey = missingKey.copy(apiKey = "gsk_test")

        assertTrue(!missingKey.isConfigured)
        assertEquals(null, missingKey.toProvider())
        assertTrue(withKey.isConfigured)
        assertEquals("backup-${withKey.id}", withKey.toProvider()?.id)
        assertEquals(AiBackupProviderKind.GROQ.defaultModel, withKey.toProvider()?.model)
    }

    @Test
    fun `every kind has usable defaults except custom`() {
        AiBackupProviderKind.entries.forEach { kind ->
            if (kind == AiBackupProviderKind.CUSTOM) {
                assertTrue(kind.defaultBaseUrl.isBlank())
            } else {
                assertTrue(kind.defaultBaseUrl.startsWith("https://"))
                assertTrue(kind.defaultModel.isNotBlank())
            }
        }
    }
}
