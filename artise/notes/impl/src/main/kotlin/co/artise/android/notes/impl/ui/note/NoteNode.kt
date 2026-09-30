/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.note

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

@ContributesNode(SessionScope::class)
@AssistedInject
class NoteNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: NotePresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(val roomId: RoomId, val path: String) : NodeInputs

    interface Callback : Plugin {
        fun openNote(roomId: RoomId, path: String)
        fun openEditor(roomId: RoomId, path: String)

        /** Replace this note's screen with the renamed note. */
        fun showRenamedNote(roomId: RoomId, newPath: String)
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
    }
    private val presenter = presenterFactory.create(inputs.roomId, inputs.path, navigator)

    @Composable
    override fun View(modifier: Modifier) {
        NoteView(
            state = presenter.present(),
            onBackClick = ::navigateUp,
            onBacklinkClick = { callback.openNote(inputs.roomId, it) },
            onEditClick = { callback.openEditor(inputs.roomId, inputs.path) },
            modifier = modifier,
        )
    }
}
