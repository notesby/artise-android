/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.auth

import dev.zacsweers.metro.Inject
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.matrix.api.auth.OAuthConfig
import io.element.android.libraries.matrix.api.auth.OAuthRedirectUrlProvider
import io.element.android.libraries.matrix.api.auth.OAuthStaticClient
import org.matrix.rustcomponents.sdk.OAuthConfiguration

@Inject
class OAuthConfigurationProvider(
    private val buildMeta: BuildMeta,
    private val oAuthRedirectUrlProvider: OAuthRedirectUrlProvider,
) {
    /** Artise: with a [staticClient], signs in with that client and its redirect URI instead of registering the app. */
    fun get(staticClient: OAuthStaticClient? = null): OAuthConfiguration = OAuthConfiguration(
        clientName = buildMeta.applicationName,
        redirectUri = staticClient?.redirectUri ?: oAuthRedirectUrlProvider.provide(),
        clientUri = OAuthConfig.CLIENT_URI,
        logoUri = OAuthConfig.LOGO_URI,
        tosUri = OAuthConfig.TOS_URI,
        policyUri = OAuthConfig.POLICY_URI,
        staticRegistrations = OAuthConfig.STATIC_REGISTRATIONS + listOfNotNull(staticClient?.let { it.issuer to it.clientId }),
    )
}
