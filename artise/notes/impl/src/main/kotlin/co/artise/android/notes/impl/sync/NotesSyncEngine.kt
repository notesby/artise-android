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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    private val backgroundScope: CoroutineScope,
    /** Where downloaded attachments are kept: inside the app's own cache, one folder per account. */
    private val attachmentsDir: File,
    /** Where attachments wait to upload: app storage, not the cache, which Android may clear. */
    private val pendingDir: File,
) : NotesRepository {
    // One sync per chat at a time, so a chat's queue is never sent twice. Different chats don't wait for each other:
    // opening one chat's notes shouldn't wait behind the background check of all the others.
    private val roomLocks = ConcurrentHashMap<RoomId, Mutex>()

    private fun lockFor(roomId: RoomId): Mutex = roomLocks.getOrPut(roomId) { Mutex() }
    private val changed = MutableSharedFlow<RoomId>(extraBufferCapacity = 16)

    override fun changes(roomId: RoomId): Flow<Unit> = changed.filter { it == roomId }.map { }

    override fun syncInBackground(roomId: RoomId) {
        backgroundScope.launch { sync(roomId) }
    }

    private fun notifyChanged(roomId: RoomId) {
        changed.tryEmit(roomId)
    }

    override suspend fun hasSeenPrivacyNotice(): Boolean = io { store.hasFlag(FLAG_PRIVACY_NOTICE) }

    override suspend fun markPrivacyNoticeSeen() {
        io { store.setFlag(FLAG_PRIVACY_NOTICE) }
    }

    override suspend fun cachedChats(): List<NotesChat> = io { store.chats() }

    override suspend fun refreshChats(): Result<List<NotesChat>> = api.chats().onSuccess { io { store.replaceChats(it) } }

    override suspend fun files(roomId: RoomId): List<LocalFile> = io { store.files(roomId) }

    override suspend fun file(roomId: RoomId, path: String): LocalFile? = io { store.file(roomId, path) }

    override suspend fun sync(roomId: RoomId): Result<SyncReport> = lockFor(roomId).withLock {
        val push = pushQueue(roomId).getOrElse {
            notifyChanged(roomId)
            return Result.failure(it)
        }
        val pull = pullTree(roomId).getOrElse {
            notifyChanged(roomId)
            return Result.failure(it)
        }
        val report = SyncReport(sent = push.sent, needChoice = push.needChoice, updated = pull.updated, removed = pull.removed, failed = push.failed)
        if (report != SyncReport(0, 0, 0, 0)) notifyChanged(roomId)
        Result.success(report)
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
        notifyChanged(roomId)
    }

    override suspend fun createNote(roomId: RoomId, path: String, content: String): Result<Unit> = io {
        store.transaction<Result<Unit>> {
            if (store.file(roomId, path) != null) {
                Result.failure(NotesException.Exists("A note named $path is already here", null))
            } else {
                store.putFile(roomId, NotesFile(path, "", content.byteSize(), clock.nowSeconds(), isNote = true), content)
                store.addEdit(roomId, EditKind.SAVE, path, content, baseVersion = null, now = clock.epochMillis())
                Result.success(Unit)
            }
        }
    }.onSuccess { notifyChanged(roomId) }

    override suspend fun deleteFile(roomId: RoomId, path: String) {
        io {
            store.transaction {
                val file = store.file(roomId, path) ?: return@transaction
                // Unsent saves (and a waiting upload) of this file no longer matter.
                store.edits(roomId).filter { it.path == path }.forEach {
                    if (it.kind == EditKind.UPLOAD) it.content?.let { waiting -> File(waiting).delete() }
                    store.deleteEdit(it.id)
                }
                store.deleteFile(roomId, path)
                // A note created offline never reached the server: nothing to delete there.
                if (file.version.isNotEmpty()) {
                    store.addEdit(roomId, EditKind.DELETE, path, content = null, baseVersion = file.version, now = clock.epochMillis())
                }
            }
        }
        notifyChanged(roomId)
    }

    override suspend fun moveNote(roomId: RoomId, from: String, to: String): Result<MovedNote> = lockFor(roomId).withLock {
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
            notifyChanged(roomId)
        }
    }

    override suspend fun addAttachment(roomId: RoomId, fileName: String, bytes: ByteArray, contentType: String): Result<String> {
        val path = io {
            store.transaction {
                val taken = store.files(roomId).map { it.path.lowercase() }.toSet()
                val path = freeAttachmentPath(fileName, taken)
                val waiting = File(pendingDir, UUID.randomUUID().toString() + path.extensionWithDot())
                waiting.parentFile?.mkdirs()
                waiting.writeBytes(bytes)
                // Listed at once without a version: it shows from the phone until it's uploaded.
                store.putFile(roomId, NotesFile(path, "", bytes.size.toLong(), clock.nowSeconds(), isNote = false), null)
                store.addUpload(roomId, path, waiting.absolutePath, contentType, clock.epochMillis())
                path
            }
        }
        notifyChanged(roomId)
        return Result.success(path)
    }

    override suspend fun attachment(roomId: RoomId, path: String): Result<File> {
        // Still waiting to upload: the phone's copy is the only one.
        io { store.pendingEdits(roomId) }.firstOrNull { it.kind == EditKind.UPLOAD && it.path == path }?.content?.let { waiting ->
            return Result.success(File(waiting))
        }
        val version = io { store.file(roomId, path) }?.version.orEmpty()
        val cached = cacheFileFor(roomId, path, version)
        if (version.isNotEmpty() && io { cached.exists() }) return Result.success(cached)
        return api.raw(roomId, path).map { bytes ->
            io {
                cached.parentFile?.mkdirs()
                cached.writeBytes(bytes)
            }
            cached
        }
    }

    /** One file per attachment and version: a changed photo is downloaded again, an unchanged one never. */
    private fun cacheFileFor(roomId: RoomId, path: String, version: String): File {
        val key = sha256("${roomId.value}/$path").take(24) + "-" + version.take(12)
        val extension = path.substringAfterLast('.', "").takeIf { it.length in 1..5 }?.let { ".$it" }.orEmpty()
        return File(attachmentsDir, key + extension).also { it.parentFile?.mkdirs() }
    }

    override suspend fun edits(roomId: RoomId): List<PendingEdit> = io { store.edits(roomId) }

    override suspend fun resolveConflict(editId: Long, content: String) {
        val changedRoom = io {
            store.transaction<RoomId?> {
                val (roomId, edit) = store.edit(editId) ?: return@transaction null
                val serverVersion = edit.serverCopy?.version ?: return@transaction null
                // The choice is now based on the server's version; save it on top of that.
                store.setContent(roomId, edit.path, content, clock.nowSeconds())
                store.setVersion(roomId, edit.path, serverVersion)
                store.requeueEdit(editId, edit.path, content, baseVersion = serverVersion)
                roomId
            }
        }
        changedRoom?.let(::notifyChanged)
    }

    override suspend fun keepDeletedNote(editId: Long) {
        val changedRoom = io {
            store.transaction<RoomId?> {
                val (roomId, edit) = store.edit(editId) ?: return@transaction null
                store.setVersion(roomId, edit.path, "")
                store.requeueEdit(editId, edit.path, edit.content, baseVersion = null)
                roomId
            }
        }
        changedRoom?.let(::notifyChanged)
    }

    override suspend fun saveUnderNewName(editId: Long, newPath: String) {
        val changedRoom = io {
            store.transaction<RoomId?> {
                val (roomId, edit) = store.edit(editId) ?: return@transaction null
                val serverCopy = edit.serverCopy
                store.renameFile(roomId, edit.path, newPath)
                store.setVersion(roomId, newPath, "")
                store.requeueEdit(editId, newPath, edit.content, baseVersion = null)
                // Keep the other person's note under the original name.
                if (serverCopy != null) {
                    val text = serverCopy.content
                    store.putFile(roomId, NotesFile(edit.path, serverCopy.version, text?.byteSize() ?: 0, clock.nowSeconds(), isNote = true), text)
                }
                roomId
            }
        }
        changedRoom?.let(::notifyChanged)
    }

    override suspend fun discardEdit(editId: Long) {
        val changedRoom = io {
            store.transaction<RoomId?> {
                val (roomId, edit) = store.edit(editId) ?: return@transaction null
                if (edit.kind == EditKind.UPLOAD) {
                    // A refused upload: drop the waiting file and the attachment it would have become.
                    edit.content?.let { File(it).delete() }
                    store.deleteEdit(editId)
                    store.deleteFile(roomId, edit.path)
                    return@transaction roomId
                }
                // Read before deleting the edit: afterwards its base version is gone.
                val baseVersion = store.baseVersion(editId)
                store.deleteEdit(editId)
                val serverCopy = edit.serverCopy
                when {
                    // The server's copy is known: show it.
                    serverCopy?.content != null -> {
                        store.setContent(roomId, edit.path, serverCopy.content.orEmpty(), clock.nowSeconds())
                        store.setVersion(roomId, edit.path, serverCopy.version)
                    }
                    // Deleted on the server, or a new note never sent: it's gone.
                    edit.state == EditState.DELETED || edit.kind == EditKind.SAVE && baseVersion == null -> store.deleteFile(roomId, edit.path)
                    // Otherwise download the server's copy again on the next sync.
                    else -> {
                        store.deleteFile(roomId, edit.path)
                        store.forgetEtag(roomId)
                    }
                }
                roomId
            }
        }
        changedRoom?.let(::notifyChanged)
    }

    override suspend fun search(roomId: RoomId, query: String): Result<List<NotesSearchResult>> = api.search(roomId, query)

    override suspend fun links(roomId: RoomId, path: String): Result<NoteLinks> = api.links(roomId, path)

    override suspend fun graph(roomId: RoomId): Result<NotesGraph> = api.graph(roomId)

    override suspend fun history(roomId: RoomId, path: String): Result<List<NoteVersion>> = api.history(roomId, path)

    override suspend fun noteAt(roomId: RoomId, path: String, commit: String): Result<Note> = api.note(roomId, path, atCommit = commit)

    private data class PushResult(val sent: Int, val needChoice: Int, val failed: Int)

    private data class PullResult(val updated: Int, val removed: Int)

    /**
     * Step 2: sends pending edits in order. Stops at a network or sign-in failure, leaving the rest queued. Any other
     * failure of one edit (an error on the server's side) sets that edit aside for the next sync and goes on with the
     * others: one bad item must never hold up the whole chat.
     */
    private suspend fun pushQueue(roomId: RoomId): Result<PushResult> {
        var sent = 0
        var needChoice = 0
        var failed = 0
        // An edit waiting for a choice holds back later edits to the same file, so they don't overtake it.
        val heldBack = io { store.edits(roomId) }.filter { it.state != EditState.PENDING }.map { it.path }.toMutableSet()
        for (edit in io { store.pendingEdits(roomId) }) {
            if (edit.path in heldBack) continue
            val outcome = when (edit.kind) {
                EditKind.SAVE -> pushSave(roomId, edit)
                EditKind.DELETE -> pushDelete(roomId, edit)
                EditKind.UPLOAD -> pushUpload(roomId, edit)
            }
            when (outcome) {
                PushOutcome.Sent -> sent++
                PushOutcome.NeedsChoice -> {
                    needChoice++
                    heldBack += edit.path
                }
                PushOutcome.Retry -> {
                    failed++
                    heldBack += edit.path
                }
                is PushOutcome.Stop -> return Result.failure(outcome.error)
            }
        }
        return Result.success(PushResult(sent, needChoice, failed))
    }

    private sealed interface PushOutcome {
        data object Sent : PushOutcome

        data object NeedsChoice : PushOutcome

        /** Failed this time for a reason on the server's side: stays queued, the push goes on with other edits. */
        data object Retry : PushOutcome

        data class Stop(val error: NotesException) : PushOutcome
    }

    private suspend fun pushSave(roomId: RoomId, edit: PendingEdit): PushOutcome {
        // Read now, not from the list taken when the push started: an upload renamed earlier in this push may have
        // changed the embed in this very edit.
        val content = io { store.edit(edit.id)?.second?.content } ?: edit.content.orEmpty()
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

    /**
     * Uploads a waiting attachment. If someone uploaded a file with the same name meanwhile, it takes the next free
     * name and the unsent note edits that embed it are changed to match, so the note still shows it.
     */
    private suspend fun pushUpload(roomId: RoomId, edit: PendingEdit): PushOutcome {
        val waiting = File(edit.content.orEmpty())
        val bytes = io { if (waiting.exists()) waiting.readBytes() else null }
            ?: return io {
                store.markEdit(edit.id, EditState.REJECTED, error = "The file is no longer on this phone")
                PushOutcome.NeedsChoice
            }
        val contentType = io { store.contentType(edit.id) } ?: "application/octet-stream"
        var path = edit.path
        repeat(MAX_RENAME_TRIES) {
            val result = api.saveRaw(roomId, path, bytes, contentType, baseVersion = null)
            val version = result.getOrNull()
            if (version != null) {
                io {
                    store.transaction {
                        store.setVersion(roomId, path, version)
                        store.deleteEdit(edit.id)
                    }
                    cacheFileFor(roomId, path, version).writeBytes(bytes)
                    waiting.delete()
                }
                return PushOutcome.Sent
            }
            val error = result.exceptionOrNull()
            if (error !is NotesException.Exists) return handleFailure(edit, error ?: IllegalStateException("Upload failed"))
            val taken = io { store.files(roomId) }.map { it.path.lowercase() }.toSet() + path.lowercase()
            val newPath = freeAttachmentPath(path.substringAfterLast('/'), taken)
            io { renameUpload(roomId, edit.id, from = path, to = newPath) }
            path = newPath
        }
        return handleFailure(edit, NotesException.Exists("No free name for ${edit.path}", null))
    }

    /** Moves a waiting upload to [to], and points unsent note edits that embed it at the new name. */
    private fun renameUpload(roomId: RoomId, editId: Long, from: String, to: String) = store.transaction {
        store.renameFile(roomId, from, to)
        store.renameEdit(editId, to)
        val oldName = from.substringAfterLast('/')
        val newName = to.substringAfterLast('/')
        for (save in store.pendingEdits(roomId).filter { it.kind == EditKind.SAVE }) {
            val content = save.content ?: continue
            val updated = content.replace("[[$oldName", "[[$newName").replace("[[$from", "[[$to")
            if (updated != content) {
                store.setEditContent(save.id, updated)
                store.setContent(roomId, save.path, updated, clock.nowSeconds())
            }
        }
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
            // Every other edit would fail the same way: stop and try the whole queue later.
            is NotesException.Network, is NotesException.Unauthorized -> PushOutcome.Stop(error)
            // Something on the server's side went wrong with this one (a 5xx, a proxy limit, an unreadable answer).
            is NotesException.Server -> {
                Timber.w("Notes: an edit failed with HTTP %d; kept for the next sync", error.status)
                PushOutcome.Retry
            }
            is NotesException -> {
                // Too big, a bad name, not a note, no longer in the chat: retrying won't help.
                store.markEdit(edit.id, EditState.REJECTED, error = error.message)
                PushOutcome.NeedsChoice
            }
            else -> PushOutcome.Retry
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

    companion object {
        private const val FLAG_PRIVACY_NOTICE = "privacy_notice_seen"
        const val ATTACHMENTS_FOLDER = "attachments"
        private const val MAX_RENAME_TRIES = 5

        private fun String.extensionWithDot() = substringAfterLast('.', "").takeIf { it.length in 1..5 }?.let { ".$it" }.orEmpty()

        /** "attachments/foto.jpg", or "attachments/foto (2).jpg" if that's taken. Folders and hidden names are refused. */
        fun freeAttachmentPath(fileName: String, taken: Set<String>): String {
            val clean = fileName.substringAfterLast('/').trim().trimStart('.').ifBlank { "archivo" }
            val base = clean.substringBeforeLast('.', clean)
            val extension = clean.substringAfterLast('.', "").let { if (it.isEmpty() || it == clean) "" else ".$it" }
            return generateSequence(1) { it + 1 }
                .map { n -> "$ATTACHMENTS_FOLDER/" + (if (n == 1) base else "$base ($n)") + extension }
                .first { it.lowercase() !in taken }
        }

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun SystemClock.nowSeconds() = epochMillis() / 1000

    private fun String.byteSize() = toByteArray().size.toLong()
}
