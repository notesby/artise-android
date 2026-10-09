/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.linkpreview.impl

import co.artise.android.linkpreview.api.LinkPreview
import co.artise.android.linkpreview.api.LinkPreviewReader
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding

@ContributesBinding(AppScope::class)
class DefaultLinkPreviewReader : LinkPreviewReader {
    override fun read(eventJson: String): LinkPreview? = LinkPreviewContent.read(eventJson)
}
