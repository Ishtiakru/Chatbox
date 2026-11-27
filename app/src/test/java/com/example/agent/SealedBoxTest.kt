package com.example.agent

import org.junit.Assert.assertEquals
import org.junit.Test

/** Vectors generated with libsodium (PyNaCl) and Python's hashlib. */
class SealedBoxTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    @Test
    fun blake2bMatchesReference() {
        val b = ByteArray(400) { (it % 200).toByte() }
        assertEquals("1c0c772ef9a0a2952f33a0bc0a7d4274f8adb219e2d83d3a", SealedBox.blake2b(b, 24).hex())
        assertEquals("ab3b5331a7135ed50d0f182d026e60abdb3646fd51bcf8a3", SealedBox.blake2b(ByteArray(0), 24).hex())
        assertEquals("2f5f6c3d92c87ec6916cd0034fea92dfd83b28dc91485d41", SealedBox.blake2b(ByteArray(128) { it.toByte() }, 24).hex())
    }

    @Test
    fun x25519PublicKeyMatchesLibsodium() {
        val sk = ByteArray(32) { (100 + it).toByte() }
        val base = ByteArray(32).also { it[0] = 9 }
        assertEquals("7d9c24316539825c1896e57f28197746793ce60cbee3ad47da9d07b85fa55e2a", SealedBox.x25519(sk, base).hex())
    }

    @Test
    fun sealMatchesLibsodium() {
        val msg = "sk-ant-test-key-123\nAIzaXYZ gemini-pro-latest\n".repeat(5).toByteArray()
        val esk = ByteArray(32) { (1 + it).toByte() }
        val ct = SealedBox.seal(msg, hex("7d9c24316539825c1896e57f28197746793ce60cbee3ad47da9d07b85fa55e2a"), esk)
        assertEquals(
            "07a37cbc142093c8b755dc1b10e86cb426374ad16aa853ed0bdfc0b2b86d1c7c6e9fd576d02df1e51e22ef2b039d42633059d1b60b4b60436a18dfef23e1832471e04b339bfe5223bcb9f5f65c165009c972a5890943513f60f41a0f75c6f87e06b3810a8e64edca418b83ab0b0e89bb9211ecb3783537f7cc5b13150baddfc0c1eb3814223b6107737d25d3a1121d6c6de50d3bf3058ba857c47dfe5242e39ea569b02f2394456d4d2580cc9b24db3bd15af61b551ac3b15ac62171b00b3b9ed002b489abe4e499a7126e589d7d467712eae4cf0567ab0fde527b5fcfef0010254f3a14d16d985bc4a04eb64cea36063374a67d1095af1be881e866d28bcb3517100687bc1f01648da279036a072025000d483a80ac",
            ct.hex()
        )
    }
}
