/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.search

import androidx.compose.foundation.text.input.TextFieldState
import kotlinx.collections.immutable.ImmutableList

data class NotesSearchState(
    val query: TextFieldState,
    val results: ImmutableList<NotesSearchHit>,
)
