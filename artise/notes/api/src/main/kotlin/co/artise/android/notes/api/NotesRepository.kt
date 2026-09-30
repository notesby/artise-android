/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.api

import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * A signed-in person's notes, offline-first: reads come from the phone's copy, edits are saved on the
 * phone at once and queued, and [sync] exchanges both ways with the server when it can.
 */
interface NotesRepository {
    /** Whether this account has seen the one-time "Ari can read notes" notice. */
    suspend fun hasSeenPrivacyNotice(): Boolean

    suspend fun markPrivacyNoticeSeen()

    /** Chats with notes, as last synced. */
    suspend fun cachedChats(): List<NotesChat>

    /** Asks the server which chats have notes, and forgets chats no longer listed. */
    suspend fun refreshChats(): Result<List<NotesChat>>

    suspend fun files(roomId: RoomId): List<LocalFile>

    suspend fun file(roomId: RoomId, path: String): LocalFile?

    /** Emits whenever the phone's copy of [roomId]'s notes changes: a local edit, a resolved choice, or a sync that changed files. */
    fun changes(roomId: RoomId): Flow<Unit>

    /** Starts a [sync] that keeps going if the screen that asked for it closes. Failures are silent: the queue stays for next time. */
    fun syncInBackground(roomId: RoomId)

    /** Sends queued edits in order, then pulls what changed. Safe to call often: an unchanged chat costs one `304`. */
    suspend fun sync(roomId: RoomId): Result<SyncReport>

    /** A live `co.artise.notes` state event said the chat's tree is now [tree]: syncs only if that differs from ours. */
    suspend fun onTreeChanged(roomId: RoomId, tree: String): Result<SyncReport?>

    /** Saves [content] on the phone and queues it. Several edits before a sync go out as one. */
    suspend fun editNote(roomId: RoomId, path: String, content: String)

    /** Creates a note on the phone and queues it. Fails with [NotesException.Exists] if the phone already has one there. */
    suspend fun createNote(roomId: RoomId, path: String, content: String): Result<Unit>

    /** Removes the file from the phone and queues its deletion. */
    suspend fun deleteFile(roomId: RoomId, path: String)

    /** Renames right away on the server; needs a connection and no unsent edits to that note. */
    suspend fun moveNote(roomId: RoomId, from: String, to: String): Result<MovedNote>

    /**
     * Adds a photo or file to the chat's `attachments/` folder, where Ari keeps files too, under a name not taken yet.
     * It's kept on the phone at once (so it shows offline) and uploaded with the next sync, before the note edits that
     * embed it. Returns the attachment's path.
     */
    suspend fun addAttachment(roomId: RoomId, fileName: String, bytes: ByteArray, contentType: String): Result<String>

    /** The photo or file at [path] as a file on the phone, downloaded once and kept for as long as it doesn't change. */
    suspend fun attachment(roomId: RoomId, path: String): Result<File>

    /** Unsent edits, including those waiting for the person to choose. */
    suspend fun edits(roomId: RoomId): List<PendingEdit>

    /** Photos and files added on this phone and not on the server yet, by path. [changes] tells when this changes. */
    suspend fun uploads(roomId: RoomId): Map<String, UploadStatus>

    /** Every photo and file in the chat's notes, with the notes that use each one (from the notes on the phone). */
    suspend fun media(roomId: RoomId): List<MediaFile>

    /** The photo or file's copy on the phone, if it's there already; never downloads. */
    suspend fun cachedAttachment(roomId: RoomId, path: String): File?

    /**
     * Deletes a photo or file. It needs a connection: the notes are refreshed and the server is asked which notes use
     * it, so nothing in use is deleted by mistake. When notes use it, it fails with [MediaInUseException], unless
     * [removeFromNotes], which first takes it out of those notes. A file not uploaded yet is just dropped.
     */
    suspend fun deleteMedia(roomId: RoomId, path: String, removeFromNotes: Boolean): Result<Unit>

    /** Tries a waiting, failing or refused upload again now. */
    suspend fun retryUpload(roomId: RoomId, path: String)

    /** Drops an upload not sent yet, and takes it out of the notes that embed it. */
    suspend fun cancelUpload(roomId: RoomId, path: String)

    /** For a [EditState.CONFLICT]: saves [content] (the person's choice or combination) over the server's version. */
    suspend fun resolveConflict(editId: Long, content: String)

    /** For a [EditState.DELETED]: saves this phone's copy again as a new note. */
    suspend fun keepDeletedNote(editId: Long)

    /** For an [EditState.EXISTS]: saves this phone's note under [newPath], keeping both. */
    suspend fun saveUnderNewName(editId: Long, newPath: String)

    /** Drops the edit and goes back to the server's copy. */
    suspend fun discardEdit(editId: Long)

    // Online-only reads.

    suspend fun search(roomId: RoomId, query: String): Result<List<NotesSearchResult>>

    suspend fun links(roomId: RoomId, path: String): Result<NoteLinks>

    suspend fun graph(roomId: RoomId): Result<NotesGraph>

    suspend fun history(roomId: RoomId, path: String): Result<List<NoteVersion>>

    /** A note as it was at [commit] from [history]. To restore it, pass its content to [editNote]. */
    suspend fun noteAt(roomId: RoomId, path: String, commit: String): Result<Note>
}
