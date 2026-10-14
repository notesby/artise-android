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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import co.artise.android.workspace.api.SignInWorkspace
import co.artise.android.workspace.api.SignInWorkspaceHolder
import co.artise.android.workspace.api.WorkspaceConfigRepository
import co.artise.android.workspace.api.WorkspaceLookup
import co.artise.android.workspace.api.WorkspaceLookupResult
import co.artise.android.workspace.api.WorkspaceSignInEntryPoint
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * "Your workspace": the name is looked up, then its server is set on the authentication service, as Element's own server screen does.
 * The workspace's name and logo are read at the same time, but only for a few seconds: branding never holds up signing in.
 */
@AssistedInject
class WorkspaceSignInPresenter(
    @Assisted private val params: WorkspaceSignInEntryPoint.Params,
    private val lookup: WorkspaceLookup,
    private val configRepository: WorkspaceConfigRepository,
    private val authenticationService: MatrixAuthenticationService,
    private val holder: SignInWorkspaceHolder,
) : Presenter<WorkspaceSignInState> {
    @AssistedFactory
    fun interface Factory {
        fun create(params: WorkspaceSignInEntryPoint.Params): WorkspaceSignInPresenter
    }

    @Composable
    override fun present(): WorkspaceSignInState {
        val scope = rememberCoroutineScope()
        var input by rememberSaveable { mutableStateOf(params.workspace ?: holder.lastTyped().orEmpty()) }
        var isLoading by remember { mutableStateOf(false) }
        var problem by remember { mutableStateOf<WorkspaceProblem?>(null) }
        var readyServerName by remember { mutableStateOf<String?>(null) }

        fun submit() {
            if (isLoading) return
            isLoading = true
            problem = null
            scope.launch {
                when (val result = resolve(input)) {
                    is Resolved.Ready -> {
                        holder.choose(result.workspace)
                        readyServerName = result.workspace.serverName
                    }
                    is Resolved.Problem -> problem = result.problem
                }
                isLoading = false
            }
        }

        LaunchedEffect(Unit) {
            if (params.continueRightAway && input.isNotBlank()) submit()
        }

        return WorkspaceSignInState(
            input = input,
            isLoading = isLoading,
            problem = problem,
            readyServerName = readyServerName,
            eventSink = { event ->
                when (event) {
                    is WorkspaceSignInEvent.InputChanged -> {
                        input = event.input
                        problem = null
                    }
                    WorkspaceSignInEvent.Continue -> submit()
                    WorkspaceSignInEvent.ReadyHandled -> readyServerName = null
                }
            },
        )
    }

    private sealed interface Resolved {
        data class Ready(val workspace: SignInWorkspace) : Resolved

        data class Problem(val problem: WorkspaceProblem) : Resolved
    }

    private suspend fun resolve(input: String): Resolved {
        val typed = input.trim().lowercase()
        val (serverName, lookupName) = when (val found = lookup.find(input)) {
            is WorkspaceLookupResult.Found -> found.workspace.serverName to found.workspace.name
            is WorkspaceLookupResult.ServerName -> found.serverName to null
            WorkspaceLookupResult.Invalid -> return Resolved.Problem(WorkspaceProblem.Invalid)
            is WorkspaceLookupResult.NotFound -> return Resolved.Problem(WorkspaceProblem.NotFound(typed))
            is WorkspaceLookupResult.Suspended -> return Resolved.Problem(WorkspaceProblem.Paused)
            WorkspaceLookupResult.TooManyTries -> return Resolved.Problem(WorkspaceProblem.TooManyTries)
            WorkspaceLookupResult.Unreachable -> return Resolved.Problem(WorkspaceProblem.Unreachable)
        }
        return coroutineScope {
            val config = async { withTimeoutOrNull(BRANDING_TIMEOUT) { configRepository.fetch(serverName) } }
            val details = authenticationService.setHomeserver(serverName)
            val name = config.await()?.name ?: lookupName ?: serverName
            details.fold(
                onSuccess = {
                    if (it.supportsPasswordLogin) {
                        Resolved.Ready(SignInWorkspace(typed = typed, name = name, serverName = serverName, logoUrl = config.await()?.logoUrl))
                    } else {
                        Resolved.Problem(WorkspaceProblem.NoPasswordSignIn(name))
                    }
                },
                onFailure = { Resolved.Problem(WorkspaceProblem.ServerUnreachable(name)) },
            )
        }
    }

    private companion object {
        val BRANDING_TIMEOUT = 4.seconds
    }
}
