/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.note

sealed interface NoteEvent {
    /** A `[[link]]` was tapped: open that note, or explain it doesn't exist yet. */
    data class OpenNoteLink(val target: String) : NoteEvent

    data object DismissMissingNote : NoteEvent
}
