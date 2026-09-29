/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.folder

sealed interface NotesFolderEvent {
    data object Refresh : NotesFolderEvent

    data object DismissPrivacyNotice : NotesFolderEvent
}
