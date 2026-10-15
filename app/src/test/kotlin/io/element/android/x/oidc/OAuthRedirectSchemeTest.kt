/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x.oidc

import android.net.Uri
import android.os.Build
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.auth.OAuthConfig
import io.element.android.x.BuildConfig
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Artise: a workspace's sign-in service (MAS) registers the app and accepts a native app's custom-scheme redirect only when the scheme
 * starts with the client URI's host reversed. The client URI is https://artise.co/apps/android, so the scheme must start with "co.artise".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class OAuthRedirectSchemeTest {
    /** The redirect scheme of this build (co.artise.app, plus .debug or .nightly) starts with the website's host reversed. */
    @Test
    fun `the redirect scheme starts with the website host reversed`() {
        val scheme = BuildConfig.LOGIN_REDIRECT_SCHEME
        val reversedHost = Uri.parse(OAuthConfig.CLIENT_URI).host.orEmpty().split('.').reversed()
        assertThat(scheme.split('.').take(reversedHost.size)).isEqualTo(reversedHost)
        assertThat(scheme).isIn(listOf("co.artise.app", "co.artise.app.debug", "co.artise.app.nightly"))
    }

    /** The logo, terms and privacy pages MAS asks for are on the client URI's host. */
    @Test
    fun `the client metadata links are on the website host`() {
        val host = Uri.parse(OAuthConfig.CLIENT_URI).host
        assertThat(host).isEqualTo("artise.co")
        listOf(OAuthConfig.LOGO_URI, OAuthConfig.TOS_URI, OAuthConfig.POLICY_URI).forEach { url ->
            assertThat(url).startsWith("https://")
            assertThat(Uri.parse(url).host).isEqualTo(host)
        }
    }
}
