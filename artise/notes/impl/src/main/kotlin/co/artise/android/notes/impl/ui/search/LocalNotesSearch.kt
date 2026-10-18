/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.search

import co.artise.android.notes.api.LocalFile
import java.text.Normalizer

/** A note matching a search, with up to [LocalNotesSearch.MAX_LINES] matching lines. */
data class NotesSearchHit(
    val path: String,
    val lines: List<String>,
)

/**
 * Searches the notes saved on the phone, so search works offline. It matches the way the server does:
 * file names and lines, ignoring case and accents ("cafe" finds "Café"), notes whose name matches first.
 */
object LocalNotesSearch {
    const val MAX_RESULTS = 50
    const val MAX_LINES = 3

    fun search(files: List<LocalFile>, query: String): List<NotesSearchHit> {
        val words = query.normalized().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return files
            .filter { it.isNote }
            .mapNotNull { file ->
                val nameMatches = words.all { it in file.path.normalized() }
                val lines = file.content.orEmpty().lines().filter { line -> line.isNotBlank() && words.all { it in line.normalized() } }
                if (!nameMatches && lines.isEmpty()) return@mapNotNull null
                Triple(file.path, nameMatches, lines.take(MAX_LINES).map { it.trim() })
            }
            .sortedWith(compareByDescending<Triple<String, Boolean, List<String>>> { it.second }.thenBy { it.first.lowercase() })
            .take(MAX_RESULTS)
            .map { NotesSearchHit(it.first, it.third) }
    }

    private fun String.normalized(): String = Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")

    private val DIACRITICS = Regex("\\p{Mn}+")
}
