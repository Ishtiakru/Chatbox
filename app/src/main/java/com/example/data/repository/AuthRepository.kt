package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult
    data object Cancelled : AuthResult
    data class Failure(val message: String) : AuthResult
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

class AuthRepository(private val context: Context) {
    private val auth: FirebaseAuth = FirebaseProviders.auth()

    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun currentUser(): FirebaseUser? = auth.currentUser

    /**
     * Keeps a silent sign-in limited to the single authorized account that Credential Manager
     * is allowed to auto-select. On any failure it still returns [AuthResult.Cancelled] (instead
     * of a hard error) so the login screen can offer the user-facing Google account picker.
     */
    suspend fun trySilentGoogleSignIn(activityContext: Context): AuthResult {
        val uiContext = activityContext.findActivity() ?: activityContext
        return try {
            val serverClientId = context.getString(R.string.default_web_client_id)
            val credentialManager = CredentialManager.create(uiContext)
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val result = credentialManager.getCredential(uiContext, request)
            signInWithCredential(result.credential)
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoCredentialException) {
            AuthResult.Cancelled
        } catch (_: GetCredentialCancellationException) {
            AuthResult.Cancelled
        } catch (e: Exception) {
            Log.d("AuthRepository", "Silent sign-in skipped: ${e.message}")
            AuthResult.Cancelled
        }
    }

    suspend fun signInWithGoogleInteractive(activityContext: Context): AuthResult {
        val uiContext = activityContext.findActivity() ?: activityContext
        return try {
            val serverClientId = context.getString(R.string.default_web_client_id)
            val credentialManager = CredentialManager.create(uiContext)
            // Clear any remembered Credential Manager selection so tapping Google always shows
            // the Gmail account picker instead of silently reconnecting the previous account.
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d("AuthRepository", "Clear credential state skipped: ${e.message}")
            }

            val result = try {
                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()
                credentialManager.getCredential(uiContext, request)
            } catch (e: NoCredentialException) {
                Log.w("AuthRepository", "GetSignInWithGoogleOption returned NoCredentialException; trying unfiltered picker.", e)
                val fallbackPicker = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .setServerClientId(serverClientId)
                    .build()
                val fallbackReq = GetCredentialRequest.Builder()
                    .addCredentialOption(fallbackPicker)
                    .build()
                credentialManager.getCredential(uiContext, fallbackReq)
            } catch (e: GetCredentialUnsupportedException) {
                Log.w("AuthRepository", "GetSignInWithGoogleOption unsupported; trying GetGoogleIdOption picker.", e)
                val fallbackPicker = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .setServerClientId(serverClientId)
                    .build()
                val fallbackReq = GetCredentialRequest.Builder()
                    .addCredentialOption(fallbackPicker)
                    .build()
                credentialManager.getCredential(uiContext, fallbackReq)
            }
            signInWithCredential(result.credential)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w("AuthRepository", "Google Sign-In cancelled or dismissed: ${e.message}", e)
            val msg = e.message.orEmpty().lowercase()
            if (msg.contains("developer") || msg.contains("28444") || msg.contains("10:") || msg.contains("sha")) {
                AuthResult.Failure(
                    "Google Sign-In configuration mismatch (SHA-1/Web Client ID). Verify google-services.json matches the APK signing key."
                )
            } else {
                AuthResult.Cancelled
            }
        } catch (e: NoCredentialException) {
            Log.w("AuthRepository", "No Google credentials found on device", e)
            AuthResult.Failure("No Google account found on this device. Add a Google/Gmail account in device settings first.")
        } catch (e: GetCredentialException) {
            Log.e("AuthRepository", "Google sign-in credential error", e)
            AuthResult.Failure(e.errorMessage?.toString() ?: e.localizedMessage ?: "Google sign-in failed. Please try again.")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Google sign-in failed", e)
            AuthResult.Failure(e.localizedMessage ?: "Unable to complete Google Sign-In.")
        }
    }

    /**
     * Shows the system Google account picker listing every Google account on the device,
     * so the user can pick a different Gmail than the previously selected one.
     * When [clearPrevious] is true the last remembered credential selection is cleared first.
     */
    suspend fun signInWithGoogleAccountPicker(activityContext: Context, clearPrevious: Boolean): AuthResult {
        val uiContext = activityContext.findActivity() ?: activityContext
        return try {
            val serverClientId = context.getString(R.string.default_web_client_id)
            val credentialManager = CredentialManager.create(uiContext)
            if (clearPrevious) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.d("AuthRepository", "Clear credential state skipped: ${e.message}")
                }
            }
            val result = try {
                val option = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .setServerClientId(serverClientId)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(option)
                    .build()
                credentialManager.getCredential(uiContext, request)
            } catch (e: NoCredentialException) {
                Log.w("AuthRepository", "GetGoogleIdOption returned NoCredentialException; trying GetSignInWithGoogleOption.", e)
                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
                val fallbackReq = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()
                credentialManager.getCredential(uiContext, fallbackReq)
            }
            signInWithCredential(result.credential)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w("AuthRepository", "Google account picker cancelled or dismissed: ${e.message}", e)
            val msg = e.message.orEmpty().lowercase()
            if (msg.contains("developer") || msg.contains("28444") || msg.contains("10:") || msg.contains("sha")) {
                AuthResult.Failure(
                    "Google Sign-In configuration mismatch (SHA-1/Web Client ID). Verify google-services.json matches the APK signing key."
                )
            } else {
                AuthResult.Cancelled
            }
        } catch (e: NoCredentialException) {
            Log.w("AuthRepository", "No Google credentials found on device", e)
            AuthResult.Failure("No Google account found on this device. Add a Google/Gmail account in device settings first.")
        } catch (e: GetCredentialException) {
            Log.e("AuthRepository", "Google sign-in credential error", e)
            AuthResult.Failure(e.errorMessage?.toString() ?: e.localizedMessage ?: "Google sign-in failed. Please try again.")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Google sign-in failed", e)
            AuthResult.Failure(e.localizedMessage ?: "Unable to complete Google Sign-In.")
        }
    }

    suspend fun reauthenticateWithGoogle(activityContext: Context): Result<Unit> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Sign in with Google first."))
        return try {
            val serverClientId = context.getString(R.string.default_web_client_id)
            val credentialManager = CredentialManager.create(activityContext)
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            val result = credentialManager.getCredential(activityContext, request)
            val custom = result.credential as? CustomCredential
                ?: return Result.failure(IllegalStateException("Unexpected credential type."))
            if (custom.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return Result.failure(IllegalStateException("Expected a Google ID token credential."))
            }
            val googleCred = GoogleIdTokenCredential.createFrom(custom.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
            currentUser.reauthenticate(firebaseCred).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Google verification cancelled.", e))
        } catch (e: Exception) {
            Log.e("AuthRepository", "Re-authentication failed", e)
            Result.failure(e)
        }
    }

    suspend fun ensureRecentGoogleAuth(
        activityContext: Context,
        maxAgeSeconds: Long = 240L
    ): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Sign in with Google first."))
        val authTimestampSec = runCatching {
            user.getIdToken(false).await().authTimestamp
        }.getOrDefault(0L)
        val nowSec = System.currentTimeMillis() / 1000L
        if (authTimestampSec > 0L && (nowSec - authTimestampSec) in -60L..maxAgeSeconds) {
            return Result.success(Unit)
        }
        return reauthenticateWithGoogle(activityContext)
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        withContext(NonCancellable) {
            runCatching {
                CredentialManager.create(context)
                    .clearCredentialState(ClearCredentialStateRequest())
            }
            auth.signOut()
        }
    }

    private suspend fun signInWithCredential(credential: androidx.credentials.Credential): AuthResult {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return AuthResult.Failure("Selected credential is not a Google account.")
        }
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCred = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
        val authResult = auth.signInWithCredential(firebaseCred).await()
        val user = authResult.user
            ?: return AuthResult.Failure("Chatbox authentication returned no user.")
        return AuthResult.Success(user)
    }
}
