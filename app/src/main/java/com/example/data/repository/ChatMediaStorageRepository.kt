package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.storage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

class ChatMediaStorageRepository(
    private val appContext: Context,
    private val storage: FirebaseStorage = Firebase.storage,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private companion object {
        const val TAG = "ChatMediaStorage"
        const val MAX_DIMENSION_PX = 1600
        const val JPEG_QUALITY = 82
        const val MAX_UPLOAD_BYTES = 9 * 1024 * 1024
        const val CAMERA_CACHE_DIR = "camera_images"
    }

    fun createCameraImageUri(): Uri {
        val dir = File(appContext.cacheDir, CAMERA_CACHE_DIR).apply { mkdirs() }
        val file = File(dir, "chat_photo_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
    }

    suspend fun uploadChatImage(
        conversationId: String,
        senderId: String,
        imageUri: Uri,
        onProgress: (Float) -> Unit
    ): Result<String> {
        return try {
            val currentUid = auth.currentUser?.uid
                ?: throw IllegalStateException("Sign in with Google before attaching images.")
            check(currentUid == senderId) { "Signed-in user changed; refusing image upload." }
            require(conversationId.isNotBlank() && !conversationId.contains('/')) {
                "Invalid conversation ID."
            }

            onProgress(0.05f)
            val jpegBytes = withContext(Dispatchers.IO) {
                compressImageToJpeg(imageUri)
            }
            require(jpegBytes.isNotEmpty() && jpegBytes.size <= MAX_UPLOAD_BYTES) {
                "Image exceeds the 9 MB upload limit."
            }

            val fileName = "${UUID.randomUUID()}.jpg"
            val storageRef = storage.reference
                .child("chat_media")
                .child(conversationId)
                .child(senderId)
                .child(fileName)

            val metadata = storageMetadata {
                contentType = "image/jpeg"
                setCustomMetadata("conversationId", conversationId)
                setCustomMetadata("senderId", senderId)
            }

            val uploadTask = storageRef.putBytes(jpegBytes, metadata)
            uploadTask.addOnProgressListener { snapshot ->
                val total = snapshot.totalByteCount
                if (total > 0L) {
                    val ratio = (snapshot.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0.05f, 0.95f)
                    onProgress(ratio)
                }
            }
            uploadTask.await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            onProgress(1f)
            deleteTempCameraFileIfOwned(imageUri)
            Result.success(downloadUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to upload chat image", e)
            Result.failure(IllegalStateException(friendlyStorageError(e), e))
        }
    }

    fun cleanupTempCameraFile(uriString: String?) {
        val parsed = uriString?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return
        deleteTempCameraFileIfOwned(parsed)
    }

    suspend fun uploadVoiceNote(
        conversationId: String,
        senderId: String,
        audioFile: File,
        onProgress: (Float) -> Unit
    ): Result<String> {
        return try {
            val currentUid = auth.currentUser?.uid
                ?: throw IllegalStateException("Sign in with Google before sending voice notes.")
            check(currentUid == senderId) { "Signed-in user changed; refusing voice upload." }
            require(conversationId.isNotBlank() && !conversationId.contains('/')) {
                "Invalid conversation ID."
            }

            onProgress(0.05f)
            val audioBytes = withContext(Dispatchers.IO) {
                require(audioFile.exists() && audioFile.isFile) { "Recorded voice note file is missing." }
                audioFile.readBytes()
            }
            require(audioBytes.isNotEmpty() && audioBytes.size <= MAX_UPLOAD_BYTES) {
                "Voice note exceeds the 9 MB upload limit."
            }

            val fileName = "voice_${UUID.randomUUID()}.m4a"
            val storageRef = storage.reference
                .child("chat_media")
                .child(conversationId)
                .child(senderId)
                .child(fileName)

            val metadata = storageMetadata {
                contentType = "audio/mp4"
                setCustomMetadata("conversationId", conversationId)
                setCustomMetadata("senderId", senderId)
            }

            val uploadTask = storageRef.putBytes(audioBytes, metadata)
            uploadTask.addOnProgressListener { snapshot ->
                val total = snapshot.totalByteCount
                if (total > 0L) {
                    val ratio = (snapshot.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0.05f, 0.95f)
                    onProgress(ratio)
                }
            }
            uploadTask.await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            onProgress(1f)
            runCatching { if (audioFile.exists()) audioFile.delete() }
            Result.success(downloadUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to upload voice note", e)
            Result.failure(IllegalStateException(friendlyStorageError(e), e))
        }
    }

    private fun deleteTempCameraFileIfOwned(uri: Uri) {
        if (uri.scheme != "content" || uri.authority != "${appContext.packageName}.fileprovider") return
        val fileName = uri.lastPathSegment ?: return
        val candidate = File(File(appContext.cacheDir, CAMERA_CACHE_DIR), fileName)
        if (candidate.exists()) {
            runCatching { candidate.delete() }
        }
    }

    private fun compressImageToJpeg(sourceUri: Uri): ByteArray {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(sourceUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: throw IllegalArgumentException("Unable to read selected image.")

        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Selected file is not a valid image."
        }

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION_PX)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = resolver.openInputStream(sourceUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: throw IllegalArgumentException("Unable to decode selected image.")

        val orientation = readExifOrientation(sourceUri)
        val oriented = applyExifAndScale(decoded, orientation, MAX_DIMENSION_PX)
        if (oriented !== decoded) {
            decoded.recycle()
        }

        return try {
            ByteArrayOutputStream().use { out ->
                oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        } finally {
            oriented.recycle()
        }
    }

    private fun readExifOrientation(sourceUri: Uri): Int {
        return runCatching {
            appContext.contentResolver.openInputStream(sourceUri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    private fun applyExifAndScale(source: Bitmap, orientation: Int, maxDim: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }

        val rotated = if (!matrix.isIdentity) {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }

        val maxSide = max(rotated.width, rotated.height)
        if (maxSide <= maxDim) return rotated

        val scale = maxDim.toFloat() / maxSide.toFloat()
        val targetW = (rotated.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (rotated.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(rotated, targetW, targetH, true)
        if (scaled !== rotated && rotated !== source) {
            rotated.recycle()
        }
        return scaled
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqMaxDim: Int): Int {
        var inSampleSize = 1
        val maxSide = max(width, height)
        if (maxSide > reqMaxDim) {
            var halfSide = maxSide / 2
            while (halfSide / inSampleSize >= reqMaxDim) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun friendlyStorageError(error: Throwable): String {
        if (error is StorageException) {
            return when (error.errorCode) {
                StorageException.ERROR_NOT_AUTHORIZED,
                StorageException.ERROR_NOT_AUTHENTICATED ->
                    "Image upload was denied. Check Firebase Storage rules and sign-in status."
                StorageException.ERROR_BUCKET_NOT_FOUND,
                StorageException.ERROR_PROJECT_NOT_FOUND ->
                    "Firebase Storage is not enabled yet in your Firebase Console."
                StorageException.ERROR_RETRY_LIMIT_EXCEEDED ->
                    "Network timed out while uploading the image. Please try again."
                else -> error.localizedMessage ?: "Unable to upload image to Firebase Storage."
            }
        }
        return error.message?.takeIf { it.isNotBlank() } ?: "Unable to upload image."
    }
}
