/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.linkpreview.api

import androidx.compose.runtime.Immutable
import io.element.android.libraries.matrix.api.media.MediaSource
import java.io.File

/**
 * A shared link's preview, as it travels inside a message (MSC4095, `com.beeper.linkpreviews`): the sender's phone
 * read the page, so the people receiving it never contact the website and the server never sees the link.
 */
@Immutable
data class LinkPreview(
    val url: String,
    val title: String?,
    val description: String?,
    val siteName: String?,
    val image: LinkPreviewImage?,
)

/** The page's picture, uploaded with the message (encrypted in encrypted chats). */
data class LinkPreviewImage(val source: MediaSource, val width: Int?, val height: Int?)

/** A preview read while typing, before sending: its picture is still only on this phone. */
@Immutable
data class DraftLinkPreview(
    val url: String,
    val title: String?,
    val description: String?,
    val siteName: String?,
    val image: DraftLinkPreviewImage?,
)

/** A JPEG, already made small enough to send. */
data class DraftLinkPreviewImage(val file: File, val width: Int, val height: Int, val size: Long)

/** The composer's preview: [preview] is `null` while the page is being read. */
@Immutable
data class ComposerLinkPreview(val url: String, val preview: DraftLinkPreview?)
