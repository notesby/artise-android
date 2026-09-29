/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.sync

import co.artise.android.notes.api.EditKind
import co.artise.android.notes.api.EditState
import co.artise.android.notes.api.LocalFile
import co.artise.android.notes.api.MovedNote
import co.artise.android.notes.api.Note
import co.artise.android.notes.api.NoteLinks
import co.artise.android.notes.api.NoteVersion
import co.artise.android.notes.api.NotesChat
import co.artise.android.notes.api.NotesException
import co.artise.android.notes.api.NotesFile
import co.artise.android.notes.api.NotesGraph
import co.artise.android.notes.api.NotesRepository
import co.artise.android.notes.api.NotesSearchResult
import co.artise.android.notes.api.PendingEdit
import co.artise.android.notes.api.SyncReport
import co.artise.android.notes.impl.local.NotesLocalStore
import co.artise.android.notes.impl.remote.NotesApiClient
import co.artise.android.notes.impl.remote.TreeResponse
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [NotesRepository] over the [NotesApiClient] and the phone's [NotesLocalStore], following the contract's
 * "Offline sync, step by step":
 * 1. edits are saved locally at once and queued with the version they started from;
 * 2. [sync] sends the queue in order; conflicts wait for the person instead of blocking other notes;
 * 3. then the tree is pulled with its ETag, updating changed files and dropping removed ones,
 *    except files with local edits, which are never overwritten.
 */
class NotesSyncEngine(
    private val api: NotesApiClient,
    private val store: NotesLocalStore,
    private val clock: SystemClock,
    private val dispatchers: CoroutineDispatchers,
) : NotesRepository {
    // One sync at a time, so the queue is never sent twice.
    private val syncMutex = Mutex()

    override suspend fun cachedChats(): List<NotesChat> = io { store.chats() }

    override suspend fun refreshChats(): Result<List<NotesChat>> = api.chats().onSuccess { io { store.replaceChats(it) } }

    override suspend fun files(roomId: RoomId): List<LocalFile> = io { store.files(roomId) }

    override suspend fun file(roomId: RoomId, path: String): LocalFile? = io { store.file(roomId, path) }

    override suspend fun sync(roomId: RoomId): Result<SyncReport> = syncMutex.withLock {
        val push = pushQueue(roomId).getOrElse { return Result.failure(it) }
        val pull = pullTree(roomId).getOrElse { return Result.failure(it) }
        Result.success(SyncReport(sent = push.sent, needChoice = push.needChoice, updated = pull.updated, removed = pull.removed))
    }

    override suspend fun onTreeChanged(roomId: RoomId, tree: String): Result<SyncReport?> {
        val known = io { store.cachedTree(roomId) }?.tree
        return if (known == tree) Result.success(null) else sync(roomId)
    }

    override suspend fun editNote(roomId: RoomId, path: String, content: String) {
        io {
            store.transaction {
                val now = clock.nowSeconds()
                val file = store.file(roomId, path)
                if (file == null) {
                    // A note the phone never had: treat the edit as creating it.
                    store.putFile(roomId, NotesFile(path, "", content.byteSize(), now, isNote = true), content)
                    store.addEdit(roomId, EditKind.SAVE, path, content, baseVersion = null, now = clock.epochMillis())
                } else {
                    store.setContent(roomId, path, content, now)
                    val pending = store.pendingSave(roomId, path)
                    if (pending != null) {
                        // Coalesce: the earlier unsent edit keeps its base version, so the server still merges from there.
                        store.setEditContent(pending.id, content)
                    } else {
                        store.addEdit(roomId, EditKind.SAVE, path, content, baseVersion = file.version.ifEmpty { null }, now = clock.epochMillis())
                    }
                }
            }
        }
    }

    override suspend fun createNote(roomId: RoomId, path: String, content: String): Result<Unit> = io {
        store.transaction {
            if (store.file(roomId, path) != null) {
                Result.failure(NotesException.Exists("A note named $path is already here", null))
            } else {
                store.putFile(roomId, NotesFile(path, "", content.byteSize(), clock.nowSeconds(), isNote = true), content)
                store.addEdit(roomId, EditKind.SAVE, path, content, baseVersion = null, now = clock.epochMillis())
                Result.success(Unit)
            }
        }
    }

    override suspend fun deleteFile(roomId: RoomId, path: String) {
        io {
            store.transaction {
                val file = store.file(roomId, path) ?: return@transaction
                // Unsent saves to this file no longer matter.
                store.edits(roomId).filter { it.path == path }.forEach { store.deleteEdit(it.id) }
                store.deleteFile(roomId, path)
                // A note created offline never reached the server: nothing to delete there.
                if (file.version.isNotEmpty()) {
                    store.addEdit(roomId, EditKind.DELETE, path, content = null, baseVersion = file.version, now = clock.epochMillis())
                }
            }
        }
    }

    override suspend fun moveNote(roomId: RoomId, from: String, to: String): Result<MovedNote> = syncMutex.withLock {
        val file = io { store.file(roomId, from) } ?: return Result.failure(NotesException.NotFound("No note named $from"))
        if (file.hasLocalEdits || file.version.isEmpty()) {
            return Result.failure(NotesException.Conflict("$from has changes that haven't been sent yet. Sync first.", null))
        }
        api.move(roomId, from, to, file.version).onSuccess { moved ->
            io {
                store.transaction {
                    store.renameFile(roomId, from, to)
                    store.setVersion(roomId, to, moved.version)
                }
            }
            // The server rewrote links in other notes; pull them.
            if (moved.linksUpdated.isNotEmpty()) pullTree(roomId)
        }
    }

    override suspend fun edits(roomId: RoomId): List<PendingEdit> = io { store.edits(roomId) }

    override suspend fun resolveConflict(editId: Long, content: String) {
        io {
            store.transaction {
                val (roomId, edit) = store.edit(editId) ?: return@transaction
                val serverVersion = edit.serverCopy?.version ?: return@transaction
                // The choice is now based on the server's version; save it on top of that.
                store.setContent(roomId, edit.path, content, clock.nowSeconds())
                store.setVersion(roomId, edit.path, serverVersion)
                store.requeueEdit(editId, edit.path, content, baseVersion = serverVersion)
            }
        }
    }

    override suspend fun keepDeletedNote(editId: Long) {
        io {
            store.transaction {
                val (roomId, edit) = store.edit(editId) ?: return@transaction
                store.setVersion(roomId, edit.path, "")
                store.requeueEdit(editId, edit.path, edit.content, baseVersion = null)
            }
        }
    }

    override suspend fun saveUnderNewName(editId: Long, newPath: String) {
        io {
            store.transaction {
                val (roomId, edit) = store.edit(editId) ?: return@transaction
                val serverCopy = edit.serverCopy
                store.renameFile(roomId, edit.path, newPath)
                store.setVersion(roomId, newPath, "")
                store.requeueEdit(editId, newPath, edit.content, baseVersion = null)
                // Keep the other person's note under the original name.
                if (serverCopy != null) {
                    val text = serverCopy.content
                    store.putFile(roomId, NotesFile(edit.path, serverCopy.version, text?.byteSize() ?: 0, clock.nowSeconds(), isNote = true), text)
                }
            }
        }
    }

    override suspend fun discardEdit(editId: Long) {
        io {
            store.transaction {
                val (roomId, edit) = store.edit(editId) ?: return@transaction
                store.deleteEdit(editId)
                val serverCopy = edit.serverCopy
                when {
                    // The server's copy is known: show it.
                    serverCopy?.content != null -> {
                        store.setContent(roomId, edit.path, serverCopy.content.orEmpty(), clock.nowSeconds())
                        store.setVersion(roomId, edit.path, serverCopy.version)
                    }
                    // Deleted on the server, or a new note never sent: it's gone.
                    edit.state == EditState.DELETED || edit.kind == EditKind.SAVE && store.baseVersion(editId) == null -> store.deleteFile(roomId, edit.path)
                    // Otherwise download the server's copy again on the next sync.
                    else -> {
                        store.deleteFile(roomId, edit.path)
                        store.forgetEtag(roomId)
                    }
                }
            }
        }
    }

    override suspend fun search(roomId: RoomId, query: String): Result<List<NotesSearchResult>> = api.search(roomId, query)

    override suspend fun links(roomId: RoomId, path: String): Result<NoteLinks> = api.links(roomId, path)

    override suspend fun graph(roomId: RoomId): Result<NotesGraph> = api.graph(roomId)

    override suspend fun history(roomId: RoomId, path: String): Result<List<NoteVersion>> = api.history(roomId, path)

    override suspend fun noteAt(roomId: RoomId, path: String, commit: String): Result<Note> = api.note(roomId, path, atCommit = commit)

    private data class PushResult(val sent: Int, val needChoice: Int)

    private data class PullResult(val updated: Int, val removed: Int)

    /** Step 2: sends pending edits in order. Stops at a network or sign-in failure, leaving the rest queued. */
    private suspend fun pushQueue(roomId: RoomId): Result<PushResult> {
        var sent = 0
        var needChoice = 0
        // An edit waiting for a choice holds back later edits to the same file, so they don't overtake it.
        val heldBack = io { store.edits(roomId) }.filter { it.state != EditState.PENDING }.map { it.path }.toMutableSet()
        for (edit in io { store.pendingEdits(roomId) }) {
            if (edit.path in heldBack) continue
            val outcome = when (edit.kind) {
                EditKind.SAVE -> pushSave(roomId, edit)
                EditKind.DELETE -> pushDelete(roomId, edit)
            }
            when (outcome) {
                PushOutcome.Sent -> sent++
                PushOutcome.NeedsChoice -> {
                    needChoice++
                    heldBack += edit.path
                }
                is PushOutcome.Stop -> return Result.failure(outcome.error)
            }
        }
        return Result.success(PushResult(sent, needChoice))
    }

    private sealed interface PushOutcome {
        data object Sent : PushOutcome

        data object NeedsChoice : PushOutcome

        data class Stop(val error: NotesException) : PushOutcome
    }

    private suspend fun pushSave(roomId: RoomId, edit: PendingEdit): PushOutcome {
        val content = edit.content.orEmpty()
        val base = io { store.baseVersion(edit.id) }
        return api.saveNote(roomId, edit.path, content, base).fold(
            onSuccess = { saved ->
                io {
                    store.transaction {
                        store.setVersion(roomId, edit.path, saved.version)
                        // The server merged our edit with someone else's: the merged text is now the note.
                        if (saved.merged && saved.content != null) store.setContent(roomId, edit.path, saved.content.orEmpty(), clock.nowSeconds())
                        store.deleteEdit(edit.id)
                    }
                }
                PushOutcome.Sent
            },
            onFailure = { handleFailure(edit, it) },
        )
    }

    private suspend fun pushDelete(roomId: RoomId, edit: PendingEdit): PushOutcome {
        val base = io { store.baseVersion(edit.id) }.orEmpty()
        return api.delete(roomId, edit.path, base).fold(
            onSuccess = {
                io { store.deleteEdit(edit.id) }
                PushOutcome.Sent
            },
            onFailure = { error ->
                // Already gone on the server: that's what we wanted.
                if (error is NotesException.NotFound || error is NotesException.Deleted) {
                    io { store.deleteEdit(edit.id) }
                    PushOutcome.Sent
                } else {
                    handleFailure(edit, error)
                }
            },
        )
    }

    private suspend fun handleFailure(edit: PendingEdit, error: Throwable): PushOutcome = io {
        when (error) {
            is NotesException.Conflict -> {
                store.markEdit(edit.id, EditState.CONFLICT, error.current)
                PushOutcome.NeedsChoice
            }
            is NotesException.Deleted -> {
                store.markEdit(edit.id, EditState.DELETED)
                PushOutcome.NeedsChoice
            }
            is NotesException.Exists -> {
                store.markEdit(edit.id, EditState.EXISTS, error.current)
                PushOutcome.NeedsChoice
            }
            is NotesException.Network, is NotesException.Unauthorized, is NotesException.Server -> PushOutcome.Stop(error as NotesException)
            is NotesException -> {
                // Too big, a bad name, not a note, no longer in the chat: retrying won't help.
                store.markEdit(edit.id, EditState.REJECTED, error = error.message)
                PushOutcome.NeedsChoice
            }
            else -> PushOutcome.Stop(NotesException.Server(0, error.message ?: "Unknown error"))
        }
    }

    /** Step 3: pulls the tree with the cached ETag and updates what changed, never touching files with local edits. */
    private suspend fun pullTree(roomId: RoomId): Result<PullResult> {
        val cached = io { store.cachedTree(roomId) }
        val response = api.tree(roomId, cached?.etag).getOrElse { return Result.failure(it) }
        val changed = when (response) {
            TreeResponse.NotModified -> return Result.success(PullResult(0, 0))
            is TreeResponse.Changed -> response
        }
        val remote = changed.tree.files.associateBy { it.path }
        val local = io { store.files(roomId) }.associateBy { it.path }
        var updated = 0
        for (file in remote.values.filter { needsDownload(it, local[it.path]) }) {
            // Anything but "deleted meanwhile" stops the pull without saving the ETag, so the next sync tries again.
            if (pullFile(roomId, file).getOrElse { return Result.failure(it) }) updated++
        }
        var removed = 0
        io {
            store.transaction {
                for (mine in local.values) {
                    if (mine.path !in remote && !mine.hasLocalEdits && mine.version.isNotEmpty()) {
                        store.deleteFile(roomId, mine.path)
                        removed++
                    }
                }
                store.saveTree(roomId, changed.tree.tree, changed.etag)
            }
        }
        return Result.success(PullResult(updated, removed))
    }

    /** A file changed on the server, or a note whose text we don't have yet. Files with local edits are never overwritten. */
    private fun needsDownload(file: NotesFile, mine: LocalFile?): Boolean = when {
        mine == null -> true
        mine.hasLocalEdits -> false
        mine.version != file.version -> true
        else -> file.isNote && mine.content == null
    }

    /** Stores one changed file; `false` when it was deleted between the tree and this request. */
    private suspend fun pullFile(roomId: RoomId, file: NotesFile): Result<Boolean> {
        if (!file.isNote) {
            // Photos and documents are downloaded when opened; keep the listing only.
            io { store.putFile(roomId, file, null) }
            return Result.success(true)
        }
        return api.note(roomId, file.path).fold(
            onSuccess = { note ->
                io { store.putFile(roomId, file.copy(version = note.version), note.content) }
                Result.success(true)
            },
            // Deleted between the tree and this request: the next tree won't list it.
            onFailure = { if (it is NotesException.NotFound) Result.success(false) else Result.failure(it) },
        )
    }

    private suspend fun <T> io(block: () -> T): T = withContext(dispatchers.io) { block() }

    private fun SystemClock.nowSeconds() = epochMillis() / 1000

    private fun String.byteSize() = toByteArray().size.toLong()
}
