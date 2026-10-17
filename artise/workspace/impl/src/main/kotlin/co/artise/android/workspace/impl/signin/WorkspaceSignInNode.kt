/*
 * Copyright 2026 Artise.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package co.artise.android.workspace.impl.signin

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import co.artise.android.workspace.api.WorkspaceSignInEntryPoint
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.androidutils.browser.openUrlInChromeCustomTab
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.inputs

@ContributesNode(AppScope::class)
@AssistedInject
class WorkspaceSignInNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: WorkspaceSignInPresenter.Factory,
    artiseIdPresenterFactory: ArtiseIdSignInPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    private val callback: WorkspaceSignInEntryPoint.Callback = callback()
    private val presenter = presenterFactory.create(inputs<WorkspaceSignInEntryPoint.Params>())
    private val artiseIdPresenter = artiseIdPresenterFactory.create(inputs<WorkspaceSignInEntryPoint.Params>())

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        val artiseId = artiseIdPresenter.present()
        val activity = LocalActivity.current
        val isDark = !ElementTheme.isLightTheme
        // Artise ID first when it's offered; "Type your workspace" (and back) switch to and from today's screen.
        var typing by rememberSaveable { mutableStateOf(false) }
        val artiseIdOffered = artiseId.screen != ArtiseIdScreen.Unavailable
        BackHandler(enabled = typing && artiseIdOffered) { typing = false }
        // Artise ID's page, in the same browser as the workspace's sign-in page, so its session carries over.
        LaunchedEffect(artiseId.authorizationUrl) {
            val url = artiseId.authorizationUrl ?: return@LaunchedEffect
            activity?.openUrlInChromeCustomTab(null, darkTheme = isDark, url = url)
            artiseId.eventSink(ArtiseIdSignInEvent.AuthorizationOpened)
        }
        LaunchedEffect(artiseId.chosen) {
            val workspace = artiseId.chosen ?: return@LaunchedEffect
            artiseId.eventSink(ArtiseIdSignInEvent.ChosenHandled)
            state.eventSink(WorkspaceSignInEvent.Open(slug = workspace.slug, name = workspace.name, serverName = workspace.serverName))
        }
        // An invite's join page, in the browser; coming back (resume) lets sign-in go on.
        LaunchedEffect(state.joinPageUrl) {
            val url = state.joinPageUrl ?: return@LaunchedEffect
            activity?.openUrlInChromeCustomTab(null, darkTheme = isDark, url = url)
            state.eventSink(WorkspaceSignInEvent.JoinPageOpened)
        }
        LifecycleResumeEffect(Unit) {
            state.eventSink(WorkspaceSignInEvent.Resumed)
            artiseId.eventSink(ArtiseIdSignInEvent.Resumed)
            onPauseOrDispose {}
        }
        LaunchedEffect(state.readyServerName) {
            state.readyServerName?.let {
                state.eventSink(WorkspaceSignInEvent.ReadyHandled)
                callback.onWorkspaceReady(it)
            }
        }
        if (artiseIdOffered && !typing) {
            ArtiseIdSignInView(
                state = artiseId,
                isOpening = state.isLoading,
                openingProblem = state.problem?.let { problemText(it) },
                onTypeWorkspaceClick = { typing = true },
                onBackClick = ::navigateUp,
                modifier = modifier,
            )
        } else {
            WorkspaceSignInView(state = state, onBackClick = { if (typing && artiseIdOffered) typing = false else navigateUp() }, modifier = modifier)
        }
    }
}
