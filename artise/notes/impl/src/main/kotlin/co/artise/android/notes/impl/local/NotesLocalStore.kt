/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.local

import co.artise.android.notes.api.EditKind
import co.artise.android.notes.api.EditState
import co.artise.android.notes.api.LocalFile
import co.artise.android.notes.api.NotesChat
import co.artise.android.notes.api.NotesFile
import co.artise.android.notes.api.PendingEdit
import co.artise.android.notes.api.ServerCopy
import co.artise.android.notes.impl.db.NotesDatabase
import co.artise.android.notes.impl.db.NotesEditEntity
import co.artise.android.notes.impl.db.NotesFileEntity
import io.element.android.libraries.matrix.api.core.RoomId

/** A chat's cached tree hash and the ETag to send with the next tree request. */
data class CachedTree(val tree: String?, val etag: String?)

/**
 * The offline copy: chats, files and the edit queue, in the encrypted [NotesDatabase].
 * Plain reads and writes only; the rules of when to change what live in the sync engine.
 */
class NotesLocalStore(private val database: NotesDatabase) {
    private val queries = database.notesQueries

    fun <T> transaction(block: () -> T): T = database.transactionWithResult { block() }

    // Flags

    fun hasFlag(name: String): Boolean = queries.hasFlag(name).executeAsOne() > 0

    fun setFlag(name: String) = queries.setFlag(name)

    // Chats

    fun chats(): List<NotesChat> = queries.selectChats().executeAsList().map { NotesChat(RoomId(it.room_id), it.name, it.tree.orEmpty()) }

    fun cachedTree(roomId: RoomId): CachedTree? = queries.selectChat(roomId.value).executeAsOneOrNull()?.let { CachedTree(it.tree, it.etag) }

    /** Keeps exactly [chats]: adds new ones, renames, and forgets chats no longer listed with all their files and edits. */
    fun replaceChats(chats: List<NotesChat>) = transaction {
        val keep = chats.map { it.roomId.value }.toSet()
        queries.selectChats().executeAsList().map { it.room_id }.filterNot { it in keep }.forEach { forgetChat(RoomId(it)) }
        chats.forEach {
            queries.insertChatIfMissing(it.roomId.value, it.name)
            queries.renameChat(it.name, it.roomId.value)
        }
    }

    /** Saves what we pulled. A chat synced before the chat list was loaded gets its row now; its name comes with the list. */
    fun saveTree(roomId: RoomId, tree: String, etag: String?) = transaction {
        queries.insertChatIfMissing(roomId.value, "")
        queries.updateChatTree(tree, etag, roomId.value)
    }

    /** Clears the ETag so the next tree request downloads the whole list again. */
    fun forgetEtag(roomId: RoomId) {
        val cached = cachedTree(roomId) ?: return
        queries.updateChatTree(cached.tree, null, roomId.value)
    }

    private fun forgetChat(roomId: RoomId) {
        queries.deleteChatEdits(roomId.value)
        queries.deleteChatFiles(roomId.value)
        queries.deleteChat(roomId.value)
    }

    // Files

    fun files(roomId: RoomId): List<LocalFile> {
        val edited = queries.selectEdits(roomId.value).executeAsList().map { it.path }.toSet()
        return queries.selectFiles(roomId.value).executeAsList().map { it.toLocalFile(it.path in edited) }
    }

    fun file(roomId: RoomId, path: String): LocalFile? =
        queries.selectFile(roomId.value, path).executeAsOneOrNull()?.toLocalFile(hasEdits(roomId, path))

    fun putFile(roomId: RoomId, file: NotesFile, content: String?) =
        queries.upsertFile(roomId.value, file.path, file.version, file.size, file.modified, if (file.isNote) 1 else 0, content)

    fun setContent(roomId: RoomId, path: String, content: String, modified: Long) =
        queries.updateFileContent(content, content.toByteArray().size.toLong(), modified, roomId.value, path)

    fun setVersion(roomId: RoomId, path: String, version: String) = queries.updateFileVersion(version, roomId.value, path)

    fun renameFile(roomId: RoomId, from: String, to: String) = queries.renameFile(to_path = to, room_id = roomId.value, from_path = from)

    fun deleteFile(roomId: RoomId, path: String) = queries.deleteFile(roomId.value, path)

    // Edits

    fun edits(roomId: RoomId): List<PendingEdit> = queries.selectEdits(roomId.value).executeAsList().map { it.toPendingEdit() }

    fun pendingEdits(roomId: RoomId): List<PendingEdit> = queries.selectPendingEdits(roomId.value).executeAsList().map { it.toPendingEdit() }

    fun edit(id: Long): Pair<RoomId, PendingEdit>? = queries.selectEdit(id).executeAsOneOrNull()?.let { RoomId(it.room_id) to it.toPendingEdit() }

    fun baseVersion(id: Long): String? = queries.selectEdit(id).executeAsOneOrNull()?.base_version

    fun pendingSave(roomId: RoomId, path: String): PendingEdit? = queries.selectPendingSave(roomId.value, path).executeAsOneOrNull()?.toPendingEdit()

    fun hasEdits(roomId: RoomId, path: String): Boolean = queries.countEditsForPath(roomId.value, path).executeAsOne() > 0

    fun waitingEditCount(roomId: RoomId): Int = queries.countWaitingEdits(roomId.value).executeAsOne().toInt()

    fun addEdit(roomId: RoomId, kind: EditKind, path: String, content: String?, baseVersion: String?, now: Long): Long = transaction {
        queries.insertEdit(roomId.value, kind.dbValue, path, content, baseVersion, now)
        queries.lastInsertedId().executeAsOne()
    }

    fun addUpload(roomId: RoomId, path: String, localFile: String, contentType: String, now: Long): Long = transaction {
        queries.insertUpload(roomId.value, path, localFile, now, contentType)
        queries.lastInsertedId().executeAsOne()
    }

    fun contentType(id: Long): String? = queries.contentType(id).executeAsOneOrNull()?.content_type

    fun renameEdit(id: Long, path: String) = queries.renameEdit(path, id)

    fun setEditContent(id: Long, content: String) = queries.updateEditContent(content, id)

    fun markEdit(id: Long, state: EditState, serverCopy: ServerCopy? = null, error: String? = null) =
        queries.markEdit(state.dbValue, serverCopy?.version, serverCopy?.content, error, id)

    fun requeueEdit(id: Long, path: String, content: String?, baseVersion: String?) = queries.requeueEdit(path, content, baseVersion, id)

    fun deleteEdit(id: Long) = queries.deleteEdit(id)
}

private fun NotesFileEntity.toLocalFile(hasLocalEdits: Boolean) = LocalFile(
    path = path,
    version = version,
    size = size,
    modified = modified,
    isNote = is_note != 0L,
    content = content,
    hasLocalEdits = hasLocalEdits,
)

private fun NotesEditEntity.toPendingEdit() = PendingEdit(
    id = id,
    path = path,
    kind = EditKind.entries.first { it.dbValue == kind },
    state = EditState.entries.first { it.dbValue == state },
    content = content,
    serverCopy = server_version?.let { ServerCopy(path, it, server_content) },
    error = error,
)

private val EditKind.dbValue get() = name.lowercase()

private val EditState.dbValue get() = name.lowercase()
