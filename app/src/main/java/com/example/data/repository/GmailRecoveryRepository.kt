package com.example.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Any number of Gmails per mobile-number account, used for password recovery.
 *
 * Firebase lets one account link only ONE Google sign-in, so the Gmails are kept in
 * Firestore instead:
 *  - users/{uid}/private/recovery            { emails: [...] }   (owner only)
 *  - gmailRecovery/{gmail}/accounts/{uid}    { login, secret }   (readable only by
 *    someone signed in with that Gmail and a VERIFIED email — via Google on the phone,
 *    or after clicking the link Firebase emails to that Gmail)
 * `secret` is the account's current password, encrypted, so the Gmail owner can open
 * the account and set a new password without any server.
 */
class GmailRecoveryRepository(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseProviders.auth()
) {
    private val db: FirebaseFirestore by lazy { FirebaseProviders.firestore(context) }
    private val prefs by lazy { context.getSharedPreferences("chatbox_gmail", Context.MODE_PRIVATE) }

    // ---- local list (works offline, decides whether the popup must show) ----

    fun localGmails(uid: String): List<String> =
        (prefs.getStringSet("gmails_$uid", emptySet()).orEmpty() +
            listOfNotNull(prefs.getString("gmail_$uid", null))).sorted()

    private fun rememberLocal(uid: String, gmail: String) {
        val set = prefs.getStringSet("gmails_$uid", emptySet()).orEmpty().toMutableSet()
        set += gmail
        prefs.edit().putStringSet("gmails_$uid", set).apply()
    }

    /** Gmails saved in the cloud for the signed-in account (merged into the local list). */
    suspend fun cloudGmails(): List<String> {
        val user = auth.currentUser ?: return emptyList()
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            val list = (db.document("users/${user.uid}/private/recovery").get().await().get("emails") as? List<String>).orEmpty()
            list.forEach { rememberLocal(user.uid, it) }
            list
        }.getOrDefault(emptyList())
    }

    /**
     * Adds [rawGmail] to the signed-in account (no limit, any Gmail, also one already
     * used by another number). [password] = the account's current password; when known
     * the Gmail can reset the password later.
     */
    suspend fun addGmail(rawGmail: String, password: String?): Result<String> = runCatching {
        val user = auth.currentUser ?: error("আগে Log in করুন।")
        val gmail = normalize(rawGmail) ?: error("সঠিক Gmail লিখুন, যেমন: name@gmail.com")
        rememberLocal(user.uid, gmail)
        runCatching {
            db.document("users/${user.uid}/private/recovery").set(
                mapOf("emails" to FieldValue.arrayUnion(gmail), "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            ).await()
        }.onFailure { Log.w(TAG, "save gmail list failed", it) }
        val login = user.email
        if (password != null && login != null && login.endsWith(PHONE_DOMAIN)) {
            writeSecret(gmail, user.uid, login, password)
        }
        gmail
    }

    /** After every login / password change: refresh the secret stored for each Gmail. */
    suspend fun refreshSecrets(password: String) {
        val user = auth.currentUser ?: return
        val login = user.email?.takeIf { it.endsWith(PHONE_DOMAIN) } ?: return
        val all = (cloudGmails() + localGmails(user.uid)).distinct()
        all.forEach { gmail -> runCatching { writeSecret(gmail, user.uid, login, password) } }
        if (all.isNotEmpty()) {
            runCatching {
                db.document("users/${user.uid}/private/recovery").set(
                    mapOf("emails" to FieldValue.arrayUnion(*all.toTypedArray()), "updatedAt" to FieldValue.serverTimestamp()),
                    SetOptions.merge()
                ).await()
            }
        }
    }

    private suspend fun writeSecret(gmail: String, uid: String, login: String, password: String) {
        db.document("gmailRecovery/$gmail/accounts/$uid").set(
            mapOf(
                "login" to login,
                "secret" to encrypt(login, password),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /**
     * The current user is signed in with a verified [gmail]. Finds the number account
     * [targetLogin] that added this Gmail and returns its current password, or null.
     */
    suspend fun secretFor(gmail: String, targetLogin: String): String? {
        val snap = db.collection("gmailRecovery/$gmail/accounts")
            .whereEqualTo("login", targetLogin)
            .limit(1)
            .get()
            .await()
        val doc = snap.documents.firstOrNull() ?: return null
        val secret = doc.getString("secret") ?: return null
        return runCatching { decrypt(targetLogin, secret) }.getOrNull()
    }

    // ---- "code to Gmail": Firebase emails a verification link to the Gmail ----

    /** Sends the link. Returns false when it cannot (the Gmail is a Google login already). */
    suspend fun sendEmailCode(rawGmail: String): Result<String> = runCatching {
        val gmail = normalize(rawGmail) ?: error("সঠিক Gmail লিখুন, যেমন: name@gmail.com")
        val user = signInCodeUser(gmail, create = true)
        try {
            runCatching { user.reload().await() }
            if (!user.isEmailVerified) user.sendEmailVerification().await()
        } finally {
            auth.signOut()
        }
        gmail
    }

    /**
     * After the user clicked the emailed link: signs in as the (now verified) Gmail,
     * reads the account password for [targetLogin] and deletes the temporary login.
     */
    suspend fun secretAfterEmailCode(rawGmail: String, targetLogin: String): Result<String> = runCatching {
        val gmail = normalize(rawGmail) ?: error("সঠিক Gmail লিখুন।")
        val user = signInCodeUser(gmail, create = false)
        try {
            user.reload().await()
            if (!user.isEmailVerified) error("এখনো লিংকে চাপা হয়নি। Gmail খুলে Chatbox-এর লিংকে চাপুন, তারপর আবার চেষ্টা করুন। (Spam ফোল্ডারও দেখুন)")
            user.getIdToken(true).await()
            val secret = secretFor(gmail, targetLogin)
                ?: error("এই Gmail এই নম্বরের account-এ যুক্ত নেই। Login-এর পর যে Gmail দিয়েছিলেন সেটা লিখুন।")
            runCatching { user.delete().await() }
            prefs.edit().remove(codeKey(gmail)).apply()
            secret
        } finally {
            if (auth.currentUser?.uid == user.uid) auth.signOut()
        }
    }

    private suspend fun signInCodeUser(gmail: String, create: Boolean): com.google.firebase.auth.FirebaseUser {
        val stored = prefs.getString(codeKey(gmail), null)
        if (stored != null) {
            val signedIn = runCatching { auth.signInWithEmailAndPassword(gmail, stored).await().user }.getOrNull()
            if (signedIn != null) return signedIn
        }
        if (!create) error("আগে \"Gmail-এ লিংক পাঠান\" চাপুন।")
        val pass = randomPassword()
        val created = try {
            auth.createUserWithEmailAndPassword(gmail, pass).await().user
        } catch (e: com.google.firebase.auth.FirebaseAuthUserCollisionException) {
            error("এই Gmail-এ লিংক পাঠানো যাচ্ছে না। Gmail-টা এই ফোনে থাকলে \"ফোনের Gmail বেছে নিন\" চাপুন, না হলে আপনার অন্য একটা Gmail দিন।")
        } ?: error("আবার চেষ্টা করুন।")
        prefs.edit().putString(codeKey(gmail), pass).apply()
        return created
    }

    private fun codeKey(gmail: String) = "code_pw_$gmail"

    private fun randomPassword(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    companion object {
        private const val TAG = "GmailRecovery"
        const val PHONE_DOMAIN = "@phone.chatbox.app"
        private val GMAIL = Regex("^[a-z0-9][a-z0-9._%+-]{0,63}@gmail\\.com$")

        fun normalize(raw: String): String? = raw.trim().lowercase().takeIf { GMAIL.matches(it) }

        private fun key(login: String): SecretKeySpec =
            SecretKeySpec(MessageDigest.getInstance("SHA-256").digest("chatbox-gmail-recovery-v1|$login".toByteArray()), "AES")

        fun encrypt(login: String, plain: String): String {
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(login), GCMParameterSpec(128, iv))
            return java.util.Base64.getEncoder().encodeToString(iv + cipher.doFinal(plain.toByteArray()))
        }

        fun decrypt(login: String, encoded: String): String {
            val all = java.util.Base64.getDecoder().decode(encoded)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(login), GCMParameterSpec(128, all.copyOfRange(0, 12)))
            return String(cipher.doFinal(all.copyOfRange(12, all.size)))
        }
    }
}
