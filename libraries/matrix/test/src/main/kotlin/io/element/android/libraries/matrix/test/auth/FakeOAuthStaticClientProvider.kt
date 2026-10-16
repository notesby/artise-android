/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.test.auth

import io.element.android.libraries.matrix.api.auth.OAuthStaticClient
import io.element.android.libraries.matrix.api.auth.OAuthStaticClientProvider

class FakeOAuthStaticClientProvider(
    private val client: OAuthStaticClient? = null,
) : OAuthStaticClientProvider {
    override var pendingRedirectUri: String? = null
        private set

    override suspend fun get(serverName: String, homeserverUrl: String): OAuthStaticClient? =
        client.also { pendingRedirectUri = it?.redirectUri }

    /** As if [get] had returned a client with [redirectUri]. */
    fun givenPendingRedirectUri(redirectUri: String?) {
        pendingRedirectUri = redirectUri
    }
}
