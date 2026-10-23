/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.api

import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * A file or photo message that is the notes' own copy of a file.
 *
 * When Ari posts a document from the notes, or a PDF it made and saved there, the message content carries
 * `co.artise.note`: the file's path in the chat's notes. The app opens that path through the Notes API, so the
 * person sees the current version, instead of downloading the attachment.
 */
object NoteAttachment {
    const val FIELD = "co.artise.note"

    /** What opens a file of the notes. */
    enum class Kind {
        /** Markdown or plain text: the notes editor. */
        NOTE,
        PDF,
        IMAGE,

        /** A CSV or TSV file: read in the app as text. */
        TABLE,

        /** A `.chart` file (YAML) the server draws: the chart screen. */
        CHART,

        /** A note whose front matter says `type: dashboard` (or `type: report`): the server draws the page. Only the server, a card or the tree can say so. */
        DASHBOARD,

        /** Word, Excel or PowerPoint: read in the app as the server's PDF conversion. */
        OFFICE,

        /** No viewer or editor in the app (audio, archives...): the file downloads as any attachment. */
        OTHER,
    }

    private val noteExtensions = setOf("md", "txt")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "avif", "bmp")
    private val officeExtensions = setOf("docx", "doc", "odt", "rtf", "xlsx", "xls", "ods", "pptx", "ppt", "odp")

    /** The notes path of the file the event's JSON carries, or null for an ordinary attachment. */
    fun read(eventJson: String?): String? {
        if (eventJson.isNullOrBlank()) return null
        val root = runCatchingExceptions { Json.parseToJsonElement(eventJson).jsonObject }.getOrNull() ?: return null
        val content = root["content"] as? JsonObject ?: root
        val value = content[FIELD] as? JsonPrimitive ?: return null
        return value.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * What the file is by its name alone. A dashboard is a `.md` and reads as [Kind.NOTE] here: where the server said the
     * kind (a card, the tree), use [kindOf] with it.
     */
    fun kindOf(path: String): Kind = when (path.substringAfterLast('.', "").lowercase()) {
        in noteExtensions -> Kind.NOTE
        "pdf" -> Kind.PDF
        in imageExtensions -> Kind.IMAGE
        "csv", "tsv" -> Kind.TABLE
        "chart" -> Kind.CHART
        in officeExtensions -> Kind.OFFICE
        else -> Kind.OTHER
    }

    /** The kind the server said (`chart`, `dashboard`...), else what the name says. */
    fun kindOf(path: String, serverKind: String?): Kind = fromServer(serverKind) ?: kindOf(path)

    /** The server's name for a kind, as in `co.artise.documents` and `GET tree`; `null` when missing or unknown. */
    fun fromServer(kind: String?): Kind? = when (kind) {
        "note" -> Kind.NOTE
        "table" -> Kind.TABLE
        "chart" -> Kind.CHART
        "dashboard" -> Kind.DASHBOARD
        "pdf" -> Kind.PDF
        "image" -> Kind.IMAGE
        "office" -> Kind.OFFICE
        "other" -> Kind.OTHER
        else -> null
    }
}
