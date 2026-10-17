/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.api

/**
 * A company's space in Artise: its own Matrix server, found by the short name people type ("acme").
 *
 * [serverName] is the Matrix server name to sign in to. It is not always `<slug>.artise.co` (the "artise" workspace is `artise.co`).
 */
data class Workspace(
    val slug: String,
    val name: String,
    val serverName: String,
    val homeserverUrl: String,
    val isSuspended: Boolean,
    /** Where invite codes are redeemed (`{joinUrl}/{code}`); older servers don't send it. */
    val joinUrl: String?,
)

/** What the workspace name the person typed led to. */
sealed interface WorkspaceLookupResult {
    data class Found(val workspace: Workspace) : WorkspaceLookupResult

    /** The lookup couldn't be reached, but the input looks like a server name ("artise.co"): sign in to it directly. */
    data class ServerName(val serverName: String) : WorkspaceLookupResult

    /** Not a valid workspace name; no request was made. */
    data object Invalid : WorkspaceLookupResult

    data class NotFound(val slug: String) : WorkspaceLookupResult

    /** The workspace exists but is paused: don't sign in. */
    data class Suspended(val workspace: Workspace) : WorkspaceLookupResult

    data object TooManyTries : WorkspaceLookupResult

    /** No network, or the lookup failed: worth retrying. Never falls back to artise.co. */
    data object Unreachable : WorkspaceLookupResult
}

fun interface WorkspaceLookup {
    /** Looks up what the person typed: a workspace name ("acme"), or a server name ("artise.co") when the lookup can't be reached. */
    suspend fun find(input: String): WorkspaceLookupResult
}
