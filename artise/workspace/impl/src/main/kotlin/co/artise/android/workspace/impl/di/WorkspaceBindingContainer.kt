/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.di

import android.content.Context
import co.artise.android.workspace.api.ArtiseIdCallback
import co.artise.android.workspace.api.WorkspaceLookup
import co.artise.android.workspace.impl.WorkspaceLookupClient
import co.artise.android.workspace.impl.artiseid.ArtiseIdAuth
import co.artise.android.workspace.impl.artiseid.ArtiseIdConfigClient
import co.artise.android.workspace.impl.artiseid.ArtiseIdTokenStore
import co.artise.android.workspace.impl.artiseid.EncryptedArtiseIdTokenStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.cryptography.api.EncryptionDecryptionService
import io.element.android.libraries.cryptography.api.SecretKeyRepository
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient

@BindingContainer
@ContributesTo(AppScope::class)
object WorkspaceBindingContainer {
    @Provides
    @SingleIn(AppScope::class)
    fun providesWorkspaceLookup(
        okHttpClient: OkHttpClient,
        systemClock: SystemClock,
        dispatchers: CoroutineDispatchers,
    ): WorkspaceLookup = WorkspaceLookupClient(okHttpClient, systemClock, dispatchers)

    @Provides
    @SingleIn(AppScope::class)
    fun providesArtiseIdConfigClient(
        okHttpClient: OkHttpClient,
        systemClock: SystemClock,
        dispatchers: CoroutineDispatchers,
    ): ArtiseIdConfigClient = ArtiseIdConfigClient(okHttpClient, systemClock, dispatchers)

    @Provides
    @SingleIn(AppScope::class)
    fun providesArtiseIdTokenStore(
        @ApplicationContext context: Context,
        secretKeyRepository: SecretKeyRepository,
        encryptionDecryptionService: EncryptionDecryptionService,
        dispatchers: CoroutineDispatchers,
    ): ArtiseIdTokenStore = EncryptedArtiseIdTokenStore(context, secretKeyRepository, encryptionDecryptionService, dispatchers)

    @Provides
    @SingleIn(AppScope::class)
    fun providesArtiseIdAuth(
        configClient: ArtiseIdConfigClient,
        tokenStore: ArtiseIdTokenStore,
        okHttpClient: OkHttpClient,
        systemClock: SystemClock,
        dispatchers: CoroutineDispatchers,
        @AppCoroutineScope appScope: CoroutineScope,
    ): ArtiseIdAuth = ArtiseIdAuth(configClient, tokenStore, okHttpClient, systemClock, dispatchers, appScope)

    @Provides
    fun providesArtiseIdCallback(artiseIdAuth: ArtiseIdAuth): ArtiseIdCallback = artiseIdAuth
}
