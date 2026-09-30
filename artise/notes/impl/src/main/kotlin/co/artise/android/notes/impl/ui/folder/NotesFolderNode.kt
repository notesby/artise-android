/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.folder

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
class NotesFolderNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: NotesFolderPresenter.Factory,
    analyticsService: AnalyticsService,
) : Node(buildContext, plugins = plugins) {
    init {
        trackScreen(analyticsService, NotesScreen.NotesFolder)
    }

    data class Inputs(val roomId: RoomId, val folder: String) : NodeInputs

    interface Callback : Plugin {
        fun openFolder(roomId: RoomId, folder: String)
        fun openNote(roomId: RoomId, path: String)
        fun openSearch(roomId: RoomId)
        fun openEditor(roomId: RoomId, path: String)
        fun openChoices(roomId: RoomId)
        fun openMap(roomId: RoomId)
    }

    private val inputs: Inputs = inputs()
    private val callback: Callback = callback()
    private val presenter = presenterFactory.create(inputs.roomId, inputs.folder) { path -> callback.openEditor(inputs.roomId, path) }

    @Composable
    override fun View(modifier: Modifier) {
        NotesFolderView(
            state = presenter.present(),
            onBackClick = ::navigateUp,
            onFolderClick = { callback.openFolder(inputs.roomId, it) },
            onNoteClick = { callback.openNote(inputs.roomId, it) },
            onSearchClick = { callback.openSearch(inputs.roomId) },
            onReviewChoicesClick = { callback.openChoices(inputs.roomId) },
            onMapClick = { callback.openMap(inputs.roomId) },
            modifier = modifier,
        )
    }
}
