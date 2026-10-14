/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl

import android.os.Build
import co.artise.android.workspace.api.WorkspaceConfig
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.services.appnavstate.test.FakeAppForegroundStateService
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.QueueDispatcher
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class DefaultWorkspaceConfigRepositoryTest {
    // Fail fast: a background check with nothing queued gets a 404 instead of waiting.
    private val server = MockWebServer().apply {
        (dispatcher as QueueDispatcher).setFailFast(true)
        start()
    }
    private val clock = FakeSystemClock()
    private val foreground = FakeAppForegroundStateService(initialForegroundValue = true)
    private val ana = SessionId("@ana:acme.artise.co")

    @After
    fun tearDown() = server.shutdown()

    private fun TestScope.repository() = DefaultWorkspaceConfigRepository(
        context = RuntimeEnvironment.getApplication(),
        okHttpClient = OkHttpClient(),
        clock = clock,
        dispatchers = testCoroutineDispatchers(),
        appScope = backgroundScope,
        appForegroundStateService = foreground,
    ).apply { wellKnownUrlFor = { serverName -> server.url("/$serverName/.well-known/matrix/client").toString() } }

    private fun file(name: String) = MockResponse().setBody("""{"co.artise.workspace": {"name": "$name"}}""").setHeader("Cache-Control", "max-age=300")

    /** The file comes from the server in the account's ID, and is saved for the next start. */
    @Test
    fun `settings are read from the account's server and saved`() = runTest {
        server.enqueue(file("Acme"))
        repository().refresh(ana)
        assertThat(server.takeRequest().path).isEqualTo("/acme.artise.co/.well-known/matrix/client")
        // A new start reads the saved copy (and checks the server again, finding the same).
        server.enqueue(file("Acme"))
        val next = repository()
        assertThat(next.cached(ana)).isEqualTo(WorkspaceConfig(name = "Acme"))
    }

    /** Within max-age nothing is asked again; after it, or when forced, it is. */
    @Test
    fun `max-age is respected`() = runTest {
        val repository = repository()
        server.enqueue(file("Acme"))
        repository.refresh(ana)
        repository.refresh(ana)
        assertThat(server.requestCount).isEqualTo(1)
        server.enqueue(file("Acme 2"))
        clock.epochMillisResult += 301_000
        repository.refresh(ana)
        assertThat(repository.cached(ana)?.name).isEqualTo("Acme 2")
        server.enqueue(file("Acme 3"))
        repository.refresh(ana, force = true)
        assertThat(repository.cached(ana)?.name).isEqualTo("Acme 3")
    }

    /** When the server can't be reached, the saved look stays; a server without the section means the default look. */
    @Test
    fun `failures keep what was saved`() = runTest {
        val repository = repository()
        server.enqueue(file("Acme"))
        repository.refresh(ana)
        server.enqueue(MockResponse().setResponseCode(503))
        repository.refresh(ana, force = true)
        assertThat(repository.cached(ana)?.name).isEqualTo("Acme")
        server.enqueue(MockResponse().setBody("""{"m.homeserver": {"base_url": "https://matrix.acme.artise.co"}}"""))
        repository.refresh(ana, force = true)
        assertThat(repository.cached(ana)).isNull()
    }

    /** Back in the foreground, accounts in use are checked again (an admin's !workspace change shows on reopening). */
    @Test
    fun `coming back to the foreground refreshes`() = runTest {
        val repository = repository()
        server.enqueue(file("Acme"))
        repository.cached(ana)
        runCurrent()
        assertThat(server.requestCount).isEqualTo(1)
        server.enqueue(file("Acme blue"))
        clock.epochMillisResult += 301_000
        foreground.givenIsInForeground(false)
        runCurrent()
        foreground.givenIsInForeground(true)
        runCurrent()
        assertThat(server.requestCount).isEqualTo(2)
        assertThat(repository.cached(ana)?.name).isEqualTo("Acme blue")
    }

    /** The sign-in screen can read a server's settings without saving them. */
    @Test
    fun `fetch reads without saving`() = runTest {
        val repository = repository()
        server.enqueue(file("Acme"))
        assertThat(repository.fetch("acme.artise.co")?.name).isEqualTo("Acme")
        assertThat(repository.cached(SessionId("@x:acme.artise.co"))).isNull()
    }
}
