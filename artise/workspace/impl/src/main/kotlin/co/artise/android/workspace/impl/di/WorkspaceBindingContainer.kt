/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.di

import co.artise.android.workspace.api.WorkspaceLookup
import co.artise.android.workspace.impl.WorkspaceLookupClient
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.services.toolbox.api.systemclock.SystemClock
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
}
