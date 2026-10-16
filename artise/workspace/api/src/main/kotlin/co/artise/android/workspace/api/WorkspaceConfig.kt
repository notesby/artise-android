/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.api

import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.Flow

/**
 * The workspace's own settings, from the `co.artise.workspace` section of its `.well-known/matrix/client`.
 * Every field is optional: missing or invalid ones mean Artise's default.
 */
data class WorkspaceConfig(
    val name: String? = null,
    val logoUrl: String? = null,
    /** `0xFFRRGGBB`; only set when the server sent a valid `#RRGGBB`. */
    val accentColor: Long? = null,
    val assistantName: String? = null,
    /** Welcome text by language code (en, es, pt). */
    val welcome: Map<String, String> = emptyMap(),
    val language: String? = null,
    /** The features the workspace has, or null when it didn't say (then nothing is hidden). */
    val features: Set<String>? = null,
    val pushGateway: String? = null,
    /** The server's web pages and APIs; null when it has no web host (then no notes, no delete-account page). */
    val links: WorkspaceLinks? = null,
    /** The workspace's own sign-in clients; null when its sign-in service doesn't have them (then the app registers itself). */
    val oauth: WorkspaceOAuth? = null,
) {
    /** False only when the workspace lists its features and [feature] isn't one of them. */
    fun hasFeature(feature: String): Boolean = features?.contains(feature) ?: true

    /** The welcome text in [deviceLanguage], else in the workspace's language, else none. */
    fun welcomeFor(deviceLanguage: String): String? = welcome[deviceLanguage] ?: language?.let { welcome[it] }

    companion object {
        /** Artise's shared push gateway, for every workspace that doesn't name its own. */
        const val DEFAULT_PUSH_GATEWAY = "https://push.artise.co/_matrix/push/v1/notify"

        /** The push gateway to register with: the workspace's, if it's an https URL, else Artise's shared one. */
        fun pushGatewayFor(config: WorkspaceConfig?): String =
            config?.pushGateway?.takeIf { it.startsWith("https://") && it.length > "https://".length } ?: DEFAULT_PUSH_GATEWAY
    }
}

/**
 * Full URLs from the workspace file's `links`. The web host is a per-server setting: never derived from the server name, and a
 * workspace without `links` has no web pages (artise.co included).
 */
data class WorkspaceLinks(
    val web: String?,
    /** The Notes API base; the paths in family-wiki docs/notes-api.md are relative to it. */
    val notesApi: String?,
    val deleteAccount: String?,
)

/**
 * The workspace file's `oauth`: the sign-in service's clients for Artise's apps, registered ahead of time so it skips its consent page.
 * Only the release app can use them, since [redirectUri] opens only the app the App Link file names.
 */
data class WorkspaceOAuth(
    val androidClientId: String,
    /** An https App Link, e.g. https://join.artise.co/oauth. */
    val redirectUri: String,
)

/** The server part of an account's Matrix ID ("@ana:acme.artise.co" → "acme.artise.co"). */
fun SessionId.serverName(): String = value.substringAfter(':')

/** Each account's workspace settings, kept on the phone and refreshed from its server. */
interface WorkspaceConfigRepository {
    /** The settings saved for [sessionId], without network: null before the first fetch, or for a server without them. */
    fun cached(sessionId: SessionId): WorkspaceConfig?

    fun configFlow(sessionId: SessionId): Flow<WorkspaceConfig?>

    /** Fetches the account's settings again unless they're still fresh (the server's `max-age`), or always with [force]. */
    suspend fun refresh(sessionId: SessionId, force: Boolean = false)

    /** The settings of [serverName], for the sign-in screen (nothing saved). Null when it has none or can't be reached. */
    suspend fun fetch(serverName: String): WorkspaceConfig?

    /** Forgets the account's settings (at sign-out). */
    fun clear(sessionId: SessionId)
}
