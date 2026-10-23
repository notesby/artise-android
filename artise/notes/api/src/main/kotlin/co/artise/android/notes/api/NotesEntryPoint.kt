/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.api

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.matrix.api.core.RoomId

/** Opens the notes screens: every chat's notes, or straight into one chat's. */
fun interface NotesEntryPoint : FeatureEntryPoint {
    /**
     * [roomId] `null` starts at the list of chats with notes (from home); otherwise at that chat's notes.
     * [openPath] opens that file of the chat's notes straight away: the reading view for a note, the viewer for a PDF or
     * photo, the chart or dashboard screen. [openKind] is the kind the server said (a card's `kind`): a dashboard is a
     * `.md`, so only that tells it from a note.
     */
    data class Params(val roomId: RoomId?, val openPath: String? = null, val openKind: String? = null) : NodeInputs

    interface Callback : Plugin {
        /** The person left the notes screens. */
        fun onDone()
    }

    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: Params,
        callback: Callback,
    ): Node
}
