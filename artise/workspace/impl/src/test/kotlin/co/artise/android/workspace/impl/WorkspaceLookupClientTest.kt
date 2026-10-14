/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl

import co.artise.android.workspace.api.Workspace
import co.artise.android.workspace.api.WorkspaceLookupResult
import com.google.common.truth.Truth.assertThat
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test

class WorkspaceLookupClientTest {
    private val server = MockWebServer().apply { start() }
    private val clock = FakeSystemClock()

    @After
    fun tearDown() = server.shutdown()

    private fun TestScope.client() = WorkspaceLookupClient(OkHttpClient(), clock, testCoroutineDispatchers(), server.url("/v1/workspaces").toString())

    private fun answer(code: Int, body: String = "", maxAge: Int? = 300) = server.enqueue(
        MockResponse().setResponseCode(code).setBody(body).apply { if (maxAge != null) setHeader("Cache-Control", "max-age=$maxAge") }
    )

    private val artise = """{"slug":"artise","name":"Artise","server_name":"artise.co","homeserver_url":"https://matrix.artise.co","status":"active"}"""

    /** 200 active: found, with the server name from the answer (artise is artise.co, not artise.artise.co). Typed input is cleaned up first. */
    @Test
    fun `an active workspace is found`() = runTest {
        answer(200, artise)
        val result = client().find("  Artise ")
        assertThat(result).isEqualTo(
            WorkspaceLookupResult.Found(Workspace("artise", "Artise", "artise.co", "https://matrix.artise.co", isSuspended = false, joinUrl = null))
        )
        assertThat(server.takeRequest().path).isEqualTo("/v1/workspaces/artise")
    }

    /** Fields added later (join_url) are read; unknown ones are ignored. */
    @Test
    fun `join url and unknown fields`() = runTest {
        answer(
            200,
            """
            {"slug":"acme","name":"Acme","server_name":"acme.artise.co","homeserver_url":"https://matrix.acme.artise.co",
            "status":"active","join_url":"https://join.artise.co/acme","plan":"gold"}
            """.trimIndent(),
        )
        val workspace = (client().find("acme") as WorkspaceLookupResult.Found).workspace
        assertThat(workspace.joinUrl).isEqualTo("https://join.artise.co/acme")
    }

    /** A paused workspace: no sign-in. */
    @Test
    fun `a suspended workspace`() = runTest {
        answer(200, artise.replace("active", "suspended"))
        assertThat(client().find("artise")).isInstanceOf(WorkspaceLookupResult.Suspended::class.java)
    }

    /** 404 → not found; 429 → too many tries; any other answer → can't reach, worth retrying. */
    @Test
    fun `not found, too many tries and errors`() = runTest {
        val client = client()
        answer(404, """{"errcode":"NOT_FOUND"}""")
        assertThat(client.find("nonexistent-ws")).isEqualTo(WorkspaceLookupResult.NotFound("nonexistent-ws"))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))
        assertThat(client.find("acme")).isEqualTo(WorkspaceLookupResult.TooManyTries)
        answer(500, "oops")
        assertThat(client.find("beta")).isEqualTo(WorkspaceLookupResult.Unreachable)
        answer(200, "<html>proxy error</html>")
        assertThat(client.find("gamma")).isEqualTo(WorkspaceLookupResult.Unreachable)
    }

    /** No network: can't reach, never a silent fallback to artise.co. */
    @Test
    fun `network failure`() = runTest {
        val client = client()
        server.shutdown()
        assertThat(client.find("acme")).isEqualTo(WorkspaceLookupResult.Unreachable)
    }

    /** An invalid name ("ab") is refused without asking the server. */
    @Test
    fun `invalid names make no request`() = runTest {
        assertThat(client().find("ab")).isEqualTo(WorkspaceLookupResult.Invalid)
        assertThat(server.requestCount).isEqualTo(0)
    }

    /** A server name ("artise.co", even as a URL) goes straight to that server, without the lookup. */
    @Test
    fun `server names skip the lookup`() = runTest {
        val client = client()
        assertThat(client.find("artise.co")).isEqualTo(WorkspaceLookupResult.ServerName("artise.co"))
        assertThat(client.find("https://Matrix.Artise.co/")).isEqualTo(WorkspaceLookupResult.ServerName("matrix.artise.co"))
        assertThat(client.find("bad..name")).isEqualTo(WorkspaceLookupResult.Invalid)
        assertThat(server.requestCount).isEqualTo(0)
    }

    /** An answer is reused for max-age (5 minutes), then asked again. */
    @Test
    fun `answers are kept for max-age`() = runTest {
        val client = client()
        answer(200, artise)
        answer(200, artise.replace("active", "suspended"))
        assertThat(client.find("artise")).isInstanceOf(WorkspaceLookupResult.Found::class.java)
        clock.epochMillisResult += 299_000
        assertThat(client.find("artise")).isInstanceOf(WorkspaceLookupResult.Found::class.java)
        assertThat(server.requestCount).isEqualTo(1)
        clock.epochMillisResult += 2_000
        assertThat(client.find("artise")).isInstanceOf(WorkspaceLookupResult.Suspended::class.java)
        assertThat(server.requestCount).isEqualTo(2)
    }
}
