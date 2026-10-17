/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.api

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** A workspace signed in on this phone. */
@Immutable
data class OpenWorkspace(
    val sessionId: SessionId,
    val name: String,
    val logoUrl: String?,
)

/** The workspaces signed in on this phone, the one on screen, and how to switch or add one. */
@Immutable
data class WorkspaceSwitcher(
    val workspaces: ImmutableList<OpenWorkspace>,
    val current: SessionId?,
    val switchTo: (SessionId) -> Unit,
    val add: () -> Unit,
)

/** Provided by [WorkspaceBrandingRenderer] next to [LocalWorkspaceBranding]. */
@Suppress("CompositionLocalAllowlist")
val LocalWorkspaceSwitcher = staticCompositionLocalOf {
    WorkspaceSwitcher(workspaces = persistentListOf(), current = null, switchTo = {}, add = {})
}
