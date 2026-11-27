package com.example.saira

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-device "only my voice" check for Saira voice mode (pure Kotlin, no Android deps).
 *
 * Enrollment: ~15 s of the owner talking → MFCC frames → an 8-component diagonal GMM
 * plus the median pitch. Later, each utterance is scored against that model and only
 * utterances that look like the owner are allowed to command Saira.
 *
 * This is a lightweight voice match (not bank-grade biometrics): it reliably separates
 * clearly different voices (other people, TV, kids) but a very similar voice can pass.
 * Nothing leaves the phone — only these numbers are stored.
 */
data class Voiceprint(
    val means: List<DoubleArray>,
    val variances: List<DoubleArray>,
    val weights: DoubleArray,
    /** Mean / std of the owner's own per-chunk scores (used to place the threshold). */
    val selfMean: Double,
    val selfStd: Double,
    val pitchHz: Double,
    val voicedSeconds: Double,
    val createdAtMs: Long
) {
    fun serialize(): String = buildString {
        append("v1;")
        append(weights.joinToString(",")); append(';')
        append(means.joinToString("|") { it.joinToString(",") }); append(';')
        append(variances.joinToString("|") { it.joinToString(",") }); append(';')
        append("$selfMean;$selfStd;$pitchHz;$voicedSeconds;$createdAtMs")
    }

    companion object {
        fun deserialize(raw: String?): Voiceprint? = runCatching {
            if (raw.isNullOrBlank()) return null
            val p = raw.split(';')
            if (p[0] != "v1" || p.size < 9) return null
            val w = p[1].split(',').map { it.toDouble() }.toDoubleArray()
            val m = p[2].split('|').map { r -> r.split(',').map { it.toDouble() }.toDoubleArray() }
            val v = p[3].split('|').map { r -> r.split(',').map { it.toDouble() }.toDoubleArray() }
            Voiceprint(m, v, w, p[4].toDouble(), p[5].toDouble(), p[6].toDouble(), p[7].toDouble(), p[8].toLong())
        }.getOrNull()
    }
}

data class VoiceMatch(val ok: Boolean, val score: Double, val threshold: Double, val pitchHz: Double, val reason: String)

object SairaVoiceprint {
    const val SAMPLE_RATE = 16_000
    private const val FRAME = 400      // 25 ms
    private const val HOP = 160        // 10 ms
    private const val NFFT = 512
    private const val MELS = 26
    private const val CEPS = 12
    private const val COMPONENTS = 8
    const val MIN_ENROLL_VOICED_SECONDS = 6.0
    private const val MIN_TEST_FRAMES = 35   // ~0.35 s of voiced speech
    const val MIN_COMMAND_FRAMES = MIN_TEST_FRAMES
    const val MIN_SHORT_FRAMES = 22          // a short "হ্যাঁ" / "না"

    /** 0 = strict, 1 = normal, 2 = relaxed. */
    const val STRICT = 0
    const val NORMAL = 1
    const val RELAXED = 2

    private val melBank: Array<DoubleArray> by lazy { buildMelBank() }
    private val window: DoubleArray by lazy { DoubleArray(FRAME) { 0.54 - 0.46 * cos(2 * PI * it / (FRAME - 1)) } }
    private val lifter: DoubleArray by lazy { DoubleArray(CEPS) { 1 + 11.0 * sin(PI * (it + 1) / 22.0) } }

    // ---------------------------------------------------------------- features
    /** Voiced MFCC frames (c1..c12, liftered). */
    fun features(pcm: ShortArray): List<DoubleArray> {
        if (pcm.size < FRAME) return emptyList()
        val n = (pcm.size - FRAME) / HOP + 1
        val energies = DoubleArray(n)
        val ceps = ArrayList<DoubleArray>(n)
        val re = DoubleArray(NFFT)
        val im = DoubleArray(NFFT)
        for (f in 0 until n) {
            val off = f * HOP
            var e = 0.0
            var prev = if (off > 0) pcm[off - 1].toDouble() else 0.0
            for (i in 0 until NFFT) { re[i] = 0.0; im[i] = 0.0 }
            for (i in 0 until FRAME) {
                val x = pcm[off + i].toDouble()
                e += x * x
                re[i] = (x - 0.97 * prev) * window[i]
                prev = x
            }
            energies[f] = ln(e / FRAME + 1e-9)
            // LPC spectral envelope (vocal-tract shape) instead of the raw spectrum, so the
            // voiceprint does not depend on how high or low the owner happens to speak.
            val power = lpcEnvelope(re, re2 = im)
            val mel = DoubleArray(MELS) { m ->
                var s = 0.0
                val bank = melBank[m]
                for (k in bank.indices) s += bank[k] * power[k]
                ln(s + 1e-10)
            }
            val c = DoubleArray(CEPS) { j ->
                val q = j + 1
                var s = 0.0
                for (m in 0 until MELS) s += mel[m] * cos(PI * q * (m + 0.5) / MELS)
                s * lifter[j]
            }
            ceps.add(c)
        }
        val maxE = energies.maxOrNull() ?: return emptyList()
        val absFloor = ln(160.0 * 160.0)          // RMS ≈ 160 / 32768 — quieter is silence
        val out = ArrayList<DoubleArray>()
        for (f in 0 until n) if (energies[f] > maxE - 4.0 && energies[f] > absFloor) out.add(ceps[f])
        return out
    }

    /** Median fundamental frequency of the voiced parts (0 when unknown). */
    fun pitch(pcm: ShortArray): Double {
        val win = 640
        val minLag = SAMPLE_RATE / 400
        val maxLag = SAMPLE_RATE / 60
        if (pcm.size < win + maxLag) return 0.0
        val values = ArrayList<Double>()
        var off = 0
        var maxRms = 1.0
        // find loudness reference
        while (off + win <= pcm.size) {
            var e = 0.0
            for (i in 0 until win) { val x = pcm[off + i].toDouble(); e += x * x }
            maxRms = max(maxRms, sqrt(e / win))
            off += win
        }
        off = 0
        while (off + win + maxLag <= pcm.size) {
            var e0 = 0.0
            for (i in 0 until win) { val x = pcm[off + i].toDouble(); e0 += x * x }
            val rms = sqrt(e0 / win)
            if (rms > 200 && rms > maxRms * 0.12) {
                var bestLag = -1
                var best = 0.0
                for (lag in minLag..maxLag) {
                    var num = 0.0
                    var e1 = 0.0
                    for (i in 0 until win) {
                        val a = pcm[off + i].toDouble()
                        val b = pcm[off + i + lag].toDouble()
                        num += a * b
                        e1 += b * b
                    }
                    val r = num / sqrt(e0 * e1 + 1e-9)
                    if (r > best) { best = r; bestLag = lag }
                }
                if (best > 0.5 && bestLag > 0) values.add(SAMPLE_RATE.toDouble() / bestLag)
            }
            off += win / 2
        }
        if (values.size < 3) return 0.0
        values.sort()
        return values[values.size / 2]
    }

    fun voicedSeconds(pcm: ShortArray): Double = features(pcm).size * HOP / SAMPLE_RATE.toDouble()

    // ---------------------------------------------------------------- enrollment
    fun enroll(pcm: ShortArray, createdAtMs: Long): Voiceprint? {
        val frames = features(pcm)
        val voiced = frames.size * HOP / SAMPLE_RATE.toDouble()
        if (voiced < MIN_ENROLL_VOICED_SECONDS) return null
        val gmm = trainGmm(frames)
        // Owner's own score spread over ~1.5 s chunks.
        val chunk = 150
        val chunkScores = ArrayList<Double>()
        var i = 0
        while (i + chunk <= frames.size) {
            chunkScores.add(avgLogLik(gmm, frames.subList(i, i + chunk)))
            i += chunk
        }
        if (chunkScores.isEmpty()) chunkScores.add(avgLogLik(gmm, frames))
        val mean = chunkScores.average()
        val std = sqrt(chunkScores.sumOf { (it - mean) * (it - mean) } / chunkScores.size)
        return Voiceprint(
            means = gmm.means, variances = gmm.vars, weights = gmm.weights,
            selfMean = mean, selfStd = std, pitchHz = pitch(pcm), voicedSeconds = voiced, createdAtMs = createdAtMs
        )
    }

    fun thresholdFor(vp: Voiceprint, sensitivity: Int): Double {
        val (k, margin) = when (sensitivity) {
            STRICT -> 2.0 to 1.8
            RELAXED -> 4.5 to 4.5
            else -> 3.0 to 3.0
        }
        return vp.selfMean - max(k * vp.selfStd, margin)
    }

    fun score(vp: Voiceprint, pcm: ShortArray): Double {
        val frames = features(pcm)
        if (frames.size < MIN_TEST_FRAMES) return Double.NaN
        return avgLogLik(Gmm(vp.means, vp.variances, vp.weights), frames)
    }

    fun match(vp: Voiceprint, pcm: ShortArray, sensitivity: Int = NORMAL, minFrames: Int = MIN_TEST_FRAMES): VoiceMatch {
        val frames = features(pcm)
        val th = thresholdFor(vp, sensitivity)
        if (frames.size < minFrames) return VoiceMatch(false, Double.NaN, th, 0.0, "too short")
        val s = avgLogLik(Gmm(vp.means, vp.variances, vp.weights), frames)
        val p = pitch(pcm)
        if (s < th) return VoiceMatch(false, s, th, p, "voice differs")
        if (vp.pitchHz > 0 && p > 0) {
            val (lo, hi) = when (sensitivity) {
                STRICT -> 0.80 to 1.28
                RELAXED -> 0.60 to 1.65
                else -> 0.70 to 1.45
            }
            val ratio = p / vp.pitchHz
            if (ratio < lo || ratio > hi) return VoiceMatch(false, s, th, p, "pitch differs")
        }
        return VoiceMatch(true, s, th, p, "ok")
    }

    // ---------------------------------------------------------------- GMM
    private class Gmm(val means: List<DoubleArray>, val vars: List<DoubleArray>, val weights: DoubleArray)

    private fun trainGmm(frames: List<DoubleArray>): Gmm {
        val d = CEPS
        val k = minOf(COMPONENTS, max(1, frames.size / 40))
        val rnd = java.util.Random(42)
        // global variance (for floors)
        val gMean = DoubleArray(d)
        frames.forEach { f -> for (j in 0 until d) gMean[j] += f[j] }
        for (j in 0 until d) gMean[j] /= frames.size
        val gVar = DoubleArray(d)
        frames.forEach { f -> for (j in 0 until d) { val t = f[j] - gMean[j]; gVar[j] += t * t } }
        for (j in 0 until d) gVar[j] = max(gVar[j] / frames.size, 1e-3)
        val floor = DoubleArray(d) { gVar[it] * 0.02 }

        // k-means++ init
        val centers = ArrayList<DoubleArray>()
        centers.add(frames[rnd.nextInt(frames.size)].copyOf())
        val dist = DoubleArray(frames.size) { Double.MAX_VALUE }
        while (centers.size < k) {
            var total = 0.0
            for (i in frames.indices) {
                dist[i] = minOf(dist[i], sq(frames[i], centers.last()))
                total += dist[i]
            }
            var r = rnd.nextDouble() * total
            var pick = 0
            for (i in frames.indices) { r -= dist[i]; if (r <= 0) { pick = i; break } }
            centers.add(frames[pick].copyOf())
        }
        val assign = IntArray(frames.size)
        repeat(12) {
            for (i in frames.indices) {
                var best = 0; var bd = Double.MAX_VALUE
                for (c in centers.indices) { val dd = sq(frames[i], centers[c]); if (dd < bd) { bd = dd; best = c } }
                assign[i] = best
            }
            for (c in centers.indices) {
                val acc = DoubleArray(d); var cnt = 0
                for (i in frames.indices) if (assign[i] == c) { cnt++; for (j in 0 until d) acc[j] += frames[i][j] }
                if (cnt > 0) for (j in 0 until d) centers[c][j] = acc[j] / cnt
            }
        }
        var means = centers.map { it.copyOf() }
        var vars = List(k) { gVar.copyOf() }
        var weights = DoubleArray(k) { 1.0 / k }
        // a few EM iterations
        repeat(6) {
            val resp = Array(frames.size) { DoubleArray(k) }
            for (i in frames.indices) {
                val logs = DoubleArray(k) { c -> ln(weights[c] + 1e-12) + logGauss(frames[i], means[c], vars[c]) }
                val mx = logs.max()
                var s = 0.0
                for (c in 0 until k) { resp[i][c] = exp(logs[c] - mx); s += resp[i][c] }
                for (c in 0 until k) resp[i][c] /= s
            }
            val nm = ArrayList<DoubleArray>(); val nv = ArrayList<DoubleArray>(); val nw = DoubleArray(k)
            for (c in 0 until k) {
                var nk = 0.0
                val mu = DoubleArray(d)
                for (i in frames.indices) { val r = resp[i][c]; nk += r; for (j in 0 until d) mu[j] += r * frames[i][j] }
                if (nk < 1e-6) { nm.add(means[c]); nv.add(vars[c]); nw[c] = 1e-6; continue }
                for (j in 0 until d) mu[j] /= nk
                val v = DoubleArray(d)
                for (i in frames.indices) { val r = resp[i][c]; for (j in 0 until d) { val t = frames[i][j] - mu[j]; v[j] += r * t * t } }
                for (j in 0 until d) v[j] = max(v[j] / nk, floor[j])
                nm.add(mu); nv.add(v); nw[c] = nk / frames.size
            }
            means = nm; vars = nv; weights = nw
        }
        return Gmm(means, vars, weights)
    }

    private fun avgLogLik(g: Gmm, frames: List<DoubleArray>): Double {
        var total = 0.0
        val k = g.weights.size
        for (f in frames) {
            val logs = DoubleArray(k) { c -> ln(g.weights[c] + 1e-12) + logGauss(f, g.means[c], g.vars[c]) }
            val mx = logs.max()
            var s = 0.0
            for (c in 0 until k) s += exp(logs[c] - mx)
            total += mx + ln(s)
        }
        return total / frames.size
    }

    private fun logGauss(x: DoubleArray, m: DoubleArray, v: DoubleArray): Double {
        var s = 0.0
        for (j in x.indices) {
            val t = x[j] - m[j]
            s += ln(2 * PI * v[j]) + t * t / v[j]
        }
        return -0.5 * s
    }

    private fun sq(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (j in a.indices) { val t = a[j] - b[j]; s += t * t }
        return s
    }

    private const val LPC_ORDER = 18

    /** Power envelope 1/|A(e^jw)|^2 of the frame in [frame] (first FRAME samples); [re2] is scratch. */
    private fun lpcEnvelope(frame: DoubleArray, re2: DoubleArray): DoubleArray {
        val r = DoubleArray(LPC_ORDER + 1)
        for (lag in 0..LPC_ORDER) {
            var s = 0.0
            for (i in 0 until FRAME - lag) s += frame[i] * frame[i + lag]
            r[lag] = s
        }
        r[0] = r[0] * 1.0001 + 1e-6 // white-noise correction
        val a = DoubleArray(LPC_ORDER + 1)
        a[0] = 1.0
        var err = r[0]
        val tmp = DoubleArray(LPC_ORDER + 1)
        for (i in 1..LPC_ORDER) {
            var acc = r[i]
            for (j in 1 until i) acc += a[j] * r[i - j]
            val k = -acc / err
            for (j in 0..i) tmp[j] = a[j]
            for (j in 1 until i) a[j] = tmp[j] + k * tmp[i - j]
            a[i] = k
            err *= (1 - k * k)
            if (err <= 1e-9) break
        }
        val re = DoubleArray(NFFT)
        for (j in 0 until NFFT) { re[j] = 0.0; re2[j] = 0.0 }
        for (j in 0..LPC_ORDER) re[j] = a[j]
        fft(re, re2)
        return DoubleArray(NFFT / 2 + 1) { k -> 1.0 / (re[k] * re[k] + re2[k] * re2[k] + 1e-12) }
    }

    // ---------------------------------------------------------------- DSP
    private fun hzToMel(hz: Double) = 2595.0 * kotlin.math.log10(1 + hz / 700.0)
    private fun melToHz(mel: Double) = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1)

    private fun buildMelBank(): Array<DoubleArray> {
        val bins = NFFT / 2 + 1
        val lo = hzToMel(80.0)
        val hi = hzToMel(7600.0)
        val pts = DoubleArray(MELS + 2) { melToHz(lo + (hi - lo) * it / (MELS + 1)) }
        val bin = IntArray(MELS + 2) { ((NFFT + 1) * pts[it] / SAMPLE_RATE).toInt().coerceIn(0, bins - 1) }
        return Array(MELS) { m ->
            val row = DoubleArray(bins)
            val l = bin[m]; val c = bin[m + 1]; val r = bin[m + 2]
            for (k in l until c) if (c > l) row[k] = (k - l).toDouble() / (c - l)
            for (k in c until r) if (r > c) row[k] = (r - k).toDouble() / (r - c)
            if (c in 0 until bins && row[c] == 0.0) row[c] = 1.0
            row
        }
    }

    /** In-place iterative radix-2 FFT. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val ar = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val ai = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k + len / 2] = re[i + k] - ar
                    im[i + k + len / 2] = im[i + k] - ai
                    re[i + k] += ar
                    im[i + k] += ai
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }

    // ---------------------------------------------------------------- audio utils
    /** 16-bit mono WAV bytes for the Gemini transcription request. */
    fun toWav(pcm: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val dataLen = pcm.size * 2
        val out = java.io.ByteArrayOutputStream(44 + dataLen)
        fun i32(v: Int) { out.write(v and 0xff); out.write((v shr 8) and 0xff); out.write((v shr 16) and 0xff); out.write((v shr 24) and 0xff) }
        fun i16(v: Int) { out.write(v and 0xff); out.write((v shr 8) and 0xff) }
        out.write("RIFF".toByteArray()); i32(36 + dataLen); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); i32(16); i16(1); i16(1); i32(sampleRate); i32(sampleRate * 2); i16(2); i16(16)
        out.write("data".toByteArray()); i32(dataLen)
        for (s in pcm) i16(s.toInt())
        return out.toByteArray()
    }

    fun rms(buf: ShortArray, len: Int = buf.size): Double {
        if (len <= 0) return 0.0
        var s = 0.0
        for (i in 0 until len) { val x = buf[i].toDouble(); s += x * x }
        return sqrt(s / len)
    }

    @Suppress("unused")
    private fun absMax(buf: ShortArray): Int = buf.maxOfOrNull { abs(it.toInt()) } ?: 0
}
