/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.test

import co.artise.android.workspace.api.SignInWorkspace
import co.artise.android.workspace.api.SignInWorkspaceHolder
import co.artise.android.workspace.api.WorkspaceInvite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSignInWorkspaceHolder(
    initial: SignInWorkspace? = null,
    private var lastTyped: String? = null,
) : SignInWorkspaceHolder {
    private val chosen = MutableStateFlow(initial)

    override val current: StateFlow<SignInWorkspace?> = chosen

    override fun choose(workspace: SignInWorkspace) {
        chosen.value = workspace
        lastTyped = workspace.typed
    }

    override fun lastTyped(): String? = lastTyped

    private val invite = MutableStateFlow<WorkspaceInvite?>(null)

    override val pendingInvite: StateFlow<WorkspaceInvite?> = invite

    override fun offerInvite(invite: WorkspaceInvite) {
        this.invite.value = invite
    }

    override fun takeInvite(): WorkspaceInvite? = invite.value.also { invite.value = null }
}
