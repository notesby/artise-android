/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.notes.impl.auth

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where the Notes API client gets its sign-in token; a seam so tests don't need a Matrix client. */
interface NotesTokenSource {
    /** A valid token, requesting a new one when needed. */
    suspend fun token(): Result<String>

    /** Forgets [rejected] after the server refused it, so the next [token] call asks for a new one. */
    suspend fun invalidate(rejected: String)
}

/**
 * Hands out the OpenID token the Notes API signs in with. The Matrix access token never leaves the app.
 *
 * A token is reused until [REFRESH_MARGIN_MILLIS] before it expires; [invalidate] drops it after a 401.
 */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultNotesTokenSource(
    private val matrixClient: MatrixClient,
    private val systemClock: SystemClock,
) : NotesTokenSource {
    private val mutex = Mutex()
    private var token: String? = null
    private var expiresAtMillis: Long = 0

    override suspend fun token(): Result<String> = mutex.withLock {
        val now = systemClock.epochMillis()
        val current = token
        if (current != null && now < expiresAtMillis - REFRESH_MARGIN_MILLIS) {
            return Result.success(current)
        }
        matrixClient.requestOpenIdToken().map { openId ->
            token = openId.accessToken
            expiresAtMillis = now + openId.expiresInSeconds * 1000
            openId.accessToken
        }
    }

    override suspend fun invalidate(rejected: String) = mutex.withLock {
        // Another request may already have refreshed it; only drop the token that was refused.
        if (token == rejected) {
            token = null
            expiresAtMillis = 0
        }
    }

    companion object {
        /** Refresh a minute early so a request never goes out with a token about to expire. */
        const val REFRESH_MARGIN_MILLIS = 60_000L
    }
}
