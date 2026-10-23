/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.note

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import co.artise.android.notes.api.NoteAttachment
import co.artise.android.notes.impl.analytics.NotesScreen
import co.artise.android.notes.impl.analytics.trackScreen
import co.artise.android.notes.impl.share.DocumentSharePresenter
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
class NoteNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: NotePresenter.Factory,
    sharePresenterFactory: DocumentSharePresenter.Factory,
    analyticsService: AnalyticsService,
) : Node(buildContext, plugins = plugins) {
    init {
        trackScreen(analyticsService, NotesScreen.Note)
    }

    /** [opensDrawn] `false`: shown as a note even if the phone thinks it's a dashboard (the server said it isn't). */
    data class Inputs(val roomId: RoomId, val path: String, val opensDrawn: Boolean = true) : NodeInputs

    interface Callback : Plugin {
        fun openNote(roomId: RoomId, path: String)
        fun openEditor(roomId: RoomId, path: String)

        /** Replace this note's screen with the renamed note. */
        fun showRenamedNote(roomId: RoomId, newPath: String)

        /** The note's versions, each readable. */
        fun openNoteHistory(roomId: RoomId, path: String)

        /** Replace this note's screen with the server's drawing of it: it's a chart or a dashboard. */
        fun showDrawn(roomId: RoomId, path: String, kind: NoteAttachment.Kind)
    }

    private val inputs: Inputs = inputs()
    private val callback: Callback = callback()
    private val navigator = object : NoteNavigator {
        override fun openNote(path: String) = callback.openNote(inputs.roomId, path)

        override fun openEditor(path: String) = callback.openEditor(inputs.roomId, path)

        override fun onRenamed(newPath: String) = callback.showRenamedNote(inputs.roomId, newPath)

        override fun onDeleted() {
            navigateUp()
        }

        override fun onDrawn(kind: NoteAttachment.Kind) {
            if (inputs.opensDrawn) callback.showDrawn(inputs.roomId, inputs.path, kind)
        }
    }
    private val presenter = presenterFactory.create(inputs.roomId, inputs.path, navigator)
    private val sharePresenter = sharePresenterFactory.create(inputs.roomId, inputs.path)

    @Composable
    override fun View(modifier: Modifier) {
        NoteView(
            state = presenter.present(),
            share = sharePresenter.present(),
            onBackClick = ::navigateUp,
            onBacklinkClick = { callback.openNote(inputs.roomId, it) },
            onEditClick = { callback.openEditor(inputs.roomId, inputs.path) },
            onHistoryClick = { callback.openNoteHistory(inputs.roomId, inputs.path) },
            modifier = modifier,
        )
    }
}
