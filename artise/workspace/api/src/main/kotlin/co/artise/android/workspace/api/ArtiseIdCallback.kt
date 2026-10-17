/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.api

/**
 * The return of an Artise ID sign-in (https://join.artise.co/oauth?code=…&state=…). A workspace's sign-in service returns to the same
 * address, so the two are told apart by the `state` the app sent.
 */
interface ArtiseIdCallback {
    /** Whether [url] answers the Artise ID sign-in in progress (its redirect, with that sign-in's state). */
    fun handles(url: String): Boolean

    /** Finishes the Artise ID sign-in with [url]; the sign-in screen shows the outcome. */
    fun onCallback(url: String)
}
