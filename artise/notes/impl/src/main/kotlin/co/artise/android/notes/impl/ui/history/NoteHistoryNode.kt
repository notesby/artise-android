/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.history

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

/** A note's own history. */
@ContributesNode(SessionScope::class)
@AssistedInject
class NoteHistoryNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: NoteHistoryPresenter.Factory,
    analyticsService: AnalyticsService,
) : Node(buildContext, plugins = plugins) {
    init {
        trackScreen(analyticsService, NotesScreen.NoteHistory)
    }

    data class Inputs(val roomId: RoomId, val path: String) : NodeInputs

    interface Callback : Plugin {
        /** Show the note [path] as it was at [commit]; [savedAt] says when, for the title. */
        fun openNoteVersion(roomId: RoomId, path: String, commit: String, savedAt: String)
    }

    private val inputs: Inputs = inputs()
    private val callback: Callback = callback()
    private val presenter = presenterFactory.create(inputs.roomId, inputs.path)

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        NoteHistoryView(
            state = state,
            onBackClick = ::navigateUp,
            onVersionClick = { entry -> callback.openNoteVersion(inputs.roomId, inputs.path, entry.commit, state.whenOf(entry.commit).orEmpty()) },
            modifier = modifier,
        )
    }
}
