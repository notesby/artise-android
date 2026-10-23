/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.artise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.artise.android.notes.api.DocumentCard
import co.artise.android.notes.api.DocumentCardRenderer
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList

/** What the timeline needs to draw Ari's document cards and open them: the chat, the renderer, and where a tap goes. */
@Immutable
data class DocumentCardsHost(
    val roomId: RoomId,
    val renderer: DocumentCardRenderer,
    /** Opens the card's document; its kind (from the server) tells a dashboard from a note. */
    val onOpen: (card: DocumentCard) -> Unit,
)

/**
 * Provided by the chat screen when the workspace has notes; without it (threads' previews, pinned messages, a server
 * with no notes) the cards don't show and the message reads as plain text.
 */
@Suppress("CompositionLocalAllowlist")
val LocalDocumentCards = staticCompositionLocalOf<DocumentCardsHost?> { null }

/** The cards under a message's text, one per document, newest change first as the server listed them. */
@Composable
internal fun DocumentCardsView(documents: ImmutableList<DocumentCard>, modifier: Modifier = Modifier) {
    val host = LocalDocumentCards.current ?: return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        documents.forEach { card ->
            host.renderer.Render(
                roomId = host.roomId,
                card = card,
                onClick = { host.onOpen(card) },
                modifier = Modifier
                    .padding(top = 2.dp)
                    .widthIn(max = 320.dp)
                    .fillMaxWidth(),
            )
        }
    }
}
