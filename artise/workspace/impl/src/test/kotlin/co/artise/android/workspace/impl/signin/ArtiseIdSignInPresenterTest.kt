/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.signin

import co.artise.android.workspace.api.WorkspaceInvite
import co.artise.android.workspace.api.WorkspaceSignInEntryPoint
import co.artise.android.workspace.impl.artiseid.ArtiseIdAuth
import co.artise.android.workspace.impl.artiseid.ArtiseIdConfigClient
import co.artise.android.workspace.impl.artiseid.ArtiseIdProvider
import co.artise.android.workspace.impl.artiseid.ArtiseIdTestServer
import co.artise.android.workspace.impl.artiseid.ArtiseIdTestServer.Companion.REDIRECT
import co.artise.android.workspace.impl.artiseid.ArtiseIdTestServer.Companion.SIGN_IN
import co.artise.android.workspace.impl.artiseid.ArtiseIdTestServer.Companion.WORKSPACES_NONE
import co.artise.android.workspace.impl.artiseid.ArtiseIdTestServer.Companion.WORKSPACES_TWO
import co.artise.android.workspace.impl.artiseid.ArtiseIdTokens
import co.artise.android.workspace.impl.artiseid.ArtiseIdWorkspace
import co.artise.android.workspace.impl.artiseid.InMemoryArtiseIdTokenStore
import com.google.common.truth.Truth.assertThat
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Test

class ArtiseIdSignInPresenterTest {
    private val server = ArtiseIdTestServer()
    private val store = InMemoryArtiseIdTokenStore()
    private val masK8s = ArtiseIdWorkspace("mas-k8s", "Prueba MAS", "mas-k8s.artise.co", "https://matrix.mas-k8s.artise.co")
    private lateinit var auth: ArtiseIdAuth

    @After
    fun tearDown() = server.close()

    private fun TestScope.createArtiseIdSignInPresenter(
        params: WorkspaceSignInEntryPoint.Params = WorkspaceSignInEntryPoint.Params(),
        opensJoinLinks: Boolean = true,
    ): ArtiseIdSignInPresenter {
        val dispatchers = testCoroutineDispatchers()
        val clock = FakeSystemClock()
        val configClient = ArtiseIdConfigClient(server.okHttpClient, clock, dispatchers)
        auth = ArtiseIdAuth(configClient, store, server.okHttpClient, clock, dispatchers, backgroundScope)
        return ArtiseIdSignInPresenter(params, configClient, auth, joinLinkCheck = { opensJoinLinks })
    }

    /** Signs in through [provider] as if Artise ID's page had answered with a code. */
    private suspend fun ArtiseIdSignInState.signInAndAnswer(provider: ArtiseIdProvider?, awaitUrl: suspend () -> String) {
        eventSink(ArtiseIdSignInEvent.SignIn(provider))
        val state = awaitUrl().toHttpUrl().queryParameter("state")
        auth.onCallback("$REDIRECT?code=the-code&state=$state")
    }

    /** "Artise" offers the providers the settings name (Google, then Microsoft; Apple is missing here), plus email. */
    @Test
    fun `the first screen offers the providers`() = runTest {
        server.givenArtiseId()
        createArtiseIdSignInPresenter().test {
            val screen = consumeItemsUntilPredicate { it.screen is ArtiseIdScreen.Providers }.last().screen as ArtiseIdScreen.Providers
            assertThat(screen.providers).containsExactly(ArtiseIdProvider.GOOGLE, ArtiseIdProvider.MICROSOFT).inOrder()
            assertThat(screen.isBusy).isFalse()
        }
    }

    /** Without Artise ID's settings (404 or offline), or when join.artise.co links don't open the app, sign-in starts with typing the name. */
    @Test
    fun `no Artise ID means today's screen`() = runTest {
        server.givenArtiseId()
        server.on(SIGN_IN, MockResponse().setResponseCode(404))
        createArtiseIdSignInPresenter().test {
            assertThat(consumeItemsUntilPredicate { it.screen != ArtiseIdScreen.Checking }.last().screen).isEqualTo(ArtiseIdScreen.Unavailable)
        }
        server.givenArtiseId()
        createArtiseIdSignInPresenter(opensJoinLinks = false).test {
            assertThat(consumeItemsUntilPredicate { it.screen != ArtiseIdScreen.Checking }.last().screen).isEqualTo(ArtiseIdScreen.Unavailable)
        }
    }

    /** An invite link (or a prefilled workspace) goes the way it does today, without Artise ID. */
    @Test
    fun `an invite skips Artise ID`() = runTest {
        server.givenArtiseId()
        createArtiseIdSignInPresenter(params = WorkspaceSignInEntryPoint.Params(invite = WorkspaceInvite("acme", "abc123"))).test {
            assertThat(consumeItemsUntilPredicate { it.screen != ArtiseIdScreen.Checking }.last().screen).isEqualTo(ArtiseIdScreen.Unavailable)
        }
        assertThat(server.requestsTo(SIGN_IN)).isEmpty()
    }

    /** After signing in, a person with one workspace goes straight into it. */
    @Test
    fun `one workspace opens directly`() = runTest {
        server.givenArtiseId()
        createArtiseIdSignInPresenter().test {
            val providers = consumeItemsUntilPredicate { it.screen is ArtiseIdScreen.Providers }.last()
            providers.signInAndAnswer(ArtiseIdProvider.GOOGLE) {
                val opened = consumeItemsUntilPredicate { it.authorizationUrl != null }.last()
                opened.eventSink(ArtiseIdSignInEvent.AuthorizationOpened)
                requireNotNull(opened.authorizationUrl)
            }
            val chosen = consumeItemsUntilPredicate { it.chosen != null }.last()
            assertThat(chosen.chosen).isEqualTo(masK8s)
            assertThat(store.tokens?.accessToken).isEqualTo("at-1")
        }
    }

    /** Several workspaces are listed, none opened; tapping one opens it. */
    @Test
    fun `several workspaces are listed`() = runTest {
        server.givenArtiseId(workspaces = WORKSPACES_TWO)
        createArtiseIdSignInPresenter().test {
            val providers = consumeItemsUntilPredicate { it.screen is ArtiseIdScreen.Providers }.last()
            providers.signInAndAnswer(null) {
                requireNotNull(consumeItemsUntilPredicate { it.authorizationUrl != null }.last().authorizationUrl)
            }
            val listed = consumeItemsUntilPredicate { it.screen is ArtiseIdScreen.Workspaces }.last()
            assertThat((listed.screen as ArtiseIdScreen.Workspaces).workspaces.map { it.slug }).containsExactly("mas-k8s", "acme").inOrder()
            assertThat(listed.chosen).isNull()
            listed.eventSink(ArtiseIdSignInEvent.Choose(masK8s))
            assertThat(consumeItemsUntilPredicate { it.chosen != null }.last().chosen).isEqualTo(masK8s)
        }
    }

    /** No workspaces yet: how to get an invite. */
    @Test
    fun `no workspaces shows how to get an invite`() = runTest {
        server.givenArtiseId(workspaces = WORKSPACES_NONE)
        createArtiseIdSignInPresenter().test {
            val providers = consumeItemsUntilPredicate { it.screen is ArtiseIdScreen.Providers }.last()
            providers.signInAndAnswer(ArtiseIdProvider.MICROSOFT) {
                requireNotNull(consumeItemsUntilPredicate { it.authorizationUrl != null }.last().authorizationUrl)
            }
            assertThat(consumeItemsUntilPredicate { it.screen == ArtiseIdScreen.NoWorkspaces }.last().chosen).isNull()
        }
    }

    /** Already signed in to Artise ID (adding an account): the list shows without a page, and even one workspace waits for a tap. */
    @Test
    fun `kept tokens show the list`() = runTest {
        server.givenArtiseId()
        store.tokens = ArtiseIdTokens("at-1", "rt-1", null)
        createArtiseIdSignInPresenter().test {
            val listed = consumeItemsUntilPredicate { it.screen is ArtiseIdScreen.Workspaces }.last()
            assertThat((listed.screen as ArtiseIdScreen.Workspaces).workspaces).containsExactly(masK8s)
            assertThat(listed.chosen).isNull()
        }
    }

    /** Closing Artise ID's page without an answer brings the buttons back. */
    @Test
    fun `a closed page brings the buttons back`() = runTest {
        server.givenArtiseId()
        createArtiseIdSignInPresenter().test {
            consumeItemsUntilPredicate { it.screen is ArtiseIdScreen.Providers }.last().eventSink(ArtiseIdSignInEvent.SignIn(null))
            val opened = consumeItemsUntilPredicate { it.authorizationUrl != null }.last()
            assertThat((opened.screen as ArtiseIdScreen.Providers).isBusy).isTrue()
            opened.eventSink(ArtiseIdSignInEvent.AuthorizationOpened)
            opened.eventSink(ArtiseIdSignInEvent.Resumed)
            val back = consumeItemsUntilPredicate { (it.screen as? ArtiseIdScreen.Providers)?.isBusy == false }.last()
            assertThat((back.screen as ArtiseIdScreen.Providers).problem).isNull()
        }
    }
}
