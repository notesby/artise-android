/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.api

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Gives [content] the workspace's accent as the primary colour (filled buttons, highlights). For Artise screens only. */
@Composable
fun WorkspaceAccent(content: @Composable () -> Unit) {
    val accent = LocalWorkspaceBranding.current.accent
    if (accent == null) {
        content()
    } else {
        // The server only accepts accents with at least 3:1 contrast against white text.
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(primary = accent, onPrimary = Color.White),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}
