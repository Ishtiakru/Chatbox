package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "Auth"

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
    scope.launch {
        try {
            val cred = credentialManager.getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()
    scope.launch {
        try {
            val activity = context as? Activity
                ?: throw IllegalStateException("Activity context required for Google Sign-In")
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type returned by Google Sign-In.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign-in failed. Please try again.")
        }
    }
}

suspend fun reauthenticateCurrentGoogleUser(
    context: Context,
    credentialManager: CredentialManager
): Result<Unit> {
    return try {
        val currentUser = Firebase.auth.currentUser
            ?: return Result.failure(IllegalStateException("No signed-in user."))
        val clientId = context.getString(R.string.default_web_client_id)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
        val activity = context as? Activity
            ?: return Result.failure(IllegalStateException("Activity context required."))
        val cred = credentialManager.getCredential(activity, request).credential
        if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
            val parsed = GoogleIdTokenCredential.createFrom(cred.data)
            val expectedEmail = currentUser.email?.lowercase()
            if (expectedEmail != null && parsed.id.lowercase() != expectedEmail) {
                return Result.failure(IllegalStateException("Please verify with the same Google account ($expectedEmail)."))
            }
            currentUser.reauthenticate(GoogleAuthProvider.getCredential(token, null)).await()
            currentUser.getIdToken(true).await()
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Unexpected credential type."))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: GetCredentialCancellationException) {
        Log.w(TAG, "Google reauthentication cancelled: ${e.message}", e)
        Result.failure(e)
    } catch (e: Exception) {
        Log.e(TAG, "Google reauthentication failed", e)
        Result.failure(e)
    }
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onBeforeSignOut: suspend () -> Unit = {},
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                onBeforeSignOut()
            } catch (e: Exception) {
                Log.w(TAG, "Pre-signout cleanup failed: ${e.message}")
            }
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

suspend fun sendPasswordResetToGmail(
    rawEmail: String,
    isBengali: Boolean = false
): Result<Unit> {
    val email = rawEmail.trim().lowercase()
    if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
        return Result.failure(
            IllegalArgumentException(
                if (isBengali) "সঠিক ইমেল / Gmail ঠিকানা দিন।" else "Please enter a valid email address."
            )
        )
    }
    return try {
        Firebase.auth.sendPasswordResetEmail(email).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: com.google.firebase.FirebaseNetworkException) {
        Log.w(TAG, "Password reset network error", e)
        Result.failure(
            IllegalStateException(
                if (isBengali) {
                    "ইন্টারনেট সংযোগ পাওয়া যায়নি। নেটওয়ার্ক চেক করে আবার চেষ্টা করুন।"
                } else {
                    "No internet connection. Please check your network and try again."
                },
                e
            )
        )
    } catch (e: FirebaseAuthException) {
        Log.w(TAG, "Password reset failed (${e.errorCode}): ${e.message}", e)
        val friendly = when (e.errorCode) {
            "ERROR_OPERATION_NOT_ALLOWED" -> if (isBengali) {
                "Firebase Console → Authentication → Sign-in method থেকে Email/Password চালু করুন, অথবা নিচে Google দিয়ে সাইন ইন করুন।"
            } else {
                "Email/Password recovery is not enabled in Firebase Console yet. Enable Authentication → Sign-in method → Email/Password, or continue with Google Sign-In."
            }
            "ERROR_USER_NOT_FOUND" -> if (isBengali) {
                "$email ঠিকানায় কোনো পাসওয়ার্ড অ্যাকাউন্ট পাওয়া যায়নি। আপনি Google Sign-In ব্যবহার করলে আলাদা পাসওয়ার্ড লাগবে না।"
            } else {
                "No password-based account was found for $email. If you use Google Sign-In, you don't need a separate password."
            }
            "ERROR_INVALID_EMAIL" -> if (isBengali) {
                "সঠিক ইমেল / Gmail ঠিকানা দিন।"
            } else {
                "Please enter a valid email address."
            }
            "ERROR_TOO_MANY_REQUESTS" -> if (isBengali) {
                "অনেকবার চেষ্টা করা হয়েছে। কিছুক্ষণ পর আবার চেষ্টা করুন।"
            } else {
                "Too many reset requests. Please wait a bit and try again."
            }
            else -> e.localizedMessage ?: if (isBengali) {
                "পাসওয়ার্ড রিসেট ইমেল পাঠানো যায়নি।"
            } else {
                "Could not send password reset email."
            }
        }
        Result.failure(IllegalStateException(friendly, e))
    } catch (e: Exception) {
        Log.e(TAG, "Password reset error", e)
        Result.failure(e)
    }
}

fun openGmailInbox(context: Context): Boolean {
    val gmailLaunch = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
    if (gmailLaunch != null) {
        return runCatching {
            gmailLaunch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(gmailLaunch)
            true
        }.getOrDefault(false)
    }
    val emailAppIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
        addCategory(android.content.Intent.CATEGORY_APP_EMAIL)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (runCatching { context.startActivity(emailAppIntent); true }.getOrDefault(false)) {
        return true
    }
    val webIntent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("https://mail.google.com")
    ).apply {
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { context.startActivity(webIntent); true }.getOrDefault(false)
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun normalizePhoneNumber(raw: String): String {
    val cleaned = raw.trim().replace(Regex("[\\s\\-()]"), "")
    return when {
        cleaned.startsWith("+") -> cleaned
        cleaned.startsWith("01") && cleaned.length == 11 -> "+88$cleaned"
        cleaned.startsWith("880") && cleaned.length >= 13 -> "+$cleaned"
        else -> cleaned
    }
}

fun validatePhoneNumber(raw: String, isBengali: Boolean = false): Result<String> {
    val normalized = normalizePhoneNumber(raw)
    return if (normalized.matches(Regex("^\\+[1-9]\\d{7,14}$"))) {
        Result.success(normalized)
    } else {
        Result.failure(
            IllegalArgumentException(
                if (isBengali) {
                    "সঠিক মোবাইল নম্বর দিন (যেমন: 01XXXXXXXXX অথবা +8801XXXXXXXXX)।"
                } else {
                    "Enter a valid mobile number with country code (e.g., 01XXXXXXXXX or +8801XXXXXXXXX)."
                }
            )
        )
    }
}

fun validatePasswordStrength(password: String, isBengali: Boolean = false): String? {
    if (password.isBlank()) {
        return if (isBengali) "পাসওয়ার্ড দিন।" else "Password is required."
    }
    if (password.length < 6) {
        return if (isBengali) {
            "পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।"
        } else {
            "Password must be at least 6 characters."
        }
    }
    return null
}

fun startPhoneNumberVerification(
    context: Context,
    rawPhone: String,
    scope: CoroutineScope,
    resendToken: PhoneAuthProvider.ForceResendingToken? = null,
    onCodeSent: (verificationId: String, token: PhoneAuthProvider.ForceResendingToken?) -> Unit,
    onAutoVerified: () -> Unit,
    onError: (String) -> Unit
) {
    val activity = context.findActivity()
    if (activity == null) {
        onError("Activity context required for phone verification.")
        return
    }
    val phoneResult = validatePhoneNumber(rawPhone)
    val e164Phone = phoneResult.getOrElse { err ->
        onError(err.message ?: "Invalid phone number.")
        return
    }

    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            scope.launch {
                try {
                    Firebase.auth.signInWithCredential(credential).await()
                    onAutoVerified()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Auto phone credential sign-in failed", e)
                    onError(e.localizedMessage ?: "Phone verification failed.")
                }
            }
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.w(TAG, "Phone verification failed: ${e.message}", e)
            val code = (e as? FirebaseAuthException)?.errorCode
            val friendly = when (code) {
                "ERROR_OPERATION_NOT_ALLOWED" ->
                    "Phone sign-in is not enabled in Firebase Console yet. Enable Authentication → Sign-in method → Phone, or sign in with Google below."
                "ERROR_INVALID_PHONE_NUMBER" ->
                    "Invalid phone number format. Include country code (e.g., +8801XXXXXXXXX)."
                "ERROR_TOO_MANY_REQUESTS", "ERROR_QUOTA_EXCEEDED" ->
                    "Too many verification attempts. Please try again later or use Google Sign-In."
                "ERROR_USER_DISABLED" ->
                    "This account has been disabled."
                else -> e.localizedMessage ?: "Unable to verify phone number. You can also sign in with Google below."
            }
            onError(friendly)
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            onCodeSent(verificationId, token)
        }
    }

    val builder = PhoneAuthOptions.newBuilder(Firebase.auth)
        .setPhoneNumber(e164Phone)
        .setTimeout(60L, TimeUnit.SECONDS)
        .setActivity(activity)
        .setCallbacks(callbacks)
    if (resendToken != null) {
        builder.setForceResendingToken(resendToken)
    }
    PhoneAuthProvider.verifyPhoneNumber(builder.build())
}

suspend fun verifyPhoneOtpCode(verificationId: String, code: String): Result<Unit> {
    val trimmed = code.trim()
    if (trimmed.length < 4) {
        return Result.failure(IllegalArgumentException("Please enter the 6-digit verification code sent to your phone."))
    }
    return try {
        val credential = PhoneAuthProvider.getCredential(verificationId, trimmed)
        Firebase.auth.signInWithCredential(credential).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseAuthException) {
        Log.w(TAG, "OTP verification failed (${e.errorCode}): ${e.message}", e)
        val friendly = when (e.errorCode) {
            "ERROR_INVALID_VERIFICATION_CODE" -> "Invalid verification code. Check the SMS and try again."
            "ERROR_SESSION_EXPIRED" -> "Verification code expired. Tap Resend code to get a new one."
            "ERROR_USER_DISABLED" -> "This account has been disabled."
            else -> e.localizedMessage ?: "Verification failed. Please try again."
        }
        Result.failure(IllegalStateException(friendly, e))
    } catch (e: Exception) {
        Log.w(TAG, "OTP verification failed", e)
        Result.failure(e)
    }
}
