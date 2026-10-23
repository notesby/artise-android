/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.api

import io.element.android.libraries.matrix.api.core.RoomId
import java.io.File

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
    /** What the server says the file is (`note`, `chart`, `dashboard`...); `null` from older servers. */
    val kind: String? = null,
    /** A note's or dashboard's front matter `title`, a chart's `title`; `null` when there's none. */
    val title: String? = null,
    /** A page Ari rewrites each period (a business report): offer Share, never Edit. */
    val isReadOnly: Boolean = false,
)

/** A chat's whole file list, identified by its [tree] hash. */
data class NotesTree(
    val tree: String,
    val files: List<NotesFile>,
    /** Every folder on the server, empty ones included ("Recetas", "Recetas/Postres"). */
    val folders: List<String> = emptyList(),
)

/** A folder moved or renamed on the server: where each file inside went, and the notes whose links changed. */
data class MovedFolder(
    val from: String,
    val to: String,
    val moved: List<MovedNote>,
    val linksUpdated: List<String>,
)

/** A folder deleted on the server, with how many notes and files went with it. */
data class DeletedFolder(val path: String, val notes: Int, val files: Int)

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

/**
 * A link from a note. [path] is `null` when the target doesn't exist yet, unless [candidates] names two or more
 * notes that share that name: then the link is ambiguous, and the person chooses before anything opens.
 */
data class OutgoingLink(
    val target: String,
    val path: String?,
    val candidates: List<String> = emptyList(),
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

/**
 * One page of a chat's notes: a person, place or thing with its own note in a folder the chat's `SCHEMA.md` marks
 * `(pages)`, or a photo or document. [title] and [aliases] are what `[[links]]` may call it, besides its file name.
 */
data class NotesPage(
    val path: String,
    val title: String,
    /** `person`, `place`, `thing`, `client`, `image`, `document`... as the server names it. */
    val type: String,
    /** The `(pages)` folder it lives in, without the slash; empty for photos and documents. */
    val folder: String,
    val aliases: List<String>,
    /** One line about who or what it is; may be empty. */
    val summary: String,
    /** `true` for a note, `false` for a photo or document. */
    val isNote: Boolean,
)

/** Every note and link in a chat, for the map. */
data class NotesGraph(
    val nodes: List<String>,
    val edges: List<Pair<String, String>>,
)

/** One saved version of a note: when, the server's description, and who it was for and how it was made. */
data class NoteVersion(
    val commit: String,
    /** ISO 8601 with the server's offset, e.g. "2026-09-29T18:02:11-06:00". */
    val at: String,
    val message: String,
    val author: ChangeAuthor,
)

/** Who a change in a chat's notes was for and how it was made, as the server records it (docs/notes-api.md). */
data class ChangeAuthor(
    /** The person's display name; `null` for Ari on its own and synced devices. */
    val who: String?,
    /** ari, automation, routine, brief, ari_own, reaction, chat, app, files or sync; `null` from older servers. */
    val how: String?,
    /** Which automation or routine, e.g. "#3"; empty when there's none. */
    val detail: String,
    /** The plain name older servers send: the person's name, "Ari", or "synced device". The fallback for unknown [how]s. */
    val by: String,
)

/** One change to a chat's notes and the files it touched. */
data class NotesChange(
    val commit: String,
    /** ISO 8601 with the server's offset. */
    val at: String,
    val message: String,
    val author: ChangeAuthor,
    val files: List<ChangedFile>,
)

/** A file a change touched; [from] is the old path of a renamed or copied file. */
data class ChangedFile(
    val path: String,
    val change: FileChange,
    val from: String?,
)

enum class FileChange {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
    COPIED,
}

/** A page of a chat's changes, newest first; [more] means older ones can be asked for. */
data class NotesChangesPage(
    val changes: List<NotesChange>,
    val more: Boolean,
)

/** What a file looks like before opening it, from the server's `/preview`: a small picture, the first lines, or nothing. */
sealed interface NotesPreview {
    /** A PDF's first page or a photo scaled down, as a PNG on the phone; [width] and [height] in pixels. */
    data class Image(val file: File, val width: Int, val height: Int) : NotesPreview

    /** The first lines of a text file. */
    data class Text(val text: String) : NotesPreview

    /** No preview for this kind of file (Office files, audio...), or none could be made. */
    data object None : NotesPreview
}

/** A chart as the server reads it ([spec]) and draws it ([svg], for the asked light or dark). */
data class NotesChart(
    val path: String,
    val version: String,
    val spec: ChartSpec,
    /** The day a chart that reads records was drawn for ("2026-10-22"); empty for a chart with its own numbers. */
    val asOf: String,
    val svg: String,
)

/** What `GET chart` says a chart is: its title and type, and the rows it shows now. */
data class ChartSpec(
    val title: String,
    val type: String,
    val columns: List<String>,
    val rows: List<List<String>>,
    val series: List<String>,
    val unit: String,
    val caption: String,
    val colors: List<String>,
    val compare: String,
)

/** A kind of record the chat keeps (`GET records`): what a sourced chart may read, and the fields it may name. */
data class RecordType(
    val type: String,
    val fields: List<String>,
    /** The field that dates a record, if any: what `x: month` and periods go by. */
    val dateField: String?,
    /** The field holding its amount, if any: the likely `sum(...)`. */
    val amountField: String?,
)

/** A file the server made to share (a chart's PNG, a PDF), on the phone, under [name]. */
data class NotesExport(val file: File, val name: String, val mimeType: String)

enum class ExportFormat(val value: String, val mimeType: String) {
    PNG("png", "image/png"),
    PDF("pdf", "application/pdf"),
}

/** A document's text for searching, page by page; [version] changes when the file does. */
data class NotesText(val path: String, val version: String, val pages: List<NotesTextPage>)

/** One page of text; [n] from 1, as the viewer shows pages. A scanned page with no text layer is empty. */
data class NotesTextPage(val n: Int, val text: String)
