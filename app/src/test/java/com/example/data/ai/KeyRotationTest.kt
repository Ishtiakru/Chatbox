package com.example.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyRotationTest {

    @Test
    fun `empty pool yields no keys`() {
        val out = KeyRotation.order(emptyList(), emptyMap(), now = 1000L, cursor = 0)
        assertTrue(out.isEmpty())
    }

    @Test
    fun `all keys cooling yields no keys`() {
        val keys = listOf("a", "b")
        val cooling = mapOf("a" to 2000L, "b" to 2000L)
        val out = KeyRotation.order(keys, cooling, now = 1000L, cursor = 0)
        assertTrue(out.isEmpty())
    }

    @Test
    fun `cooling keys are skipped`() {
        val keys = listOf("a", "b", "c")
        val cooling = mapOf("b" to 2000L)
        val out = KeyRotation.order(keys, cooling, now = 1000L, cursor = 0)
        assertEquals(listOf("a", "c"), out)
    }

    @Test
    fun `expired cooldown makes the key available again`() {
        val keys = listOf("a", "b")
        val cooling = mapOf("a" to 500L)
        val out = KeyRotation.order(keys, cooling, now = 1000L, cursor = 0)
        assertEquals(listOf("a", "b"), out)
    }

    @Test
    fun `cursor rotates the start key round-robin`() {
        val keys = listOf("a", "b", "c")
        assertEquals(listOf("a", "b", "c"), KeyRotation.order(keys, emptyMap(), 0L, cursor = 0))
        assertEquals(listOf("b", "c", "a"), KeyRotation.order(keys, emptyMap(), 0L, cursor = 1))
        assertEquals(listOf("c", "a", "b"), KeyRotation.order(keys, emptyMap(), 0L, cursor = 2))
        assertEquals(listOf("a", "b", "c"), KeyRotation.order(keys, emptyMap(), 0L, cursor = 3))
        assertEquals(listOf("b", "c", "a"), KeyRotation.order(keys, emptyMap(), 0L, cursor = 4))
    }

    @Test
    fun `negative cursor still indexes safely`() {
        val keys = listOf("a", "b")
        val out = KeyRotation.order(keys, emptyMap(), 0L, cursor = -1)
        assertEquals(listOf("b", "a"), out)
    }
}
