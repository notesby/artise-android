/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.oauth.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.auth.FAKE_REDIRECT_URL
import io.element.android.libraries.matrix.test.auth.FakeOAuthRedirectUrlProvider
import io.element.android.libraries.matrix.test.auth.FakeOAuthStaticClientProvider
import io.element.android.libraries.oauth.api.OAuthAction
import org.junit.Test

class DefaultOAuthUrlParserTest {
    @Test
    fun `test empty url`() {
        val sut = createDefaultOAuthUrlParser()
        assertThat(sut.parse("")).isNull()
    }

    @Test
    fun `test regular url`() {
        val sut = createDefaultOAuthUrlParser()
        assertThat(sut.parse("https://matrix.org")).isNull()
    }

    @Test
    fun `test cancel url`() {
        val sut = createDefaultOAuthUrlParser()
        val aCancelUrl = "$FAKE_REDIRECT_URL?error=access_denied&state=IFF1UETGye2ZA8pO"
        assertThat(sut.parse(aCancelUrl)).isEqualTo(OAuthAction.GoBack())
    }

    @Test
    fun `test success url`() {
        val sut = createDefaultOAuthUrlParser()
        val aSuccessUrl = "$FAKE_REDIRECT_URL?state=IFF1UETGye2ZA8pO&code=y6X1GZeqA3xxOWcTeShgv8nkgFJXyzWB"
        assertThat(sut.parse(aSuccessUrl)).isEqualTo(OAuthAction.Success(aSuccessUrl))
    }

    @Test
    fun `test unknown url`() {
        val sut = createDefaultOAuthUrlParser()
        val anUnknownUrl = "$FAKE_REDIRECT_URL?state=IFF1UETGye2ZA8pO&goat=y6X1GZeqA3xxOWcTeShgv8nkgFJXyzWB"
        assertThat(sut.parse(anUnknownUrl)).isNull()
    }

    /** Artise: a workspace's own client returns to its https redirect URI, with the code in the query. */
    @Test
    fun `a workspace client's https result is a success`() {
        val sut = createDefaultOAuthUrlParser(pendingRedirectUri = ARTISE_REDIRECT)
        val aSuccessUrl = "$ARTISE_REDIRECT?code=y6X1GZeqA3xxOWcTeShgv8nkgFJXyzWB&state=IFF1UETGye2ZA8pO"
        assertThat(sut.parse(aSuccessUrl)).isEqualTo(OAuthAction.Success(aSuccessUrl))
        assertThat(sut.parse("$ARTISE_REDIRECT?error=access_denied&state=IFF1UETGye2ZA8pO")).isEqualTo(OAuthAction.GoBack())
    }

    /** Artise: the https redirect is only a sign-in result while that client is signing in, and only on its exact path. */
    @Test
    fun `other https links are not sign-in results`() {
        val code = "?code=y6X1GZeqA3xxOWcTeShgv8nkgFJXyzWB&state=IFF1UETGye2ZA8pO"
        assertThat(createDefaultOAuthUrlParser(pendingRedirectUri = null).parse("$ARTISE_REDIRECT$code")).isNull()
        val sut = createDefaultOAuthUrlParser(pendingRedirectUri = ARTISE_REDIRECT)
        assertThat(sut.parse("${ARTISE_REDIRECT}x$code")).isNull()
        assertThat(sut.parse("https://join.artise.co/acme/AbCdEf12$code")).isNull()
    }

    private fun createDefaultOAuthUrlParser(pendingRedirectUri: String? = null): DefaultOAuthUrlParser {
        return DefaultOAuthUrlParser(
            oAuthRedirectUrlProvider = FakeOAuthRedirectUrlProvider(),
            oAuthStaticClientProvider = FakeOAuthStaticClientProvider().apply { givenPendingRedirectUri(pendingRedirectUri) },
        )
    }
}

private const val ARTISE_REDIRECT = "https://join.artise.co/oauth"
