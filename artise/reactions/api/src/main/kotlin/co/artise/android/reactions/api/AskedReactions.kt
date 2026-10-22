/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.reactions.api

import androidx.annotation.StringRes
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * The reactions a message asks for.
 *
 * Ari marks a message that waits for a reaction with `co.artise.reactions`, the emojis it answers to in the
 * order to show them, and `co.artise.proposal` when the message is a proposal (approve with ✅, decline with ❌).
 * Both keys sit in the event content, so an encrypted chat delivers them decrypted with the rest.
 */
data class AskedReactions(
    /** The emojis to put first in the quick-reaction bar, as the server wrote them (no variation selector). */
    val emojis: List<String>,
    /** True when the message is a proposal, whatever its kind. */
    val isProposal: Boolean,
) {
    companion object {
        const val REACTIONS_FIELD = "co.artise.reactions"
        const val PROPOSAL_FIELD = "co.artise.proposal"

        private const val VARIATION_SELECTOR = "️"
        private const val APPROVE = "✅"
        private const val DECLINE = "❌"

        /**
         * Reads the asked reactions from an event's JSON, or null when the message asks for nothing.
         *
         * The keys are looked up in `content`, or at the root when the JSON is the content itself.
         * A malformed `co.artise.reactions` (not a list, or a list with something other than strings) is ignored.
         */
        fun read(eventJson: String?): AskedReactions? {
            if (eventJson.isNullOrBlank()) return null
            val root = runCatchingExceptions { Json.parseToJsonElement(eventJson).jsonObject }.getOrNull() ?: return null
            val content = root["content"] as? JsonObject ?: root
            val emojis = (content[REACTIONS_FIELD] as? JsonArray)?.strings().orEmpty()
            val isProposal = (content[PROPOSAL_FIELD] as? JsonPrimitive)?.takeIf { it.isString }?.content?.isNotBlank() == true
            if (emojis.isEmpty() && !isProposal) return null
            return AskedReactions(emojis = emojis, isProposal = isProposal)
        }

        /**
         * The quick-reaction bar: the asked emojis first, in their order, then [others] without the ones already shown.
         * An emoji with and without the variation selector counts as the same emoji.
         */
        fun quickBar(asked: List<String>, others: List<String>): List<String> {
            val shown = asked.map { it.plain() }.toMutableSet()
            return asked.distinctBy { it.plain() } + others.filter { shown.add(it.plain()) }
        }

        /** "Approve" for ✅ and "Decline" for ❌ on a proposal; null for every other emoji or when not a proposal. */
        @StringRes
        fun proposalLabel(emoji: String, isProposal: Boolean): Int? {
            if (!isProposal) return null
            return when (emoji.plain()) {
                APPROVE -> R.string.artise_reaction_approve
                DECLINE -> R.string.artise_reaction_decline
                else -> null
            }
        }

        private fun JsonArray.strings(): List<String>? {
            val values = map { element ->
                val primitive = element as? JsonPrimitive ?: return null
                if (!primitive.isString) return null
                primitive.content
            }
            return values.filter { it.isNotBlank() }
        }

        private fun String.plain(): String = replace(VARIATION_SELECTOR, "")
    }
}
