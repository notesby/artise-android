/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.api

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.StateFlow

/** The workspace being signed in to, chosen on the workspace step and shown on the password screen. */
@Immutable
data class SignInWorkspace(
    /** What was typed to find it ("acme"), or the server name when it was typed instead. */
    val typed: String,
    val name: String,
    val serverName: String,
    val logoUrl: String?,
)

/** Remembers the workspace chosen while signing in, and the last one used to prefill the field next time. */
interface SignInWorkspaceHolder {
    val current: StateFlow<SignInWorkspace?>

    fun choose(workspace: SignInWorkspace)

    fun lastTyped(): String?
}
