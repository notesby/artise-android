/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import co.artise.android.linkpreview.api.DraftLinkPreview
import co.artise.android.linkpreview.api.LinkPreviewService

/** Finds `http(s)://` links; reading pages and building the message's content are scripted. */
class FakeLinkPreviewService(
    private val fetchResult: (url: String) -> Result<DraftLinkPreview> = { Result.failure(IllegalStateException("No page")) },
    private val extraContentResult: (preview: DraftLinkPreview, encrypted: Boolean) -> String = { preview, _ -> """{"preview":"${preview.url}"}""" },
) : LinkPreviewService {
    val fetched = mutableListOf<String>()

    override fun firstLink(text: String): String? = Regex("""https?://\S+""").find(text)?.value

    override suspend fun fetch(url: String): Result<DraftLinkPreview> {
        fetched += url
        return fetchResult(url)
    }

    override suspend fun extraContent(preview: DraftLinkPreview, encrypted: Boolean): String = extraContentResult(preview, encrypted)
}
