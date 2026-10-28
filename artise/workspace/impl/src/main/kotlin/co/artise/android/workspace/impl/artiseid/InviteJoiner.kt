/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.artiseid

/** What joining a workspace from an invite with this phone's Artise ID came to. */
sealed interface InviteJoin {
    /** Joined, or that Artise ID already had an account there: the workspace's "Continue with Artise" finds the account. */
    data object Joined : InviteJoin

    /** No Artise ID on this phone, or it no longer takes the tokens even after a refresh: the join page instead, which signs in to it. */
    data object NoSession : InviteJoin

    /** The workspace doesn't join from the app (no Artise ID there, an older server, a server error): the join page instead. */
    data object PageOnly : InviteJoin

    /** Artise ID hasn't verified the person's email yet. */
    data object EmailUnverified : InviteJoin

    /** The invite was used already, expired or was cancelled. */
    data object InviteInvalid : InviteJoin

    data object TooManyTries : InviteJoin

    data object Unreachable : InviteJoin
}

/** Joins a workspace from an invite for the person signed in to Artise ID on this phone, with no page in between. */
fun interface InviteJoiner {
    /** [joinUrl] is the workspace's join address (lookup `join_url`, e.g. https://chat.artise.co/join), [code] the invite's code. */
    suspend fun join(joinUrl: String, code: String): InviteJoin
}
