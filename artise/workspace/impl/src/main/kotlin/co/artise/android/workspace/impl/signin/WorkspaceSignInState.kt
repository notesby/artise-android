/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.signin

import androidx.compose.runtime.Immutable

data class WorkspaceSignInState(
    val input: String,
    val isLoading: Boolean,
    val problem: WorkspaceProblem?,
    /** Set once the server is ready; the node then moves on to the password screen. */
    val readyServerName: String?,
    /** An invite's join page to open in the browser (once), where the person picks a username and password. */
    val joinPageUrl: String?,
    val eventSink: (WorkspaceSignInEvent) -> Unit,
)

/** Why the workspace can't be used yet, said in plain words under the field. Each one can be retried. */
@Immutable
sealed interface WorkspaceProblem {
    data object Invalid : WorkspaceProblem

    data class NotFound(val typed: String) : WorkspaceProblem

    data object Paused : WorkspaceProblem

    data object TooManyTries : WorkspaceProblem

    /** The lookup couldn't be reached (no network?). */
    data object Unreachable : WorkspaceProblem

    /** The workspace was found but its server didn't answer. */
    data class ServerUnreachable(val name: String) : WorkspaceProblem

    /** The server doesn't take a username and password. */
    data class NoPasswordSignIn(val name: String) : WorkspaceProblem

    /** The workspace doesn't take invites from the app yet (its server has no join page). */
    data object InviteUnavailable : WorkspaceProblem
}

sealed interface WorkspaceSignInEvent {
    data class InputChanged(val input: String) : WorkspaceSignInEvent

    data object Continue : WorkspaceSignInEvent

    /** The node moved on to the password screen. */
    data object ReadyHandled : WorkspaceSignInEvent

    data object JoinPageOpened : WorkspaceSignInEvent

    /** Back from the browser (or any return to the screen): after a join page, sign-in for its workspace goes on. */
    data object Resumed : WorkspaceSignInEvent
}
