/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.linkpreview.impl

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream

private const val MAX_TITLE = 200
private const val MAX_DESCRIPTION = 300

/** What a page says about itself, from its Open Graph and Twitter tags, else its `<title>` and description. */
internal data class PageMetadata(
    val title: String?,
    val description: String?,
    val siteName: String?,
    /** An absolute URL. */
    val imageUrl: String?,
) {
    val isEmpty get() = title == null && description == null && imageUrl == null

    companion object {
        fun parse(html: String, pageUrl: String): PageMetadata = parse(Jsoup.parse(html, pageUrl))

        /** From the page's bytes: its encoding comes from [charset] (the response's), else its own `<meta charset>`. */
        fun parse(bytes: ByteArray, charset: String?, pageUrl: String): PageMetadata = parse(Jsoup.parse(ByteArrayInputStream(bytes), charset, pageUrl))

        private fun parse(document: Document): PageMetadata {
            return PageMetadata(
                title = (document.meta("og:title") ?: document.meta("twitter:title") ?: document.title()).clean(MAX_TITLE),
                description = (document.meta("og:description") ?: document.meta("twitter:description") ?: document.meta("description"))
                    .clean(MAX_DESCRIPTION),
                siteName = document.meta("og:site_name").clean(MAX_TITLE),
                imageUrl = listOf("og:image:secure_url", "og:image", "og:image:url", "twitter:image", "twitter:image:src")
                    .firstNotNullOfOrNull { document.metaUrl(it) }
                    ?: document.selectFirst("link[rel=image_src]")?.absUrl("href")?.ifEmpty { null },
            )
        }

        /** The content of `<meta property=name>` or `<meta name=name>`. */
        private fun Document.meta(name: String): String? =
            selectFirst("meta[property=$name], meta[name=$name]")?.attr("content")?.ifBlank { null }

        private fun Document.metaUrl(name: String): String? =
            selectFirst("meta[property=$name], meta[name=$name]")?.absUrl("content")?.takeIf { it.startsWith("http") }

        /** One line, without extra spaces, cut at [max] characters. */
        private fun String?.clean(max: Int): String? {
            val text = this?.replace(Regex("\\s+"), " ")?.trim()?.ifEmpty { null } ?: return null
            return if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"
        }
    }
}
