/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.recovery.impl

import co.artise.android.recovery.api.RecoveryKeyVault
import co.artise.android.recovery.api.SignInRecovery
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.verification.SessionVerificationService
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultSignInRecovery(
    private val vault: RecoveryKeyVault,
    private val encryptionService: EncryptionService,
    private val sessionVerificationService: SessionVerificationService,
) : SignInRecovery {
    private var restoreResult: Boolean? = null

    override suspend fun restore(): Boolean {
        // Once per session: a key that didn't work won't work a second time, and one that did needn't run again.
        restoreResult?.let { return it }
        return tryRestore().also { restoreResult = it }
    }

    private suspend fun tryRestore(): Boolean {
        val key = vault.load() ?: return false
        val recovered = encryptionService.recover(key)
            .onFailure { Timber.w(it, "The saved recovery key didn't unlock the chats") }
            .isSuccess
        // Only a phone that ends up verified counts, so identity confirmation is never skipped by mistake.
        return recovered && withTimeoutOrNull(VERIFIED_TIMEOUT) {
            sessionVerificationService.sessionVerifiedStatus.first { it == SessionVerifiedStatus.Verified }
        } != null
    }

    override suspend fun setUpIfMissing() {
        if (!vault.isAvailable()) return
        // Only an account with no recovery at all: never replace a key the person may have written down.
        val state = encryptionService.recoveryStateStateFlow.first { it != RecoveryState.UNKNOWN && it != RecoveryState.WAITING_FOR_SYNC }
        if (state != RecoveryState.DISABLED) return
        if (encryptionService.doesBackupExistOnServer().getOrNull() != false) return
        encryptionService.enableRecovery(waitForBackupsToUpload = false)
            .onSuccess { vault.save(it) }
            .onFailure { Timber.w(it, "Couldn't set up recovery after sign-in") }
    }

    private companion object {
        val VERIFIED_TIMEOUT = 15.seconds
    }
}
