/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.notifications.artise

import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * How much an Ari automation or reminder post must be seen, as the bot marks it inside the message content:
 * `"co.artise.automation": {"id": "…", "level": "normal|important|critical"}`.
 *
 * Important and critical posts go to the "Reminders" channel; critical ones also always make a sound, even in a chat whose
 * notifications are silent.
 */
enum class AutomationLevel {
    NORMAL,
    IMPORTANT,
    CRITICAL;

    /** Whether the post belongs on the "Reminders" channel. */
    val isReminder: Boolean get() = this != NORMAL

    companion object {
        const val CONTENT_FIELD = "co.artise.automation"

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * The level of the (decrypted) raw event [rawEvent], or `null` when it isn't an automation post, is still encrypted or
         * can't be read. An unknown level counts as [NORMAL], so a newer bot can't make an older app misbehave.
         */
        fun fromRawEvent(rawEvent: String?): AutomationLevel? {
            if (rawEvent.isNullOrBlank() || !rawEvent.contains(CONTENT_FIELD)) return null
            return runCatchingExceptions {
                val content = json.parseToJsonElement(rawEvent).asObject()?.get("content")?.asObject()
                val automation = content?.get(CONTENT_FIELD)?.asObject() ?: return@runCatchingExceptions null
                when (automation["level"]?.jsonPrimitive?.contentOrNull) {
                    "important" -> IMPORTANT
                    "critical" -> CRITICAL
                    else -> NORMAL
                }
            }.getOrNull()
        }

        private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
    }
}
