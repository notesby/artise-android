/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.folder

import co.artise.android.notes.api.LocalFile

/**
 * What one folder shows: its subfolders (with their note counts) then its notes, each sorted by name.
 * Only notes are listed for now; photos and documents open from the chat.
 */
internal object NotesFolderEntries {
    fun of(files: List<LocalFile>, folder: String): List<NotesFolderEntry> {
        val prefix = if (folder.isEmpty()) "" else "$folder/"
        val inFolder = files.filter { it.isNote && it.path.startsWith(prefix) }
        val folders = inFolder
            .map { it.path.removePrefix(prefix) }
            .filter { it.contains('/') }
            .groupingBy { it.substringBefore('/') }
            .eachCount()
            .map { (name, count) -> NotesFolderEntry.Folder(name, prefix + name, count) }
            .sortedBy { it.name.lowercase() }
        val notes = inFolder
            .filter { !it.path.removePrefix(prefix).contains('/') }
            .map { NotesFolderEntry.Note(noteName(it.path), it.path, it.hasLocalEdits) }
            .sortedBy { it.name.lowercase() }
        return folders + notes
    }

    /** "Recetas/Mole.md" → "Mole"; other text files keep their extension so "lista.csv" stays recognisable. */
    fun noteName(path: String): String = path.substringAfterLast('/').removeSuffix(".md")
}
