package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Media host for chat photo / video / voice uploads.
 *
 * The app's Firebase project is on the Spark plan, and Cloud Storage for
 * Firebase requires the Blaze plan (since 2024-10-30), so bucket uploads are
 * impossible here. Files go to catbox.moe instead: free, anonymous, permanent
 * direct links, no API key (nothing secret embedded in the app).
 *
 * Returns the direct https URL, e.g. https://files.catbox.moe/ab12cd.mp4
 */
object MediaHost {
    private const val ENDPOINT = "https://catbox.moe/user/api.php"
    private const val MAX_BYTES = 50L * 1024 * 1024 // catbox allows 200 MB; keep mobile-friendly
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 180_000

    /** Uploads the content behind [uri] (content:// or file://). */
    suspend fun upload(context: Context, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val size = querySize(context, uri)
                if (size != null && size > MAX_BYTES) {
                    return@withContext Result.failure(
                        IOException("File is ${size / (1024 * 1024)} MB (limit 50 MB).")
                    )
                }
                val input = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(IOException("Cannot open selected file."))
                input.use { uploadStream(it) }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Uploads a local file (recorded voice notes). */
    suspend fun upload(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || file.length() == 0L) {
                return@withContext Result.failure(IOException("File is empty."))
            }
            if (file.length() > MAX_BYTES) {
                return@withContext Result.failure(IOException("File exceeds the 50 MB limit."))
            }
            file.inputStream().use { uploadStream(it) }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun uploadStream(input: java.io.InputStream): Result<String> {
        val boundary = "----chatboxBoundary${System.currentTimeMillis()}"
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        return try {
            conn.doOutput = true
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.outputStream.use { out ->
                out.write(
                    ("--$boundary\r\n" +
                        "Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n" +
                        "fileupload\r\n" +
                        "--$boundary\r\n" +
                        "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"chatbox_upload\"\r\n" +
                        "Content-Type: application/octet-stream\r\n\r\n").toByteArray(Charsets.UTF_8)
                )
                input.copyTo(out)
                out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
                out.flush()
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.readBytes()?.toString(Charsets.UTF_8)?.trim().orEmpty()
            if (code in 200..299 && body.startsWith("https://")) {
                Result.success(body)
            } else {
                Result.failure(IOException("Upload failed (HTTP $code): ${body.take(160)}"))
            }
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun querySize(context: Context, uri: Uri): Long? = runCatching {
        if (uri.scheme == "file") return File(uri.path ?: return null).length()
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !c.isNull(idx)) c.getLong(idx) else null
            } else null
        }
    }.getOrNull()
}
