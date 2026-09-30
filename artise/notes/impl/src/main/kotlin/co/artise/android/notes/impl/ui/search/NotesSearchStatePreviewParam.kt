/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import kotlinx.collections.immutable.persistentListOf

open class NotesSearchStatePreviewParam : PreviewParameterProvider<NotesSearchState> {
    override val values: Sequence<NotesSearchState>
        get() = sequenceOf(
            NotesSearchState(
                query = TextFieldState("chocolate"),
                results = persistentListOf(
                    NotesSearchHit("Recetas/Mole.md", listOf("- chocolate amargo")),
                    NotesSearchHit("Súper.md", listOf("- chocolate en polvo")),
                ),
            ),
            NotesSearchState(query = TextFieldState("cebolla"), results = persistentListOf()),
        )
}
