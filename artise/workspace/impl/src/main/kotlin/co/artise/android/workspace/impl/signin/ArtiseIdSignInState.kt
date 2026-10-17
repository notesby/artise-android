/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.signin

import androidx.compose.runtime.Immutable
import co.artise.android.workspace.impl.artiseid.ArtiseIdProvider
import co.artise.android.workspace.impl.artiseid.ArtiseIdWorkspace
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

data class ArtiseIdSignInState(
    val screen: ArtiseIdScreen,
    /** Artise ID's page to open in the browser, once. */
    val authorizationUrl: String?,
    /** A workspace to open, once: the workspace step signs in to it as if its name had been typed. */
    val chosen: ArtiseIdWorkspace?,
    val eventSink: (ArtiseIdSignInEvent) -> Unit,
)

@Immutable
sealed interface ArtiseIdScreen {
    /** Reading whether Artise ID is offered and if the person is already signed in to it. */
    data object Checking : ArtiseIdScreen

    /** No Artise ID (its settings are missing, or join.artise.co links don't open the app): sign-in starts with typing the name. */
    data object Unavailable : ArtiseIdScreen

    /** "Artise", with a button per way in. [isBusy] while Artise ID's page is open or the workspaces are being read. */
    data class Providers(
        val providers: ImmutableList<ArtiseIdProvider>,
        val isBusy: Boolean,
        val problem: ArtiseIdProblem?,
    ) : ArtiseIdScreen

    /** [openServerNames]: workspaces already signed in on this phone ("Ya abierto"), which can't be opened a second time. */
    data class Workspaces(
        val workspaces: ImmutableList<ArtiseIdWorkspace>,
        val openServerNames: ImmutableSet<String> = persistentSetOf(),
    ) : ArtiseIdScreen

    data object NoWorkspaces : ArtiseIdScreen
}

enum class ArtiseIdProblem {
    SignInFailed,
    Unreachable,
}

sealed interface ArtiseIdSignInEvent {
    /** Sign in to Artise ID, straight to [provider]'s page, or Artise ID's own page (email) when null. */
    data class SignIn(val provider: ArtiseIdProvider?) : ArtiseIdSignInEvent

    data object AuthorizationOpened : ArtiseIdSignInEvent

    data class Choose(val workspace: ArtiseIdWorkspace) : ArtiseIdSignInEvent

    data object ChosenHandled : ArtiseIdSignInEvent

    /** Back on the screen: if Artise ID's page was closed without an answer, the buttons work again. */
    data object Resumed : ArtiseIdSignInEvent
}
