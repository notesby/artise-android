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
    /** [roomId] `null` starts at the list of chats with notes (from home); otherwise at that chat's notes. */
    data class Params(val roomId: RoomId?) : NodeInputs

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
