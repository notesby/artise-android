/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.enterprise.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.element.android.libraries.di.identifiers.SentrySdkDsn

/**
 * Artise builds use Element's analytics service with Firebase only (see `ModulesConfig`), so neither Element's
 * Sentry module nor its no-op module is there to answer this: the matrix SDK gets no Sentry address.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object ArtiseAnalyticsBindingContainer {
    @Provides
    fun providesSentrySdkDsn(): SentrySdkDsn? = null
}
