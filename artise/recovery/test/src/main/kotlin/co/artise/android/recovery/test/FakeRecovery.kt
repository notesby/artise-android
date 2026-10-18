/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.recovery.test

import co.artise.android.recovery.api.CloudKeyStore
import co.artise.android.recovery.api.RecoveryKeyVault
import co.artise.android.recovery.api.SignInRecovery

class FakeRecoveryKeyVault(
    var available: Boolean = true,
    var key: String? = null,
) : RecoveryKeyVault {
    override suspend fun isAvailable() = available

    override suspend fun save(recoveryKey: String) {
        if (available) key = recoveryKey
    }

    override suspend fun load() = key

    override suspend fun delete() {
        key = null
    }
}

class FakeCloudKeyStore(var endToEnd: Boolean = true) : CloudKeyStore {
    val values = mutableMapOf<String, ByteArray>()

    override suspend fun isEndToEndEncrypted() = endToEnd

    override suspend fun save(name: String, value: ByteArray): Boolean {
        values[name] = value
        return true
    }

    override suspend fun load(name: String) = values[name]

    override suspend fun delete(name: String) {
        values.remove(name)
    }
}

class FakeSignInRecovery(
    private val restoreResult: Boolean = false,
) : SignInRecovery {
    var restoreCount = 0
    var setUpCount = 0

    override suspend fun restore(): Boolean {
        restoreCount++
        return restoreResult
    }

    override suspend fun setUpIfMissing() {
        setUpCount++
    }
}
