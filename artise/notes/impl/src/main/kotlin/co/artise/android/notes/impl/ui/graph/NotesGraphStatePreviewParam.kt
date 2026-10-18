/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.graph

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import co.artise.android.notes.api.LocalFile

open class NotesGraphStatePreviewParam : PreviewParameterProvider<NotesGraphState> {
    override val values: Sequence<NotesGraphState>
        get() = sequenceOf(
            NotesGraphState(
                NotesGraphBuilder.build(
                    listOf(
                        note("Súper.md", "[[Mole]] [[Sopa]] [[Menú]]"),
                        note("Recetas/Mole.md", "[[Menú]]"),
                        note("Recetas/Sopa.md", ""),
                        note("Menú.md", ""),
                        note("Ideas.md", ""),
                    ),
                ),
            ),
            NotesGraphState(NotesGraphModel(emptyList(), emptyList())),
            NotesGraphState(null),
        )

    private fun note(path: String, content: String) = LocalFile(path, "v", 1, 1, isNote = true, content = content, hasLocalEdits = false)
}
