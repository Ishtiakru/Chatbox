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
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
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

/** Result of asking Firebase to send an SMS code. */
sealed interface PhoneCodeResult {
    data class CodeSent(
        val verificationId: String,
        val resendToken: PhoneAuthProvider.ForceResendingToken?
    ) : PhoneCodeResult
    /** Android auto-read the SMS (or instant verification) and already signed in. */
    data class SignedIn(val user: FirebaseUser) : PhoneCodeResult
    data class Failure(val message: String) : PhoneCodeResult
}

object PhoneNumbers {
    /** "01712345678" / "8801712345678" / "+8801712345678" → "+8801712345678"; null if invalid. */
    fun toE164(raw: String): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        val e164 = when {
            trimmed.startsWith("+") -> "+$digits"
            digits.startsWith("880") && digits.length == 13 -> "+$digits"
            digits.startsWith("01") && digits.length == 11 -> "+88$digits"
            digits.startsWith("1") && digits.length == 10 -> "+880$digits"
            else -> return null
        }
        return if (e164.length in 9..16) e164 else null
    }
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
     * Sends an SMS verification code via Firebase Phone Auth. Resolves once:
     * code sent, instant/auto verification signed in, or failure.
     */
    suspend fun sendPhoneCode(
        uiContext: Context,
        phoneE164: String,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null
    ): PhoneCodeResult {
        val activity = uiContext.findActivity()
            ?: return PhoneCodeResult.Failure("অ্যাপটি আবার খুলে চেষ্টা করুন।")
        return suspendCancellableCoroutine { cont ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    auth.signInWithCredential(credential)
                        .addOnSuccessListener { res ->
                            val user = res.user
                            if (cont.isActive) {
                                cont.resume(
                                    if (user != null) PhoneCodeResult.SignedIn(user)
                                    else PhoneCodeResult.Failure("Login সম্পূর্ণ হয়নি, আবার চেষ্টা করুন।")
                                )
                            }
                        }
                        .addOnFailureListener { e ->
                            if (cont.isActive) cont.resume(PhoneCodeResult.Failure(friendlyPhoneError(e)))
                        }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.w(TAG, "Phone verification failed", e)
                    if (cont.isActive) cont.resume(PhoneCodeResult.Failure(friendlyPhoneError(e)))
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    if (cont.isActive) cont.resume(PhoneCodeResult.CodeSent(verificationId, token))
                }
            }
            val builder = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneE164)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
            if (resendToken != null) builder.setForceResendingToken(resendToken)
            runCatching { PhoneAuthProvider.verifyPhoneNumber(builder.build()) }
                .onFailure { e ->
                    if (cont.isActive) cont.resume(PhoneCodeResult.Failure(friendlyPhoneError(e)))
                }
        }
    }

    /** Verifies the 6-digit SMS code and signs in. */
    suspend fun verifyPhoneCode(verificationId: String, code: String): AuthResult = try {
        val credential = PhoneAuthProvider.getCredential(verificationId, code.trim())
        val user = auth.signInWithCredential(credential).await().user
        if (user != null) AuthResult.Success(user) else AuthResult.Failure("Login সম্পূর্ণ হয়নি।")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AuthResult.Failure(friendlyPhoneError(e))
    }

    // ---- Mobile number / email + password (verified by Firebase) ----------

    /** True when this account can open the app: it has a password or Google sign-in. */
    fun hasPasswordOrGoogle(user: FirebaseUser?): Boolean =
        user != null && user.providerData.any { it.providerId == "password" || it.providerId == "google.com" }

    /** Signs in with a mobile number (or email) + password. Firebase checks the password. */
    suspend fun signInWithPassword(identifier: String, password: String): AuthResult = try {
        val email = loginEmailFor(identifier)
            ?: return AuthResult.Failure("সঠিক মোবাইল নম্বর লিখুন, যেমন: 01712345678")
        val user = auth.signInWithEmailAndPassword(email, password).await().user
        if (user != null) AuthResult.Success(user) else AuthResult.Failure("Login সম্পূর্ণ হয়নি।")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AuthResult.Failure(friendlyPasswordError(e))
    }

    /**
     * Attaches (or replaces) the password on the currently signed-in account for
     * [identifier]. For a mobile number the account must have just verified that
     * number by SMS code, so nobody can set a password for someone else's number.
     */
    suspend fun setPasswordForVerifiedAccount(identifier: String, password: String): Result<FirebaseUser> = runCatching {
        val user = auth.currentUser ?: error("আগে SMS কোড দিয়ে নম্বর যাচাই করুন।")
        val email = loginEmailFor(identifier) ?: error("সঠিক মোবাইল নম্বর লিখুন।")
        if (!identifier.contains("@")) {
            val e164 = PhoneNumbers.toE164(identifier)
            if (e164 == null || user.phoneNumber != e164) error("আগে SMS কোড দিয়ে এই নম্বরটি যাচাই করুন।")
        }
        val hasPassword = user.providerData.any { it.providerId == "password" }
        if (hasPassword) {
            user.updatePassword(password).await()
        } else {
            user.linkWithCredential(com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)).await()
        }
        user
    }.recoverCatching { e -> throw IllegalStateException(friendlyPasswordError(e), e) }

    /** New email account (email + password); sends a verification email. */
    suspend fun createEmailAccount(email: String, password: String): AuthResult = try {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
        if (user != null) {
            runCatching { user.sendEmailVerification().await() }
            AuthResult.Success(user)
        } else {
            AuthResult.Failure("Account তৈরি হয়নি।")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AuthResult.Failure(friendlyPasswordError(e))
    }

    /** New account = mobile number + any password (no SMS code). */
    suspend fun createPhonePasswordAccount(identifier: String, password: String): AuthResult = try {
        val email = loginEmailFor(identifier)?.takeIf { !identifier.contains("@") }
            ?: return AuthResult.Failure("সঠিক মোবাইল নম্বর লিখুন, যেমন: 01712345678")
        val user = auth.createUserWithEmailAndPassword(email, password).await().user
        if (user != null) AuthResult.Success(user) else AuthResult.Failure("Account তৈরি হয়নি। আবার চেষ্টা করুন।")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AuthResult.Failure(friendlyPasswordError(e))
    }

    fun hasGoogle(user: FirebaseUser?): Boolean =
        user != null && user.providerData.any { it.providerId == "google.com" }

    fun hasPassword(user: FirebaseUser?): Boolean =
        user != null && user.providerData.any { it.providerId == "password" }

    /**
     * Opens the system sheet listing every Gmail account on the phone and returns the
     * chosen one as a Firebase credential + its address.
     */
    private suspend fun pickGmail(activityContext: Context): Pair<com.google.firebase.auth.AuthCredential, String> {
        val uiContext = activityContext.findActivity() ?: activityContext
        val serverClientId = context.getString(R.string.default_web_client_id)
        val credentialManager = CredentialManager.create(uiContext)
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
        val result = try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .setServerClientId(serverClientId)
                .build()
            credentialManager.getCredential(uiContext, GetCredentialRequest.Builder().addCredentialOption(option).build())
        } catch (e: NoCredentialException) {
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
            credentialManager.getCredential(uiContext, GetCredentialRequest.Builder().addCredentialOption(signInOption).build())
        }
        val custom = result.credential as? CustomCredential
        if (custom == null || custom.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            error("Gmail পাওয়া যায়নি। আবার চেষ্টা করুন।")
        }
        val google = GoogleIdTokenCredential.createFrom(custom.data)
        return GoogleAuthProvider.getCredential(google.idToken, null) to google.id
    }

    private fun friendlyGmailError(e: Throwable): String = when {
        e is GetCredentialCancellationException -> "একটা Gmail বেছে নিন।"
        e is NoCredentialException ->
            "ফোনে কোনো Gmail নেই। Settings → Accounts → Add account → Google থেকে Gmail যোগ করে আবার চেষ্টা করুন।"
        e is com.google.firebase.auth.FirebaseAuthUserCollisionException ||
            (e as? com.google.firebase.auth.FirebaseAuthException)?.errorCode == "ERROR_CREDENTIAL_ALREADY_IN_USE" ->
            "এই Gmail অন্য একটি account-এ যুক্ত আছে। অন্য Gmail বেছে নিন।"
        e is com.google.firebase.FirebaseNetworkException -> "ইন্টারনেট সংযোগ নেই। নেট চালু করে আবার চেষ্টা করুন।"
        e is IllegalStateException && !e.message.isNullOrBlank() -> e.message!!
        else -> "Gmail যুক্ত করা যায়নি। আবার চেষ্টা করুন।"
    }

    /** Links the chosen Gmail to the signed-in account. Returns the Gmail address. */
    suspend fun linkGmail(activityContext: Context): Result<String> = try {
        val user = auth.currentUser ?: error("আগে Log in করুন।")
        val (credential, gmail) = pickGmail(activityContext)
        if (!hasGoogle(user)) {
            try {
                user.linkWithCredential(credential).await()
            } catch (e: com.google.firebase.auth.FirebaseAuthException) {
                if (e.errorCode != "ERROR_PROVIDER_ALREADY_LINKED") throw e
            }
        }
        runCatching { user.reload().await() }
        Result.success(gmail)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "linkGmail failed", e)
        Result.failure(IllegalStateException(friendlyGmailError(e), e))
    }

    /**
     * Forgot password without SMS: the user picks the Gmail linked to the number's
     * account; only if it really belongs to that account is the new password saved.
     */
    suspend fun resetPasswordWithGmail(activityContext: Context, identifier: String, newPassword: String): Result<FirebaseUser> = try {
        val email = loginEmailFor(identifier) ?: error("সঠিক মোবাইল নম্বর লিখুন, যেমন: 01712345678")
        val (credential, _) = pickGmail(activityContext)
        val result = auth.signInWithCredential(credential).await()
        val user = result.user ?: error("আবার চেষ্টা করুন।")
        val owns = user.providerData.any { it.providerId == "password" && it.email.equals(email, ignoreCase = true) }
        if (!owns) {
            if (result.additionalUserInfo?.isNewUser == true) runCatching { user.delete().await() }
            auth.signOut()
            error("এই Gmail এই নম্বরের account-এ যুক্ত নয়। Account খোলার পর যে Gmail দিয়েছিলেন সেটা বেছে নিন।")
        }
        user.updatePassword(newPassword).await()
        Result.success(user)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "resetPasswordWithGmail failed", e)
        val msg = if (e is com.google.firebase.auth.FirebaseAuthWeakPasswordException) "নতুন পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের দিন।" else friendlyGmailError(e)
        Result.failure(IllegalStateException(msg, e))
    }

    private fun friendlyPasswordError(e: Throwable): String {
        if (e is IllegalStateException && e.cause == null && !e.message.isNullOrBlank()) return e.message!!
        val code = (e as? com.google.firebase.auth.FirebaseAuthException)?.errorCode.orEmpty()
        val msg = (e.message ?: "").uppercase()
        return when {
            e is com.google.firebase.FirebaseTooManyRequestsException || "TOO_MANY" in msg ->
                "অনেকবার চেষ্টা হয়েছে। কিছুক্ষণ পরে আবার চেষ্টা করুন।"
            e is com.google.firebase.FirebaseNetworkException -> "ইন্টারনেট সংযোগ নেই। নেট চালু করে আবার চেষ্টা করুন।"
            code == "ERROR_WEAK_PASSWORD" || e is com.google.firebase.auth.FirebaseAuthWeakPasswordException ->
                "পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের দিন।"
            code == "ERROR_EMAIL_ALREADY_IN_USE" || code == "ERROR_CREDENTIAL_ALREADY_IN_USE" ||
                e is com.google.firebase.auth.FirebaseAuthUserCollisionException ->
                "এই নম্বর/ইমেইলে আগেই account আছে। Log in করুন, পাসওয়ার্ড ভুলে গেলে Forgot password চাপুন।"
            code == "ERROR_REQUIRES_RECENT_LOGIN" -> "নিরাপত্তার জন্য আবার SMS কোড দিয়ে যাচাই করুন।"
            code == "ERROR_OPERATION_NOT_ALLOWED" -> "পাসওয়ার্ড Login এখনো চালু হয়নি। একটু পরে আবার চেষ্টা করুন।"
            e is com.google.firebase.auth.FirebaseAuthInvalidUserException ||
                e is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ||
                "INVALID_LOGIN_CREDENTIALS" in msg ->
                "নম্বর বা পাসওয়ার্ড ভুল। নতুন হলে \"Create new account\", পাসওয়ার্ড ভুলে গেলে \"Forgot password?\" চাপুন।"
            else -> "Login করা যায়নি। আবার চেষ্টা করুন।"
        }
    }

    companion object {
        /**
         * Firebase has no "phone + password" provider, so each mobile number gets a
         * private sign-in address (never emailed). Real emails are used as-is.
         */
        fun loginEmailFor(identifier: String): String? {
            val clean = identifier.trim()
            if (clean.contains("@")) return clean.lowercase()
            val e164 = PhoneNumbers.toE164(clean) ?: return null
            return "${e164.removePrefix("+")}@phone.chatbox.app"
        }
    }

    suspend fun updateDisplayName(name: String) {
        val user = auth.currentUser ?: return
        runCatching { user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await() }
    }

    private fun friendlyPhoneError(e: Throwable): String {
        val msg = e.message.orEmpty()
        return when {
            e is FirebaseAuthInvalidCredentialsException && msg.contains("code", true) ->
                "কোডটি ভুল বা মেয়াদ শেষ। আবার দেখে লিখুন বা নতুন কোড নিন।"
            e is FirebaseAuthInvalidCredentialsException ->
                "মোবাইল নম্বরটি সঠিক নয়। যেমন: 01712345678"
            e is FirebaseTooManyRequestsException ->
                "অনেকবার চেষ্টা হয়েছে। কিছুক্ষণ পরে আবার চেষ্টা করুন।"
            msg.contains("BILLING", true) ->
                "SMS পাঠানো এখন বন্ধ আছে (Firebase billing)। Admin-কে জানান।"
            msg.contains("network", true) ->
                "ইন্টারনেট সংযোগ চেক করে আবার চেষ্টা করুন।"
            else -> "কোড পাঠানো/যাচাই করা যায়নি। আবার চেষ্টা করুন। (${msg.take(80)})"
        }
    }

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

private const val TAG = "AuthRepository"
