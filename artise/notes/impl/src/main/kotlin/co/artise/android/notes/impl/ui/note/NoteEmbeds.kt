/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui.note

import co.artise.android.notes.impl.markdown.NoteLinkResolver
import java.net.URLDecoder

/** Finds the photos and files a note embeds, and which of the chat's files they are. */
object NoteEmbeds {
    private val WIKI_EMBED = Regex("!\\[\\[([^\\[\\]|#\\n]+)(?:[|#][^\\]\\n]*)?]]")
    private val MARKDOWN_IMAGE = Regex("!\\[[^\\]\\n]*]\\(<?([^)>\\s]+)>?\\)")
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp")

    /** Each embed's target, as written, mapped to the file it resolves to (notes aren't embeds here). */
    fun resolve(markdown: String, paths: Collection<String>): Map<String, String> {
        val targets = WIKI_EMBED.findAll(markdown).map { it.groupValues[1].trim() } +
            MARKDOWN_IMAGE.findAll(markdown).map { it.groupValues[1] }.filter { "://" !in it }
                .map { URLDecoder.decode(it, Charsets.UTF_8.name()).removePrefix("./") }
        return targets
            .distinct()
            .mapNotNull { target -> NoteLinkResolver.resolve(target, paths)?.takeIf { !it.endsWith(".md") }?.let { target to it } }
            .toMap()
    }

    fun isImage(path: String): Boolean = path.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS

    fun nameOf(path: String): String = path.substringAfterLast('/')
}
