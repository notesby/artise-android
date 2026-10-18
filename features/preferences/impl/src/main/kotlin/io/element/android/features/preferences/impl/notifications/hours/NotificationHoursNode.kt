/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.notifications.hours

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import co.artise.android.quiet.api.NotificationHoursState
import co.artise.android.quiet.api.NotificationHoursView
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.di.SessionScope

/** Artise: Ajustes → Notificaciones → "Horario de notificaciones" (artise/quiet). */
@ContributesNode(SessionScope::class)
@AssistedInject
class NotificationHoursNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: Presenter<NotificationHoursState>,
) : Node(buildContext, plugins = plugins) {
    @Composable
    override fun View(modifier: Modifier) {
        NotificationHoursView(state = presenter.present(), onBackClick = ::navigateUp, modifier = modifier)
    }
}
