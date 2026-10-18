/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.enterprise.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.element.android.services.analyticsproviders.api.AnalyticsProvider

/**
 * Artise builds without Firebase (the F-Droid flavor) have no analytics provider at all: the set may be empty.
 */
@ContributesTo(AppScope::class)
interface ArtiseAnalyticsProviders {
    @Multibinds(allowEmpty = true)
    fun analyticsProviders(): Set<AnalyticsProvider>
}
