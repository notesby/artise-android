/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Wire shapes of the Notes API v1, named as in docs/notes-api.md.

@Serializable
internal data class ChatsDto(val user: String, val chats: List<ChatDto>)

@Serializable
internal data class ChatDto(val id: String, val name: String, val tree: String)

@Serializable
internal data class TreeDto(val tree: String, val files: List<FileDto>, val folders: List<String> = emptyList())

@Serializable
internal data class FileDto(
    val path: String,
    val version: String,
    val size: Long,
    val modified: Long,
    val note: Boolean,
    val kind: String? = null,
    val title: String? = null,
    val readonly: Boolean = false,
)

@Serializable
internal data class NoteDto(val path: String, val version: String, val content: String)

/** `base_version` has no default so it is always sent, as `null` when creating a note. */
@Serializable
internal data class SaveNoteDto(
    val path: String,
    val content: String,
    @SerialName("base_version") val baseVersion: String?,
)

@Serializable
internal data class SavedDto(
    val path: String,
    val version: String,
    val merged: Boolean = false,
    val content: String? = null,
)

@Serializable
internal data class MoveDto(
    val from: String,
    val to: String,
    @SerialName("base_version") val baseVersion: String,
)

@Serializable
internal data class MovedDto(
    val from: String,
    val to: String,
    val version: String,
    @SerialName("links_updated") val linksUpdated: List<String> = emptyList(),
)

@Serializable
internal data class SearchDto(val q: String, val results: List<SearchResultDto>)

@Serializable
internal data class SearchResultDto(val path: String, val lines: List<String> = emptyList())

@Serializable
internal data class LinksDto(
    val path: String,
    val outgoing: List<OutgoingDto> = emptyList(),
    val backlinks: List<BacklinkDto> = emptyList(),
)

@Serializable
internal data class OutgoingDto(val target: String, val path: String? = null, val candidates: List<String> = emptyList())

@Serializable
internal data class PagesDto(val pages: List<PageDto> = emptyList())

@Serializable
internal data class PageDto(
    val path: String,
    val title: String,
    val type: String = "",
    val folder: String = "",
    val aliases: List<String> = emptyList(),
    val summary: String = "",
    val note: Boolean = true,
)

@Serializable
internal data class BacklinkDto(val path: String, val line: String)

@Serializable
internal data class GraphDto(val nodes: List<String>, val edges: List<List<String>>)

@Serializable
internal data class HistoryDto(val path: String, val versions: List<VersionDto>)

@Serializable
internal data class VersionDto(
    val commit: String,
    val at: String,
    val message: String,
    val by: String = "",
    val who: String? = null,
    val how: String? = null,
    val detail: String = "",
)

@Serializable
internal data class ChangesDto(val changes: List<ChangeDto> = emptyList(), val more: Boolean = false)

@Serializable
internal data class ChangeDto(
    val commit: String,
    val at: String,
    val message: String,
    val by: String = "",
    val who: String? = null,
    val how: String? = null,
    val detail: String = "",
    val files: List<ChangedFileDto> = emptyList(),
)

@Serializable
internal data class ChangedFileDto(val path: String, val change: String, val from: String? = null)

@Serializable
internal data class ErrorDto(
    val error: String,
    val message: String? = null,
    val current: CurrentDto? = null,
    /** For `in_use`: the notes that use the file. */
    @SerialName("used_by") val usedBy: List<String> = emptyList(),
    /** For `not_empty`: what a folder holds. */
    val files: Int = 0,
    val folders: Int = 0,
)

@Serializable
internal data class FolderDto(val path: String)

@Serializable
// No default for [folder]: the encoder leaves default values out, and the server needs it.
internal data class MoveFolderDto(val from: String, val to: String, val folder: Boolean)

@Serializable
internal data class MovedFileDto(val from: String, val to: String, val version: String)

@Serializable
internal data class MovedFolderDto(
    val from: String,
    val to: String,
    val moved: List<MovedFileDto> = emptyList(),
    @SerialName("links_updated") val linksUpdated: List<String> = emptyList(),
)

@Serializable
internal data class DeletedFolderDto(val path: String, val notes: Int = 0, val files: Int = 0)

@Serializable
internal data class CurrentDto(val path: String, val version: String, val content: String? = null)

@Serializable
internal data class TextDto(val path: String, val version: String = "", val pages: List<TextPageDto> = emptyList())

@Serializable
internal data class TextPageDto(val n: Int, val text: String = "")

@Serializable
internal data class ChartDto(
    val path: String,
    val version: String = "",
    val spec: ChartSpecDto,
    @SerialName("as_of") val asOf: String? = null,
    val svg: String,
)

/** Rows come as JSON values (numbers, text, null): each is kept as its text. */
@Serializable
internal data class ChartSpecDto(
    val title: String = "",
    val type: String = "",
    val columns: List<String> = emptyList(),
    val rows: List<List<JsonElement>> = emptyList(),
    val series: List<String> = emptyList(),
    val unit: String? = null,
    val caption: String? = null,
    val colors: List<String> = emptyList(),
    val compare: String? = null,
)
