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
import io.element.android.libraries.matrix.test.core.aBuildMeta
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

    private fun TestScope.provider(applicationId: String = "co.artise.app", config: WorkspaceConfig? = WorkspaceConfig(oauth = oauth)) =
        DefaultOAuthStaticClientProvider(
            buildMeta = aBuildMeta(applicationId = applicationId),
            workspaceConfigRepository = FakeWorkspaceConfigRepository(fetchResult = { if (it == "acme.artise.co") config else null }),
            okHttpClient = OkHttpClient(),
            dispatchers = testCoroutineDispatchers(),
        )

    private fun homeserverUrl() = server.url("/").toString()

    /** The release app uses the workspace's android client for the issuer in the homeserver's auth metadata, and its https redirect. */
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

    /** Debug and nightly builds aren't in join.artise.co's App Link file, so they keep registering themselves (custom scheme). */
    @Test
    fun `other builds register themselves`() = runTest {
        for (applicationId in listOf("co.artise.app.debug", "co.artise.app.nightly")) {
            val provider = provider(applicationId = applicationId)
            assertThat(provider.get(serverName = "acme.artise.co", homeserverUrl = homeserverUrl())).isNull()
            assertThat(provider.pendingRedirectUri).isNull()
        }
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
