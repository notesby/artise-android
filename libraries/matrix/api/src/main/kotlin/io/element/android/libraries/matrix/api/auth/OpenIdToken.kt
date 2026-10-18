/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.auth

/**
 * A short-lived OpenID token that proves the user's identity to a third-party service,
 * without handing it the Matrix access token.
 *
 * @property accessToken the token to send to the third party. Never log it.
 * @property tokenType always "Bearer".
 * @property matrixServerName the homeserver the third party must check the token with.
 * @property expiresInSeconds how long the token is valid, counted from when it was issued.
 */
data class OpenIdToken(
    val accessToken: String,
    val tokenType: String,
    val matrixServerName: String,
    val expiresInSeconds: Long,
) {
    // Keep the token out of logs and crash reports that print data classes.
    override fun toString() = "OpenIdToken(matrixServerName=$matrixServerName, expiresInSeconds=$expiresInSeconds)"
}
