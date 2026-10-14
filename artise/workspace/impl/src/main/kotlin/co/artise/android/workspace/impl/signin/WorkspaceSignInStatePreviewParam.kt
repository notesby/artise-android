/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.signin

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

open class WorkspaceSignInStatePreviewParam : PreviewParameterProvider<WorkspaceSignInState> {
    override val values: Sequence<WorkspaceSignInState>
        get() = sequenceOf(
            aWorkspaceSignInState(),
            aWorkspaceSignInState(input = "acme", isLoading = true),
            aWorkspaceSignInState(input = "nonexistent-ws", problem = WorkspaceProblem.NotFound("nonexistent-ws")),
            aWorkspaceSignInState(input = "acme", problem = WorkspaceProblem.Unreachable),
            aWorkspaceSignInState(input = "acme", problem = WorkspaceProblem.Paused),
        )
}

internal fun aWorkspaceSignInState(
    input: String = "",
    isLoading: Boolean = false,
    problem: WorkspaceProblem? = null,
) = WorkspaceSignInState(
    input = input,
    isLoading = isLoading,
    problem = problem,
    readyServerName = null,
    eventSink = {},
)
