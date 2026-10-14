/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.loginpassword

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import co.artise.android.workspace.api.SignInWorkspace
import io.element.android.features.login.impl.accountprovider.anAccountProviderManaged
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.accountprovider.AccountProvider
import io.element.android.libraries.matrix.api.core.SessionId

open class LoginPasswordStatePreviewParam : PreviewParameterProvider<LoginPasswordState> {
    override val values: Sequence<LoginPasswordState>
        get() = sequenceOf(
            aLoginPasswordState(),
            // Loading
            aLoginPasswordState(loginAction = AsyncData.Loading()),
            // Error
            aLoginPasswordState(loginAction = AsyncData.Failure(Exception("An error occurred"))),
            // Artise: a workspace without a logo
            aLoginPasswordState(workspace = SignInWorkspace("acme", "Acme", "element.io", logoUrl = null)),
        )
}

fun aLoginPasswordState(
    accountProvider: AccountProvider = anAccountProviderManaged(),
    formState: LoginFormState = LoginFormState.Default,
    loginAction: AsyncData<SessionId> = AsyncData.Uninitialized,
    eventSink: (LoginPasswordEvent) -> Unit = {},
    workspace: SignInWorkspace? = null,
) = LoginPasswordState(
    accountProvider = accountProvider,
    formState = formState,
    loginAction = loginAction,
    eventSink = eventSink,
    workspace = workspace,
)

fun aLoginFormState(
    login: String = "",
    password: String = "",
) = LoginFormState(
    login = login,
    password = password,
)
