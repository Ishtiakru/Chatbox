package com.example.saira

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Neural speaker encoder (pure Kotlin, on-device): turns speech into a 256-number "voice fingerprint"
 * (d-vector). Two clips of the same person give fingerprints pointing the same way (cosine ≈ 0.9),
 * different people give clearly lower values — even with other people talking in the background,
 * which the older MFCC/GMM check could not handle.
 *
 * Model: GE2E speaker encoder (3-layer LSTM 256 → linear 256, ReLU, L2) from Resemblyzer
 * (Apache-2.0, trained on thousands of real-world speakers), weights stored as float16 in
 * `assets/saira_voice_encoder.bin` with the exact librosa 40-band mel filterbank it was trained on.
 */
class SairaVoiceEncoder private constructor(
    private val melFb: FloatArray,              // 40 × 201
    private val w: Array<FloatArray>,           // per layer: 1024 rows of [W_ih | W_hh] (in + 256)
    private val bias: Array<FloatArray>,        // per layer: 1024 (ih + hh)
    private val linW: FloatArray,               // 256 × 256
    private val linB: FloatArray
) {
    /** Fingerprint of a 16 kHz utterance (L2-normalised), or null when it is too short. */
    fun embed(pcm: ShortArray, threads: Int = DEFAULT_THREADS): FloatArray? {
        val parts = embedPartials(pcm, threads) ?: return null
        val avg = FloatArray(EMB)
        for (p in parts) for (j in 0 until EMB) avg[j] += p[j]
        return l2(avg)
    }

    /** One fingerprint per 1.6 s window of the utterance. */
    fun embedPartials(pcm: ShortArray, threads: Int = DEFAULT_THREADS): List<FloatArray>? {
        if (pcm.size < SAMPLE_RATE / 2) return null
        var wav = FloatArray(pcm.size) { pcm[it] / 32767f }
        wav = normalizeVolume(wav)
        // Partial windows of 1.6 s, 1.3 per second (as in training); the last one is kept if ≥ 75 % covered.
        val nFrames = Math.ceil((wav.size + 1) / HOP.toDouble()).toInt()
        val step = Math.round(SAMPLE_RATE / RATE / HOP).toInt()
        val starts = ArrayList<Int>()
        var i = 0
        val steps = maxOf(1, nFrames - PARTIAL + step + 1)
        while (i < steps) { starts.add(i); i += step }
        val coverage = (wav.size - starts.last() * HOP).toDouble() / (PARTIAL * HOP)
        if (coverage < 0.75 && starts.size > 1) starts.removeAt(starts.size - 1)
        val need = (starts.last() + PARTIAL) * HOP
        if (need > wav.size) wav = wav.copyOf(need)
        val mel = melSpectrogram(wav)
        val partials = arrayOfNulls<FloatArray>(starts.size)
        val workers = minOf(threads, starts.size).coerceAtLeast(1)
        if (workers == 1) {
            starts.forEachIndexed { k, s -> partials[k] = embedFrames(mel, s) }
        } else {
            val pool = (0 until workers).map { w ->
                Thread {
                    var k = w
                    while (k < starts.size) { partials[k] = embedFrames(mel, starts[k]); k += workers }
                }.apply { start() }
            }
            pool.forEach { it.join() }
        }
        return partials.map { it!! }
    }

    /** Mel power spectrogram (librosa: n_fft 400, hop 160, hann, centre zero-padding, power 2). */
    fun melSpectrogram(wav: FloatArray): Array<FloatArray> {
        val n = 1 + wav.size / HOP
        val out = Array(n) { FloatArray(MELS) }
        val frame = FloatArray(NFFT)
        val power = FloatArray(BINS)
        for (f in 0 until n) {
            val start = f * HOP - NFFT / 2
            for (t in 0 until NFFT) {
                val idx = start + t
                frame[t] = if (idx in wav.indices) wav[idx] * HANN[t] else 0f
            }
            for (k in 0 until BINS) {
                var re = 0.0; var im = 0.0
                val base = k * NFFT
                for (t in 0 until NFFT) { val x = frame[t]; re += x * COS[base + t]; im -= x * SIN[base + t] }
                power[k] = (re * re + im * im).toFloat()
            }
            val row = out[f]
            for (m in 0 until MELS) {
                var s = 0f
                val o = m * BINS
                for (k in 0 until BINS) s += melFb[o + k] * power[k]
                row[m] = s
            }
        }
        return out
    }

    private fun embedFrames(mel: Array<FloatArray>, start: Int): FloatArray {
        var input = Array(PARTIAL) { t -> mel.getOrNull(start + t) ?: FloatArray(MELS) }
        var h = FloatArray(H)
        for (layer in 0 until 3) {
            val inDim = if (layer == 0) MELS else H
            val width = inDim + H
            val wl = w[layer]; val b = bias[layer]
            h = FloatArray(H)
            val c = FloatArray(H)
            val g = FloatArray(4 * H)
            val xh = FloatArray(width)
            val outs = Array(PARTIAL) { FloatArray(H) }
            for (t in 0 until PARTIAL) {
                input[t].copyInto(xh, 0)
                h.copyInto(xh, inDim)
                for (r in 0 until 4 * H) g[r] = b[r] + dot(wl, r * width, xh, width)
                for (j in 0 until H) {
                    val ig = sigmoid(g[j]); val fg = sigmoid(g[H + j])
                    val gg = tanh(g[2 * H + j]); val og = sigmoid(g[3 * H + j])
                    c[j] = fg * c[j] + ig * gg
                    h[j] = og * tanh(c[j])
                }
                h.copyInto(outs[t])
            }
            input = outs
        }
        val e = FloatArray(EMB) { r ->
            var s = linB[r]
            val o = r * H
            for (j in 0 until H) s += linW[o + j] * h[j]
            if (s > 0f) s else 0f
        }
        return l2(e)
    }

    companion object {
        /** Dot product with 4 independent sums (lets the CPU overlap the multiplies). [n] is a multiple of 4. */
        private fun dot(a: FloatArray, off: Int, x: FloatArray, n: Int): Float {
            var s0 = 0f; var s1 = 0f; var s2 = 0f; var s3 = 0f
            var j = 0
            var o = off
            while (j < n) {
                s0 += a[o] * x[j]; s1 += a[o + 1] * x[j + 1]; s2 += a[o + 2] * x[j + 2]; s3 += a[o + 3] * x[j + 3]
                j += 4; o += 4
            }
            return (s0 + s1) + (s2 + s3)
        }

        const val SAMPLE_RATE = 16_000
        const val EMB = 256
        private const val H = 256
        private const val MELS = 40
        private const val NFFT = 400
        private const val BINS = NFFT / 2 + 1
        private const val HOP = 160
        private const val PARTIAL = 160
        private const val RATE = 1.3
        const val ASSET = "saira_voice_encoder.bin"
        /** Windows are independent, so they run on up to 4 cores (≈ 0.2–0.4 s for a 3 s command). */
        val DEFAULT_THREADS: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

        private val HANN = FloatArray(NFFT) { (0.5 - 0.5 * cos(2 * PI * it / NFFT)).toFloat() }
        private val COS: FloatArray by lazy { FloatArray(BINS * NFFT) { val k = it / NFFT; val t = it % NFFT; cos(2 * PI * ((k * t) % NFFT) / NFFT).toFloat() } }
        private val SIN: FloatArray by lazy { FloatArray(BINS * NFFT) { val k = it / NFFT; val t = it % NFFT; kotlin.math.sin(2 * PI * ((k * t) % NFFT) / NFFT).toFloat() } }

        fun load(stream: InputStream): SairaVoiceEncoder {
            val bytes = stream.use { it.readBytes() }
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4).also { buf.get(it) }
            require(String(magic) == "SVE1") { "bad voice encoder file" }
            val melFb = FloatArray(MELS * BINS) { buf.float }
            fun f16(n: Int) = FloatArray(n) { halfToFloat(buf.short.toInt() and 0xffff) }
            val w = arrayOfNulls<FloatArray>(3); val b = arrayOfNulls<FloatArray>(3)
            for (l in 0 until 3) {
                val inDim = if (l == 0) MELS else H
                val wi = f16(4 * H * inDim)
                val wh = f16(4 * H * H)
                val width = inDim + H
                w[l] = FloatArray(4 * H * width).also { m ->
                    for (r in 0 until 4 * H) {
                        System.arraycopy(wi, r * inDim, m, r * width, inDim)
                        System.arraycopy(wh, r * H, m, r * width + inDim, H)
                    }
                }
                b[l] = f16(4 * H)
            }
            val linW = f16(EMB * H)
            val linB = f16(EMB)
            require(!buf.hasRemaining()) { "voice encoder file size mismatch" }
            return SairaVoiceEncoder(melFb, w.requireNoNulls(), b.requireNoNulls(), linW, linB)
        }

        fun cosine(a: FloatArray, b: FloatArray): Double {
            var s = 0.0; var na = 0.0; var nb = 0.0
            for (i in a.indices) { s += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
            return s / (sqrt(na * nb) + 1e-12)
        }

        fun l2(v: FloatArray): FloatArray {
            var n = 0.0
            for (x in v) n += x * x
            val inv = (1.0 / (sqrt(n) + 1e-12)).toFloat()
            return FloatArray(v.size) { v[it] * inv }
        }

        /** Raise quiet speech to −30 dBFS (never lower), like the training pipeline. */
        private fun normalizeVolume(wav: FloatArray): FloatArray {
            var s = 0.0
            for (x in wav) s += (x * 32767.0) * (x * 32767.0)
            val rms = sqrt(s / wav.size)
            val db = 20 * log10(rms / 32767.0 + 1e-12)
            val change = -30.0 - db
            if (change <= 0) return wav
            val g = Math.pow(10.0, change / 20).toFloat()
            return FloatArray(wav.size) { wav[it] * g }
        }

        private fun sigmoid(x: Float): Float = (1.0 / (1.0 + exp(-x.toDouble()))).toFloat()

        fun halfToFloat(h: Int): Float {
            val sign = (h ushr 15) and 1
            val exp = (h ushr 10) and 0x1f
            val mant = h and 0x3ff
            val v = when (exp) {
                0 -> mant / 1024f * Math.pow(2.0, -14.0).toFloat()
                31 -> if (mant == 0) Float.POSITIVE_INFINITY else Float.NaN
                else -> (1 + mant / 1024f) * Math.pow(2.0, (exp - 15).toDouble()).toFloat()
            }
            return if (sign == 1) -v else v
        }
    }
}
