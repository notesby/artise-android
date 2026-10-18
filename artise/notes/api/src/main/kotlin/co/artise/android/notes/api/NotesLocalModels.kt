/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.api

/** A file as the phone has it: the server's copy, plus any edit not sent yet. */
data class LocalFile(
    val path: String,
    /** The server version this copy is based on; empty for a note created offline and not sent yet. */
    val version: String,
    val size: Long,
    val modified: Long,
    val isNote: Boolean,
    /** The text for notes, `null` for raw files and for notes not downloaded yet. */
    val content: String?,
    /** An edit to this file is waiting to be sent or for the person to choose. */
    val hasLocalEdits: Boolean,
)

enum class EditKind {
    SAVE,
    DELETE,

    /** A photo or file added on this phone, waiting to be uploaded. */
    UPLOAD,
}

enum class EditState {
    /** Waiting to be sent. */
    PENDING,

    /** Someone changed the same lines meanwhile: show both, let the person choose or combine. */
    CONFLICT,

    /** Someone deleted the note meanwhile: keep this copy, or drop it. */
    DELETED,

    /** A note with this name appeared meanwhile: keep both under different names. */
    EXISTS,

    /** The server refused it for good (too big, not allowed); [PendingEdit.error] says why. */
    REJECTED,
}

/** Where a photo or file added on this phone is on its way to the server. No status: it's on the server. */
enum class UploadStatus {
    /** Queued: it goes up with the next sync that has a connection. */
    WAITING,

    /** Being sent right now. */
    UPLOADING,

    /** The last try failed on the server's side; it's tried again with the next sync. */
    RETRYING,

    /** The server refused it for good (too big, a bad name): it won't be sent. */
    FAILED,
}

/** A photo or file in a chat's notes, and the notes that use it (embedded or linked). */
data class MediaFile(
    val path: String,
    val size: Long,
    val modified: Long,
    /** The notes that show or link it; empty when no note uses it. */
    val usedBy: List<String>,
    /** Added on this phone and not on the server yet. */
    val upload: UploadStatus?,
)

/** A photo or file wasn't deleted because notes use it: [usedBy] says which. */
class MediaInUseException(val usedBy: List<String>) : Exception("Used by ${usedBy.size} notes")

/** An edit made on this phone that hasn't reached the server. */
data class PendingEdit(
    val id: Long,
    val path: String,
    val kind: EditKind,
    val state: EditState,
    /** The text this phone wants to save, for [EditKind.SAVE]. */
    val content: String?,
    /** The server's copy, for [EditState.CONFLICT] and [EditState.EXISTS]. */
    val serverCopy: ServerCopy?,
    val error: String?,
)

/** What one sync of a chat did. */
data class SyncReport(
    /** Edits the server accepted. */
    val sent: Int,
    /** Edits now waiting for the person to choose. */
    val needChoice: Int,
    /** Files downloaded or updated from the server. */
    val updated: Int,
    /** Files removed because they're gone from the server. */
    val removed: Int,
    /** Edits the server couldn't take this time (e.g. an error on its side); they stay queued for the next sync. */
    val failed: Int = 0,
)
