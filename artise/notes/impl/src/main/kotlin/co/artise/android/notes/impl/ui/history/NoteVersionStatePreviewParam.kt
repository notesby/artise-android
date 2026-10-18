/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.history

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

open class NoteVersionStatePreviewParam : PreviewParameterProvider<NoteVersionState> {
    override val values: Sequence<NoteVersionState>
        get() = sequenceOf(
            aNoteVersionState(content = "# Súper\n- [x] leche\n- [ ] pan\n- huevos"),
            aNoteVersionState(isLoading = true),
            aNoteVersionState(failed = true),
        )
}

internal fun aNoteVersionState(content: String? = null, isLoading: Boolean = false, failed: Boolean = false) = NoteVersionState(
    title = "Súper",
    savedAt = "Today, 18:02",
    content = content,
    isLoading = isLoading,
    failed = failed,
    eventSink = {},
)
