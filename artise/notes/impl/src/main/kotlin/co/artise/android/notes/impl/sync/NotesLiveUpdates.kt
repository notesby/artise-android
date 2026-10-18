/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.sync

import co.artise.android.notes.api.NotesRepository
import dev.zacsweers.metro.Inject
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Keeps chats' notes current while notes are on screen, as the contract's "Live updates" asks:
 * - each chat's `co.artise.notes` room state carries its tree hash; when it differs from ours, the chat syncs;
 * - a chat without that state (its power levels don't allow it) gets one ETag check instead, which costs a `304`
 *   when nothing changed. Starting again when the app comes back to the foreground repeats that check.
 */
@Inject
class NotesLiveUpdates(
    private val matrixClient: MatrixClient,
    private val repository: NotesRepository,
) {
    /** Follows [roomIds] until cancelled. */
    suspend fun keepUpToDate(roomIds: Collection<RoomId>) = coroutineScope {
        roomIds.distinct().forEach { roomId -> launch { follow(roomId) } }
    }

    private suspend fun follow(roomId: RoomId) {
        val room = matrixClient.getRoom(roomId)
        if (room == null) {
            // Not a room this phone knows: the ETag check is all we can do.
            repository.sync(roomId)
            return
        }
        room.use {
            var checkedWithoutState = false
            it.customStateEventsFlow(NOTES_STATE_EVENT_TYPE)
                .map { byStateKey -> treeOf(byStateKey[""]) }
                .distinctUntilChanged()
                .collect { tree ->
                    when {
                        tree != null -> repository.onTreeChanged(roomId, tree)
                        !checkedWithoutState -> {
                            checkedWithoutState = true
                            repository.sync(roomId)
                        }
                    }
                }
        }
    }

    companion object {
        const val NOTES_STATE_EVENT_TYPE = "co.artise.notes"

        private val json = Json { ignoreUnknownKeys = true }

        /** The tree hash from the state content `{"tree": "9f1c…"}`, or `null` if it's missing or unreadable. */
        fun treeOf(content: String?): String? = content?.let {
            tryOrNull { json.parseToJsonElement(it).jsonObject["tree"]?.jsonPrimitive?.content }
        }?.takeIf { it.isNotBlank() }
    }
}
