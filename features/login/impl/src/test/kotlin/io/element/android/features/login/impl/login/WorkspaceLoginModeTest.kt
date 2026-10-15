/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.login.impl.login

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.auth.OAuthDetails
import io.element.android.tests.testutils.EnsureNeverCalled
import io.element.android.tests.testutils.EnsureNeverCalledWithParam
import io.element.android.tests.testutils.EventsRecorder
import io.element.android.tests.testutils.ensureCalledOnce
import io.element.android.tests.testutils.ensureCalledOnceWithParam
import io.element.android.tests.testutils.robolectric.RobolectricTestParameter
import org.junit.Test

class WorkspaceLoginModeTest : RobolectricTestParameter() {
    /** Once the workspace step has found the server, it is submitted once over https, so the server decides between OAuth and password. */
    @Test
    fun `the workspace's server is submitted once`() = runAndroidComposeUiTest {
        val eventSink = EventsRecorder<LoginModeEvent>()
        ensureCalledOnce { onSubmitServer ->
            setWorkspaceLoginMode(
                serverName = "mas-k8s.artise.co",
                loginModeState = aLoginModeState(eventSink = eventSink),
                onSubmitServer = onSubmitServer,
            )
        }
        eventSink.assertSingle(
            LoginModeEvent.Submit(
                isAccountCreation = false,
                homeserverUrl = "https://mas-k8s.artise.co",
                resolvedHomeserverUrl = null,
                loginHint = null,
            )
        )
    }

    /** Before the workspace step has found a server, nothing is submitted. */
    @Test
    fun `nothing is submitted without a server`() = runAndroidComposeUiTest {
        val eventSink = EventsRecorder<LoginModeEvent>(expectEvents = false)
        setWorkspaceLoginMode(serverName = null, loginModeState = aLoginModeState(eventSink = eventSink))
    }

    /** A server with OAuth (e.g. MAS) opens its sign-in page. */
    @Test
    fun `a server with OAuth opens its sign-in page`() = runAndroidComposeUiTest {
        val eventSink = EventsRecorder<LoginModeEvent>()
        val oAuthDetails = OAuthDetails("https://auth.mas-k8s.artise.co/authorize")
        ensureCalledOnceWithParam(oAuthDetails) { onOAuthDetails ->
            setWorkspaceLoginMode(
                serverName = null,
                loginModeState = aLoginModeState(loginMode = AsyncData.Success(LoginMode.OAuth(oAuthDetails)), eventSink = eventSink),
                onOAuthDetails = onOAuthDetails,
            )
        }
        eventSink.assertSingle(LoginModeEvent.ClearError)
    }

    /** A server with passwords only goes to the password screen. */
    @Test
    fun `a password server goes to the password screen`() = runAndroidComposeUiTest {
        val eventSink = EventsRecorder<LoginModeEvent>()
        ensureCalledOnce { onNeedLoginPassword ->
            setWorkspaceLoginMode(
                serverName = null,
                loginModeState = aLoginModeState(loginMode = AsyncData.Success(LoginMode.PasswordLogin), eventSink = eventSink),
                onNeedLoginPassword = onNeedLoginPassword,
            )
        }
        eventSink.assertSingle(LoginModeEvent.ClearError)
    }

    private fun AndroidComposeUiTest<ComponentActivity>.setWorkspaceLoginMode(
        serverName: String?,
        loginModeState: LoginModeState,
        onSubmitServer: () -> Unit = {},
        onOAuthDetails: (OAuthDetails) -> Unit = EnsureNeverCalledWithParam(),
        onNeedLoginPassword: () -> Unit = EnsureNeverCalled(),
    ) {
        setContent {
            WorkspaceLoginMode(
                serverName = serverName,
                loginModePresenter = Presenter { loginModeState },
                onSubmitServer = onSubmitServer,
                onLearnMoreClick = EnsureNeverCalled(),
                onOAuthDetails = onOAuthDetails,
                onNeedLoginPassword = onNeedLoginPassword,
            )
        }
    }
}
