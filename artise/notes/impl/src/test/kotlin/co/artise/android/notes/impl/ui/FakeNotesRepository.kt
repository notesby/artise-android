/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.ui

import co.artise.android.notes.api.LocalFile
import co.artise.android.notes.api.MovedNote
import co.artise.android.notes.api.Note
import co.artise.android.notes.api.NoteLinks
import co.artise.android.notes.api.NoteVersion
import co.artise.android.notes.api.NotesChat
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.NotesGraph
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.api.NotesSearchResult
import co.artise.android.notes.api.PendingEdit
import co.artise.android.notes.api.SyncReport
import io.element.android.libraries.matrix.api.core.RoomId

/** In-memory [NotesRepository] for presenter tests: files per chat, and scripted results for server calls. */
class FakeNotesRepository(
    var chats: List<NotesChat> = emptyList(),
    val files: MutableMap<RoomId, List<LocalFile>> = mutableMapOf(),
    var refreshChatsResult: () -> Result<List<NotesChat>> = { Result.success(chats) },
    /** Runs on each sync; may change [files] to simulate a download. */
    var syncResult: (RoomId) -> Result<SyncReport> = { Result.success(SyncReport(0, 0, 0, 0)) },
    var linksResult: (RoomId, String) -> Result<NoteLinks> = { _, path -> Result.success(NoteLinks(path, emptyList(), emptyList())) },
    var seenPrivacyNotice: Boolean = false,
) : NotesRepository {
    var syncCount = 0

    override suspend fun hasSeenPrivacyNotice() = seenPrivacyNotice

    override suspend fun markPrivacyNoticeSeen() {
        seenPrivacyNotice = true
    }

    override suspend fun cachedChats() = chats

    override suspend fun refreshChats() = refreshChatsResult().onSuccess { chats = it }

    override suspend fun files(roomId: RoomId) = files[roomId].orEmpty()

    override suspend fun file(roomId: RoomId, path: String) = files[roomId]?.firstOrNull { it.path == path }

    override suspend fun sync(roomId: RoomId): Result<SyncReport> {
        syncCount++
        return syncResult(roomId)
    }

    override suspend fun onTreeChanged(roomId: RoomId, tree: String): Result<SyncReport?> = sync(roomId)

    override suspend fun editNote(roomId: RoomId, path: String, content: String) = Unit

    override suspend fun createNote(roomId: RoomId, path: String, content: String) = Result.success(Unit)

    override suspend fun deleteFile(roomId: RoomId, path: String) = Unit

    override suspend fun moveNote(roomId: RoomId, from: String, to: String) = Result.failure<MovedNote>(NotesException.Network(IllegalStateException()))

    override suspend fun edits(roomId: RoomId) = emptyList<PendingEdit>()

    override suspend fun resolveConflict(editId: Long, content: String) = Unit

    override suspend fun keepDeletedNote(editId: Long) = Unit

    override suspend fun saveUnderNewName(editId: Long, newPath: String) = Unit

    override suspend fun discardEdit(editId: Long) = Unit

    override suspend fun search(roomId: RoomId, query: String) = Result.success(emptyList<NotesSearchResult>())

    override suspend fun links(roomId: RoomId, path: String) = linksResult(roomId, path)

    override suspend fun graph(roomId: RoomId) = Result.success(NotesGraph(emptyList(), emptyList()))

    override suspend fun history(roomId: RoomId, path: String) = Result.success(emptyList<NoteVersion>())

    override suspend fun noteAt(roomId: RoomId, path: String, commit: String) = Result.failure<Note>(NotesException.NotFound("none"))
}

fun aNote(path: String, content: String? = "text", hasLocalEdits: Boolean = false) =
    LocalFile(path = path, version = "v", size = 1, modified = 1, isNote = true, content = content, hasLocalEdits = hasLocalEdits)
