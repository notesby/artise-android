/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.signin

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import co.artise.android.workspace.impl.artiseid.ArtiseIdProvider
import co.artise.android.workspace.impl.artiseid.ArtiseIdWorkspace
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

open class ArtiseIdSignInStatePreviewParam : PreviewParameterProvider<ArtiseIdSignInState> {
    override val values: Sequence<ArtiseIdSignInState>
        get() = sequenceOf(
            anArtiseIdSignInState(),
            anArtiseIdSignInState(ArtiseIdScreen.Providers(ArtiseIdProvider.entries.toImmutableList(), isBusy = true, problem = null)),
            anArtiseIdSignInState(ArtiseIdScreen.Providers(ArtiseIdProvider.entries.toImmutableList(), isBusy = false, problem = ArtiseIdProblem.SignInFailed)),
            anArtiseIdSignInState(
                ArtiseIdScreen.Workspaces(
                    persistentListOf(
                        ArtiseIdWorkspace("acme", "Acme", "acme.artise.co", "https://matrix.acme.artise.co"),
                        ArtiseIdWorkspace("familia", "Familia Pérez", "familia.artise.co", "https://matrix.familia.artise.co"),
                    )
                )
            ),
            anArtiseIdSignInState(ArtiseIdScreen.NoWorkspaces),
            anArtiseIdSignInState(ArtiseIdScreen.Checking),
        )
}

internal fun anArtiseIdSignInState(
    screen: ArtiseIdScreen = ArtiseIdScreen.Providers(ArtiseIdProvider.entries.toImmutableList(), isBusy = false, problem = null),
) = ArtiseIdSignInState(
    screen = screen,
    authorizationUrl = null,
    chosen = null,
    eventSink = {},
)
