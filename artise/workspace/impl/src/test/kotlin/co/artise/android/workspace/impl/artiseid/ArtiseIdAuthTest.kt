/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.artiseid

import co.artise.android.workspace.impl.artiseid.ArtiseIdTestServer.Companion.DISCOVERY
import co.artise.android.workspace.impl.artiseid.ArtiseIdTestServer.Companion.REDIRECT
import co.artise.android.workspace.impl.artiseid.ArtiseIdTestServer.Companion.TOKEN
import co.artise.android.workspace.impl.artiseid.ArtiseIdTestServer.Companion.WORKSPACES
import co.artise.android.workspace.impl.artiseid.ArtiseIdTestServer.Companion.WORKSPACES_TWO
import co.artise.android.workspace.impl.artiseid.ArtiseIdTestServer.Companion.json
import com.google.common.truth.Truth.assertThat
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Test

class ArtiseIdAuthTest {
    private val server = ArtiseIdTestServer().apply { givenArtiseId() }
    private val clock = FakeSystemClock()
    private val store = InMemoryArtiseIdTokenStore()

    @After
    fun tearDown() = server.close()

    private fun TestScope.auth(): Pair<ArtiseIdAuth, ArtiseIdConfigClient> {
        val dispatchers = testCoroutineDispatchers()
        val configClient = ArtiseIdConfigClient(server.okHttpClient, clock, dispatchers)
        return ArtiseIdAuth(configClient, store, server.okHttpClient, clock, dispatchers, backgroundScope) to configClient
    }

    private suspend fun ArtiseIdConfigClient.config() = requireNotNull(get())

    /** The sign-in page is the issuer's authorization endpoint, with PKCE S256, the redirect, and the provider's scope to open it directly. */
    @Test
    fun `the sign-in page goes straight to the provider`() = runTest {
        val (auth, configClient) = auth()
        val url = requireNotNull(auth.authorizationUrl(configClient.config(), ArtiseIdProvider.GOOGLE)).toHttpUrl()
        assertThat(url.host).isEqualTo("id.artise.co")
        assertThat(url.encodedPath).isEqualTo("/oauth/v2/authorize")
        assertThat(url.queryParameter("response_type")).isEqualTo("code")
        assertThat(url.queryParameter("client_id")).isEqualTo("395423549856219219")
        assertThat(url.queryParameter("redirect_uri")).isEqualTo(REDIRECT)
        assertThat(url.queryParameter("scope")).isEqualTo("openid profile email urn:zitadel:iam:org:idp:id:g-idp")
        assertThat(url.queryParameter("code_challenge_method")).isEqualTo("S256")
        assertThat(url.queryParameter("state")).isNotEmpty()
        assertThat(auth.status.value).isEqualTo(ArtiseIdSignInStatus.InBrowser)
        // Email: Artise ID's own page, no provider scope.
        val email = requireNotNull(auth.authorizationUrl(configClient.config(), null)).toHttpUrl()
        assertThat(email.queryParameter("scope")).isEqualTo("openid profile email")
        assertThat(server.requestsTo(DISCOVERY)).hasSize(1)
    }

    /** Only the redirect with this sign-in's state is Artise ID's answer; a workspace sign-in on the same address has another state. */
    @Test
    fun `the answer is recognised by its state`() = runTest {
        val (auth, configClient) = auth()
        assertThat(auth.handles("$REDIRECT?code=abc&state=anything")).isFalse()
        val state = requireNotNull(auth.authorizationUrl(configClient.config(), null)).toHttpUrl().queryParameter("state")
        assertThat(auth.handles("$REDIRECT?code=abc&state=$state")).isTrue()
        assertThat(auth.handles("$REDIRECT?code=abc&state=other")).isFalse()
        assertThat(auth.handles("https://join.artise.co/acme/abc123?state=$state")).isFalse()
    }

    /** The code is traded for tokens with the PKCE verifier (no secret); the tokens are kept and the sign-in is done. */
    @Test
    fun `the code is traded for tokens`() = runTest {
        val (auth, configClient) = auth()
        val authorization = requireNotNull(auth.authorizationUrl(configClient.config(), null)).toHttpUrl()
        auth.onCallback("$REDIRECT?code=the-code&state=${authorization.queryParameter("state")}")
        runCurrent()
        assertThat(auth.status.value).isEqualTo(ArtiseIdSignInStatus.SignedIn)
        val form = server.requestsTo(TOKEN).single().body.readUtf8().split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
        assertThat(form["grant_type"]).isEqualTo("authorization_code")
        assertThat(form["code"]).isEqualTo("the-code")
        assertThat(form["client_id"]).isEqualTo("395423549856219219")
        assertThat(form).doesNotContainKey("client_secret")
        assertThat(ArtiseIdAuth.challengeOf(requireNotNull(form["code_verifier"]))).isEqualTo(authorization.queryParameter("code_challenge"))
        assertThat(store.tokens).isEqualTo(ArtiseIdTokens("at-1", "rt-1", clock.epochMillisResult + 3_600_000))
        // The same answer twice does nothing more.
        assertThat(auth.handles("$REDIRECT?code=the-code&state=${authorization.queryParameter("state")}")).isFalse()
    }

    /** Cancelling on Artise ID's page goes back to the buttons with no problem shown; another error is a failed sign-in. */
    @Test
    fun `a cancelled or failed answer`() = runTest {
        val (auth, configClient) = auth()
        var state = requireNotNull(auth.authorizationUrl(configClient.config(), null)).toHttpUrl().queryParameter("state")
        auth.onCallback("$REDIRECT?error=access_denied&state=$state")
        assertThat(auth.status.value).isEqualTo(ArtiseIdSignInStatus.Idle)
        state = requireNotNull(auth.authorizationUrl(configClient.config(), null)).toHttpUrl().queryParameter("state")
        auth.onCallback("$REDIRECT?error=server_error&state=$state")
        assertThat(auth.status.value).isEqualTo(ArtiseIdSignInStatus.Failed)
        server.on(TOKEN, MockResponse().setResponseCode(400))
        state = requireNotNull(auth.authorizationUrl(configClient.config(), null)).toHttpUrl().queryParameter("state")
        auth.onCallback("$REDIRECT?code=bad&state=$state")
        runCurrent()
        assertThat(auth.status.value).isEqualTo(ArtiseIdSignInStatus.Failed)
        assertThat(store.tokens).isNull()
    }

    /** The workspaces are read with the access token as a bearer token. */
    @Test
    fun `workspaces are read with the access token`() = runTest {
        server.on(WORKSPACES, json(WORKSPACES_TWO))
        store.tokens = ArtiseIdTokens("at-1", "rt-1", null)
        val (auth, _) = auth()
        val result = auth.workspaces()
        assertThat(result).isEqualTo(
            ArtiseIdWorkspaces.Found(
                listOf(
                    ArtiseIdWorkspace("mas-k8s", "Prueba MAS", "mas-k8s.artise.co", "https://matrix.mas-k8s.artise.co"),
                    ArtiseIdWorkspace("acme", "Acme", "acme.artise.co", "https://matrix.acme.artise.co"),
                )
            )
        )
        assertThat(server.requestsTo(WORKSPACES).single().getHeader("Authorization")).isEqualTo("Bearer at-1")
    }

    /** A refused access token is refreshed once and the list read again; without a refresh token, the person is signed out of Artise ID. */
    @Test
    fun `a refused token is refreshed, else signed out`() = runTest {
        server.on(WORKSPACES, MockResponse().setResponseCode(401), json(WORKSPACES_TWO))
        server.on(TOKEN, json("""{"access_token": "at-2", "expires_in": 3600}"""))
        store.tokens = ArtiseIdTokens("at-1", "rt-1", null)
        val (auth, _) = auth()
        assertThat(auth.workspaces()).isInstanceOf(ArtiseIdWorkspaces.Found::class.java)
        assertThat(server.requestsTo(WORKSPACES).map { it.getHeader("Authorization") }).containsExactly("Bearer at-1", "Bearer at-2").inOrder()
        assertThat(server.requestsTo(TOKEN).single().body.readUtf8()).contains("grant_type=refresh_token")
        // The refresh answer had no refresh token: the old one is kept.
        assertThat(store.tokens?.refreshToken).isEqualTo("rt-1")

        server.on(WORKSPACES, MockResponse().setResponseCode(401))
        store.tokens = ArtiseIdTokens("at-1", refreshToken = null, expiresAtMillis = null)
        assertThat(auth.workspaces()).isEqualTo(ArtiseIdWorkspaces.SignedOut)
        assertThat(store.tokens).isNull()
    }

    /** Offline: the list can't be read, but the tokens stay for next time. */
    @Test
    fun `unreachable keeps the tokens`() = runTest {
        server.on(WORKSPACES, MockResponse().setResponseCode(503))
        store.tokens = ArtiseIdTokens("at-1", "rt-1", null)
        val (auth, _) = auth()
        assertThat(auth.workspaces()).isEqualTo(ArtiseIdWorkspaces.Unreachable)
        assertThat(store.tokens).isNotNull()
        auth.signOut()
        assertThat(store.tokens).isNull()
    }
}
