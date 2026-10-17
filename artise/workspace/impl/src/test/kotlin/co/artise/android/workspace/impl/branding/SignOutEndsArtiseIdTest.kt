/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.branding

import co.artise.android.workspace.impl.artiseid.ArtiseIdAuth
import co.artise.android.workspace.impl.artiseid.ArtiseIdConfigClient
import co.artise.android.workspace.impl.artiseid.ArtiseIdTokens
import co.artise.android.workspace.impl.artiseid.InMemoryArtiseIdTokenStore
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Test

/** The sign-out confirmation adds "you'll also be signed out of Artise ID" only for the last workspace, and only with Artise ID. */
class SignOutEndsArtiseIdTest {
    private fun TestScope.auth(tokens: ArtiseIdTokens?): ArtiseIdAuth {
        val dispatchers = testCoroutineDispatchers()
        val clock = FakeSystemClock()
        return ArtiseIdAuth(
            ArtiseIdConfigClient(OkHttpClient(), clock, dispatchers),
            InMemoryArtiseIdTokenStore(tokens),
            OkHttpClient(),
            clock,
            dispatchers,
            backgroundScope,
        )
    }

    /** One workspace, signed in with Artise ID: signing out ends Artise ID too. */
    @Test
    fun `last workspace with Artise ID`() = runTest {
        val store = InMemorySessionStore(listOf(aSessionData()))
        assertThat(signOutEndsArtiseId(store, auth(ArtiseIdTokens("access")))).isTrue()
    }

    /** One workspace whose name was typed (no Artise ID): nothing about Artise ID. */
    @Test
    fun `last workspace without Artise ID`() = runTest {
        val store = InMemorySessionStore(listOf(aSessionData()))
        assertThat(signOutEndsArtiseId(store, auth(null))).isFalse()
    }

    /** Two workspaces: Artise ID stays for the other one. */
    @Test
    fun `another workspace still signed in`() = runTest {
        val store = InMemorySessionStore(listOf(aSessionData(), aSessionData(sessionId = "@alice:other.org")))
        assertThat(signOutEndsArtiseId(store, auth(ArtiseIdTokens("access")))).isFalse()
    }
}
