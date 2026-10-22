/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.sync

import co.artise.android.notes.api.NotesPreview
import java.io.File

/**
 * A file's preview kept on the phone: a small text file with the file version it belongs to, the server's ETag and what the
 * preview is, next to the PNG when it is a picture.
 */
internal class PreviewCache(private val base: File) {
    data class Entry(val version: String, val etag: String?, val preview: NotesPreview)

    val imageFile: File get() = File(base.path + ".png")
    private val metaFile: File get() = File(base.path + ".meta")

    fun read(): Entry? {
        if (!metaFile.exists()) return null
        val lines = metaFile.readText().split("\n")
        if (lines.size < MIN_LINES) return null
        val version = lines[0]
        val etag = lines[1].takeIf { it.isNotEmpty() }
        val preview = when (lines[2]) {
            KIND_IMAGE -> {
                if (!imageFile.exists()) return null
                NotesPreview.Image(imageFile, lines.getOrNull(3)?.toIntOrNull() ?: 0, lines.getOrNull(4)?.toIntOrNull() ?: 0)
            }
            KIND_TEXT -> NotesPreview.Text(lines.drop(MIN_LINES).joinToString("\n"))
            KIND_NONE -> NotesPreview.None
            else -> return null
        }
        return Entry(version, etag, preview)
    }

    fun write(entry: Entry) {
        val body = when (val preview = entry.preview) {
            is NotesPreview.Image -> listOf(KIND_IMAGE, preview.width.toString(), preview.height.toString())
            is NotesPreview.Text -> listOf(KIND_TEXT, preview.text)
            NotesPreview.None -> listOf(KIND_NONE)
        }
        if (entry.preview !is NotesPreview.Image) imageFile.delete()
        metaFile.writeText((listOf(entry.version, entry.etag.orEmpty()) + body).joinToString("\n"))
    }

    private companion object {
        const val MIN_LINES = 3
        const val KIND_IMAGE = "image"
        const val KIND_TEXT = "text"
        const val KIND_NONE = "none"
    }
}
