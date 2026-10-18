/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.recovery.blockstore

import android.content.Context
import co.artise.android.recovery.api.CloudKeyStore
import com.google.android.gms.auth.blockstore.Blockstore
import com.google.android.gms.auth.blockstore.DeleteBytesRequest
import com.google.android.gms.auth.blockstore.RetrieveBytesRequest
import com.google.android.gms.auth.blockstore.StoreBytesData
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Google's Block Store: a few small values per app, kept on the phone and in its Google backup, end-to-end encrypted with the phone's
 * screen lock, restored to a new phone set up from that backup and kept when the app is reinstalled.
 */
@ContributesIntoSet(AppScope::class)
class BlockStoreCloudKeyStore(
    @ApplicationContext context: Context,
) : CloudKeyStore {
    private val client = Blockstore.getClient(context)

    override suspend fun isEndToEndEncrypted(): Boolean = runCatchingExceptions { client.isEndToEndEncryptionAvailable.await() }
        .onFailure { Timber.w(it, "Block Store unavailable") }
        .getOrDefault(false)

    override suspend fun save(name: String, value: ByteArray): Boolean = runCatchingExceptions {
        client.storeBytes(StoreBytesData.Builder().setKey(name).setBytes(value).setShouldBackupToCloud(true).build()).await()
        true
    }
        .onFailure { Timber.w(it, "Couldn't keep the recovery key in Block Store") }
        .getOrDefault(false)

    override suspend fun load(name: String): ByteArray? = runCatchingExceptions {
        client.retrieveBytes(RetrieveBytesRequest.Builder().setKeys(listOf(name)).build()).await().blockstoreDataMap[name]?.bytes
    }
        .onFailure { Timber.w(it, "Couldn't read the recovery key from Block Store") }
        .getOrNull()

    override suspend fun delete(name: String) {
        runCatchingExceptions { client.deleteBytes(DeleteBytesRequest.Builder().setKeys(listOf(name)).build()).await() }
            .onFailure { Timber.w(it, "Couldn't delete the recovery key from Block Store") }
    }
}
