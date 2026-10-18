/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.quiet.impl.di

import co.artise.android.quiet.api.QuietHoursState
import co.artise.android.quiet.impl.QuietHoursPresenter
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.di.SessionScope

@ContributesTo(SessionScope::class)
@BindingContainer
interface QuietHoursBindings {
    @Binds
    fun bindQuietHoursPresenter(presenter: QuietHoursPresenter): Presenter<QuietHoursState>
}
