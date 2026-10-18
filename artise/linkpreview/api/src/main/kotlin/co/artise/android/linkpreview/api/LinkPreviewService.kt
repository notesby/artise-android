/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.linkpreview.api

/** Builds previews on the sender's phone, and puts them in the message being sent. */
interface LinkPreviewService {
    /** The first web link in [text] (`https://…`, `http://…` or `www.…`), as a full URL, or `null`. */
    fun firstLink(text: String): String?

    /** Reads the page at [url] (its title, description and picture). */
    suspend fun fetch(url: String): Result<DraftLinkPreview>

    /**
     * The JSON object to add to the message's content for [preview]. Its picture is uploaded first, encrypted when
     * the chat is [encrypted]; if that fails, the preview goes without it.
     */
    suspend fun extraContent(preview: DraftLinkPreview, encrypted: Boolean): String
}

/** Reads the preview inside a received message. */
fun interface LinkPreviewReader {
    /** The preview in [eventJson] (the decrypted event) for a link in the message, or `null`. */
    fun read(eventJson: String): LinkPreview?
}
