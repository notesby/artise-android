/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl

import co.artise.android.workspace.api.WorkspaceConfig
import co.artise.android.workspace.api.WorkspaceLinks
import co.artise.android.workspace.api.WorkspaceOAuth
import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads Artise's `co.artise.workspace` section of a `.well-known/matrix/client` file.
 *
 * Field by field and forgiving: a wrong or missing field is skipped, unknown fields are ignored, and a newer `version` is read for the
 * fields this app knows. Branding must never stop someone from signing in.
 */
object WorkspaceFileParser {
    const val SECTION = "co.artise.workspace"

    private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")
    private const val OPAQUE = 0xFF000000L
    private const val MAX_PALETTE = 8

    /** The section in [wellKnown] (the whole file), or null when it has none or isn't JSON. */
    fun parse(wellKnown: String): WorkspaceConfig? {
        val file = runCatchingExceptions { Json.parseToJsonElement(wellKnown) }.getOrNull() as? JsonObject ?: return null
        return parseSection(file[SECTION] as? JsonObject ?: return null)
    }

    fun parseSection(section: JsonObject): WorkspaceConfig = WorkspaceConfig(
        name = section.string("name"),
        logoUrl = section.string("logo")?.takeIf { it.startsWith("https://") },
        accentColor = (section["colors"] as? JsonObject)?.string("accent")?.let(::parseColor),
        chartPalette = ((section["colors"] as? JsonObject)?.get("palette") as? JsonArray)
            ?.mapNotNull { it.stringOrNull()?.let(::parseColor) }
            ?.take(MAX_PALETTE)
            .orEmpty(),
        assistantName = section.string("assistant_name"),
        welcome = (section["welcome"] as? JsonObject)
            ?.mapNotNull { (language, text) -> text.stringOrNull()?.let { language to it } }
            ?.toMap()
            .orEmpty(),
        language = section.string("language"),
        features = (section["features"] as? JsonArray)?.mapNotNull { it.stringOrNull() }?.toSet(),
        pushGateway = section.string("push_gateway"),
        links = (section["links"] as? JsonObject)?.let { links ->
            WorkspaceLinks(
                web = links.httpsUrl("web"),
                notesApi = links.httpsUrl("notes_api"),
                deleteAccount = links.httpsUrl("delete_account"),
                help = links.httpsUrl("help"),
            )
        },
        oauth = (section["oauth"] as? JsonObject)?.let { oauth ->
            val clientId = oauth.string("android") ?: return@let null
            val redirectUri = oauth.httpsUrl("redirect_uri") ?: return@let null
            WorkspaceOAuth(androidClientId = clientId, redirectUri = redirectUri)
        },
        assistantUserId = section.string("assistant_user_id")?.takeIf { it.startsWith("@") && ':' in it },
        contactUserId = section.string("contact_user_id")?.takeIf { it.startsWith("@") && ':' in it },
        minAndroidVersionCode = (section["min_app_version"] as? JsonObject)?.get("android")
            ?.let { (it as? JsonPrimitive)?.takeIf { primitive -> !primitive.isString }?.content?.toLongOrNull() },
        androidStoreLink = (section["store_links"] as? JsonObject)?.httpsUrl("android"),
    )

    /** `#RRGGBB` as `0xFFRRGGBB`; anything else (names, `#RGB`, alpha) is refused. */
    fun parseColor(value: String): Long? = value.takeIf { HEX_COLOR.matches(it) }?.drop(1)?.toLong(16)?.or(OPAQUE)

    private fun JsonObject.string(key: String): String? = this[key]?.stringOrNull()

    private fun JsonObject.httpsUrl(key: String): String? = string(key)?.takeIf { it.startsWith("https://") }

    private fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
}
