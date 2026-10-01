/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.folder

import co.artise.android.notes.api.LocalFile
import co.artise.android.notes.impl.ui.note.NoteEmbeds

/**
 * What one folder shows: its subfolders (with how many notes and files they hold) then its notes and files, each
 * sorted by name.
 */
internal object NotesFolderEntries {
    fun of(files: List<LocalFile>, folder: String): List<NotesFolderEntry> {
        val prefix = if (folder.isEmpty()) "" else "$folder/"
        val inFolder = files.filter { it.path.startsWith(prefix) }
        val folders = inFolder
            .filter { it.path.removePrefix(prefix).contains('/') }
            .groupBy { it.path.removePrefix(prefix).substringBefore('/') }
            .map { (name, inside) -> NotesFolderEntry.Folder(name, prefix + name, inside.count { it.isNote }, inside.count { !it.isNote }) }
            .sortedBy { it.name.lowercase() }
        val here = inFolder.filter { !it.path.removePrefix(prefix).contains('/') }
        val notes = here
            .filter { it.isNote }
            .map { NotesFolderEntry.Note(noteName(it.path), it.path, it.hasLocalEdits) }
            .sortedBy { it.name.lowercase() }
        val others = here
            .filterNot { it.isNote }
            .map { NotesFolderEntry.File(it.path.substringAfterLast('/'), it.path, NoteEmbeds.isImage(it.path), it.size) }
            .sortedBy { it.name.lowercase() }
        return folders + notes + others
    }

    /** Every folder in the chat's notes, parents before children: "Recetas", "Recetas/Postres", "Viajes". */
    fun allFolders(files: List<LocalFile>): List<String> = files
        .flatMap { file ->
            val parts = file.path.split('/').dropLast(1)
            parts.indices.map { parts.subList(0, it + 1).joinToString("/") }
        }
        .distinct()
        .sortedBy { it.lowercase() }

    /** "Recetas/Mole.md" → "Mole"; other text files keep their extension so "lista.csv" stays recognisable. */
    fun noteName(path: String): String = path.substringAfterLast('/').removeSuffix(".md")
}
