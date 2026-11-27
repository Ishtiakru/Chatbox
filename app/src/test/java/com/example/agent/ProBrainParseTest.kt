package com.example.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class ProBrainParseTest {
    @Test
    fun detectsProvidersAndOrdersStrongestFirst() {
        val keys = ProBrain.parse(
            """
            # comment
            gsk_abcdefghijklmnopqrstuvwx
            sk-or-v1-abcdefghijklmnop
            AIzaSyAbcdefghijklmnopqrstu gemini-pro-latest
            sk-0123456789abcdef0123456789abcdef
            deepseek:sk-proj-somethinglonger12
            sk-proj-abcdefghijklmnopqrstuvwxyz
            sk-ant-api03-abcdefghijklmnop claude-fable-5-2
            xai-abcdefghijklmnopqrstu
            junk-key-that-is-unknown-123
            sk-ant-api03-abcdefghijklmnop
            """.trimIndent()
        )
        assertEquals(
            listOf("anthropic", "openai", "gemini", "openrouter", "xai", "deepseek", "deepseek", "groq"),
            keys.map { it.provider }
        )
        assertEquals("claude-fable-5-2", keys.first().model)
        assertEquals("sk-proj-somethinglonger12", keys.first { it.key.startsWith("sk-proj-some") }.key)
        // round trip keeps the explicit deepseek prefix
        assertEquals(keys, ProBrain.parse(ProBrain.serialize(keys)))
    }

    @Test
    fun masksKeys() {
        assertEquals("sk-ant-…mnop", ProBrain.mask("sk-ant-api03-abcdefghijklmnop"))
    }
}

class KeyIntakeExtractTest {
    @Test
    fun cutsKeysOutOfAMessageAndUndoesMarkdownEscapes() {
        // Fake, generated at runtime (never a real key in the repo).
        val fake = "AQ." + "Test" + "_ab-9".repeat(9)
        val escaped = fake.replace("_", "\\_")
        val msg = "notun api gula add kore felo\n$escaped  \n$fake\nsk-ant-" + "x1".repeat(12) + ", thanks"
        val r = KeyIntake.extract(msg)
        assertEquals(listOf(fake, "sk-ant-" + "x1".repeat(12)), r.keys)
        assertEquals("notun api gula add kore felo\n, thanks", r.redacted)
    }

    @Test
    fun leavesNormalMessagesAlone() {
        val r = KeyIntake.extract("profile page e logout button add koro")
        assertEquals(emptyList<String>(), r.keys)
        assertEquals("profile page e logout button add koro", r.redacted)
    }
}
