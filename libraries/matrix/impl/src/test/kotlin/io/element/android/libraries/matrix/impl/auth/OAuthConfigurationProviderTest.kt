/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.auth

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.auth.OAuthStaticClient
import io.element.android.libraries.matrix.test.auth.FAKE_REDIRECT_URL
import io.element.android.libraries.matrix.test.auth.FakeOAuthRedirectUrlProvider
import io.element.android.libraries.matrix.test.core.aBuildMeta
import org.junit.Test

class OAuthConfigurationProviderTest {
    @Test
    fun get() {
        val result = OAuthConfigurationProvider(
            buildMeta = aBuildMeta(
                applicationName = "myName",
            ),
            oAuthRedirectUrlProvider = FakeOAuthRedirectUrlProvider(),
        ).get()
        assertThat(result.clientName).isEqualTo("myName")
        assertThat(result.redirectUri).isEqualTo(FAKE_REDIRECT_URL)
        assertThat(result.staticRegistrations).doesNotContainKey("https://auth.acme.artise.co/")
    }

    /** Artise: a workspace's own client is used for its issuer, and the result comes back to that client's https redirect. */
    @Test
    fun `get with a static client`() {
        val result = OAuthConfigurationProvider(
            buildMeta = aBuildMeta(),
            oAuthRedirectUrlProvider = FakeOAuthRedirectUrlProvider(),
        ).get(OAuthStaticClient(issuer = "https://auth.acme.artise.co/", clientId = "01K7ARTSEAPPANDR0D00000000", redirectUri = "https://join.artise.co/oauth"))
        assertThat(result.redirectUri).isEqualTo("https://join.artise.co/oauth")
        assertThat(result.staticRegistrations).containsEntry("https://auth.acme.artise.co/", "01K7ARTSEAPPANDR0D00000000")
        assertThat(result.staticRegistrations).containsEntry("https://id.thirdroom.io/realms/thirdroom", "elementx")
    }
}
