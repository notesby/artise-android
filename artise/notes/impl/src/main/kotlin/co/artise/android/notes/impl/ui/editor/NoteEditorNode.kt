/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.editor

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
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.services.analytics.api.AnalyticsService

@ContributesNode(SessionScope::class)
@AssistedInject
class NoteEditorNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: NoteEditorPresenter.Factory,
    analyticsService: AnalyticsService,
) : Node(buildContext, plugins = plugins) {
    init {
        trackScreen(analyticsService, NotesScreen.NoteEditor)
    }

    /** [resolveEditId] set: combine the two versions of that conflicted edit. */
    data class Inputs(val roomId: RoomId, val path: String, val resolveEditId: Long?) : NodeInputs

    private val inputs: Inputs = inputs()
    private val presenter = presenterFactory.create(inputs.roomId, inputs.path, inputs.resolveEditId, ::navigateUp)

    @Composable
    override fun View(modifier: Modifier) {
        NoteEditorView(state = presenter.present(), modifier = modifier)
    }
}
