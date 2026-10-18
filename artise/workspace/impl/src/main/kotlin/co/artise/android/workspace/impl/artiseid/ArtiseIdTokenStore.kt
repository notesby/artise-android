/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.artiseid

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.cryptography.api.EncryptionDecryptionService
import io.element.android.libraries.cryptography.api.EncryptionResult
import io.element.android.libraries.cryptography.api.SecretKeyRepository
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/** Artise ID's tokens, kept only to read the person's workspaces again. */
@Serializable
data class ArtiseIdTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    /** When [accessToken] expires; null when the server didn't say. */
    val expiresAtMillis: Long? = null,
)

interface ArtiseIdTokenStore {
    suspend fun load(): ArtiseIdTokens?

    suspend fun save(tokens: ArtiseIdTokens)

    suspend fun clear()

    /** After signing out of Artise ID, the next sign-in asks again instead of reusing the browser's session. */
    suspend fun isFreshSignInNeeded(): Boolean

    suspend fun setFreshSignInNeeded(needed: Boolean)
}

/**
 * Keeps [ArtiseIdTokens] encrypted with a key in the Android Keystore (as EncryptedSharedPreferences did), in their own preferences
 * file. Anything that can't be read back (a lost key after a restore to a new phone) counts as signed out.
 */
class EncryptedArtiseIdTokenStore(
    private val context: Context,
    private val secretKeyRepository: SecretKeyRepository,
    private val encryptionDecryptionService: EncryptionDecryptionService,
    private val dispatchers: CoroutineDispatchers,
) : ArtiseIdTokenStore {
    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }

    override suspend fun load(): ArtiseIdTokens? = withContext(dispatchers.io) {
        val saved = preferences.getString(KEY_TOKENS, null) ?: return@withContext null
        runCatchingExceptions {
            val key = secretKeyRepository.getOrCreateKey(KEY_ALIAS, requiresUserAuthentication = false)
            val json = encryptionDecryptionService.decrypt(key, EncryptionResult.fromBase64(saved)).decodeToString()
            Json.decodeFromString<ArtiseIdTokens>(json)
        }.onFailure {
            Timber.w(it, "Artise ID tokens unreadable, forgetting them")
            preferences.edit { remove(KEY_TOKENS) }
        }.getOrNull()
    }

    override suspend fun save(tokens: ArtiseIdTokens) = withContext(dispatchers.io) {
        val key = secretKeyRepository.getOrCreateKey(KEY_ALIAS, requiresUserAuthentication = false)
        val encrypted = encryptionDecryptionService.encrypt(key, Json.encodeToString(tokens).encodeToByteArray())
        preferences.edit { putString(KEY_TOKENS, encrypted.toBase64()) }
    }

    override suspend fun clear() = withContext(dispatchers.io) {
        preferences.edit { remove(KEY_TOKENS) }
        secretKeyRepository.deleteKey(KEY_ALIAS)
    }

    override suspend fun isFreshSignInNeeded(): Boolean = withContext(dispatchers.io) { preferences.getBoolean(KEY_FRESH_SIGN_IN, false) }

    override suspend fun setFreshSignInNeeded(needed: Boolean) = withContext(dispatchers.io) {
        preferences.edit { putBoolean(KEY_FRESH_SIGN_IN, needed) }
    }

    private companion object {
        const val PREFERENCES = "artise_id"
        const val KEY_TOKENS = "tokens"
        const val KEY_FRESH_SIGN_IN = "fresh_sign_in"
        const val KEY_ALIAS = "artise_id_tokens"
    }
}
