/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.api

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint
import io.element.android.libraries.architecture.NodeInputs

/** The "Your workspace" step of signing in: finds the workspace's server and gets it ready for the password screen. */
interface WorkspaceSignInEntryPoint : FeatureEntryPoint {
    /**
     * [workspace] prefills the field. With an [invite], the workspace is looked up straight away and the invite's join page opens in
     * the browser (to pick a username and password); when it's closed, sign-in for that workspace goes on.
     */
    data class Params(val workspace: String? = null, val invite: WorkspaceInvite? = null) : NodeInputs

    fun createNode(parentNode: Node, buildContext: BuildContext, params: Params, callback: Callback): Node

    interface Callback : Plugin {
        /** The server is set on the authentication service and supports passwords: show the password screen for [serverName]. */
        fun onWorkspaceReady(serverName: String)
    }
}
