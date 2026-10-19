package com.ayeshiai.assistant.data

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
 * Stores the Gemini API key encrypted with an AES-256-GCM key that lives in the
 * Android Keystore. The key is never logged and never written in plain text.
 */
class SecureKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("ayeshi_secure", Context.MODE_PRIVATE)

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    /** Throws on Keystore failure so the caller can show a real error. */
    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(PREF).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val enc = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(PREF, Base64.encodeToString(iv + enc, Base64.NO_WRAP)).apply()
    }

    fun loadApiKey(): String {
        val s = prefs.getString(PREF, null) ?: return ""
        return try {
            val all = Base64.decode(s, Base64.NO_WRAP)
            val iv = all.copyOfRange(0, 12)
            val data = all.copyOfRange(12, all.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    fun hasKey(): Boolean = loadApiKey().isNotBlank()

    private companion object {
        const val ALIAS = "ayeshi_api_key_v1"
        const val PREF = "gemini_key_enc"
    }
}
