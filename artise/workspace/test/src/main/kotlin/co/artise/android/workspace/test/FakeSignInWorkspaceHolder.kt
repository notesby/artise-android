/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.test

import co.artise.android.workspace.api.SignInWorkspace
import co.artise.android.workspace.api.SignInWorkspaceHolder
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
}
