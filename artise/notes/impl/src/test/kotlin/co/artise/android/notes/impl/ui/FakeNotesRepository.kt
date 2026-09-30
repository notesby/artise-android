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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File

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
    val backgroundSyncs = mutableListOf<RoomId>()
    val changesFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val edits = mutableMapOf<RoomId, List<PendingEdit>>()
    val savedEdits = mutableListOf<Pair<String, String>>()
    val createdNotes = mutableListOf<Pair<String, String>>()
    val deletedFiles = mutableListOf<String>()
    val resolved = mutableListOf<Pair<Long, String?>>()
    var moveResult: (String, String) -> Result<MovedNote> = { _, _ -> Result.failure(NotesException.Network(IllegalStateException())) }

    override fun changes(roomId: RoomId): Flow<Unit> = changesFlow

    override fun syncInBackground(roomId: RoomId) {
        backgroundSyncs += roomId
    }

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

    var onTreeChangedResult: (RoomId, String) -> Result<SyncReport?> = { roomId, _ -> Result.success(null) }

    override suspend fun onTreeChanged(roomId: RoomId, tree: String): Result<SyncReport?> = onTreeChangedResult(roomId, tree)

    override suspend fun editNote(roomId: RoomId, path: String, content: String) {
        savedEdits += path to content
        files[roomId] = files[roomId].orEmpty().filterNot { it.path == path } + aNote(path, content, hasLocalEdits = true)
    }

    override suspend fun createNote(roomId: RoomId, path: String, content: String): Result<Unit> {
        if (files[roomId].orEmpty().any { it.path == path }) return Result.failure(NotesException.Exists("exists", null))
        createdNotes += path to content
        files[roomId] = files[roomId].orEmpty() + aNote(path, content, hasLocalEdits = true)
        return Result.success(Unit)
    }

    override suspend fun deleteFile(roomId: RoomId, path: String) {
        deletedFiles += path
        files[roomId] = files[roomId].orEmpty().filterNot { it.path == path }
    }

    override suspend fun moveNote(roomId: RoomId, from: String, to: String) = moveResult(from, to)

    val attachments = mutableListOf<Pair<String, ByteArray>>()
    var addAttachmentResult: (String) -> Result<String> = { name -> Result.success("attachments/$name") }
    var attachmentResult: (String) -> Result<File> = { Result.failure(NotesException.Network(IllegalStateException())) }

    override suspend fun addAttachment(roomId: RoomId, fileName: String, bytes: ByteArray, contentType: String): Result<String> =
        addAttachmentResult(fileName).onSuccess { attachments += it to bytes }

    override suspend fun attachment(roomId: RoomId, path: String): Result<File> = attachmentResult(path)

    override suspend fun edits(roomId: RoomId) = edits[roomId].orEmpty()

    override suspend fun resolveConflict(editId: Long, content: String) {
        resolved += editId to content
    }

    override suspend fun keepDeletedNote(editId: Long) {
        resolved += editId to "keep"
    }

    override suspend fun saveUnderNewName(editId: Long, newPath: String) {
        resolved += editId to newPath
    }

    override suspend fun discardEdit(editId: Long) {
        resolved += editId to null
    }

    override suspend fun search(roomId: RoomId, query: String) = Result.success(emptyList<NotesSearchResult>())

    override suspend fun links(roomId: RoomId, path: String) = linksResult(roomId, path)

    override suspend fun graph(roomId: RoomId) = Result.success(NotesGraph(emptyList(), emptyList()))

    override suspend fun history(roomId: RoomId, path: String) = Result.success(emptyList<NoteVersion>())

    override suspend fun noteAt(roomId: RoomId, path: String, commit: String) = Result.failure<Note>(NotesException.NotFound("none"))
}

fun aNote(path: String, content: String? = "text", hasLocalEdits: Boolean = false) =
    LocalFile(path = path, version = "v", size = 1, modified = 1, isNote = true, content = content, hasLocalEdits = hasLocalEdits)
