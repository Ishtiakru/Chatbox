package com.example.agent

import java.math.BigInteger
import java.security.SecureRandom

/**
 * libsodium `crypto_box_seal` in pure Kotlin (X25519 + XSalsa20-Poly1305, nonce = BLAKE2b-24(epk‖pk)).
 * GitHub requires secrets to be encrypted this way before they are stored, so the admin's AI keys
 * can be sent from the phone straight into the repository's Actions secrets — never in plain text.
 */
object SealedBox {

    fun seal(message: ByteArray, recipientPublicKey: ByteArray, ephemeralSecret: ByteArray? = null): ByteArray {
        require(recipientPublicKey.size == 32) { "public key must be 32 bytes" }
        val esk = ephemeralSecret ?: ByteArray(32).also { SecureRandom().nextBytes(it) }
        val epk = x25519(esk, BASE_POINT)
        val nonce = blake2b(epk + recipientPublicKey, 24)
        val shared = x25519(esk, recipientPublicKey)
        val k = hsalsa20(shared, ByteArray(16))
        val sub = hsalsa20(k, nonce.copyOfRange(0, 16))
        val stream = salsa20Stream(sub, nonce.copyOfRange(16, 24), 32 + message.size)
        val cipher = ByteArray(message.size) { i -> (message[i].toInt() xor stream[32 + i].toInt()).toByte() }
        val tag = poly1305(stream.copyOfRange(0, 32), cipher)
        return epk + tag + cipher
    }

    // ── X25519 (RFC 7748 Montgomery ladder; BigInteger is fine for a one-off operation) ──
    private val P: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val A24: BigInteger = BigInteger.valueOf(121665)
    private val BASE_POINT = ByteArray(32).also { it[0] = 9 }

    private fun leToBig(b: ByteArray): BigInteger = BigInteger(1, b.reversedArray())

    private fun bigToLe(v: BigInteger, len: Int): ByteArray {
        val be = v.toByteArray()
        val out = ByteArray(len)
        for (i in 0 until minOf(len, be.size)) out[i] = be[be.size - 1 - i]
        return out
    }

    internal fun x25519(scalar: ByteArray, u: ByteArray): ByteArray {
        val k = scalar.copyOf()
        k[0] = (k[0].toInt() and 248).toByte()
        k[31] = ((k[31].toInt() and 127) or 64).toByte()
        val kn = leToBig(k)
        val uu = u.copyOf().also { it[31] = (it[31].toInt() and 127).toByte() }
        val x1 = leToBig(uu)
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = x1
        var z3 = BigInteger.ONE
        var swap = 0
        for (t in 254 downTo 0) {
            val kt = if (kn.testBit(t)) 1 else 0
            if ((swap xor kt) == 1) {
                x2 = x3.also { x3 = x2 }
                z2 = z3.also { z3 = z2 }
            }
            swap = kt
            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)
            x3 = da.add(cb).pow(2).mod(P)
            z3 = x1.multiply(da.subtract(cb).pow(2)).mod(P)
            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e))).mod(P)
        }
        if (swap == 1) {
            x2 = x3.also { x3 = x2 }
            z2 = z3.also { z3 = z2 }
        }
        return bigToLe(x2.multiply(z2.modPow(P.subtract(BigInteger.valueOf(2)), P)).mod(P), 32)
    }

    // ── Salsa20 / HSalsa20 ──
    private val SIGMA = intArrayOf(0x61707865, 0x3320646e, 0x79622d32, 0x6b206574)

    private fun u32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8) or
            ((b[off + 2].toInt() and 0xff) shl 16) or ((b[off + 3].toInt() and 0xff) shl 24)

    private fun putU32(out: ByteArray, off: Int, v: Int) {
        out[off] = v.toByte()
        out[off + 1] = (v ushr 8).toByte()
        out[off + 2] = (v ushr 16).toByte()
        out[off + 3] = (v ushr 24).toByte()
    }

    private fun state(key: ByteArray, n16: ByteArray): IntArray = intArrayOf(
        SIGMA[0], u32(key, 0), u32(key, 4), u32(key, 8), u32(key, 12), SIGMA[1],
        u32(n16, 0), u32(n16, 4), u32(n16, 8), u32(n16, 12),
        SIGMA[2], u32(key, 16), u32(key, 20), u32(key, 24), u32(key, 28), SIGMA[3]
    )

    private fun rounds(input: IntArray): IntArray {
        val x = input.copyOf()
        fun qr(a: Int, b: Int, c: Int, d: Int) {
            x[b] = x[b] xor Integer.rotateLeft(x[a] + x[d], 7)
            x[c] = x[c] xor Integer.rotateLeft(x[b] + x[a], 9)
            x[d] = x[d] xor Integer.rotateLeft(x[c] + x[b], 13)
            x[a] = x[a] xor Integer.rotateLeft(x[d] + x[c], 18)
        }
        repeat(10) {
            qr(0, 4, 8, 12); qr(5, 9, 13, 1); qr(10, 14, 2, 6); qr(15, 3, 7, 11)
            qr(0, 1, 2, 3); qr(5, 6, 7, 4); qr(10, 11, 8, 9); qr(15, 12, 13, 14)
        }
        return x
    }

    internal fun hsalsa20(key: ByteArray, n16: ByteArray): ByteArray {
        val x = rounds(state(key, n16))
        val out = ByteArray(32)
        intArrayOf(0, 5, 10, 15, 6, 7, 8, 9).forEachIndexed { i, idx -> putU32(out, i * 4, x[idx]) }
        return out
    }

    private fun salsa20Stream(key: ByteArray, n8: ByteArray, length: Int): ByteArray {
        val out = ByteArray(length)
        var pos = 0
        var counter = 0L
        val n16 = ByteArray(16)
        System.arraycopy(n8, 0, n16, 0, 8)
        val block = ByteArray(64)
        while (pos < length) {
            for (i in 0 until 8) n16[8 + i] = (counter ushr (8 * i)).toByte()
            val st = state(key, n16)
            val x = rounds(st)
            for (i in 0 until 16) putU32(block, i * 4, x[i] + st[i])
            val n = minOf(64, length - pos)
            System.arraycopy(block, 0, out, pos, n)
            pos += n
            counter++
        }
        return out
    }

    // ── Poly1305 ──
    private val P130: BigInteger = BigInteger.ONE.shiftLeft(130).subtract(BigInteger.valueOf(5))
    private val CLAMP = BigInteger("0ffffffc0ffffffc0ffffffc0fffffff", 16)
    private val MASK128: BigInteger = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)

    internal fun poly1305(key: ByteArray, msg: ByteArray): ByteArray {
        val r = leToBig(key.copyOfRange(0, 16)).and(CLAMP)
        val s = leToBig(key.copyOfRange(16, 32))
        var acc = BigInteger.ZERO
        var i = 0
        while (i < msg.size) {
            val end = minOf(i + 16, msg.size)
            val blk = msg.copyOfRange(i, end) + byteArrayOf(1)
            acc = acc.add(leToBig(blk)).multiply(r).mod(P130)
            i = end
        }
        return bigToLe(acc.add(s).and(MASK128), 16)
    }

    // ── BLAKE2b (unkeyed) ──
    private val IV = longArrayOf(
        0x6a09e667f3bcc908L, -0x4498517a7b3558c5L, 0x3c6ef372fe94f82bL, -0x5ab00ac5a0e2c90fL,
        0x510e527fade682d1L, -0x64fa9773d4c193e1L, 0x1f83d9abfb41bd6bL, 0x5be0cd19137e2179L
    )
    private val SIG = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
        intArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
        intArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
        intArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
        intArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
        intArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
        intArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
        intArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
        intArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0)
    )

    private fun u64(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (b[off + i].toLong() and 0xff)
        return v
    }

    internal fun blake2b(input: ByteArray, outLen: Int): ByteArray {
        val h = IV.copyOf()
        h[0] = h[0] xor (0x01010000L or outLen.toLong())
        val blocks = if (input.isEmpty()) 1 else (input.size + 127) / 128
        val buf = ByteArray(128)
        var counter = 0L
        for (bi in 0 until blocks) {
            val off = bi * 128
            val n = minOf(128, input.size - off).coerceAtLeast(0)
            java.util.Arrays.fill(buf, 0)
            if (n > 0) System.arraycopy(input, off, buf, 0, n)
            counter += n
            val last = bi == blocks - 1
            val m = LongArray(16) { u64(buf, it * 8) }
            val v = LongArray(16)
            for (i in 0 until 8) { v[i] = h[i]; v[i + 8] = IV[i] }
            v[12] = v[12] xor counter
            if (last) v[14] = v[14].inv()
            fun g(a: Int, b: Int, c: Int, d: Int, x: Long, y: Long) {
                v[a] = v[a] + v[b] + x
                v[d] = java.lang.Long.rotateRight(v[d] xor v[a], 32)
                v[c] = v[c] + v[d]
                v[b] = java.lang.Long.rotateRight(v[b] xor v[c], 24)
                v[a] = v[a] + v[b] + y
                v[d] = java.lang.Long.rotateRight(v[d] xor v[a], 16)
                v[c] = v[c] + v[d]
                v[b] = java.lang.Long.rotateRight(v[b] xor v[c], 63)
            }
            for (r in 0 until 12) {
                val s = SIG[r % 10]
                g(0, 4, 8, 12, m[s[0]], m[s[1]])
                g(1, 5, 9, 13, m[s[2]], m[s[3]])
                g(2, 6, 10, 14, m[s[4]], m[s[5]])
                g(3, 7, 11, 15, m[s[6]], m[s[7]])
                g(0, 5, 10, 15, m[s[8]], m[s[9]])
                g(1, 6, 11, 12, m[s[10]], m[s[11]])
                g(2, 7, 8, 13, m[s[12]], m[s[13]])
                g(3, 4, 9, 14, m[s[14]], m[s[15]])
            }
            for (i in 0 until 8) h[i] = h[i] xor v[i] xor v[i + 8]
        }
        val out = ByteArray(64)
        for (i in 0 until 8) for (j in 0 until 8) out[i * 8 + j] = (h[i] ushr (8 * j)).toByte()
        return out.copyOf(outLen)
    }
}
