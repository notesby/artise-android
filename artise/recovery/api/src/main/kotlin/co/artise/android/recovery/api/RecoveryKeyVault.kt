/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.recovery.api

/**
 * The signed-in account's recovery key, kept in the phone's end-to-end encrypted backup ([CloudKeyStore]): Google's Block Store in Play
 * builds. Neither Google nor Artise can read it. A new phone restored from that backup, or the app reinstalled on the same phone, finds
 * the key and opens the encrypted chats without asking for it. One per account (session scope).
 */
interface RecoveryKeyVault {
    /** True when the phone can keep the key end-to-end encrypted in its backup. False in builds without one (F-Droid). */
    suspend fun isAvailable(): Boolean

    /** Keeps [recoveryKey], replacing any earlier one for this account. */
    suspend fun save(recoveryKey: String)

    suspend fun load(): String?

    suspend fun delete()
}

/**
 * Where [RecoveryKeyVault] keeps keys: a small end-to-end encrypted store that the phone backs up and restores to a new phone. Contributed
 * into a set by Play builds only; with none, there's no vault.
 */
interface CloudKeyStore {
    /** True when what's saved is backed up end-to-end encrypted (the phone has a screen lock and a Google account). */
    suspend fun isEndToEndEncrypted(): Boolean

    suspend fun save(name: String, value: ByteArray): Boolean

    suspend fun load(name: String): ByteArray?

    suspend fun delete(name: String)
}

/**
 * Right after sign-in: unlocks the chats with the saved key when this phone isn't verified yet, and on an account's first phone sets up
 * recovery with a key the person never has to see, kept in the vault.
 */
interface SignInRecovery {
    /** Tries the saved key. True when it unlocked the chats, so identity confirmation isn't needed. */
    suspend fun restore(): Boolean

    /** When the account has no recovery yet and the vault is available: turns recovery on and keeps the new key in the vault. */
    suspend fun setUpIfMissing()
}
