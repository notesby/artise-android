/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.folder

import co.artise.android.notes.api.LocalFile
import co.artise.android.notes.api.NoteAttachment
import co.artise.android.notes.impl.ui.note.NoteEmbeds

/**
 * What one folder shows: its subfolders (with how many notes and files they hold) then its notes and files, each
 * sorted by name.
 */
internal object NotesFolderEntries {
    fun of(files: List<LocalFile>, folder: String, allFolders: Collection<String> = emptyList()): List<NotesFolderEntry> {
        val prefix = if (folder.isEmpty()) "" else "$folder/"
        val inFolder = files.filter { it.path.startsWith(prefix) }
        val withFiles = inFolder
            .filter { it.path.removePrefix(prefix).contains('/') }
            .groupBy { it.path.removePrefix(prefix).substringBefore('/') }
        // Folders without any file inside (empty, or holding only empty folders) show too.
        val names = withFiles.keys + allFolders.filter { it.startsWith(prefix) && it.length > prefix.length }
            .map { it.removePrefix(prefix).substringBefore('/') }
        val folders = names.distinct()
            .map { name ->
                val inside = withFiles[name].orEmpty()
                NotesFolderEntry.Folder(name, prefix + name, inside.count { it.isNote }, inside.count { !it.isNote })
            }
            .sortedBy { it.name.lowercase() }
        val here = inFolder.filter { !it.path.removePrefix(prefix).contains('/') }
        val notes = here
            .filter { it.isNote }
            .map { file ->
                val kind = file.kind
                if (kind == NoteAttachment.Kind.CHART || kind == NoteAttachment.Kind.DASHBOARD) {
                    // Listed by the title the server read (its picture shows in the row), not by its YAML or front matter.
                    NotesFolderEntry.Note(file.title?.takeIf { it.isNotBlank() } ?: noteName(file.path), file.path, file.hasLocalEdits, kind = kind)
                } else {
                    NotesFolderEntry.Note(noteName(file.path), file.path, file.hasLocalEdits, firstLines(file.content))
                }
            }
            .sortedBy { it.name.lowercase() }
        val others = here
            .filterNot { it.isNote }
            .map { NotesFolderEntry.File(it.path.substringAfterLast('/'), it.path, NoteEmbeds.isImage(it.path), it.size) }
            .sortedBy { it.name.lowercase() }
        return folders + notes + others
    }

    /**
     * How a note starts, for its row: the first [FIRST_LINES] lines with text, without the `---` header and Markdown marks.
     * Empty for a note not downloaded yet.
     */
    fun firstLines(content: String?): String {
        if (content.isNullOrBlank()) return ""
        val lines = content.lines()
        val body = if (lines.first().trim() == "---") {
            val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
            if (end >= 0) lines.drop(end + 2) else lines
        } else {
            lines
        }
        return body.asSequence()
            .map { it.trim().trimStart('#', '>', '-', '*', ' ').removePrefix("[ ] ").removePrefix("[x] ").trim() }
            .filter { it.isNotEmpty() }
            .take(FIRST_LINES)
            .joinToString("\n")
    }

    private const val FIRST_LINES = 2

    /** Every folder in the chat's notes, parents before children: "Recetas", "Recetas/Postres", "Viajes". */
    fun allFolders(files: List<LocalFile>): List<String> = files
        .flatMap { file ->
            val parts = file.path.split('/').dropLast(1)
            parts.indices.map { parts.subList(0, it + 1).joinToString("/") }
        }
        .distinct()
        .sortedBy { it.lowercase() }

    /** "Recetas/Mole.md" → "Mole", "charts/ventas.chart" → "ventas"; other text files keep their extension so "lista.csv" stays recognisable. */
    fun noteName(path: String): String = path.substringAfterLast('/').removeSuffix(".md").removeSuffix(".chart")
}
