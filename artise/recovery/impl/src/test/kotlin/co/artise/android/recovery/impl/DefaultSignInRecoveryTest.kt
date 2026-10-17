/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.recovery.impl

import co.artise.android.recovery.test.FakeCloudKeyStore
import co.artise.android.recovery.test.FakeRecoveryKeyVault
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID_2
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.encryption.FakeEncryptionService
import io.element.android.libraries.matrix.test.verification.FakeSessionVerificationService
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultSignInRecoveryTest {
    private fun recovery(
        vault: FakeRecoveryKeyVault,
        encryptionService: FakeEncryptionService = FakeEncryptionService(),
        verifiedStatus: SessionVerifiedStatus = SessionVerifiedStatus.Verified,
    ) = DefaultSignInRecovery(vault, encryptionService, FakeSessionVerificationService(initialSessionVerifiedStatus = verifiedStatus))

    /** A saved key that unlocks the chats and leaves the phone verified: no identity confirmation, and it isn't tried twice. */
    @Test
    fun `the saved key restores the chats`() = runTest {
        val recovery = recovery(FakeRecoveryKeyVault(key = "EsTj 3yST y93F"))
        assertThat(recovery.restore()).isTrue()
        assertThat(recovery.restore()).isTrue()
    }

    /** No saved key: the usual identity confirmation follows. */
    @Test
    fun `no saved key`() = runTest {
        assertThat(recovery(FakeRecoveryKeyVault(key = null)).restore()).isFalse()
    }

    /** A saved key the account no longer accepts (it was changed elsewhere): the usual confirmation, and no second try. */
    @Test
    fun `a stale saved key`() = runTest {
        val encryptionService = FakeEncryptionService().apply { givenRecoverFailure(IllegalStateException("wrong key")) }
        val recovery = recovery(FakeRecoveryKeyVault(key = "old"), encryptionService)
        assertThat(recovery.restore()).isFalse()
        encryptionService.givenRecoverFailure(null)
        assertThat(recovery.restore()).isFalse()
    }

    /** First phone of an account without recovery, with an encrypted phone backup: recovery is turned on and its key kept. */
    @Test
    fun `recovery is set up on the first phone`() = runTest {
        val vault = FakeRecoveryKeyVault()
        val encryptionService = FakeEncryptionService(enableRecoveryLambda = { _, _ -> Result.success("NEW KEY") }).apply {
            emitRecoveryState(RecoveryState.DISABLED)
            givenDoesBackupExistOnServerResult(Result.success(false))
        }
        recovery(vault, encryptionService).setUpIfMissing()
        assertThat(vault.key).isEqualTo("NEW KEY")
    }

    /** An account that already has recovery: its key is never replaced. */
    @Test
    fun `existing recovery is left alone`() = runTest {
        val vault = FakeRecoveryKeyVault()
        val encryptionService = FakeEncryptionService().apply { emitRecoveryState(RecoveryState.ENABLED) }
        recovery(vault, encryptionService).setUpIfMissing()
        assertThat(vault.key).isNull()
    }

    /** No encrypted phone backup (no screen lock, or an F-Droid build): nothing is set up, so no key exists that nobody has. */
    @Test
    fun `nothing is set up without the phone backup`() = runTest {
        val vault = FakeRecoveryKeyVault(available = false)
        val encryptionService = FakeEncryptionService().apply {
            emitRecoveryState(RecoveryState.DISABLED)
            givenDoesBackupExistOnServerResult(Result.success(false))
        }
        recovery(vault, encryptionService).setUpIfMissing()
        assertThat(vault.key).isNull()
    }

    /** Each account has its own entry in the phone backup, and nothing is kept when the backup isn't end-to-end encrypted. */
    @Test
    fun `the vault keeps one key per account`() = runTest {
        val store = FakeCloudKeyStore()
        val first = DefaultRecoveryKeyVault(FakeMatrixClient(sessionId = A_SESSION_ID), setOf(store))
        val second = DefaultRecoveryKeyVault(FakeMatrixClient(sessionId = A_SESSION_ID_2), setOf(store))
        first.save("KEY ONE")
        second.save("KEY TWO")
        assertThat(first.load()).isEqualTo("KEY ONE")
        assertThat(second.load()).isEqualTo("KEY TWO")
        assertThat(store.values.keys.none { it.contains(A_SESSION_ID.value) }).isTrue()
        first.delete()
        assertThat(first.load()).isNull()

        val notEncrypted = DefaultRecoveryKeyVault(FakeMatrixClient(), setOf(FakeCloudKeyStore(endToEnd = false)))
        notEncrypted.save("KEY")
        assertThat(notEncrypted.load()).isNull()
        assertThat(DefaultRecoveryKeyVault(FakeMatrixClient(), emptySet()).isAvailable()).isFalse()
    }
}
