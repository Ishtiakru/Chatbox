package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.data.ai.AiImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns a photo (file, https link, content:// uri or bitmap) into an [AiImage] Saira can look at.
 * Big photos are scaled to [MAX_SIDE] px and re-encoded as JPEG: small text (prescriptions,
 * documents, screenshots) stays readable while the upload stays fast.
 */
object AiImagePrep {
    private const val TAG = "AiImagePrep"
    private const val MAX_SIDE = 1600
    private const val MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024

    suspend fun fromPath(context: Context, path: String): AiImage? = withContext(Dispatchers.IO) {
        runCatching {
            when {
                path.startsWith("https://") || path.startsWith("http://") -> download(path)?.let { fromBytes(it) }
                path.startsWith("content://") || path.startsWith("file://") ->
                    context.contentResolver.openInputStream(Uri.parse(path))?.use { it.readBytes() }?.let { fromBytes(it) }
                else -> File(path).takeIf { it.exists() }?.readBytes()?.let { fromBytes(it) }
            }
        }.onFailure { Log.w(TAG, "load failed: ${path.take(80)}", it) }.getOrNull()
    }

    suspend fun fromUri(context: Context, uri: Uri): AiImage? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { fromBytes(it) }
        }.onFailure { Log.w(TAG, "uri not readable: $uri", it) }.getOrNull()
    }

    fun fromBitmap(bitmap: Bitmap): AiImage? = runCatching { encode(scaleDown(bitmap)) }.getOrNull()

    fun fromBytes(bytes: ByteArray): AiImage? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val scaled = scaleDown(decoded)
        val image = encode(scaled)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return image
    }

    private fun scaleDown(src: Bitmap): Bitmap {
        val side = max(src.width, src.height)
        if (side <= MAX_SIDE) return src
        val f = MAX_SIDE.toFloat() / side
        return Bitmap.createScaledBitmap(src, (src.width * f).roundToInt().coerceAtLeast(1), (src.height * f).roundToInt().coerceAtLeast(1), true)
    }

    private fun encode(bmp: Bitmap): AiImage {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
        return AiImage("image/jpeg", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
    }

    private fun download(url: String): ByteArray? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = true
        return try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_DOWNLOAD_BYTES) return null
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }
}
