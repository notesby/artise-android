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
