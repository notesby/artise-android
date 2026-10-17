/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.artiseid

import co.artise.android.workspace.impl.artiseid.ArtiseIdTestServer.Companion.SIGN_IN
import co.artise.android.workspace.impl.artiseid.ArtiseIdTestServer.Companion.SIGN_IN_JSON
import co.artise.android.workspace.impl.artiseid.ArtiseIdTestServer.Companion.json
import com.google.common.truth.Truth.assertThat
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Test

class ArtiseIdConfigClientTest {
    private val server = ArtiseIdTestServer()
    private val clock = FakeSystemClock()

    @After
    fun tearDown() = server.close()

    private fun TestScope.client() = ArtiseIdConfigClient(server.okHttpClient, clock, testCoroutineDispatchers())

    /** The settings are read with only the providers the app knows; the answer is kept for an hour. */
    @Test
    fun `settings are read and kept for an hour`() = runTest {
        server.on(SIGN_IN, json(SIGN_IN_JSON.replace("\"microsoft\": \"m-idp\"", "\"microsoft\": \"m-idp\", \"github\": \"x\"")))
        val client = client()
        val config = client.get()
        assertThat(config).isEqualTo(
            ArtiseIdConfig(
                issuer = "https://id.artise.co",
                clientId = "395423549856219219",
                redirectUri = "https://join.artise.co/oauth",
                scopes = listOf("openid", "profile", "email"),
                providers = mapOf(ArtiseIdProvider.GOOGLE to "g-idp", ArtiseIdProvider.MICROSOFT to "m-idp"),
            )
        )
        clock.epochMillisResult += 59 * 60 * 1000L
        client.get()
        assertThat(server.requestsTo(SIGN_IN)).hasSize(1)
        clock.epochMillisResult += 2 * 60 * 1000L
        client.get()
        assertThat(server.requestsTo(SIGN_IN)).hasSize(2)
    }

    /** Missing (404), broken or unsafe settings mean no Artise ID; a failure isn't kept, so the next screen asks again. */
    @Test
    fun `missing settings mean no Artise ID`() = runTest {
        val client = client()
        server.on(SIGN_IN, MockResponse().setResponseCode(404))
        assertThat(client.get()).isNull()
        server.on(SIGN_IN, json("not json"))
        assertThat(client.get()).isNull()
        server.on(SIGN_IN, json(SIGN_IN_JSON.replace("https://id.artise.co", "http://id.artise.co")))
        assertThat(client.get()).isNull()
        assertThat(server.requestsTo(SIGN_IN)).hasSize(3)
    }
}
