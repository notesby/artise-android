/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.oauth

import co.artise.android.workspace.api.WorkspaceConfig
import co.artise.android.workspace.api.WorkspaceOAuth
import co.artise.android.workspace.test.FakeWorkspaceConfigRepository
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.auth.OAuthStaticClient
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test

class DefaultOAuthStaticClientProviderTest {
    private val server = MockWebServer().apply { start() }
    private val oauth = WorkspaceOAuth(androidClientId = "01K7ARTSEAPPANDR0D00000000", redirectUri = "https://join.artise.co/oauth")

    @After
    fun tearDown() = server.shutdown()

    private fun TestScope.provider(opensJoinLinks: Boolean = true, config: WorkspaceConfig? = WorkspaceConfig(oauth = oauth)) =
        DefaultOAuthStaticClientProvider(
            joinLinkCheck = { opensJoinLinks },
            workspaceConfigRepository = FakeWorkspaceConfigRepository(fetchResult = { if (it == "acme.artise.co") config else null }),
            okHttpClient = OkHttpClient(),
            dispatchers = testCoroutineDispatchers(),
        )

    private fun homeserverUrl() = server.url("/").toString()

    /**
     * When join.artise.co links open the app, it uses the workspace's android client for the issuer in the homeserver's auth metadata,
     * and its https redirect.
     */
    @Test
    fun `the release app uses the workspace's client`() = runTest {
        server.enqueue(MockResponse().setBody("""{"issuer": "https://auth.acme.artise.co/", "token_endpoint": "https://auth.acme.artise.co/token"}"""))
        val provider = provider()
        val client = provider.get(serverName = "acme.artise.co", homeserverUrl = homeserverUrl())
        assertThat(client).isEqualTo(
            OAuthStaticClient(
                issuer = "https://auth.acme.artise.co/",
                clientId = "01K7ARTSEAPPANDR0D00000000",
                redirectUri = "https://join.artise.co/oauth",
            )
        )
        assertThat(server.takeRequest().path).isEqualTo("/_matrix/client/v1/auth_metadata")
        assertThat(provider.pendingRedirectUri).isEqualTo("https://join.artise.co/oauth")
    }

    /** When join.artise.co links don't open the app (debug and nightly builds, or an unverified link), it registers itself (custom scheme). */
    @Test
    fun `without join links the app registers itself`() = runTest {
        val provider = provider(opensJoinLinks = false)
        assertThat(provider.get(serverName = "acme.artise.co", homeserverUrl = homeserverUrl())).isNull()
        assertThat(provider.pendingRedirectUri).isNull()
        assertThat(server.requestCount).isEqualTo(0)
    }

    /** A workspace without `oauth` (or no workspace file) keeps today's sign-in. */
    @Test
    fun `a workspace without oauth registers the app`() = runTest {
        assertThat(provider(config = WorkspaceConfig(name = "Acme")).get("acme.artise.co", homeserverUrl())).isNull()
        assertThat(provider(config = null).get("acme.artise.co", homeserverUrl())).isNull()
        assertThat(server.requestCount).isEqualTo(0)
    }

    /** Without readable auth metadata the issuer is unknown, so the app registers itself; a previous sign-in's redirect is forgotten. */
    @Test
    fun `unreadable auth metadata registers the app`() = runTest {
        val provider = provider()
        server.enqueue(MockResponse().setBody("""{"issuer": "https://auth.acme.artise.co/"}"""))
        provider.get("acme.artise.co", homeserverUrl())
        server.enqueue(MockResponse().setResponseCode(404))
        assertThat(provider.get("acme.artise.co", homeserverUrl())).isNull()
        assertThat(provider.pendingRedirectUri).isNull()
        server.enqueue(MockResponse().setBody("not json"))
        assertThat(provider.get("acme.artise.co", homeserverUrl())).isNull()
    }
}
