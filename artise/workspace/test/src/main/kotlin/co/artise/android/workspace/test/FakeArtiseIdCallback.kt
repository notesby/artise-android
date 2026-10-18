/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.test

import co.artise.android.workspace.api.ArtiseIdCallback

class FakeArtiseIdCallback(
    private val handles: (String) -> Boolean = { false },
) : ArtiseIdCallback {
    val received = mutableListOf<String>()

    override fun handles(url: String): Boolean = handles.invoke(url)

    override fun onCallback(url: String) {
        received += url
    }
}
