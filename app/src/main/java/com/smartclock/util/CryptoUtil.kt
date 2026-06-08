package com.smartclock.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 基于 Android Keystore 的 AES-GCM 加解密，用于本地会话凭证保护。
 *
 * 说明：策划方案要求 DB 密码以加密形式注入（DB_PWD_ENC）。CI/构建期注入的密文
 * 由独立工具生成；此处提供本地会话级（user token 等）加解密能力，并对未加密
 * 的 BuildConfig 值做兼容（解密失败时按明文返回，便于本地联调）。
 */
object CryptoUtil {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "smartclock_master_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH = 128

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    /** 加密为 Base64(iv + ciphertext) */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = iv + ct
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * 解密 Base64(iv + ciphertext)。
     * 若输入不是本机加密的密文（如本地联调直接放明文），解密失败时原样返回。
     */
    fun decrypt(encoded: String): String {
        if (encoded.isBlank()) return ""
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val ct = combined.copyOfRange(IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            encoded // 兼容明文
        }
    }
}
