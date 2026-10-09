/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.linkpreview.impl

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** A file encrypted for an encrypted chat, and what the people in it need to decrypt it. */
@Suppress("UseDataClass") // Holds bytes: a data class's equals would compare the array by reference anyway.
internal class EncryptedAttachment(
    val bytes: ByteArray,
    /** The key, in the JSON Web Key's unpadded base64url. */
    val key: String,
    /** Unpadded base64. */
    val iv: String,
    /** The encrypted bytes' SHA-256, unpadded base64. */
    val sha256: String,
)

/**
 * Matrix's attachment encryption (v2), as for photos sent in encrypted chats: AES-256-CTR with a new random key
 * for each file, and a counter starting at 0 in the IV's last 8 bytes.
 */
internal object AttachmentEncryption {
    private val random = SecureRandom()

    fun encrypt(plain: ByteArray): EncryptedAttachment {
        val key = ByteArray(32).also(random::nextBytes)
        val iv = ByteArray(16).also { random.nextBytes(it) }
        iv.fill(0, fromIndex = 8, toIndex = 16)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
        val encrypted = cipher.doFinal(plain)
        val digest = MessageDigest.getInstance("SHA-256").digest(encrypted)
        return EncryptedAttachment(
            bytes = encrypted,
            key = Base64.getUrlEncoder().withoutPadding().encodeToString(key),
            iv = Base64.getEncoder().withoutPadding().encodeToString(iv),
            sha256 = Base64.getEncoder().withoutPadding().encodeToString(digest),
        )
    }

    /** The other way, for tests: checks the hash first, like the receiving app. */
    fun decrypt(attachment: EncryptedAttachment): ByteArray {
        val digest = Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(attachment.bytes))
        check(digest == attachment.sha256) { "Hash mismatch" }
        val key = Base64.getUrlDecoder().decode(attachment.key)
        val iv = Base64.getDecoder().decode(attachment.iv)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
        return cipher.doFinal(attachment.bytes)
    }
}
