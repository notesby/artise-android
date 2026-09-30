/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.media

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import kotlinx.collections.immutable.persistentListOf

class NotesMediaStatePreviewParam : PreviewParameterProvider<NotesMediaState> {
    override val values = sequenceOf(
        aNotesMediaState(),
        aNotesMediaState(filter = MediaFilter.UNUSED),
        aNotesMediaState(dialog = MediaDialog.ConfirmRemoveAndDelete(aMediaItem("luna.jpg", used = true), persistentListOf("Recetas/Mole.md"))),
    )
}

private fun aMediaItem(name: String, used: Boolean) = MediaItem(
    path = "attachments/$name",
    name = name,
    isImage = name.endsWith(".jpg"),
    size = 250_000,
    usedBy = if (used) persistentListOf("Recetas/Mole.md", "Viajes/Oaxaca.md") else persistentListOf(),
    upload = null,
    file = null,
)

private fun aNotesMediaState(filter: MediaFilter = MediaFilter.ALL, dialog: MediaDialog? = null) = NotesMediaState(
    items = persistentListOf(aMediaItem("factura.pdf", used = false), aMediaItem("luna.jpg", used = true)),
    filter = filter,
    unusedCount = 1,
    unusedBytes = 250_000,
    isLoading = false,
    deleting = null,
    dialog = dialog,
    openFile = null,
    eventSink = {},
)
