package com.example.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class BundledKeyCodecTest {
    @Test
    fun roundTripsKeysAndDropsBlanksAndDuplicates() {
        val keys = listOf("AQ.first-key", "AQ.second-key")
        val encoded = BundledKeyCodec.encode(keys + "AQ.first-key")
        assertEquals(keys, BundledKeyCodec.decode(encoded))
    }

    @Test
    fun matchesCiEncoderFormat() {
        // Same algorithm as release-apk.yml: XOR with mask, then base64.
        val mask = "chatbox-saira-pool".toByteArray()
        val plain = "AQ.a\nAQ.b".toByteArray()
        val enc = ByteArray(plain.size) { i -> (plain[i].toInt() xor mask[i % mask.size].toInt()).toByte() }
        val b64 = java.util.Base64.getEncoder().encodeToString(enc)
        assertEquals(listOf("AQ.a", "AQ.b"), BundledKeyCodec.decode(b64))
    }

    @Test
    fun blankInputGivesNoKeys() {
        assertEquals(emptyList<String>(), BundledKeyCodec.decode("  "))
    }
}
