/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.recovery.impl

import co.artise.android.recovery.api.CloudKeyStore
import co.artise.android.recovery.api.RecoveryKeyVault
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import java.security.MessageDigest

/** Builds without a phone backup for keys (F-Droid) have no [CloudKeyStore]: the set may be empty. */
@ContributesTo(AppScope::class)
interface CloudKeyStores {
    @Multibinds(allowEmpty = true)
    fun cloudKeyStores(): Set<CloudKeyStore>
}

@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultRecoveryKeyVault(
    matrixClient: MatrixClient,
    stores: Set<CloudKeyStore>,
) : RecoveryKeyVault {
    private val store = stores.firstOrNull()

    // The account isn't written in the backup in clear, only a short hash of it.
    private val name = "artise_recovery_" + MessageDigest.getInstance("SHA-256")
        .digest(matrixClient.sessionId.value.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(32)

    override suspend fun isAvailable(): Boolean = store?.isEndToEndEncrypted() == true

    override suspend fun save(recoveryKey: String) {
        if (!isAvailable()) return
        store?.save(name, recoveryKey.toByteArray())
    }

    override suspend fun load(): String? = store?.load(name)?.decodeToString()?.takeIf { it.isNotBlank() }

    override suspend fun delete() {
        store?.delete(name)
    }
}
