/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.linkpreview.impl

/** Finds the web link to preview in what someone typed. */
internal object LinkFinder {
    private val link = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"]+""")

    // Links into chats (matrix.to) open in the app: nothing to preview.
    private val skippedHosts = setOf("matrix.to")

    fun firstLink(text: String): String? = link.findAll(text)
        .map { trimEnd(it.value) }
        .map { if (it.startsWith("www.", ignoreCase = true)) "https://$it" else it }
        .firstOrNull { url -> hostOf(url)?.let { it.isNotEmpty() && it.contains('.') && it !in skippedHosts } == true }

    /** "https://a.com/x)." → "https://a.com/x": punctuation after a link, and a ")" it doesn't open. */
    private fun trimEnd(candidate: String): String {
        var url = candidate.trimEnd('.', ',', ';', ':', '!', '?', '\'', '*', '_', '~')
        while (url.endsWith(")") && url.count { it == '(' } < url.count { it == ')' }) {
            url = url.dropLast(1).trimEnd('.', ',', ';', ':', '!', '?')
        }
        return url
    }

    fun hostOf(url: String): String? = url.substringAfter("://", "")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('@')
        .substringBefore(':')
        .lowercase()
        .ifEmpty { null }
}
