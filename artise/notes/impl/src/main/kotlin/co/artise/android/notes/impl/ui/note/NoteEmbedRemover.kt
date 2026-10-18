/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.note

/** Takes a photo or file out of a note's text, when its upload is cancelled. */
object NoteEmbedRemover {
    /**
     * [content] without the embeds of [path] (`![[foto.jpg]]`, `![[attachments/foto.jpg|300]]`). An embed alone on its
     * line goes with that line and one blank line next to it, so no gap is left where the photo was.
     */
    fun remove(content: String, path: String): String {
        val names = listOf(path, path.substringAfterLast('/')).distinct().joinToString("|") { Regex.escape(it) }
        val embed = Regex("""!\[\[(?:$names)(?:\|[^\]\n]*)?]]""")
        if (!embed.containsMatchIn(content)) return content
        val lines = content.split('\n').toMutableList()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!embed.containsMatchIn(line)) {
                i++
                continue
            }
            if (embed.replace(line, "").isBlank()) {
                lines.removeAt(i)
                when {
                    i < lines.size && lines[i].isBlank() -> lines.removeAt(i)
                    i > 0 && lines[i - 1].isBlank() -> {
                        lines.removeAt(i - 1)
                        i--
                    }
                }
            } else {
                lines[i] = embed.replace(line, "").replace(Regex(" {2,}"), " ").trimEnd()
                i++
            }
        }
        return lines.joinToString("\n")
    }
}
