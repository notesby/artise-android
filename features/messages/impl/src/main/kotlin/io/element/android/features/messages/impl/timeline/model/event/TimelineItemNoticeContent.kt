/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2022-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.event

import co.artise.android.notes.api.DocumentCard
import io.element.android.libraries.htmlrenderer.api.DocumentNode
import io.element.android.libraries.matrix.ui.messages.toPlainText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jsoup.nodes.Document

data class TimelineItemNoticeContent(
    override val body: String,
    override val htmlDocument: Document?,
    override val formattedBody: CharSequence,
    override val isEdited: Boolean,
    override val messageTree: DocumentNode? = null,
    /** Artise: the notes documents Ari made or changed, shown as cards under the text. */
    val documents: ImmutableList<DocumentCard> = persistentListOf(),
) : TimelineItemTextBasedContent {
    override val type: String = "TimelineItemNoticeContent"
    override val plainText: String = htmlDocument?.toPlainText() ?: body
}
