package com.example.saira

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Small on-device helpers for noisy rooms (pure Kotlin).
 *  - [snrDb]: how far the speaker stands above the room noise in one utterance.
 *  - [denoise]: spectral subtraction — removes the steady part of the room noise (crowd murmur, fan,
 *    traffic) estimated from the quietest moments of the clip, before the voice check.
 */
object SairaDenoise {
    private const val N = 512
    private const val HOP = 128

    /** Loud frames (90th percentile) vs the floor (15th percentile), in dB. */
    fun snrDb(pcm: ShortArray): Double {
        val frame = 400
        val hop = 160
        if (pcm.size < frame + hop) return 0.0
        val n = (pcm.size - frame) / hop + 1
        val e = DoubleArray(n) { f ->
            var s = 0.0
            val off = f * hop
            for (i in 0 until frame) { val x = pcm[off + i].toDouble(); s += x * x }
            10 * log10(s / frame + 1.0)
        }
        e.sort()
        return e[(n * 0.9).toInt().coerceAtMost(n - 1)] - e[(n * 0.15).toInt()]
    }

    fun denoise(pcm: ShortArray, overSubtract: Double = 1.5, floor: Double = 0.08): ShortArray {
        if (pcm.size < N * 2) return pcm
        val win = DoubleArray(N) { 0.5 - 0.5 * cos(2 * PI * it / N) }
        val frames = (pcm.size - N) / HOP + 1
        val specRe = Array(frames) { DoubleArray(N) }
        val specIm = Array(frames) { DoubleArray(N) }
        val energy = DoubleArray(frames)
        for (f in 0 until frames) {
            val re = specRe[f]; val im = specIm[f]
            val off = f * HOP
            for (i in 0 until N) re[i] = pcm[off + i] * win[i]
            fft(re, im, false)
            var e = 0.0
            for (k in 0..N / 2) e += re[k] * re[k] + im[k] * im[k]
            energy[f] = e
        }
        val quiet = (0 until frames).sortedBy { energy[it] }.take(max(3, frames / 5))
        val noise = DoubleArray(N / 2 + 1)
        for (f in quiet) for (k in 0..N / 2) noise[k] += specRe[f][k] * specRe[f][k] + specIm[f][k] * specIm[f][k]
        for (k in noise.indices) noise[k] /= quiet.size
        val out = DoubleArray(pcm.size)
        val norm = DoubleArray(pcm.size)
        for (f in 0 until frames) {
            val re = specRe[f]; val im = specIm[f]
            for (k in 0..N / 2) {
                val p = re[k] * re[k] + im[k] * im[k] + 1e-9
                val g = sqrt(max(1.0 - overSubtract * noise[k] / p, floor * floor))
                re[k] *= g; im[k] *= g
                if (k in 1 until N / 2) { re[N - k] = re[k]; im[N - k] = -im[k] }
            }
            fft(re, im, true)
            val off = f * HOP
            for (i in 0 until N) { out[off + i] += re[i] * win[i]; norm[off + i] += win[i] * win[i] }
        }
        return ShortArray(pcm.size) { i ->
            val v = if (norm[i] > 1e-3) out[i] / norm[i] else 0.0
            v.coerceIn(-32767.0, 32767.0).toInt().toShort()
        }
    }

    private fun fft(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = (if (inverse) 2 else -2) * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k; val b = a + len / 2
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                    val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) for (i in 0 until n) { re[i] /= n; im[i] /= n }
    }
}
