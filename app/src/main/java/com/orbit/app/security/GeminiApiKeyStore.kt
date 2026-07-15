package com.orbit.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface GeminiApiKeyStore {
    suspend fun saveKey(apiKey: String)
    suspend fun getKey(): String?
    suspend fun hasKey(): Boolean
    suspend fun deleteKey()
}

class AndroidKeystoreGeminiApiKeyStore(context: Context) : GeminiApiKeyStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    override suspend fun saveKey(apiKey: String) {
        val cleanKey = apiKey.trim()
        require(cleanKey.isNotBlank()) { "Gemini API key cannot be blank" }
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(cleanKey.toByteArray(StandardCharsets.UTF_8))
        preferences.edit()
            .putString(CiphertextKey, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(IvKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    override suspend fun getKey(): String? {
        val ciphertext = preferences.getString(CiphertextKey, null) ?: return null
        val iv = preferences.getString(IvKey, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GcmTagBits, Base64.decode(iv, Base64.NO_WRAP)),
            )
            String(
                cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)),
                StandardCharsets.UTF_8,
            )
        }.getOrElse {
            deleteKey()
            null
        }
    }

    override suspend fun hasKey(): Boolean = getKey() != null

    override suspend fun deleteKey() {
        preferences.edit()
            .remove(CiphertextKey)
            .remove(IvKey)
            .apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        (keyStore.getKey(KeyAlias, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            AndroidKeyStore,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val AndroidKeyStore = "AndroidKeyStore"
        const val KeyAlias = "luma_gemini_api_key"
        const val Transformation = "AES/GCM/NoPadding"
        const val GcmTagBits = 128
        const val PreferencesName = "luma_secure_ai"
        const val CiphertextKey = "gemini_api_key_ciphertext"
        const val IvKey = "gemini_api_key_iv"
    }
}
