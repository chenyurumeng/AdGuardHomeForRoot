package io.github.chenyurumeng.aghmanager.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.chenyurumeng.aghmanager.model.AghCredential
import io.github.chenyurumeng.aghmanager.model.AghInstance
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AghCredentialStore(context: Context) {
    companion object {
        private const val PREFS = "agh_manager_credentials_v1"
        private const val KEY_ALIAS = "agh_manager_agh_credentials_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(instance: AghInstance): AghCredential? = runCatching {
        val payload = preferences.getString(instance.key + "_payload", null) ?: return null
        val iv = preferences.getString(instance.key + "_iv", null) ?: return null

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        val plain = cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP))
            .toString(StandardCharsets.UTF_8)
        val separator = plain.indexOf('\u0000')
        if (separator <= 0) return null

        AghCredential(
            username = plain.substring(0, separator),
            password = plain.substring(separator + 1)
        )
    }.getOrNull()

    fun save(instance: AghInstance, credential: AghCredential) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val plain = (credential.username + "\u0000" + credential.password)
            .toByteArray(StandardCharsets.UTF_8)
        val encrypted = cipher.doFinal(plain)

        preferences.edit()
            .putString(
                instance.key + "_payload",
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
            )
            .putString(
                instance.key + "_iv",
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            )
            .apply()
    }

    fun clear(instance: AghInstance) {
        preferences.edit()
            .remove(instance.key + "_payload")
            .remove(instance.key + "_iv")
            .apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
