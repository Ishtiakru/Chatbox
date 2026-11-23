package com.example.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun handleFirestoreError(
    error: Throwable,
    operationType: OperationType,
    path: String
) {
    val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
    val payload = JSONObject().apply {
        put("error", error.message ?: error.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", JSONObject().apply {
            put("userId", user?.uid ?: JSONObject.NULL)
            put("email", user?.email ?: JSONObject.NULL)
            put("emailVerified", user?.isEmailVerified ?: false)
            put("isAnonymous", user?.isAnonymous ?: false)
        })
    }
    Log.e("FirestoreError", payload.toString(), error)
}

fun friendlyFirestoreMessage(error: Throwable?): String {
    if (error == null) return "Something went wrong. Please try again."
    if (error is FirebaseFirestoreException) {
        return when (error.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Permission denied. Make sure you are signed in with a verified Google account."
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Chatbox servers are temporarily unreachable. Check your internet connection."
            FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                "An index or precondition is still preparing. Please try again in a moment."
            else -> error.localizedMessage ?: "Firestore operation failed (${error.code.name})."
        }
    }
    val msg = error.message.orEmpty()
    return when {
        msg.contains("PERMISSION_DENIED", ignoreCase = true) ||
            msg.contains("permission", ignoreCase = true) ->
            "Permission denied. Please sign in with a verified Google account."
        msg.contains("network", ignoreCase = true) ||
            msg.contains("unavailable", ignoreCase = true) ->
            "Network unavailable. Changes will sync when back online."
        else -> msg.ifBlank { "Unable to complete this request right now." }
    }
}
