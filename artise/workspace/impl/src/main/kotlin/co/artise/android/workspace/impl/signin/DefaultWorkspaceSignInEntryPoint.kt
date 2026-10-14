/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.signin

import co.artise.android.workspace.api.WorkspaceSignInEntryPoint
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.architecture.createNode

@ContributesBinding(AppScope::class)
class DefaultWorkspaceSignInEntryPoint : WorkspaceSignInEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: WorkspaceSignInEntryPoint.Params,
        callback: WorkspaceSignInEntryPoint.Callback,
    ): Node = parentNode.createNode<WorkspaceSignInNode>(buildContext, listOf(params, callback))
}
