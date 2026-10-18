/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.auth

/**
 * Artise: a sign-in service client registered ahead of time for a server, used instead of registering the app on each sign-in.
 * The sign-in service trusts such a client, so it skips its consent page.
 */
data class OAuthStaticClient(
    /** The sign-in service, as its metadata names it (e.g. "https://auth.acme.artise.co/"). */
    val issuer: String,
    val clientId: String,
    /** Where the sign-in service sends the result (an https App Link), instead of the app's custom scheme. */
    val redirectUri: String,
)

/** Artise: finds the [OAuthStaticClient] of the server being signed in to. */
interface OAuthStaticClientProvider {
    /**
     * The client for [serverName] (e.g. "acme.artise.co"), whose homeserver is at [homeserverUrl], or null to register the app on
     * sign-in as before. Remembers its redirect URI as [pendingRedirectUri].
     */
    suspend fun get(serverName: String, homeserverUrl: String): OAuthStaticClient?

    /** The redirect URI of the last client [get] returned, so its result is recognised; null when sign-in uses the custom scheme. */
    val pendingRedirectUri: String?
}
