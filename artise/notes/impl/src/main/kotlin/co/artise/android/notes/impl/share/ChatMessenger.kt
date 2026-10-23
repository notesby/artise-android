/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.share

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.IntentionalMention

/** Someone a message mentions: Ari, by its Matrix account, so it answers in a group chat too. */
data class Mention(val userId: String, val name: String)

/** Posts a message as the person in one of their chats: how the app asks Ari for a share link (there's no link API). */
interface ChatMessenger {
    /** Whether the person is in the chat, so it can be asked there. Every chat with notes has Ari in it. */
    suspend fun canAsk(roomId: RoomId): Boolean

    /** Sends [text]; when it starts with [mention]'s name, that part becomes a mention of them. */
    suspend fun send(roomId: RoomId, text: String, mention: Mention?): Result<Unit>
}

@ContributesBinding(SessionScope::class)
class MatrixChatMessenger(private val matrixClient: MatrixClient) : ChatMessenger {
    override suspend fun canAsk(roomId: RoomId): Boolean = matrixClient.getJoinedRoom(roomId)?.use { true } ?: false

    override suspend fun send(roomId: RoomId, text: String, mention: Mention?): Result<Unit> {
        val room = matrixClient.getJoinedRoom(roomId) ?: return Result.failure(IllegalStateException("Not in this chat"))
        val userId = mention?.userId?.takeIf { it.startsWith("@") && ':' in it }
        return room.use {
            it.liveTimeline.sendMessage(
                body = text,
                htmlBody = userId?.let { id -> mentionHtml(text, mention.name, id) },
                intentionalMentions = listOfNotNull(userId?.let { id -> IntentionalMention.User(UserId(id)) }),
                asPlainText = userId == null,
            )
        }
    }

    companion object {
        /** [text] as HTML with its leading [name] as a link to [userId], the way Matrix apps show a mention; the rest escaped. */
        fun mentionHtml(text: String, name: String, userId: String): String {
            val link = "<a href=\"https://matrix.to/#/$userId\">${escape(name)}</a>"
            return if (text.startsWith(name)) link + escape(text.removePrefix(name)) else "$link ${escape(text)}"
        }

        private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }
}
