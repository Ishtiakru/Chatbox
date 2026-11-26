package com.example.agent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted key/value store for the admin's connector secrets
 * (GitHub token). Values are AES-GCM encrypted with a hardware-backed
 * Android Keystore key that never leaves the phone; nothing is synced.
 */
class SecureStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("saira_agent_secure", Context.MODE_PRIVATE)

    fun put(name: String, value: String?) {
        if (value.isNullOrEmpty()) {
            prefs.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val enc = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(enc, Base64.NO_WRAP)
        prefs.edit().putString(name, packed).apply()
    }

    fun get(name: String): String? = runCatching {
        val packed = prefs.getString(name, null) ?: return null
        val (ivB64, encB64) = packed.split(":", limit = 2).let { it[0] to it[1] }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(encB64, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    /** Plain (non-secret) settings live in the same file, unencrypted. */
    fun putPlain(name: String, value: String?) {
        prefs.edit().apply { if (value == null) remove("p_$name") else putString("p_$name", value) }.apply()
    }

    fun getPlain(name: String): String? = prefs.getString("p_$name", null)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val ALIAS = "chatbox_saira_agent_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
