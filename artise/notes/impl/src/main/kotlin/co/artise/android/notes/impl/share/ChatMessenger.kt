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

/** Where a document's chat stands for "Ask Ari for a link": whether it can be asked there, and whether others will see it. */
data class ChatForLink(
    val canAsk: Boolean,
    /** More people than the person and Ari: everyone in the chat sees the request. */
    val othersSee: Boolean,
)

/** Posts a message as the person in one of their chats: how the app asks Ari for a share link (there's no link API). */
interface ChatMessenger {
    suspend fun chatForLink(roomId: RoomId): ChatForLink

    suspend fun send(roomId: RoomId, text: String): Result<Unit>
}

@ContributesBinding(SessionScope::class)
class MatrixChatMessenger(private val matrixClient: MatrixClient) : ChatMessenger {
    override suspend fun chatForLink(roomId: RoomId): ChatForLink {
        // Every chat with notes has Ari in it: the notes are Ari's. A chat the person has left can't be asked.
        val room = matrixClient.getJoinedRoom(roomId) ?: return ChatForLink(canAsk = false, othersSee = false)
        return room.use { ChatForLink(canAsk = true, othersSee = it.info().joinedMembersCount > PERSON_AND_ARI) }
    }

    override suspend fun send(roomId: RoomId, text: String): Result<Unit> {
        val room = matrixClient.getJoinedRoom(roomId) ?: return Result.failure(IllegalStateException("Not in this chat"))
        return room.use { it.liveTimeline.sendMessage(body = text, htmlBody = null, intentionalMentions = emptyList(), asPlainText = true) }
    }

    private companion object {
        const val PERSON_AND_ARI = 2
    }
}
