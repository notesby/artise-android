/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.search

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
class NotesSearchNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: NotesSearchPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(val roomId: RoomId) : NodeInputs

    interface Callback : Plugin {
        fun openNote(roomId: RoomId, path: String)
    }

    private val inputs: Inputs = inputs()
    private val callback: Callback = callback()
    private val presenter = presenterFactory.create(inputs.roomId)

    @Composable
    override fun View(modifier: Modifier) {
        NotesSearchView(
            state = presenter.present(),
            onBackClick = ::navigateUp,
            onResultClick = { callback.openNote(inputs.roomId, it) },
            modifier = modifier,
        )
    }
}
