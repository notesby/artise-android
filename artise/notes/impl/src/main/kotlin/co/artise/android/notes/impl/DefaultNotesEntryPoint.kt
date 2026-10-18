/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl

import co.artise.android.notes.api.NotesEntryPoint
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.di.SessionScope

@ContributesBinding(SessionScope::class)
class DefaultNotesEntryPoint : NotesEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: NotesEntryPoint.Params,
        callback: NotesEntryPoint.Callback,
    ): Node = parentNode.createNode<NotesFlowNode>(buildContext, listOf(params, callback))
}
