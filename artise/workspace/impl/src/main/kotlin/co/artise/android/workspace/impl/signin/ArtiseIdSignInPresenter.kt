/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.signin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import co.artise.android.workspace.api.WorkspaceSignInEntryPoint
import co.artise.android.workspace.api.serverName
import co.artise.android.workspace.impl.artiseid.ArtiseIdAuth
import co.artise.android.workspace.impl.artiseid.ArtiseIdConfig
import co.artise.android.workspace.impl.artiseid.ArtiseIdConfigClient
import co.artise.android.workspace.impl.artiseid.ArtiseIdProvider
import co.artise.android.workspace.impl.artiseid.ArtiseIdSignInStatus
import co.artise.android.workspace.impl.artiseid.ArtiseIdWorkspace
import co.artise.android.workspace.impl.artiseid.ArtiseIdWorkspaces
import co.artise.android.workspace.impl.artiseid.JoinLinkCheck
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Signing in once with Artise ID, then picking a workspace from the person's list: one workspace opens straight away, several are listed,
 * none shows how to get an invite. Not offered with an invite or a prefilled workspace, or when Artise ID isn't available: then the
 * workspace step starts with typing the name, as before.
 */
@AssistedInject
class ArtiseIdSignInPresenter(
    @Assisted private val params: WorkspaceSignInEntryPoint.Params,
    private val configClient: ArtiseIdConfigClient,
    private val auth: ArtiseIdAuth,
    private val joinLinkCheck: JoinLinkCheck,
    private val sessionStore: SessionStore,
) : Presenter<ArtiseIdSignInState> {
    @AssistedFactory
    fun interface Factory {
        fun create(params: WorkspaceSignInEntryPoint.Params): ArtiseIdSignInPresenter
    }

    @Composable
    override fun present(): ArtiseIdSignInState {
        val scope = rememberCoroutineScope()
        var config by remember { mutableStateOf<ArtiseIdConfig?>(null) }
        var screen by remember { mutableStateOf<ArtiseIdScreen>(ArtiseIdScreen.Checking) }
        var authorizationUrl by remember { mutableStateOf<String?>(null) }
        var chosen by remember { mutableStateOf<ArtiseIdWorkspace?>(null) }

        fun providers(isBusy: Boolean, problem: ArtiseIdProblem? = null): ArtiseIdScreen {
            val offered = config?.providers?.keys.orEmpty()
            return ArtiseIdScreen.Providers(ArtiseIdProvider.entries.filter { it in offered }.toImmutableList(), isBusy, problem)
        }

        // Right after signing in, a single workspace opens directly; with tokens from before (adding an account), it's listed.
        suspend fun showWorkspaces(openSingle: Boolean) {
            screen = providers(isBusy = true)
            screen = when (val result = auth.workspaces()) {
                is ArtiseIdWorkspaces.Found -> when {
                    result.workspaces.isEmpty() -> ArtiseIdScreen.NoWorkspaces
                    else -> {
                        val open = sessionStore.getAllSessions().map { SessionId(it.userId).serverName() }.toImmutableSet()
                        val single = result.workspaces.singleOrNull()
                        if (openSingle && single != null && single.serverName !in open) chosen = single
                        ArtiseIdScreen.Workspaces(result.workspaces.toImmutableList(), open)
                    }
                }
                ArtiseIdWorkspaces.SignedOut -> providers(isBusy = false, problem = ArtiseIdProblem.SignInFailed.takeIf { openSingle })
                ArtiseIdWorkspaces.Unreachable -> providers(isBusy = false, problem = ArtiseIdProblem.Unreachable)
            }
        }

        LaunchedEffect(Unit) {
            if (params.invite != null || params.workspace != null) {
                screen = ArtiseIdScreen.Unavailable
                return@LaunchedEffect
            }
            // Offline or slow: today's screen rather than a long wait.
            val found = withTimeoutOrNull(CONFIG_TIMEOUT) { configClient.get() }?.takeIf { joinLinkCheck.opensJoinLinks() }
            config = found
            when {
                found == null -> screen = ArtiseIdScreen.Unavailable
                auth.hasTokens() -> showWorkspaces(openSingle = false)
                else -> screen = providers(isBusy = auth.status.value != ArtiseIdSignInStatus.Idle)
            }
        }

        LaunchedEffect(Unit) {
            auth.status.collect { status ->
                if (config == null) return@collect
                when (status) {
                    ArtiseIdSignInStatus.Idle -> if (screen is ArtiseIdScreen.Providers) screen = providers(isBusy = false)
                    ArtiseIdSignInStatus.InBrowser, ArtiseIdSignInStatus.Finishing -> screen = providers(isBusy = true)
                    ArtiseIdSignInStatus.SignedIn -> {
                        auth.acknowledge()
                        showWorkspaces(openSingle = true)
                    }
                    ArtiseIdSignInStatus.Failed -> {
                        auth.acknowledge()
                        screen = providers(isBusy = false, problem = ArtiseIdProblem.SignInFailed)
                    }
                }
            }
        }

        return ArtiseIdSignInState(
            screen = screen,
            authorizationUrl = authorizationUrl,
            chosen = chosen,
            eventSink = { event ->
                when (event) {
                    is ArtiseIdSignInEvent.SignIn -> {
                        val current = config ?: return@ArtiseIdSignInState
                        screen = providers(isBusy = true)
                        scope.launch {
                            val url = auth.authorizationUrl(current, event.provider)
                            if (url == null) screen = providers(isBusy = false, problem = ArtiseIdProblem.Unreachable) else authorizationUrl = url
                        }
                    }
                    ArtiseIdSignInEvent.AuthorizationOpened -> authorizationUrl = null
                    is ArtiseIdSignInEvent.Choose -> chosen = event.workspace
                    ArtiseIdSignInEvent.ChosenHandled -> chosen = null
                    ArtiseIdSignInEvent.Resumed -> if (auth.status.value == ArtiseIdSignInStatus.InBrowser) {
                        // The return (an App Link) arrives before the screen resumes; still waiting a moment later means the page was closed.
                        scope.launch {
                            delay(CLOSED_PAGE_DELAY)
                            auth.cancel()
                        }
                    }
                }
            },
        )
    }

    private companion object {
        val CLOSED_PAGE_DELAY = 3.seconds
        val CONFIG_TIMEOUT = 5.seconds
    }
}
