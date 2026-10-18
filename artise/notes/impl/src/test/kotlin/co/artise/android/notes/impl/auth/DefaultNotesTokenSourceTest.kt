/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.auth

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.auth.OpenIdToken
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultNotesTokenSourceTest {
    private val clock = FakeSystemClock(epochMillisResult = 1_000_000)
    private var issued = 0

    private fun provider(result: () -> Result<OpenIdToken> = { Result.success(openId("token-${++issued}")) }) =
        DefaultNotesTokenSource(FakeMatrixClient(requestOpenIdTokenResult = result), clock)

    /** A fresh token is reused while it's valid, so each API call doesn't cost a Matrix request. */
    @Test
    fun `token is cached while valid`() = runTest {
        val provider = provider()
        assertThat(provider.token().getOrNull()).isEqualTo("token-1")
        clock.epochMillisResult += 30 * 60 * 1000
        assertThat(provider.token().getOrNull()).isEqualTo("token-1")
        assertThat(issued).isEqualTo(1)
    }

    /** Within a minute of expiring, a new token is requested, so no request goes out with an expiring one. */
    @Test
    fun `token is refreshed shortly before it expires`() = runTest {
        val provider = provider()
        provider.token()
        clock.epochMillisResult += 3600 * 1000 - 59_000
        assertThat(provider.token().getOrNull()).isEqualTo("token-2")
    }

    /** After a 401, invalidating the refused token makes the next call get a new one. */
    @Test
    fun `invalidate drops the refused token`() = runTest {
        val provider = provider()
        provider.invalidate(provider.token().getOrThrow())
        assertThat(provider.token().getOrNull()).isEqualTo("token-2")
    }

    /** A late 401 for an old token must not throw away the newer one another request already got. */
    @Test
    fun `invalidate ignores a token that was already replaced`() = runTest {
        val provider = provider()
        provider.token()
        provider.invalidate("token-1")
        provider.token()
        provider.invalidate("token-1")
        assertThat(provider.token().getOrNull()).isEqualTo("token-2")
    }

    /** If the chat server can't issue a token, the failure is passed on and nothing is cached. */
    @Test
    fun `failure to get a token is returned`() = runTest {
        val provider = provider { Result.failure(IllegalStateException("offline")) }
        assertThat(provider.token().exceptionOrNull()).hasMessageThat().isEqualTo("offline")
    }

    /** The token never appears when the value is printed, e.g. in a log line. */
    @Test
    fun `openid token toString hides the secret`() {
        assertThat(openId("secret-value").toString()).doesNotContain("secret-value")
    }

    private fun openId(token: String) = OpenIdToken(accessToken = token, tokenType = "Bearer", matrixServerName = "artise.co", expiresInSeconds = 3600)
}
