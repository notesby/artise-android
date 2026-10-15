/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.uri.ensureProtocol
import io.element.android.libraries.designsystem.components.ProgressDialog
import io.element.android.libraries.matrix.api.auth.OAuthDetails
import io.element.android.libraries.permissions.api.localnetwork.LocalNetworkPermissionDialogView

/**
 * Artise: once the workspace step has found the workspace's server ([serverName]), signs in the way that server supports, like Element's
 * account provider screens do: its sign-in page (OAuth, e.g. a workspace running MAS) or the password screen.
 *
 * Shown while the workspace step is on screen, so the sign-in page's answer (or its tab being closed) is handled here; closing the page
 * leaves the person on the workspace step.
 */
@Composable
internal fun WorkspaceLoginMode(
    serverName: String?,
    loginModePresenter: Presenter<LoginModeState>,
    onSubmitServer: () -> Unit,
    onLearnMoreClick: () -> Unit,
    onOAuthDetails: (OAuthDetails) -> Unit,
    onNeedLoginPassword: () -> Unit,
) {
    val state = presentWorkspaceLoginMode(serverName, loginModePresenter, onSubmitServer)
    if (state.loginMode is AsyncData.Loading) {
        ProgressDialog()
    }
    LoginModeView(
        loginMode = state.loginMode,
        onClearError = { state.eventSink(LoginModeEvent.ClearError) },
        onLearnMoreClick = onLearnMoreClick,
        onOAuthDetails = onOAuthDetails,
        onNeedLoginPassword = onNeedLoginPassword,
    )
    LocalNetworkPermissionDialogView(
        dialog = state.localNetworkPermissionDialog,
        onSubmit = { state.eventSink(LoginModeEvent.RequestLocalNetworkPermission) },
        onDismiss = { state.eventSink(LoginModeEvent.DismissLocalNetworkPermission) },
    )
}

/** Submits [serverName] once it's set, then calls [onSubmitServer] so the same server isn't submitted again. */
@Composable
internal fun presentWorkspaceLoginMode(
    serverName: String?,
    loginModePresenter: Presenter<LoginModeState>,
    onSubmitServer: () -> Unit,
): LoginModeState {
    val state = loginModePresenter.present()
    val latestOnSubmitServer by rememberUpdatedState(onSubmitServer)
    LaunchedEffect(serverName) {
        if (serverName != null) {
            state.eventSink(
                LoginModeEvent.Submit(
                    isAccountCreation = false,
                    homeserverUrl = serverName.ensureProtocol(),
                    resolvedHomeserverUrl = null,
                    loginHint = null,
                )
            )
            latestOnSubmitServer()
        }
    }
    return state
}
