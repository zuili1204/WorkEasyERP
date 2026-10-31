package com.workeasy.erp.tenant

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 租户数据库口令的最小加密方案（AES-256-GCM，密钥由配置项派生）。
 * 生产建议改用 KMS / Vault，并把 `db_password_ref` 指向密钥系统，本类只作为无外部依赖时的兜底。
 */
object SecretCodec {
    private const val PREFIX = "enc:"
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    fun encrypt(plain: String, secret: String): String {
        val iv = ByteArray(IV_LEN).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyOf(secret), GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(iv + ct)
    }

    fun decrypt(value: String, secret: String): String {
        val raw = Base64.getDecoder().decode(value.removePrefix(PREFIX))
        val iv = raw.copyOfRange(0, IV_LEN)
        val ct = raw.copyOfRange(IV_LEN, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keyOf(secret), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun keyOf(secret: String): SecretKeySpec =
        SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8)), "AES")
}
