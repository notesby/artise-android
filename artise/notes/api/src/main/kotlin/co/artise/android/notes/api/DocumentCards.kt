/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject

/** A document of the chat's notes that Ari made or changed, shown under its message as a card to tap. */
@Immutable
data class DocumentCard(
    val path: String,
    /** The file's version when the message was sent; `null` when the server didn't say. */
    val version: String?,
    val title: String,
    val change: Change?,
    val isDraft: Boolean,
    /** The `kind` the server sent; a dashboard can't be told from its path. */
    val serverKind: String? = null,
) {
    val kind: NoteAttachment.Kind get() = NoteAttachment.kindOf(path, serverKind)

    enum class Change {
        CREATED,
        CHANGED,
    }
}

/**
 * Reads `co.artise.documents` from a message: the documents Ari created or changed in the turn the message answers
 * (see the Notes API docs). An edited message's newest content counts, as Ari edits progress messages into replies.
 */
object DocumentCards {
    const val FIELD = "co.artise.documents"
    const val MAX = 4

    /** The cards in [eventJsons] (the latest edit first, then the original), or none. */
    fun read(vararg eventJsons: String?): List<DocumentCard> {
        for (json in eventJsons) {
            if (json.isNullOrBlank() || FIELD !in json) continue
            val cards = runCatchingExceptions { parse(json) }.getOrNull().orEmpty()
            if (cards.isNotEmpty()) return cards
        }
        return emptyList()
    }

    private fun parse(eventJson: String): List<DocumentCard> {
        val root = Json.parseToJsonElement(eventJson).jsonObject
        val content = root["content"] as? JsonObject ?: root
        val newContent = content["m.new_content"] as? JsonObject
        val list = (newContent?.get(FIELD) ?: content[FIELD]) as? JsonArray ?: return emptyList()
        return list.mapNotNull { (it as? JsonObject)?.let(::card) }.distinctBy { it.path }.take(MAX)
    }

    private fun card(item: JsonObject): DocumentCard? {
        val path = item.string("path")?.trim()?.trim('/')?.takeIf { it.isNotEmpty() } ?: return null
        return DocumentCard(
            path = path,
            version = item.string("version"),
            title = item.string("title")?.trim()?.takeIf { it.isNotEmpty() } ?: path.substringAfterLast('/'),
            change = when (item.string("change")) {
                "created" -> DocumentCard.Change.CREATED
                "changed" -> DocumentCard.Change.CHANGED
                else -> null
            },
            isDraft = (item["draft"] as? JsonPrimitive)?.booleanOrNull == true,
            serverKind = item.string("kind"),
        )
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

/**
 * Ari posts each new chart's picture as an `m.image` whose `co.artise.note` is the `.chart` file: that message shows
 * as the chart's card. `null` for any other message.
 */
fun chartCardOf(eventJson: String?, fileName: String?): DocumentCard? {
    val path = NoteAttachment.read(eventJson)?.trim('/') ?: return null
    if (NoteAttachment.kindOf(path) != NoteAttachment.Kind.CHART) return null
    val title = fileName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() } ?: path.substringAfterLast('/').substringBeforeLast('.')
    return DocumentCard(path = path, version = null, title = title, change = null, isDraft = false, serverKind = "chart")
}

/** Draws a [DocumentCard]: the document's preview, its name, and whether it's new, changed or a draft. */
fun interface DocumentCardRenderer {
    @Composable
    fun Render(roomId: RoomId, card: DocumentCard, onClick: () -> Unit, modifier: Modifier)
}
