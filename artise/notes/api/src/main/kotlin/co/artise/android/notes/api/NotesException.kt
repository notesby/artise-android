/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.api

/**
 * Every way a Notes API call can fail, one per error code in the contract (docs/notes-api.md in family-wiki).
 * [message] is the server's human text when it sent one.
 */
sealed class NotesException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No valid sign-in, even after asking for a fresh OpenID token. */
    class Unauthorized(message: String) : NotesException(message)

    /** Not a member of the chat, or it has no notes. */
    class NoChat(message: String) : NotesException(message)

    /** A hidden path, outside the chat's folder, or not a note type. */
    class BadPath(message: String) : NotesException(message)

    class NotFound(message: String) : NotesException(message)

    /** Creating a file that's already there. */
    class Exists(message: String, val current: ServerCopy?) : NotesException(message)

    /** Changed on the server meanwhile, and the same lines were edited. */
    class Conflict(message: String, val current: ServerCopy?) : NotesException(message)

    /** Deleted on the server meanwhile. */
    class Deleted(message: String) : NotesException(message)

    /** Over 1 MB for a note, 50 MB for a file. */
    class TooBig(message: String) : NotesException(message)

    /** The server keeps a photo or file that notes use ([usedBy]); take it out of them first. */
    class InUse(message: String, val usedBy: List<String>) : NotesException(message)

    /** A photo or document: use the raw endpoints. */
    class NotANote(message: String) : NotesException(message)

    /** No connection, or the request didn't complete. Safe to retry later. */
    class Network(cause: Throwable) : NotesException(cause.message ?: "Network error", cause)

    /** Anything the contract doesn't name, such as a 500. */
    class Server(val status: Int, message: String) : NotesException(message)
}
