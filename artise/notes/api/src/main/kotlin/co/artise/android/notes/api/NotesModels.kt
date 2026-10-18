/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.api

import io.element.android.libraries.matrix.api.core.RoomId

/** A chat whose notes the user can open. [tree] changes whenever anything in its notes changes. */
data class NotesChat(
    val roomId: RoomId,
    val name: String,
    val tree: String,
)

/** One file in a chat's notes. */
data class NotesFile(
    val path: String,
    /** The file's git blob hash; send it back as the base version when saving. */
    val version: String,
    val size: Long,
    /** Seconds since the epoch. */
    val modified: Long,
    /** Text opened with the note endpoints; anything else is a raw file (photo, PDF...). */
    val isNote: Boolean,
)

/** A chat's whole file list, identified by its [tree] hash. */
data class NotesTree(
    val tree: String,
    val files: List<NotesFile>,
)

/** A note's text at a given [version]. */
data class Note(
    val path: String,
    val version: String,
    val content: String,
)

/** What the server holds now, sent with `exists` and `conflict` errors. [content] is only there for notes. */
data class ServerCopy(
    val path: String,
    val version: String,
    val content: String?,
)

/** A successful note save. When [merged], [content] holds the merged text to show instead of the local one. */
data class SavedNote(
    val path: String,
    val version: String,
    val merged: Boolean,
    val content: String?,
)

/** A successful move. The notes in [linksUpdated] had their links to the old name rewritten. */
data class MovedNote(
    val from: String,
    val to: String,
    val version: String,
    val linksUpdated: List<String>,
)

/** A search hit: the file, and up to 3 matching lines. */
data class NotesSearchResult(
    val path: String,
    val lines: List<String>,
)

/** A link from a note. [path] is `null` when the target doesn't exist yet. */
data class OutgoingLink(
    val target: String,
    val path: String?,
)

/** Another note that links here, with the line the link is on. */
data class Backlink(
    val path: String,
    val line: String,
)

data class NoteLinks(
    val path: String,
    val outgoing: List<OutgoingLink>,
    val backlinks: List<Backlink>,
)

/** Every note and link in a chat, for the map. */
data class NotesGraph(
    val nodes: List<String>,
    val edges: List<Pair<String, String>>,
)

/** One saved version of a note. [by] is the person's name, "Ari", or "synced device". */
data class NoteVersion(
    val commit: String,
    val at: String,
    val message: String,
    val by: String,
)
