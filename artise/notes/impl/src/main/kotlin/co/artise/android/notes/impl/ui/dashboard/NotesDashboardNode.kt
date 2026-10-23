/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import co.artise.android.notes.impl.analytics.NotesScreen
import co.artise.android.notes.impl.analytics.trackScreen
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.services.analytics.api.AnalyticsService

@ContributesNode(SessionScope::class)
@AssistedInject
class NotesDashboardNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: NotesDashboardPresenter.Factory,
    analyticsService: AnalyticsService,
) : Node(buildContext, plugins = plugins) {
    init {
        trackScreen(analyticsService, NotesScreen.NotesDashboard)
    }

    interface Callback : Plugin {
        fun openEditor(roomId: RoomId, path: String)

        /** The note turned out not to be a dashboard: its reading view instead of this screen. */
        fun showAsNote(roomId: RoomId, path: String)
    }

    data class Inputs(val roomId: RoomId, val path: String) : NodeInputs

    private val inputs: Inputs = inputs()
    private val callback: Callback = callback()
    private val presenter = presenterFactory.create(inputs.roomId, inputs.path)

    @Composable
    override fun View(modifier: Modifier) {
        NotesDashboardView(
            state = presenter.present(),
            onBackClick = ::navigateUp,
            onEditClick = { callback.openEditor(inputs.roomId, inputs.path) },
            onOpenAsNote = { callback.showAsNote(inputs.roomId, inputs.path) },
            modifier = modifier,
        )
    }
}
