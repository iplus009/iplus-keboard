package org.futo.inputmethod.latin.themestore.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts paid theme manifests at rest using AES-256-GCM bound to Android Keystore
 * and (app_id, theme_id, device_hash) AAD so copied files cannot decrypt elsewhere.
 */
object ThemeVaultCrypto {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "keybird_themestore_vault_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    fun encrypt(
        plaintext: ByteArray,
        aadContext: String,
        keyOverride: SecretKey? = null
    ): ByteArray {
        val secretKey = keyOverride ?: getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        cipher.updateAAD(aadContext.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)

        return ByteBuffer.allocate(4 + iv.size + ciphertext.size)
            .putInt(iv.size)
            .put(iv)
            .put(ciphertext)
            .array()
    }

    fun decrypt(
        blob: ByteArray,
        aadContext: String,
        keyOverride: SecretKey? = null
    ): ByteArray? = runCatching {
        val buffer = ByteBuffer.wrap(blob)
        if (buffer.remaining() < 4) return@runCatching null
        val ivLen = buffer.int
        if (ivLen != IV_LENGTH_BYTES || buffer.remaining() <= ivLen) return@runCatching null
        val iv = ByteArray(ivLen)
        buffer.get(iv)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val secretKey = keyOverride ?: getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        cipher.updateAAD(aadContext.toByteArray(Charsets.UTF_8))
        cipher.doFinal(ciphertext)
    }.getOrNull()

    fun buildAad(appId: String, themeId: String, deviceHash: String): String =
        "$appId:$themeId:$deviceHash"

    private fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }
}
